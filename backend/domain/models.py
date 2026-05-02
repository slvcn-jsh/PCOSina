from typing import List, Optional, Dict, Any

from pydantic import BaseModel, ConfigDict, Field


class UserProfile(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    displayName: str = "User"
    age: int = 25
    heightCm: int = 160
    weightKg: int = 65
    heightUnit: str = "cm"
    weightUnit: str = "kg"
    activityLevel: str = "Lightly Active"
    goal: str = "General Health"
    insulinResistanceLevel: str = "Mild"
    symptoms: List[str] = []
    comorbidities: List[str] = []
    dietaryRestrictions: List[str] = []
    allergies: List[str] = []
    weeklyBudgetPhp: Optional[int] = Field(default=None, alias="weeklyBudgetPhp")
    householdSize: int = Field(default=1, ge=1, le=6)
    budgetWeekly: Optional[float] = None
    budgetMonthly: Optional[float] = None
    maxCookingTimeMinutes: int = 45
    varietyPreference: str = "Balanced"
    planningPriority: str = "Balanced"
    preferredMarketType: str = "Supermarket"
    pantryItems: List[str] = []
    isProfileCompleted: bool = False


class Ingredient(BaseModel):
    name: str
    quantity: str


class RecipeDetail(BaseModel):
    id: str
    title: str
    mealType: str
    calories: int
    proteinGrams: int
    carbsGrams: int
    fatsGrams: int
    fiberGrams: int
    tags: List[str]
    minutes: int
    ingredients: List[Ingredient]
    steps: List[str]
    sodiumMg: Optional[int] = None
    sugarGrams: Optional[int] = None
    nutritionCorrectionId: Optional[str] = None

class RecipeSummary(BaseModel):
    id: str
    title: str
    mealType: str
    minutes: int


class AdminRecipeUpsertRequest(BaseModel):
    id: Optional[str] = None
    title: str
    mealType: str
    calories: int
    proteinGrams: int
    carbsGrams: int
    fatsGrams: int
    fiberGrams: int
    tags: List[str] = []
    minutes: int = 25
    ingredients: List[Ingredient] = []
    steps: List[str] = []


class AdminPriceRule(BaseModel):
    id: str
    keywords: List[str] = []
    pricePhp: int
    category: str
    unit: Optional[str] = None
    active: bool = True
    notes: Optional[str] = None
    updatedAt: Optional[int] = None


class AdminPriceRuleUpsertRequest(BaseModel):
    id: Optional[str] = None
    keywords: List[str] = []
    pricePhp: int
    category: str
    unit: Optional[str] = None
    active: bool = True
    notes: Optional[str] = None


class AdminNutritionCorrection(BaseModel):
    id: str
    recipeId: str
    calories: Optional[int] = None
    proteinGrams: Optional[int] = None
    carbsGrams: Optional[int] = None
    fatsGrams: Optional[int] = None
    fiberGrams: Optional[int] = None
    sodiumMg: Optional[int] = None
    sugarGrams: Optional[int] = None
    active: bool = True
    notes: Optional[str] = None
    updatedAt: Optional[int] = None


class AdminNutritionCorrectionUpsertRequest(BaseModel):
    calories: Optional[int] = None
    proteinGrams: Optional[int] = None
    carbsGrams: Optional[int] = None
    fatsGrams: Optional[int] = None
    fiberGrams: Optional[int] = None
    sodiumMg: Optional[int] = None
    sugarGrams: Optional[int] = None
    active: bool = True
    notes: Optional[str] = None


class AdminSupportCaseNote(BaseModel):
    author: str
    message: str
    createdAtMs: int


class AdminSupportCase(BaseModel):
    id: str
    userUid: str
    relatedJobId: Optional[str] = None
    status: str
    priority: str
    assignee: Optional[str] = None
    escalated: bool = False
    summary: str
    notes: List[AdminSupportCaseNote] = []
    createdBy: Optional[str] = None
    updatedBy: Optional[str] = None
    createdAt: int
    updatedAt: int


class AdminSupportCaseCreateRequest(BaseModel):
    userUid: str
    relatedJobId: Optional[str] = None
    summary: str
    priority: str = "normal"
    assignee: Optional[str] = None
    escalated: bool = False
    initialNote: Optional[str] = None


class AdminSupportCaseUpdateRequest(BaseModel):
    status: Optional[str] = None
    priority: Optional[str] = None
    assignee: Optional[str] = None
    clearAssignee: bool = False
    escalated: Optional[bool] = None
    summary: Optional[str] = None


class AdminSupportCaseNoteRequest(BaseModel):
    message: str


class AdminSessionRecord(BaseModel):
    id: str
    uid: str
    email: Optional[str] = None
    actor: str
    roles: List[str] = []
    authType: str
    createdAt: int
    expiresAt: int
    lastSeenAt: int
    revokedAt: Optional[int] = None
    revokedBy: Optional[str] = None
    revokeReason: Optional[str] = None


class AdminSessionRevokeRequest(BaseModel):
    reason: Optional[str] = None


class AdminSessionBulkRevokeRequest(BaseModel):
    reason: Optional[str] = None
    excludeCurrentSession: bool = True


class AdminSessionCleanupRequest(BaseModel):
    retentionDays: int = 30
    includeRevoked: bool = True
    includeExpired: bool = True


class OperatorAccessOverrideRecord(BaseModel):
    uid: str
    email: Optional[str] = None
    blocked: bool = True
    reason: Optional[str] = None
    updatedBy: Optional[str] = None
    createdAt: int
    updatedAt: int


class OperatorAccessOverrideUpsertRequest(BaseModel):
    email: Optional[str] = None
    blocked: bool = True
    reason: Optional[str] = None
    revokeActiveSessions: bool = True


class OperatorAccessStatus(BaseModel):
    allowed: bool = True
    uid: str
    email: Optional[str] = None
    emailVerified: bool = False
    mfaVerified: bool = False
    roles: List[str] = []
    roleSources: Dict[str, str] = {}
    actor: str
    authType: str


class GeneratePlanRequest(BaseModel):
    profile: UserProfile
    days: int = 7
    mealsPerDay: int = 3
    startDate: Optional[str] = None


class SwapOptionsRequest(BaseModel):
    profile: UserProfile
    mealLabel: str
    currentRecipeId: Optional[str] = None
    activeRecipeIds: List[str] = []
    limit: int = 20


class PlannedMeal(BaseModel):
    mealLabel: str
    recipeId: str
    title: str


class DayPlan(BaseModel):
    dayLabel: str
    meals: List[PlannedMeal]
    totalCalories: int


class GeneratePlanResponse(BaseModel):
    weekLabel: str
    days: List[DayPlan]
    status: str
    message: str
    explanation: Optional[Dict[str, Any]] = None
    requestId: Optional[str] = None
    planId: Optional[str] = None
    groceryOutput: Optional[Dict[str, Any]] = None
    pantryUsageSummary: Optional[Dict[str, Any]] = None
    nutritionSummary: Optional[Dict[str, Any]] = None
    solverMetadata: Optional[Dict[str, Any]] = None
    policyVersion: Optional[str] = None
    diagnosticsSummary: Optional[Dict[str, Any]] = None
    machineReasonCodes: List[str] = []
    humanGuidance: List[str] = []
    suggestedRelaxations: List[str] = []
    diagnosticsReference: Optional[str] = None
    timestamps: Optional[Dict[str, Any]] = None


class FeedbackRequest(BaseModel):
    message: str


class MlClientEventRequest(BaseModel):
    eventName: str
    requestId: Optional[str] = None
    payload: Dict[str, Any] = Field(default_factory=dict)
