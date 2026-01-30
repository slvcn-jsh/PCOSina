from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, ConfigDict
from typing import List, Optional, Dict
import json
import os
import time
import traceback
import random
from ortools.sat.python import cp_model

app = FastAPI(title="PCOSINA Optimization API")

# --- Unified Models ---
class UserProfile(BaseModel):
    model_config = ConfigDict(extra='ignore') # Ignore extra fields from Android
    displayName: str = "User"
    age: int = 25
    heightCm: int = 160
    weightKg: int = 65
    activityLevel: str = "Lightly Active"
    goal: str = "General Health"
    insulinResistanceLevel: str = "Mild"
    symptoms: List[str] = []
    comorbidities: List[str] = []
    dietaryRestrictions: List[str] = []
    allergies: List[str] = []
    weeklyBudgetPhp: int = 2000
    maxCookingTimeMinutes: int = 45
    varietyPreference: str = "Balanced"

class PantryItem(BaseModel):
    ingredientName: str
    quantity: str

class GeneratePlanRequest(BaseModel):
    profile: UserProfile
    pantry: List[PantryItem] = []
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

# --- Core Logic ---
def load_recipes():
    try:
        with open("recipes.json", "r") as f:
            data = json.load(f)
            return [r for r in data if "id" in r and "mealType" in r]
    except Exception as e:
        print(f"CRITICAL: Failed to load recipes.json: {e}")
        return []

def solve_meal_plan(request: GeneratePlanRequest, recipes: List[Dict]):
    profile = request.profile
    # Calculate target
    bmr = (10 * profile.weightKg) + (6.25 * profile.heightCm) - (5 * profile.age) - 161
    maintenance = bmr * 1.375
    target_calories = int(maintenance - 500 if "Weight Loss" in profile.goal else maintenance)

    # 1. Filter
    candidates = []
    restriction_map = {"No Pork": "Pork", "No Beef": "Beef", "Vegetarian": ["Meat", "Pork", "Beef", "Chicken"]}

    for r in recipes:
        exclude = False
        for rest in profile.dietaryRestrictions:
            banned = restriction_map.get(rest, [])
            if isinstance(banned, str): banned = [banned]
            if any(b.lower() in [t.lower() for t in r.get("tags", [])] for b in banned):
                exclude = True; break
        if not exclude: candidates.append(r)

    if len(candidates) < 10: return None

    # 2. MILP
    model = cp_model.CpModel()
    num_days, num_meals = request.days, 3
    x = {}
    for d in range(num_days):
        for m in range(num_meals):
            for i in range(len(candidates)):
                x[d, m, i] = model.NewBoolVar(f'x_{d}_{m}_{i}')

    types = ["Breakfast", "Lunch", "Dinner"]
    for d in range(num_days):
        for m in range(num_meals):
            model.Add(sum(x[d, m, i] for i in range(len(candidates))) == 1)
            model.Add(sum(x[d, m, i] for i in range(len(candidates)) if candidates[i]["mealType"] == types[m]) == 1)

        day_cals = sum(x[d, m, i] * candidates[i]["calories"] for m in range(num_meals) for i in range(len(candidates)))
        model.Add(day_cals >= target_calories - 250)
        model.Add(day_cals <= target_calories + 250)

    # Variety: No same recipe 2 days in a row
    for d in range(num_days - 1):
        for m in range(num_meals):
            for i in range(len(candidates)):
                model.Add(x[d, m, i] + x[d+1, m, i] <= 1)

    # Global variety: Max 2 per week
    for i in range(len(candidates)):
        model.Add(sum(x[d, m, i] for d in range(num_days) for m in range(num_meals)) <= 2)

    # Randomize
    weights = [random.randint(1, 100) for _ in range(len(candidates))]
    model.Maximize(sum(x[d, m, i] * weights[i] for d in range(num_days) for m in range(num_meals) for i in range(len(candidates))))

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = 5.0
    status = solver.Solve(model)

    if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
        plan = []
        day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        for d in range(num_days):
            meals = []
            total = 0
            for m in range(num_meals):
                for i, r in enumerate(candidates):
                    if solver.Value(x[d, m, i]):
                        meals.append(PlannedMeal(mealLabel=r["mealType"], recipeId=r["id"], title=r["title"]))
                        total += r["calories"]
            plan.append(DayPlan(dayLabel=day_names[d], meals=meals, totalCalories=total))
        return plan
    return None

# --- Endpoints ---
@app.post("/generate-plan")
async def generate_plan(request: GeneratePlanRequest):
    print(f"\n>>> INCOMING REQUEST: {request.profile.displayName}")
    try:
        all_recipes = load_recipes()
        print(f"DEBUG: Loaded {len(all_recipes)} recipes.")

        result = solve_meal_plan(request, all_recipes)
        if result:
            return GeneratePlanResponse(
                weekLabel="PCOSINA MILP Optimized Plan",
                days=result, status="success",
                message="Plan generated with variety constraints."
            )
        else:
            print("ERROR: Solver could not find a valid plan.")
            raise HTTPException(status_code=422, detail="No feasible plan found.")
    except HTTPException as he:
        raise he
    except Exception as e:
        print("--- CRITICAL SERVER ERROR ---")
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
def health(): return {"status": "alive"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
