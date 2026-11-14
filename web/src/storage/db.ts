import { deleteDB, openDB, type DBSchema, type IDBPDatabase } from "idb";
import type {
  AppSession,
  DomainTimestamps,
  GeneratePlanResponse,
  OfflineOperationType,
  OfflineQueueOperation,
  PantryEntry,
  PlanSnapshot,
  RecipeDetail,
  UserProfile
} from "../types";
import { defaultProfile, GUEST_UID } from "../domain/profile";

const DB_NAME = "pcosina-web";
const DB_VERSION = 1;

interface PcosinaWebDb extends DBSchema {
  meta: {
    key: string;
    value: unknown;
  };
  profiles: {
    key: string;
    value: UserProfile & { uid: string };
  };
  pantry: {
    key: string;
    value: PantryEntry & { uid: string };
    indexes: { uid: string };
  };
  recipes: {
    key: string;
    value: RecipeDetail & { cachedAtMs: number };
  };
  plans: {
    key: string;
    value: PlanSnapshot;
    indexes: { uid: string; active: string };
  };
  queue: {
    key: string;
    value: OfflineQueueOperation;
    indexes: { uid: string; status: string };
  };
}

let dbPromise: Promise<IDBPDatabase<PcosinaWebDb>> | null = null;

function getDb(): Promise<IDBPDatabase<PcosinaWebDb>> {
  if (!dbPromise) {
    dbPromise = openDB<PcosinaWebDb>(DB_NAME, DB_VERSION, {
      upgrade(db) {
        db.createObjectStore("meta");
        db.createObjectStore("profiles");
        const pantry = db.createObjectStore("pantry", { keyPath: "id" });
        pantry.createIndex("uid", "uid");
        db.createObjectStore("recipes", { keyPath: "id" });
        const plans = db.createObjectStore("plans", { keyPath: "planId" });
        plans.createIndex("uid", "uid");
        plans.createIndex("active", "active");
        const queue = db.createObjectStore("queue", { keyPath: "id" });
        queue.createIndex("uid", "uid");
        queue.createIndex("status", "status");
      }
    });
  }
  return dbPromise;
}

export async function resetDatabaseForTests(): Promise<void> {
  if (dbPromise) {
    const db = await dbPromise;
    db.close();
    dbPromise = null;
  }
  await deleteDB(DB_NAME);
}

export async function getSession(): Promise<AppSession | null> {
  const db = await getDb();
  return ((await db.get("meta", "session")) as AppSession | undefined) ?? null;
}

export async function saveSession(session: AppSession): Promise<void> {
  const db = await getDb();
  await db.put("meta", session, "session");
}

export async function clearSession(): Promise<void> {
  const db = await getDb();
  await db.delete("meta", "session");
}

export async function getActiveUid(): Promise<string> {
  const session = await getSession();
  return session?.uid || GUEST_UID;
}

export async function getProfile(uid: string): Promise<UserProfile> {
  const db = await getDb();
  const stored = await db.get("profiles", uid);
  if (!stored) return defaultProfile();
  const { uid: _uid, ...profile } = stored;
  return profile;
}

export async function saveProfile(
  uid: string,
  profile: UserProfile,
  options: { queue?: boolean; updatedAtMs?: number } = {}
): Promise<UserProfile> {
  const db = await getDb();
  const updated: UserProfile = {
    ...profile,
    updatedAtMs: options.updatedAtMs ?? profile.updatedAtMs ?? Date.now()
  };
  await db.put("profiles", { ...updated, uid }, uid);
  await putMetaNumber(`profileUpdatedAtMs:${uid}`, updated.updatedAtMs);
  if (options.queue !== false) {
    await enqueueOperation(uid, "profile_upsert", updated);
  }
  return updated;
}

export async function listPantry(uid: string): Promise<PantryEntry[]> {
  const db = await getDb();
  const items = await db.getAllFromIndex("pantry", "uid", uid);
  return items
    .map(({ uid: _uid, ...entry }) => entry)
    .sort((a, b) => a.name.localeCompare(b.name));
}

export async function upsertPantryEntry(
  uid: string,
  entry: PantryEntry,
  options: { queue?: boolean; updatedAtMs?: number } = {}
): Promise<PantryEntry> {
  const db = await getDb();
  const updated = {
    ...entry,
    updatedAtMs: options.updatedAtMs ?? entry.updatedAtMs ?? Date.now()
  };
  await db.put("pantry", { ...updated, uid });
  await putMetaNumber(`pantryUpdatedAtMs:${uid}`, updated.updatedAtMs);
  if (options.queue !== false) {
    await enqueueOperation(uid, "pantry_upsert", updated);
  }
  return updated;
}

