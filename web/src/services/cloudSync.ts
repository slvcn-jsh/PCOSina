import { doc, getDoc, setDoc } from "firebase/firestore";
import type { GeneratePlanResponse, PantryEntry, SyncSummary, UserProfile } from "../types";
import {
  createPantryId,
  getActivePlanSnapshot,
  getDomainTimestamps,
  getProfile,
  listPantry,
  listPendingOperations,
  markOperationsSynced,
  replacePantry,
  savePlanSnapshot,
  saveProfile
} from "../storage/db";
import { GUEST_UID } from "../domain/profile";
import { currentFirebaseUser, getFirebaseServices } from "./firebase";

const PROFILE_COLLECTION = "profiles";
const TIMESTAMP_SKEW_MS = 1000;

const CloudKeys = {
  profileUpdatedAt: "updatedAtEpochMs",
  artifactsUpdatedAt: "artifactsUpdatedAtEpochMs",
  pantryUpdatedAt: "pantryUpdatedAtEpochMs",
  planUpdatedAt: "planUpdatedAtEpochMs",
  groceryUpdatedAt: "groceryUpdatedAtEpochMs",
  pantryEntriesJson: "pantryEntriesJson",
  lastPlanJson: "lastPlanJson",
  lastPlanTimestamp: "lastPlanTimestamp",
  activePlanId: "activePlanId",
  groceryJson: "groceryJson",
  grocerySourcesJson: "grocerySourcesJson",
  grocerySnapshotsJson: "grocerySnapshotsJson"
} as const;

export type MergeDirection = "pull-remote" | "push-local" | "no-change";

export function chooseMergeDirection(input: {
  localUpdatedAtMs?: number;
  remoteUpdatedAtMs?: number;
  localHasData: boolean;
  remoteHasData: boolean;
  skewMs?: number;
}): MergeDirection {
  const skewMs = input.skewMs ?? TIMESTAMP_SKEW_MS;
  const local = input.localUpdatedAtMs ?? 0;
  const remote = input.remoteUpdatedAtMs ?? 0;

  if (input.remoteHasData && (!input.localHasData || remote > local + skewMs)) return "pull-remote";
  if (input.localHasData && (!input.remoteHasData || local > remote + skewMs)) return "push-local";
  if (input.remoteHasData && input.localHasData && local === 0) return "pull-remote";
  return "no-change";
}

export async function syncWithCloud(uid: string): Promise<SyncSummary> {
  if (!uid || uid === GUEST_UID) {
    return { status: "skipped", message: "Guest data is stored locally on this device." };
  }
  const services = getFirebaseServices();
  const user = currentFirebaseUser();
  if (!services || !user || user.uid !== uid) {
    return { status: "skipped", message: "Firebase sign-in is not active for this profile." };
  }
  if (typeof navigator !== "undefined" && !navigator.onLine) {
    return { status: "skipped", message: "Sync is queued until this browser is online." };
  }

  try {
    const ref = doc(services.firestore, PROFILE_COLLECTION, uid);
    const snapshot = await getDoc(ref);
    const remoteData = snapshot.exists() ? snapshot.data() : {};
    let restored = false;
    let uploaded = false;

    const profileResult = await syncProfileDomain(uid, ref, remoteData);
    restored ||= profileResult === "pull-remote";
    uploaded ||= profileResult === "push-local";

    const freshSnapshot = await getDoc(ref);
    const artifactData = freshSnapshot.exists() ? freshSnapshot.data() : remoteData;
    const artifactResult = await syncArtifactDomains(uid, ref, artifactData);
    restored ||= artifactResult.restored;
    uploaded ||= artifactResult.uploaded;

    const pending = await listPendingOperations(uid);
    if (uploaded || restored || pending.length > 0) {
      await markOperationsSynced(pending.map((operation) => operation.id));
    }

    if (restored) return { status: "restored", message: "Cloud artifacts were restored to this browser." };
    if (uploaded) return { status: "uploaded", message: "Local artifacts were uploaded for same-account restore." };
    return { status: "synced", message: "Local and cloud artifacts are already aligned." };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Cloud sync failed.";
    return { status: "failed", message };
  }
}

async function syncProfileDomain(
  uid: string,
  ref: ReturnType<typeof doc>,
  remoteData: Record<string, unknown>
): Promise<MergeDirection> {
  const local = await getProfile(uid);
  const remote = profileFromCloud(remoteData);
  const direction = chooseMergeDirection({
    localUpdatedAtMs: local.updatedAtMs,
    remoteUpdatedAtMs: asNumber(remoteData[CloudKeys.profileUpdatedAt]),
    localHasData: hasProfileData(local),
    remoteHasData: hasProfileData(remote)
  });

  if (direction === "pull-remote") {
    await saveProfile(uid, remote, {
      queue: false,
      updatedAtMs: asNumber(remoteData[CloudKeys.profileUpdatedAt]) || Date.now()
    });
  } else if (direction === "push-local") {
    await setDoc(ref, profileToCloudPayload(local), { merge: true });
  }

  return direction;
}

