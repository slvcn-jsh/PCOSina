from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, ConfigDict
from typing import List, Optional, Dict, Any
import json
import os
import time
import traceback
import random
from contextlib import asynccontextmanager
from ortools.sat.python import cp_model
import database

# --- Task 8: Startup Seeding using Lifespan ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    # This runs when the server starts
    print("LOG: System startup. Initializing database...")
    database.init_db()
    database.seed_recipes()
    yield
    # This runs when the server stops
    print("LOG: System shutdown.")

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

# --- Optimization Engine ---
def solve_meal_plan(request: GeneratePlanRequest, recipes: List[Dict]):
    profile = request.profile
    # Task 5: Respect request.days (don't hardcode 7)
    num_days = request.days if 1 <= request.days <= 14 else 7
    
    # Target Calculation
    w = profile.weightKg if profile.weightKg > 30 else 60
    h = profile.heightCm if profile.heightCm > 50 else 155
    a = profile.age if profile.age > 10 else 25
    
    bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
    target = int(bmr * 1.375)
    if "Weight Loss" in profile.goal: target -= 500
    target = max(1200, target)

    # Task 6: Stronger Filtering (Case-insensitive + Tag Normalization)
    restriction_map = {
        "No Pork": ["Pork", "pork"],
        "No Beef": ["Beef", "beef"],
        "Vegetarian": ["Pork", "pork", "Beef", "beef", "Chicken", "chicken", "Fish", "fish", "Seafood", "seafood"],
        "Pescatarian": ["Pork", "pork", "Beef", "beef", "Chicken", "chicken"]
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
        print(f"ERROR: Insufficient data for restrictions. B:{len(b_list)} L:{len(l_list)} D:{len(d_list)}")
        return None

    # Task 7: Deterministic Seeding (Same input -> Same output)
    # Using User Name + First Restriction as seed for reproducible demo
    seed_str = f"{profile.displayName}_{profile.dietaryRestrictions[0] if profile.dietaryRestrictions else 'none'}"
    random.seed(seed_str)

    model = cp_model.CpModel()
    x = {} 
    for d in range(num_days):
        for m, idxs in enumerate([b_list, l_list, d_list]):
            for i in idxs: x[d, m, i] = model.NewBoolVar(f'x_{d}_{m}_{i}')

    for d in range(num_days):
        for m, idxs in enumerate([b_list, l_list, d_list]):
            model.Add(sum(x[d, m, i] for i in idxs) == 1)
        
        day_cals = sum(x[d, m, i] * candidates[i]["calories"] for m in range(3) for i in [b_list, l_list, d_list][m])
        model.Add(day_cals >= target - 250)
        model.Add(day_cals <= target + 250)

    # Variety
    for d in range(num_days - 1):
        for m in range(3):
            for i in [b_list, l_list, d_list][m]: model.Add(x[d, m, i] + x[d+1, m, i] <= 1)

    # Max 2 uses per plan period
    for i in range(len(candidates)):
        model.Add(sum(x[d, m, i] for d in range(num_days) for m in range(3) if (d, m, i) in x) <= 2)

    weights = [random.randint(1, 100) for _ in range(len(candidates))]
    model.Maximize(sum(x[d, m, i] * weights[i] for d in range(num_days) for m in range(3) if (d, m, i) in x))

    solver = cp_model.CpSolver()
    status = solver.Solve(model)

    if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
        res_plan = []
        day_labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Day 8", "Day 9", "Day 10"]
        for d in range(num_days):
            meals = []
            total = 0
            for m in range(3):
                for i in [b_list, l_list, d_list][m]:
                    if solver.Value(x[d, m, i]):
                        r = candidates[i]
                        meals.append(PlannedMeal(mealLabel=r["mealType"], recipeId=r["id"], title=r["title"]))
                        total += r["calories"]
            label = day_labels[d] if d < len(day_labels) else f"Day {d+1}"
            res_plan.append(DayPlan(dayLabel=label, meals=meals, totalCalories=total))
        return res_plan
    return None

# --- Endpoints ---

@app.get("/health")
def health(): return {"status": "alive"}

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
    raise HTTPException(status_code=404, detail="Recipe not found")

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest):
    print(f"\n>>> REQUEST: Generate {request.days} day plan for {request.profile.displayName}")
    try:
        all_recipes = database.get_all_recipes()
        result = solve_meal_plan(request, all_recipes)
        if result:
            return GeneratePlanResponse(
                weekLabel=f"PCOSINA {request.days}-Day Optimized Plan",
                days=result, status="success",
                message="Plan generated with deterministic variety logic."
            )
        else:
            raise HTTPException(status_code=422, detail="No feasible plan found.")
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