export async function replacePantry(
  uid: string,
  entries: PantryEntry[],
  options: { queue?: boolean; updatedAtMs?: number } = {}
): Promise<void> {
  const db = await getDb();
  const tx = db.transaction("pantry", "readwrite");
  const existing = await tx.store.index("uid").getAll(uid);
  await Promise.all(existing.map((entry) => tx.store.delete(entry.id)));
  const updatedAtMs = options.updatedAtMs ?? Date.now();
  await Promise.all(
    entries.map((entry) =>
      tx.store.put({
        ...entry,
        uid,
        updatedAtMs: entry.updatedAtMs || updatedAtMs
      })
    )
  );
  await tx.done;
  await putMetaNumber(`pantryUpdatedAtMs:${uid}`, updatedAtMs);
  if (options.queue !== false) {
    await enqueueOperation(uid, "pantry_upsert", entries);
  }
}

export async function deletePantryEntry(uid: string, id: string): Promise<void> {
  const db = await getDb();
  await db.delete("pantry", id);
  const updatedAtMs = Date.now();
  await putMetaNumber(`pantryUpdatedAtMs:${uid}`, updatedAtMs);
  await enqueueOperation(uid, "pantry_delete", { id });
}

export async function cacheRecipes(recipes: RecipeDetail[]): Promise<void> {
  const db = await getDb();
  const tx = db.transaction("recipes", "readwrite");
  const cachedAtMs = Date.now();
  await Promise.all(recipes.map((recipe) => tx.store.put({ ...recipe, cachedAtMs })));
  await tx.done;
  await db.put("meta", cachedAtMs, "recipesCachedAtMs");
}

export async function listCachedRecipes(): Promise<RecipeDetail[]> {
  const db = await getDb();
  const rows = await db.getAll("recipes");
  return rows
    .map(({ cachedAtMs: _cachedAtMs, ...recipe }) => recipe)
    .sort((a, b) => a.title.localeCompare(b.title));
}

export async function getCachedRecipe(id: string): Promise<RecipeDetail | undefined> {
  const db = await getDb();
  const row = await db.get("recipes", id);
  if (!row) return undefined;
  const { cachedAtMs: _cachedAtMs, ...recipe } = row;
  return recipe;
}

export async function savePlanSnapshot(
  uid: string,
  response: GeneratePlanResponse,
  source: PlanSnapshot["source"] = "backend",
  options: { queue?: boolean; generatedAtMs?: number; active?: boolean } = {}
): Promise<PlanSnapshot> {
  const db = await getDb();
  const generatedAtMs = options.generatedAtMs ?? response.timestamps?.completedAtMs ?? Date.now();
  const planId = response.planId || response.requestId || `plan-${generatedAtMs}`;
  const snapshot: PlanSnapshot = {
    planId,
    uid,
    generatedAtMs,
    updatedAtMs: Date.now(),
    active: options.active ?? true,
    source,
    response: { ...response, planId }
  };
  const tx = db.transaction("plans", "readwrite");
  if (snapshot.active) {
    const existing = await tx.store.index("uid").getAll(uid);
    await Promise.all(existing.map((plan) => tx.store.put({ ...plan, active: false })));
  }
  await tx.store.put(snapshot);
  await tx.done;
  if (snapshot.active) {
    await db.put("meta", snapshot.planId, `activePlanId:${uid}`);
  }
  await putMetaNumber(`planUpdatedAtMs:${uid}`, snapshot.updatedAtMs);
  if (snapshot.response.groceryOutput) {
    await putMetaNumber(`groceryUpdatedAtMs:${uid}`, snapshot.updatedAtMs);
  }
  if (options.queue !== false) {
    await enqueueOperation(uid, "plan_saved", {
      planId: snapshot.planId,
      generatedAtMs: snapshot.generatedAtMs
    });
    await enqueueOperation(uid, "grocery_saved", snapshot.response.groceryOutput ?? null);
  }
  return snapshot;
}

export async function listPlanSnapshots(uid: string): Promise<PlanSnapshot[]> {
  const db = await getDb();
  const rows = await db.getAllFromIndex("plans", "uid", uid);
  return rows.sort((a, b) => b.generatedAtMs - a.generatedAtMs);
}

export async function getActivePlanSnapshot(uid: string): Promise<PlanSnapshot | null> {
  const db = await getDb();
  const activePlanId = ((await db.get("meta", `activePlanId:${uid}`)) as string | undefined) ?? "";
  if (activePlanId) {
    const plan = await db.get("plans", activePlanId);
    if (plan?.uid === uid) return plan;
  }
  const newest = (await listPlanSnapshots(uid))[0];
  return newest ?? null;
}

