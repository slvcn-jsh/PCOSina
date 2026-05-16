from typing import List, Optional, Dict, Any, Annotated

from pydantic import BaseModel, ConfigDict, Field


ProfileName = Annotated[str, Field(max_length=80)]
ProfileCode = Annotated[str, Field(max_length=40)]
ProfileText = Annotated[str, Field(max_length=200)]
ProfileToken = Annotated[str, Field(max_length=80)]
PantryToken = Annotated[str, Field(max_length=120)]
IsoDateText = Annotated[str, Field(max_length=10)]


class UserProfile(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)
    displayName: ProfileName = "User"
    age: int = Field(default=25, ge=0, le=120)
    heightCm: int = Field(default=160, ge=0, le=260)
    weightKg: int = Field(default=65, ge=0, le=350)
    heightUnit: ProfileCode = "cm"
    weightUnit: ProfileCode = "kg"
    activityLevel: ProfileToken = "Lightly Active"
    goal: ProfileText = "General Health"
    insulinResistanceLevel: ProfileToken = "Mild"
    symptoms: List[ProfileToken] = Field(default_factory=list, max_length=20)
    comorbidities: List[ProfileToken] = Field(default_factory=list, max_length=20)
    dietaryRestrictions: List[ProfileToken] = Field(default_factory=list, max_length=20)
    allergies: List[ProfileToken] = Field(default_factory=list, max_length=30)
    weeklyBudgetPhp: Optional[int] = Field(default=None, ge=0, le=1_000_000, alias="weeklyBudgetPhp")
    householdSize: int = Field(default=1, ge=1, le=6)
    budgetWeekly: Optional[float] = Field(default=None, ge=0, le=1_000_000)
    budgetMonthly: Optional[float] = Field(default=None, ge=0, le=5_000_000)
    maxCookingTimeMinutes: int = Field(default=45, ge=0, le=240)
    varietyPreference: ProfileToken = "Balanced"
    planningPriority: ProfileToken = "Balanced"
    preferredMarketType: ProfileToken = "Supermarket"
    pantryItems: List[PantryToken] = Field(default_factory=list, max_length=100)
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
    nutritionDataSource: Optional[str] = None
    nutritionConfidence: Optional[str] = None
    nutritionReviewStatus: Optional[str] = None
    nutritionNotes: Optional[str] = None

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
    priceMinPhp: Optional[int] = None
    priceMaxPhp: Optional[int] = None
    category: str
    unit: Optional[str] = None
    active: bool = True
    notes: Optional[str] = None
    updatedAt: Optional[int] = None


class AdminPriceRuleUpsertRequest(BaseModel):
    id: Optional[str] = None
    keywords: List[str] = []
    pricePhp: int
    priceMinPhp: Optional[int] = None
    priceMaxPhp: Optional[int] = None
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
    days: int = Field(default=7, ge=1, le=31)
    mealsPerDay: int = Field(default=3, ge=1, le=6)
    startDate: Optional[IsoDateText] = None


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
    model_config = ConfigDict(extra="forbid")

    message: str = Field(min_length=1, max_length=2000)


class MlClientEventRequest(BaseModel):
    eventName: str
    requestId: Optional[str] = None
    payload: Dict[str, Any] = Field(default_factory=dict)
