export type SessionMode = "guest" | "firebase";

export interface AppSession {
  uid: string;
  mode: SessionMode;
  email?: string;
  displayName?: string;
  updatedAtMs: number;
}

export interface UserProfile {
  displayName: string;
  avatarId?: string;
  age: number;
  heightCm: number;
  weightKg: number;
  targetWeightKg?: number;
  targetDate?: string;
  weeklyWeightChangeGoalKg?: number;
  heightUnit: "cm";
  weightUnit: "kg";
  activityLevel: string;
  goal: string;
  symptoms: string[];
  comorbidities: string[];
  dietaryRestrictions: string[];
  allergies: string[];
  excludedIngredients: string[];
  weeklyBudgetPhp: number;
  maxCookingTimeMinutes: number;
  varietyPreference: string;
  planningPriority: string;
  preferredMarketType: string;
  pantryItems: string[];
  isProfileCompleted: boolean;
  updatedAtMs: number;
}

export interface BackendUserProfile {
  displayName: string;
  age: number;
  heightCm: number;
  weightKg: number;
  targetWeightKg?: number;
  targetDate?: string;
  weeklyWeightChangeGoalKg?: number;
  heightUnit: "cm";
  weightUnit: "kg";
  activityLevel: string;
  goal: string;
  symptoms: string[];
  comorbidities: string[];
  dietaryRestrictions: string[];
  allergies: string[];
  weeklyBudgetPhp: number;
  maxCookingTimeMinutes: number;
  varietyPreference: string;
  planningPriority: string;
  preferredMarketType: string;
  pantryItems: string[];
  isProfileCompleted: boolean;
}

export interface PantryEntry {
  id: string;
  name: string;
  quantity?: string;
  expiryDate?: string;
  amount?: number;
  unit?: string;
  updatedAtMs: number;
}

export interface Ingredient {
  name: string;
  quantity: string;
}

export interface RecipeDetail {
  id: string;
  title: string;
  mealType?: string;
  calories?: number;
  proteinGrams?: number;
  carbsGrams?: number;
  fatsGrams?: number;
  fiberGrams?: number;
  sodiumMg?: number;
  sugarGrams?: number;
  tags: string[];
  minutes?: number;
  ingredients: Ingredient[];
  steps: string[];
  nutritionDataSource?: string;
  nutritionConfidence?: string;
  nutritionReviewStatus?: string;
}

export interface PlannedMeal {
  mealLabel: string;
  recipeId: string;
  title: string;
}

export interface DayPlan {
  dayLabel: string;
  meals: PlannedMeal[];
  totalCalories: number;
}

export interface PlannerTimestamps {
  requestedAtMs?: number;
  completedAtMs?: number;
}

export interface PlanExplanation {
  confidenceScore?: number;
  targetCalories?: number;
  avgCalories?: number;
  avgCaloriesDeviation?: number;
  targetProtein?: number;
  avgProtein?: number;
  targetCarbs?: number;
  avgCarbs?: number;
  targetFats?: number;
  avgFats?: number;
  toleranceUsed?: number;
  maxPerWeek?: number;
  pantryMatches?: number;
  budgetWeekly?: number;
  estimatedWeeklyCost?: number;
  restrictionCount?: number;
  candidateExclusionSummary?: Record<string, number>;
  selectionReasonsByRecipeId?: Record<string, string[]>;
  selectionReasonCounts?: Record<string, number>;
  fiberMinTarget?: number;
  sugarMaxTarget?: number;
}

export interface GroceryOutputItem {
  key?: string;
  name: string;
  quantity?: string;
  estimatedCostPhp?: number;
  category?: string;
  source?: string;
  sourceLabel?: string;
  confidence?: string;
  originalNames?: string[];
}

export interface GroceryOutput {
  authority?: string;
  budgetAuthority?: string;
  pricingAuthority?: string;
  estimatedTotalPhp?: number;
  finalGroceryEstimatePhp?: number;
  weeklyBudgetPhp?: number;
  userBudgetPhp?: number;
  withinBudget?: boolean;
  budgetDeltaPhp?: number;
  budgetGapPhp?: number;
  displayedEstimateSource?: string;
  itemCount?: number;
  selectedMealCount?: number;
  plannerMealEstimatePhp?: number;
  solverBudgetEstimatePhp?: number;
  roughMealBudgetCapPhp?: number;
  items?: GroceryOutputItem[];
}

export type PlannerStatus = "success" | "no-safe-plan";

export interface GeneratePlanResponse {
  weekLabel: string;
  days: DayPlan[];
  status: PlannerStatus;
  message: string;
  explanation?: PlanExplanation | null;
  requestId?: string;
  planId?: string | null;
  groceryOutput?: GroceryOutput | null;
  pantryUsageSummary?: Record<string, unknown> | null;
  nutritionSummary?: Record<string, unknown> | null;
  solverMetadata?: Record<string, unknown> | null;
  policyVersion?: string;
  diagnosticsSummary?: Record<string, unknown> | null;
  machineReasonCodes?: string[];
  humanGuidance?: string[];
  suggestedRelaxations?: string[];
  diagnosticsReference?: string | null;
  timestamps?: PlannerTimestamps | null;
}

export interface GeneratePlanRequest {
  profile: BackendUserProfile;
  days: number;
  mealsPerDay: number;
  startDate?: string;
}

export interface PlanSnapshot {
  planId: string;
  uid: string;
  generatedAtMs: number;
  updatedAtMs: number;
  active: boolean;
  source: "backend" | "cloud" | "restored-cache";
  response: GeneratePlanResponse;
}

export type OfflineOperationType =
  | "profile_upsert"
  | "pantry_upsert"
  | "pantry_delete"
  | "plan_saved"
  | "grocery_saved";

export interface OfflineQueueOperation {
  id: string;
  uid: string;
  type: OfflineOperationType;
  payload: unknown;
  status: "pending" | "synced" | "dead-letter";
  attempts: number;
  createdAtMs: number;
  updatedAtMs: number;
  lastError?: string;
}

export interface DomainTimestamps {
  profileUpdatedAtMs?: number;
  pantryUpdatedAtMs?: number;
  planUpdatedAtMs?: number;
  groceryUpdatedAtMs?: number;
}

export interface SyncSummary {
  status: "skipped" | "synced" | "restored" | "uploaded" | "failed";
  message: string;
}
