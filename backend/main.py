from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, ConfigDict
from typing import List, Optional, Dict, Any
import json
import os
import time
import traceback
import random
import math
from contextlib import asynccontextmanager
from ortools.sat.python import cp_model
import database

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("LOG: System startup. Syncing database...")
    database.init_db()
    database.seed_recipes()
    yield

app = FastAPI(title="PCOSINA Optimization API", lifespan=lifespan)

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

# --- The Elastic Solver ---
def solve_meal_plan(request: GeneratePlanRequest, recipes: List[Dict]):
    profile = request.profile
    num_days = request.days if 1 <= request.days <= 14 else 7
    
    # Target Calculation
    w, h, a = profile.weightKg, profile.heightCm, profile.age
    if w < 30: w, h, a = 60, 155, 25 
    bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
    target = int(bmr * 1.375)
    if "Weight Loss" in profile.goal: target -= 500
    target = max(1200, target)

    print(f"DEBUG: Processing plan for '{profile.displayName}' | Target: {target} | Days: {num_days}")
    print(f"DEBUG: Restrictions: {profile.dietaryRestrictions}")

    # Filtering
    restriction_map = {
        "No Pork": ["Pork", "pork"],
        "No Beef": ["Beef", "beef"],
        "Vegetarian": ["Pork", "pork", "Beef", "beef", "Chicken", "chicken", "Fish", "fish", "Seafood", "seafood"]
    }
    
    candidates = []
    for r in recipes:
        exclude = False
        tags = [t.lower() for t in r.get("tags", [])]
        for rest in profile.dietaryRestrictions:
            banned = restriction_map.get(rest, [])
            if any(b.lower() in tags for b in banned):
                exclude = True; break
        if not exclude: candidates.append(r)

    b_list = [i for i, r in enumerate(candidates) if r["mealType"] == "Breakfast"]
    l_list = [i for i, r in enumerate(candidates) if r["mealType"] == "Lunch"]
    d_list = [i for i, r in enumerate(candidates) if r["mealType"] == "Dinner"]

    print(f"DEBUG: Candidate pool - Total: {len(candidates)} | B: {len(b_list)} | L: {len(l_list)} | D: {len(d_list)}")

    if not b_list or not l_list or not d_list:
        return None, "Inadequate recipe pool after filtering."

    # Elastic Search Strategy: Try strict, then relax
    for max_per_week in [2, 3, 4, 10]:
        min_needed = math.ceil(num_days / max_per_week)
        if len(b_list) < min_needed or len(l_list) < min_needed or len(d_list) < min_needed:
            print(f"DEBUG: Skipping max_per_week={max_per_week} (Need {min_needed} recipes per type)")
            continue

        model = cp_model.CpModel()
        x = {} 
        for d in range(num_days):
            for m, idxs in enumerate([b_list, l_list, d_list]):
                for i in idxs: x[d, m, i] = model.NewBoolVar(f'x_{d}_{m}_{i}')

        for d in range(num_days):
            for m, idxs in enumerate([b_list, l_list, d_list]):
                model.Add(sum(x[d, m, i] for i in idxs) == 1)
            day_cals = sum(x[d, m, i] * candidates[i]["calories"] for m in range(3) for i in [b_list, l_list, d_list][m])
            model.Add(day_cals >= target - 400) # Increased tolerance slightly for reliability
            model.Add(day_cals <= target + 400)

        # Variety constraints
        for i in range(len(candidates)):
            model.Add(sum(x[d, m, i] for d in range(num_days) for m in range(3) if (d, m, i) in x) <= max_per_week)

        # Optimization goal: Random variety
        random.seed(f"{profile.displayName}_{time.time()}")
        weights = [random.randint(1, 100) for _ in range(len(candidates))]
        model.Maximize(sum(x[d, m, i] * weights[i] for d in range(num_days) for m in range(3) if (d, m, i) in x))

        solver = cp_model.CpSolver()
        solver.parameters.max_time_in_seconds = 3.0
        status = solver.Solve(model)

        if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
            res = []
            day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Day 8", "Day 9", "Day 10"]
            for d in range(num_days):
                meals = []
                total = 0
                for m in range(3):
                    for i in [b_list, l_list, d_list][m]:
                        if solver.Value(x[d, m, i]):
                            r = candidates[i]
                            meals.append(PlannedMeal(mealLabel=r["mealType"], recipeId=r["id"], title=r["title"]))
                            total += r["calories"]
                res.append(DayPlan(dayLabel=day_names[d] if d < 7 else f"Day {d+1}", meals=meals, totalCalories=total))
            return res, f"Success (Max {max_per_week} repeats)"
            
    return None, "Mathematical infeasibility."

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest):
    print(f"\n>>> REQUEST: Generate plan for '{request.profile.displayName}'")
    try:
        all_recipes = database.get_all_recipes()
        if not all_recipes:
            print("ERROR: Database returned 0 recipes!")
            raise HTTPException(status_code=500, detail="Database is empty. Please seed recipes.")
            
        result, msg = solve_meal_plan(request, all_recipes)
        if result:
            return GeneratePlanResponse(
                weekLabel=f"PCOSINA {request.days}-Day Plan",
                days=result, status="success", message=msg
            )
        else:
            print(f"ERROR: Solver failed: {msg}")
            raise HTTPException(status_code=422, detail=f"No plan found: {msg}")
    except HTTPException: raise
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.get("/recipe/{recipe_id}", response_model=RecipeDetail)
async def get_recipe(recipe_id: str):
    print(f"DEBUG: Searching for recipe ID: {recipe_id}")
    all_recipes = database.get_all_recipes()
    recipe = next((r for r in all_recipes if r["id"] == recipe_id), None)
    if recipe: return RecipeDetail(**recipe)
    print(f"ERROR: Recipe {recipe_id} not found in {len(all_recipes)} recipes.")
    raise HTTPException(status_code=404, detail="Recipe not found")

@app.get("/health")
def health(): return {"status": "alive"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