async function syncArtifactDomains(
  uid: string,
  ref: ReturnType<typeof doc>,
  remoteData: Record<string, unknown>
): Promise<{ restored: boolean; uploaded: boolean }> {
  const timestamps = await getDomainTimestamps(uid);
  let restored = false;
  let uploaded = false;

  const pantryDirection = chooseMergeDirection({
    localUpdatedAtMs: timestamps.pantryUpdatedAtMs,
    remoteUpdatedAtMs: remoteDomainTimestamp(remoteData, CloudKeys.pantryUpdatedAt, CloudKeys.pantryEntriesJson),
    localHasData: (await listPantry(uid)).length > 0,
    remoteHasData: typeof remoteData[CloudKeys.pantryEntriesJson] === "string"
  });
  if (pantryDirection === "pull-remote") {
    await replacePantry(uid, pantryFromCloud(uid, String(remoteData[CloudKeys.pantryEntriesJson] || "[]")), {
      queue: false,
      updatedAtMs: remoteDomainTimestamp(remoteData, CloudKeys.pantryUpdatedAt, CloudKeys.pantryEntriesJson) || Date.now()
    });
    restored = true;
  } else if (pantryDirection === "push-local") {
    await setDoc(ref, await pantryPayload(uid), { merge: true });
    uploaded = true;
  }

  const planDirection = chooseMergeDirection({
    localUpdatedAtMs: timestamps.planUpdatedAtMs,
    remoteUpdatedAtMs: remoteDomainTimestamp(remoteData, CloudKeys.planUpdatedAt, CloudKeys.lastPlanJson),
    localHasData: Boolean(await getActivePlanSnapshot(uid)),
    remoteHasData: typeof remoteData[CloudKeys.lastPlanJson] === "string" && String(remoteData[CloudKeys.lastPlanJson]).trim().length > 0
  });
  if (planDirection === "pull-remote") {
    const response = parsePlanResponse(remoteData[CloudKeys.lastPlanJson]);
    if (response) {
      await savePlanSnapshot(uid, response, "cloud", {
        queue: false,
        generatedAtMs: asNumber(remoteData[CloudKeys.lastPlanTimestamp]) || response.timestamps?.completedAtMs || Date.now(),
        active: true
      });
      restored = true;
    }
  } else if (planDirection === "push-local") {
    await setDoc(ref, await planPayload(uid), { merge: true });
    uploaded = true;
  }

  return { restored, uploaded };
}

function profileToCloudPayload(profile: UserProfile): Record<string, unknown> {
  return {
    displayName: profile.displayName,
    age: profile.age,
    heightCm: profile.heightCm,
    weightKg: profile.weightKg,
    targetWeightKg: profile.targetWeightKg ?? null,
    targetDate: profile.targetDate ?? null,
    weeklyWeightChangeGoalKg: profile.weeklyWeightChangeGoalKg ?? null,
    heightUnit: profile.heightUnit,
    weightUnit: profile.weightUnit,
    activityLevel: profile.activityLevel,
    goal: profile.goal,
    symptoms: profile.symptoms,
    comorbidities: profile.comorbidities,
    dietaryRestrictions: profile.dietaryRestrictions,
    allergies: profile.allergies,
    excludedIngredients: profile.excludedIngredients,
    weeklyBudgetPhp: profile.weeklyBudgetPhp,
    maxCookingTimeMinutes: profile.maxCookingTimeMinutes,
    varietyPreference: profile.varietyPreference,
    planningPriority: profile.planningPriority,
    preferredMarketType: profile.preferredMarketType,
    pantryItems: profile.pantryItems,
    isProfileCompleted: profile.isProfileCompleted,
    [CloudKeys.profileUpdatedAt]: profile.updatedAtMs
  };
}

