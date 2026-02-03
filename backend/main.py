from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, ConfigDict
from typing import List, Optional, Dict, Any
import json
import os
import time
import traceback
import random
import math
import socket
from contextlib import asynccontextmanager
from ortools.sat.python import cp_model
import database

def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        IP = s.getsockname()[0]
    except Exception:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("\n" + "="*50)
    print(f"PCOSINA BRAIN IS STARTING...")
    print(f"LOCAL IP: {get_ip()}")
    print(f"URL FOR PHONE: http://{get_ip()}:8000")
    print("="*50 + "\n")
    database.init_db()
    database.seed_recipes()
    yield

app = FastAPI(title="PCOSINA Optimization API", lifespan=lifespan)

# ... (keep all models and solve_meal_plan logic exactly the same as before) ...

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

def solve_meal_plan(request: GeneratePlanRequest, recipes: List[Dict]):
    profile = request.profile
    num_days = max(1, int(request.days or 7))
    slot_count = num_days * 3
    slot_labels = ["Breakfast", "Lunch", "Dinner"]
    w, h, a = (profile.weightKg if profile.weightKg > 0 else 65, profile.heightCm if profile.heightCm > 0 else 160, profile.age if profile.age > 0 else 25)
    bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
    target = int(bmr * 1.375)
    if "Weight Loss" in profile.goal: target -= 500
    target = max(1200, target)
    daily_targets = [target + random.randint(-50, 50) for _ in range(num_days)]
    PORK_BAN = ["pork", "baboy", "liempo", "ham", "bacon", "lechon", "litson", "longganisa", "sausage", "hotdog", "lard", "chicharon", "pata", "isaw", "intestine", "dugo", "blood", "dinuguan", "maskara", "tenga", "ears", "sisig", "tokwa't baboy", "kasim", "pigue", "menudo", "humba", "bagnet", "meatball"]
    BEEF_BAN = ["beef", "baka", "steak", "corned", "ribeye", "sirloin", "bulalo", "beefy", "laman-loob", "tripe", "tuwalya", "bituka", "liver", "atay", "tapa", "caldereta"]
    MEAT_BAN = PORK_BAN + BEEF_BAN + ["chicken", "manok", "meat", "lamb", "goat", "kambing", "mutton", "venison", "duck", "pato", "itlog na maalat", "balut", "laman", "karne"]
    SEAFOOD_BAN = ["fish", "isda", "shrimp", "hipon", "seafood", "crab", "alimasag", "alamang", "bagoong", "bangus", "tilapia", "tuna", "salmon", "squid", "pusit", "octopus", "mussel", "tahong", "oyster", "talaba", "patis", "tinapa", "daing", "tuyo"]
    DAIRY_BAN = ["milk", "cheese", "cream", "butter", "dairy", "gatas", "keso", "creamy", "yogurt", "condensed", "evaporated"]
    restriction_map = {"No Pork": PORK_BAN, "No Beef": BEEF_BAN, "Vegetarian": MEAT_BAN + SEAFOOD_BAN, "Pescatarian": MEAT_BAN, "Lactose Intolerant": DAIRY_BAN}
    candidates = []
    for r in recipes:
        title = str(r.get("title", "")).lower()
        tags = " ".join(r.get("tags", [])).lower()
        ings_list = r.get("ingredients", [])
        ings_text = ""
        for ing in ings_list:
            if isinstance(ing, dict): ings_text += " " + str(ing.get("name", ""))
            else: ings_text += " " + str(ing)
        haystack = f"{title} {tags} {ings_text.lower()}"
        exclude = False
        for rest in profile.dietaryRestrictions:
            banned_words = restriction_map.get(rest, [])
            for word in banned_words:
                if word in haystack:
                    exclude = True; break
            if exclude: break
        if not exclude: candidates.append(r)
    if len(candidates) < 5: return None, "No safe recipes found."
    random.shuffle(candidates)
    pool = candidates[:150]
    for max_per_week in [2, 3, 4, 10]:
        model = cp_model.CpModel()
        x = {} 
        for s in range(slot_count):
            for i in range(len(pool)): x[s, i] = model.NewBoolVar(f"x_{s}_{i}")
        for s in range(slot_count): model.Add(sum(x[s, i] for i in range(len(pool))) == 1)
        for s in range(slot_count - 1):
            for i in range(len(pool)): model.Add(x[s, i] + x[s+1, i] <= 1)
        for i in range(len(pool)): model.Add(sum(x[s, i] for s in range(slot_count)) <= max_per_week)
        for d in range(num_days):
            day_slots = range(d * 3, d * 3 + 3)
            day_cals = sum(x[s, i] * int(pool[i].get("calories", 0)) for s in day_slots for i in range(len(pool)))
            err = model.NewIntVar(0, 1500, f"err_{d}")
            model.Add(err >= day_cals - daily_targets[d])
            model.Add(err >= daily_targets[d] - day_cals)
        random.seed(f"{profile.displayName}_{time.time()}")
        weights = [random.randint(1, 100) for _ in range(len(pool))]
        model.Maximize(sum(x[s, i] * weights[i] for s in range(slot_count) for i in range(len(pool))))
        solver = cp_model.CpSolver()
        solver.parameters.max_time_in_seconds = 4.0
        status = solver.Solve(model)
        if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
            res_plan = []
            day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
            for d in range(num_days):
                meals = []
                total = 0
                for m in range(3):
                    idx = d * 3 + m
                    for i in range(len(pool)):
                        if solver.Value(x[idx, i]):
                            r = pool[i]
                            meals.append(PlannedMeal(mealLabel=slot_labels[m], recipeId=r["id"], title=r["title"]))
                            total += int(r.get("calories", 0))
                            break
                res_plan.append(DayPlan(dayLabel=day_names[d] if d < 7 else f"Day {d+1}", meals=meals, totalCalories=total))
            return res_plan, "Success"
    return None, "Infeasible"

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest):
    try:
        all_recipes = database.get_all_recipes()
        result, msg = solve_meal_plan(request, all_recipes)
        if result:
            return GeneratePlanResponse(weekLabel=f"PCOSINA {request.days}-Day Plan", days=result, status="success", message=msg)
        raise HTTPException(status_code=422, detail=f"Infeasible: {msg}")
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.get("/recipe/{recipe_id}", response_model=RecipeDetail)
async def get_recipe(recipe_id: str):
    recipe = next((r for r in database.get_all_recipes() if r["id"] == recipe_id), None)
    if recipe: return RecipeDetail(**recipe)
    raise HTTPException(status_code=404, detail="Recipe not found")

@app.get("/health")
def health(): return {"status": "alive"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
