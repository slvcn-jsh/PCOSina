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
    budgetWeekly: Optional[float] = None
    budgetMonthly: Optional[float] = None
    maxCookingTimeMinutes: int = 45
    varietyPreference: str = "Balanced"
    planningPriority: str = "Balanced"
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

class RecipeSummary(BaseModel):
    id: str
    title: str
    mealType: str
    minutes: int


class GeneratePlanRequest(BaseModel):
    profile: UserProfile
    days: int = 7
    mealsPerDay: int = 3


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


class FeedbackRequest(BaseModel):
    message: str
