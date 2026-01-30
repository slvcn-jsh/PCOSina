from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel
from typing import List, Optional, Dict
import json
import os
import time
from ortools.sat.python import cp_model

app = FastAPI(title="PCOSINA Optimization API")

# --- Models ---
class UserProfile(BaseModel):
    displayName: str
    age: int
    heightCm: int
    weightKg: int
    activityLevel: str
    goal: str
    insulinResistanceLevel: str
    symptoms: List[str]
    comorbidities: List[str]
    dietaryRestrictions: List[str]
    allergies: List[str]
    weeklyBudgetPhp: int
    maxCookingTimeMinutes: int
    varietyPreference: str

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

# --- Data Loading ---
def load_recipes():
    # Attempt to load from JSON first, then fallback to DB if needed
    if os.path.exists("recipes.json"):
        with open("recipes.json", "r") as f:
            return json.load(f)
    return []

# --- MILP Logic ---
def solve_meal_plan(request: GeneratePlanRequest, all_recipes: List[Dict]):
    profile = request.profile

    # Simple target calculation
    bmr = (10 * profile.weightKg) + (6.25 * profile.heightCm) - (5 * profile.age) - 161
    target_calories = int(bmr * 1.3) # Simplified for test

    candidates = [r for r in all_recipes if r["mealType"] in ["Breakfast", "Lunch", "Dinner"]]

    if not candidates:
        return None

    model = cp_model.CpModel()
    num_days = request.days
    num_meals = 3

    x = {}
    for d in range(num_days):
        for m in range(num_meals):
            for i, r in enumerate(candidates):
                x[d, m, i] = model.NewBoolVar(f'x_{d}_{m}_{i}')

    for d in range(num_days):
        for m in range(num_meals):
            model.Add(sum(x[d, m, i] for i in range(len(candidates))) == 1)

    meal_labels = ["Breakfast", "Lunch", "Dinner"]
    for d in range(num_days):
        for m, label in enumerate(meal_labels):
            model.Add(sum(x[d, m, i] for i, r in enumerate(candidates) if r["mealType"] == label) == 1)

    solver = cp_model.CpSolver()
    status = solver.Solve(model)

    if status == cp_model.OPTIMAL or status == cp_model.FEASIBLE:
        plan_days = []
        day_labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        for d in range(num_days):
            day_meals = []
            day_total_cal = 0
            for m, label in enumerate(meal_labels):
                for i, r in enumerate(candidates):
                    if solver.Value(x[d, m, i]):
                        day_meals.append(PlannedMeal(
                            mealLabel=label,
                            recipeId=r["id"],
                            title=r["title"]
                        ))
                        day_total_cal += r["calories"]
            plan_days.append(DayPlan(dayLabel=day_labels[d], meals=day_meals, totalCalories=day_total_cal))
        return plan_days
    return None

# --- Endpoints ---
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    print(f"DEBUG: Incoming request from {request.client.host} to {request.url.path}")
    response = await call_next(request)
    return response

@app.get("/health")
def health_check():
    return {"status": "alive", "service": "PCOSINA Backend"}

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest):
    print(f"LOG: Generating plan for {request.profile.displayName}...")
    try:
        recipes = load_recipes()
        result = solve_meal_plan(request, recipes)
        if result:
            return GeneratePlanResponse(
                weekLabel=f"Optimized Plan for {request.profile.displayName}",
                days=result,
                status="success",
                message="Plan optimized successfully."
            )
        else:
            raise HTTPException(status_code=422, detail="No feasible plan found.")
    except Exception as e:
        print(f"ERROR: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    # 0.0.0.0 makes the server listen to ALL devices on your WiFi
    print("Starting PCOSINA Server on 0.0.0.0:8000...")
    uvicorn.run(app, host="0.0.0.0", port=8000)
