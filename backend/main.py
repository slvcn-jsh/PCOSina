from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, ConfigDict
from typing import List, Optional, Dict, Any
import json
import os
import time
import traceback
import random
from ortools.sat.python import cp_model
import database

app = FastAPI(title="PCOSINA Optimization API")

# --- Models ---
class UserProfile(BaseModel):
    model_config = ConfigDict(extra='ignore')
    displayName: str = "User"
    age: int = 25
    heightCm: int = 160
    weightKg: int = 65
    activityLevel: str = "Lightly Active"
    goal: str = "General Health"
    dietaryRestrictions: List[str] = []

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

# --- Optimization Brain ---
def solve_meal_plan(request: GeneratePlanRequest, recipes: List[Dict]):
    profile = request.profile

    # 1. Base Target Calculation (Mifflin-St Jeor)
    w, h, a = profile.weightKg if profile.weightKg > 0 else 65, profile.heightCm if profile.heightCm > 0 else 160, profile.age if profile.age > 0 else 25
    bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
    maintenance = bmr * 1.375
    base_target = int(maintenance - 500 if "Weight Loss" in profile.goal else maintenance)
    base_target = max(1200, base_target)

    # 2. Daily Variance (FIX for identical daily sums)
    # Give each day a slightly different target to force variety in sums
    daily_targets = [base_target + random.randint(-60, 60) for _ in range(7)]

    # 3. STRICT Restriction Filtering (FIX for No Pork)
    restriction_map = {
        "No Pork": ["Pork", "pork"],
        "No Beef": ["Beef", "beef"],
        "Vegetarian": ["Pork", "pork", "Beef", "beef", "Chicken", "chicken", "Fish", "fish", "Seafood", "seafood"]
    }

    candidates = []
    for r in recipes:
        exclude = False
        r_tags = [t.lower() for t in r.get("tags", [])]
        for rest in profile.dietaryRestrictions:
            banned_tags = restriction_map.get(rest, [])
            if any(bt.lower() in r_tags for bt in banned_tags):
                exclude = True; break
        if not exclude: candidates.append(r)

    b_list = [i for i, r in enumerate(candidates) if r["mealType"] == "Breakfast"]
    l_list = [i for i, r in enumerate(candidates) if r["mealType"] == "Lunch"]
    d_list = [i for i, r in enumerate(candidates) if r["mealType"] == "Dinner"]

    if not b_list or not l_list or not d_list:
        print(f"DEBUG: Failed filtering. B:{len(b_list)} L:{len(l_list)} D:{len(d_list)}")
        return None

    # 4. MILP Formulation
    model = cp_model.CpModel()
    x = {}
    for d in range(7):
        for m, m_idxs in enumerate([b_list, l_list, d_list]):
            for i in m_idxs: x[d, m, i] = model.NewBoolVar(f'x_{d}_{m}_{i}')

    error_vars = []
    for d in range(7):
        # Rule: Exactly one meal per slot
        for m, m_idxs in enumerate([b_list, l_list, d_list]):
            model.Add(sum(x[d, m, i] for i in m_idxs) == 1)

        # Rule: Minimize deviation from dynamic daily target
        day_cals = sum(x[d, m, i] * candidates[i]["calories"] for m in range(3) for i in [b_list, l_list, d_list][m])
        error = model.NewIntVar(0, 1000, f'err_{d}')
        model.Add(error >= day_cals - daily_targets[d])
        model.Add(error >= daily_targets[d] - day_cals)
        error_vars.append(error)

    # Rule: Variety (No same recipe 2 days in a row)
    for d in range(6):
        for m in range(3):
            for i in [b_list, l_list, d_list][m]:
                model.Add(x[d, m, i] + x[d+1, m, i] <= 1)

    # Rule: Variety (Max 2 of any recipe per week)
    for i in range(len(candidates)):
        model.Add(sum(x[d, m, i] for d in range(7) for m in range(3) if (d, m, i) in x) <= 2)

    # Objective: Minimize calorie error + Random weights for maximum variety
    weights = [random.randint(1, 100) for _ in range(len(candidates))]
    model.Minimize(sum(error_vars) * 10 - sum(x[d, m, i] * weights[i] for d in range(7) for m in range(3) if (d, m, i) in x))

    solver = cp_model.CpSolver()
    status = solver.Solve(model)

    if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
        res_plan = []
        names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        for d in range(7):
            meals = []
            total = 0
            for m in range(3):
                for i in [b_list, l_list, d_list][m]:
                    if solver.Value(x[d, m, i]):
                        r = candidates[i]
                        meals.append(PlannedMeal(mealLabel=r["mealType"], recipeId=r["id"], title=r["title"]))
                        total += r["calories"]
            res_plan.append(DayPlan(dayLabel=names[d], meals=meals, totalCalories=total))
        return res_plan
    return None

# --- API Endpoints ---

@app.get("/recipe/{recipe_id}", response_model=RecipeDetail)
async def get_recipe(recipe_id: str):
    recipes = database.get_all_recipes()
    recipe = next((r for r in recipes if r["id"] == recipe_id), None)
    if recipe:
        return RecipeDetail(
            id=recipe["id"], title=recipe["title"], mealType=recipe["mealType"],
            calories=recipe["calories"], proteinGrams=recipe["proteinGrams"],
            carbsGrams=recipe["carbsGrams"], fatsGrams=recipe["fatsGrams"],
            fiberGrams=recipe["fiberGrams"], tags=recipe["tags"],
            minutes=recipe["minutes"],
            ingredients=[Ingredient(name=i["name"], quantity=i["quantity"]) for i in recipe["ingredients"]],
            steps=recipe["steps"]
        )
    raise HTTPException(status_code=404, detail=f"Recipe {recipe_id} not found")

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest):
    print(f"\n>>> REQUEST: Generate plan for {request.profile.displayName}")
    try:
        all_recipes = database.get_all_recipes()
        result = solve_meal_plan(request, all_recipes)
        if result:
            return GeneratePlanResponse(
                weekLabel="PCOSINA MILP Optimized Plan",
                days=result, status="success",
                message="Plan generated with strict restrictions and daily variation."
            )
        else:
            raise HTTPException(status_code=422, detail="No feasible plan found.")
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
def health(): return {"status": "alive"}

if __name__ == "__main__":
    import uvicorn
    database.init_db()
    database.seed_recipes()
    uvicorn.run(app, host="0.0.0.0", port=8000)