export async function activatePlan(uid: string, planId: string): Promise<void> {
  const db = await getDb();
  const plans = await db.getAllFromIndex("plans", "uid", uid);
  const tx = db.transaction("plans", "readwrite");
  await Promise.all(
    plans.map((plan) => tx.store.put({ ...plan, active: plan.planId === planId, updatedAtMs: Date.now() }))
  );
  await tx.done;
  await db.put("meta", planId, `activePlanId:${uid}`);
  await putMetaNumber(`planUpdatedAtMs:${uid}`, Date.now());
}

export async function clearUserWeekData(uid: string): Promise<void> {
  const db = await getDb();
  const plans = await db.getAllFromIndex("plans", "uid", uid);
  const operations = await db.getAllFromIndex("queue", "uid", uid);
  const tx = db.transaction(["plans", "queue", "meta"], "readwrite");
  await Promise.all(plans.map((plan) => tx.objectStore("plans").delete(plan.planId)));
  await Promise.all(
    operations
      .filter((operation) => operation.type === "plan_saved" || operation.type === "grocery_saved")
      .map((operation) => tx.objectStore("queue").delete(operation.id))
  );
  await Promise.all([
    tx.objectStore("meta").delete(`activePlanId:${uid}`),
    tx.objectStore("meta").delete(`planUpdatedAtMs:${uid}`),
    tx.objectStore("meta").delete(`groceryUpdatedAtMs:${uid}`)
  ]);
  await tx.done;
}

export async function getDomainTimestamps(uid: string): Promise<DomainTimestamps> {
  return {
    profileUpdatedAtMs: await getMetaNumber(`profileUpdatedAtMs:${uid}`),
    pantryUpdatedAtMs: await getMetaNumber(`pantryUpdatedAtMs:${uid}`),
    planUpdatedAtMs: await getMetaNumber(`planUpdatedAtMs:${uid}`),
    groceryUpdatedAtMs: await getMetaNumber(`groceryUpdatedAtMs:${uid}`)
  };
}

export async function setDomainTimestamp(uid: string, key: keyof DomainTimestamps, value: number): Promise<void> {
  await putMetaNumber(`${key}:${uid}`, value);
}

export async function enqueueOperation(
  uid: string,
  type: OfflineOperationType,
  payload: unknown
): Promise<OfflineQueueOperation> {
  const db = await getDb();
  const now = Date.now();
  const id = `${uid}:${type}:${stableHash(stableStringify(payload))}`;
  const existing = await db.get("queue", id);
  const operation: OfflineQueueOperation = {
    id,
    uid,
    type,
    payload,
    status: "pending",
    attempts: existing?.attempts ?? 0,
    createdAtMs: existing?.createdAtMs ?? now,
    updatedAtMs: now,
    lastError: undefined
  };
  await db.put("queue", operation);
  return operation;
}

export async function listPendingOperations(uid?: string): Promise<OfflineQueueOperation[]> {
  const db = await getDb();
  const pending = await db.getAllFromIndex("queue", "status", "pending");
  return pending
    .filter((operation) => !uid || operation.uid === uid)
    .sort((a, b) => a.createdAtMs - b.createdAtMs);
}

export async function markOperationsSynced(ids: string[]): Promise<void> {
  const db = await getDb();
  const tx = db.transaction("queue", "readwrite");
  await Promise.all(
    ids.map(async (id) => {
      const operation = await tx.store.get(id);
      if (operation) {
        await tx.store.put({ ...operation, status: "synced", updatedAtMs: Date.now(), lastError: undefined });
      }
    })
  );
  await tx.done;
}

export async function markOperationFailed(id: string, error: string): Promise<void> {
  const db = await getDb();
  const operation = await db.get("queue", id);
  if (!operation) return;
  const attempts = operation.attempts + 1;
  await db.put("queue", {
    ...operation,
    attempts,
    status: attempts >= 5 ? "dead-letter" : "pending",
    updatedAtMs: Date.now(),
    lastError: error.slice(0, 240)
  });
}

async function getMetaNumber(key: string): Promise<number | undefined> {
  const db = await getDb();
  const value = await db.get("meta", key);
  return typeof value === "number" ? value : undefined;
}

async function putMetaNumber(key: string, value: number): Promise<void> {
  const db = await getDb();
  await db.put("meta", value, key);
}

export function stableStringify(value: unknown): string {
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  const object = value as Record<string, unknown>;
  return `{${Object.keys(object)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${stableStringify(object[key])}`)
    .join(",")}}`;
}

export function stableHash(value: string): string {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

export function createPantryId(uid: string, name: string): string {
  const normalized = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  return `${uid}:pantry:${normalized || stableHash(name)}`;
}
