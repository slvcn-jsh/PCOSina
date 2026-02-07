from typing import List, Optional, Dict, Any

from pydantic import BaseModel, ConfigDict


class UserProfile(BaseModel):
    model_config = ConfigDict(extra="ignore")
    displayName: str = "User"
    age: int = 25
    heightCm: int = 160
    weightKg: int = 65
    activityLevel: str = "Lightly Active"
    goal: str = "General Health"
    dietaryRestrictions: List[str] = []
    pantryItems: List[str] = []
    budgetWeekly: Optional[float] = None
    budgetMonthly: Optional[float] = None


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