function profileFromCloud(data: Record<string, unknown>): UserProfile {
  return {
    displayName: asString(data.displayName),
    age: asNumber(data.age) || 0,
    heightCm: asNumber(data.heightCm) || 0,
    weightKg: asNumber(data.weightKg) || 0,
    targetWeightKg: asNumber(data.targetWeightKg),
    targetDate: asOptionalString(data.targetDate),
    weeklyWeightChangeGoalKg: asNumber(data.weeklyWeightChangeGoalKg),
    heightUnit: "cm",
    weightUnit: "kg",
    activityLevel: asString(data.activityLevel) || "Lightly Active",
    goal: asString(data.goal) || "General Health",
    symptoms: asStringList(data.symptoms),
    comorbidities: asStringList(data.comorbidities),
    dietaryRestrictions: asStringList(data.dietaryRestrictions),
    allergies: asStringList(data.allergies),
    excludedIngredients: asStringList(data.excludedIngredients),
    weeklyBudgetPhp: asNumber(data.weeklyBudgetPhp) || 0,
    maxCookingTimeMinutes: asNumber(data.maxCookingTimeMinutes) || 45,
    varietyPreference: asString(data.varietyPreference) || "Balanced",
    planningPriority: asString(data.planningPriority) || "Balanced",
    preferredMarketType: asString(data.preferredMarketType) || "Wet Market",
    pantryItems: asStringList(data.pantryItems),
    isProfileCompleted: Boolean(data.isProfileCompleted),
    updatedAtMs: asNumber(data[CloudKeys.profileUpdatedAt]) || Date.now()
  };
}

async function pantryPayload(uid: string): Promise<Record<string, unknown>> {
  const entries = await listPantry(uid);
  const updatedAtMs = Date.now();
  return {
    [CloudKeys.pantryUpdatedAt]: updatedAtMs,
    [CloudKeys.artifactsUpdatedAt]: updatedAtMs,
    [CloudKeys.pantryEntriesJson]: JSON.stringify(
      entries.map(({ id: _id, updatedAtMs: _updatedAtMs, ...entry }) => entry)
    )
  };
}

async function planPayload(uid: string): Promise<Record<string, unknown>> {
  const active = await getActivePlanSnapshot(uid);
  const updatedAtMs = Date.now();
  const groceryItems = active?.response.groceryOutput?.items ?? [];
  return {
    [CloudKeys.planUpdatedAt]: updatedAtMs,
    [CloudKeys.groceryUpdatedAt]: updatedAtMs,
    [CloudKeys.artifactsUpdatedAt]: updatedAtMs,
    [CloudKeys.lastPlanJson]: active ? JSON.stringify(active.response) : "",
    [CloudKeys.lastPlanTimestamp]: active?.generatedAtMs ?? 0,
    [CloudKeys.activePlanId]: active?.planId ?? "",
    [CloudKeys.groceryJson]: JSON.stringify(groceryItems),
    [CloudKeys.grocerySourcesJson]: "",
    [CloudKeys.grocerySnapshotsJson]: ""
  };
}

function pantryFromCloud(uid: string, rawJson: string): PantryEntry[] {
  try {
    const raw = JSON.parse(rawJson) as Array<Partial<PantryEntry>>;
    if (!Array.isArray(raw)) return [];
    return raw
      .map((entry): PantryEntry | null => {
        const name = asString(entry.name);
        if (!name) return null;
        return {
          id: createPantryId(uid, name),
          name,
          quantity: asOptionalString(entry.quantity),
          expiryDate: asOptionalString(entry.expiryDate),
          amount: asNumber(entry.amount),
          unit: asOptionalString(entry.unit),
          updatedAtMs: Date.now()
        };
      })
      .filter((entry): entry is PantryEntry => entry !== null);
  } catch {
    return [];
  }
}

function parsePlanResponse(raw: unknown): GeneratePlanResponse | null {
  if (typeof raw !== "string" || !raw.trim()) return null;
  try {
    const parsed = JSON.parse(raw) as GeneratePlanResponse;
    if (!parsed || !Array.isArray(parsed.days) || !parsed.status) return null;
    return parsed;
  } catch {
    return null;
  }
}

function remoteDomainTimestamp(
  data: Record<string, unknown>,
  timestampKey: string,
  presenceKey: string
): number {
  return asNumber(data[timestampKey]) || (data[presenceKey] !== undefined ? asNumber(data[CloudKeys.artifactsUpdatedAt]) || 0 : 0);
}

function hasProfileData(profile: UserProfile): boolean {
  return Boolean(
    profile.displayName ||
      profile.age > 0 ||
      profile.heightCm > 0 ||
      profile.weightKg > 0 ||
      profile.goal ||
      profile.symptoms.length ||
      profile.dietaryRestrictions.length ||
      profile.allergies.length ||
      profile.excludedIngredients.length ||
      profile.isProfileCompleted
  );
}

function asString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function asOptionalString(value: unknown): string | undefined {
  const text = asString(value);
  return text || undefined;
}

function asNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item).trim()).filter(Boolean);
}
