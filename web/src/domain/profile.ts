import type { BackendUserProfile, PantryEntry, UserProfile } from "../types";

export const GUEST_UID = "guest-local";

export const defaultProfile = (): UserProfile => ({
  displayName: "",
  avatarId: "doctor_dog",
  age: 25,
  heightCm: 160,
  weightKg: 65,
  heightUnit: "cm",
  weightUnit: "kg",
  activityLevel: "Lightly Active",
  goal: "General Health",
  symptoms: [],
  comorbidities: [],
  dietaryRestrictions: [],
  allergies: [],
  excludedIngredients: [],
  weeklyBudgetPhp: 1500,
  maxCookingTimeMinutes: 45,
  varietyPreference: "Balanced",
  planningPriority: "Balanced",
  preferredMarketType: "Wet Market",
  pantryItems: [],
  isProfileCompleted: false,
  updatedAtMs: Date.now()
});

export function splitTokens(raw: string): string[] {
  return raw
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .filter((item, index, all) => all.findIndex((candidate) => candidate.toLowerCase() === item.toLowerCase()) === index)
    .slice(0, 100);
}

export function pantryNames(entries: PantryEntry[]): string[] {
  return entries
    .map((entry) => entry.name.trim())
    .filter(Boolean)
    .filter((item, index, all) => all.findIndex((candidate) => candidate.toLowerCase() === item.toLowerCase()) === index);
}

export function toBackendProfile(profile: UserProfile, pantryEntries: PantryEntry[]): BackendUserProfile {
  const exclusionsAsCustomAllergyTokens = profile.excludedIngredients.filter(Boolean);
  return {
    displayName: profile.displayName || "User",
    age: Math.max(0, Math.round(profile.age || 0)),
    heightCm: Math.max(0, Math.round(profile.heightCm || 0)),
    weightKg: Math.max(0, Math.round(profile.weightKg || 0)),
    targetWeightKg: profile.targetWeightKg,
    targetDate: profile.targetDate,
    weeklyWeightChangeGoalKg: profile.weeklyWeightChangeGoalKg,
    heightUnit: "cm",
    weightUnit: "kg",
    activityLevel: profile.activityLevel || "Lightly Active",
    goal: profile.goal || "General Health",
    symptoms: profile.symptoms,
    comorbidities: profile.comorbidities,
    dietaryRestrictions: profile.dietaryRestrictions,
    allergies: [...profile.allergies, ...exclusionsAsCustomAllergyTokens],
    weeklyBudgetPhp: Math.max(0, Math.round(profile.weeklyBudgetPhp || 0)),
    maxCookingTimeMinutes: Math.max(0, Math.round(profile.maxCookingTimeMinutes || 0)),
    varietyPreference: profile.varietyPreference || "Balanced",
    planningPriority: profile.planningPriority || "Balanced",
    preferredMarketType: profile.preferredMarketType || "Wet Market",
    pantryItems: pantryNames(pantryEntries).slice(0, 100),
    isProfileCompleted: true
  };
}

export function mergeProfilePatch(profile: UserProfile, patch: Partial<UserProfile>): UserProfile {
  return {
    ...profile,
    ...patch,
    updatedAtMs: Date.now()
  };
}
