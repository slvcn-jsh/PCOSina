from fastapi import FastAPI, HTTPException, Request, Depends, Header
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from starlette.responses import JSONResponse
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
import firebase_admin
from firebase_admin import credentials, auth
import sentry_sdk
from sentry_sdk.integrations.fastapi import FastApiIntegration

PLAN_CACHE_TTL_SECONDS = 600
PLAN_CACHE_MAX_SIZE = 200
_plan_cache = {}

sentry_dsn = os.getenv("SENTRY_DSN")
if sentry_dsn:
    sentry_sdk.init(
        dsn=sentry_dsn,
        integrations=[FastApiIntegration()],
        traces_sample_rate=float(os.getenv("SENTRY_TRACES_SAMPLE_RATE", "0.1")),
        environment=os.getenv("SENTRY_ENVIRONMENT", "production"),
        release=os.getenv("SENTRY_RELEASE"),
    )

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
    try:
        init_firebase()
    except Exception as e:
        print(f"WARNING: Firebase initialization failed: {e}")
        print("Backend will continue without Firebase Auth (Local Dev Mode)")
    
    database.init_db()
    database.seed_recipes()
    yield

docs_enabled = True # Always enable for easier testing
app = FastAPI(
    title="PCOSINA Optimization API",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)

MAX_REQUEST_BYTES = int(os.getenv("MAX_REQUEST_BYTES", str(512 * 1024)))

@app.middleware("http")
async def limit_request_size(request: Request, call_next):
    content_length = request.headers.get("content-length")
    if content_length is not None:
        try:
            if int(content_length) > MAX_REQUEST_BYTES:
                return JSONResponse(
                    status_code=413,
                    content={"detail": "Request too large"},
                )
        except ValueError:
            return JSONResponse(
                status_code=400,
                content={"detail": "Invalid Content-Length"},
            )
    return await call_next(request)

allowed_hosts = ["*"] # Allow all for local phone testing
app.add_middleware(TrustedHostMiddleware, allowed_hosts=allowed_hosts)

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

class FeedbackRequest(BaseModel):
    message: str

def init_firebase():
    if firebase_admin._apps:
        return
    if os.getenv("FIREBASE_AUTH_DISABLED", "").lower() == "true":
        return

    credentials_json = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
    credentials_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "backend/secrets/firebase-service-account.json")

    if credentials_json:
        try:
            cred = credentials.Certificate(json.loads(credentials_json))
        except Exception as e:
            raise RuntimeError("Invalid FIREBASE_SERVICE_ACCOUNT_JSON") from e
    elif os.path.exists(credentials_path):
        cred = credentials.Certificate(credentials_path)
    else:
        # Instead of crashing, we'll return a warning to the lifespan handler
        raise FileNotFoundError("Firebase serviceAccountKey.json missing")

    firebase_admin.initialize_app(cred)

def require_firebase_auth(authorization: str = Header(None)):
    # Local dev: If no firebase app is initialized, bypass auth
    if not firebase_admin._apps:
        return {"uid": "local-dev-user"}
        
    if os.getenv("FIREBASE_AUTH_DISABLED", "").lower() == "true":
        return {"uid": "auth-disabled-user"}
        
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization.split(" ", 1)[1].strip()
    try:
        decoded = auth.verify_id_token(token, check_revoked=True)
        return decoded
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

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
    pool = candidates[:120]
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
        solver.parameters.max_time_in_seconds = 3.0
        solver.parameters.num_search_workers = 8
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

def _cache_key(request: GeneratePlanRequest) -> str:
    try:
        return json.dumps(request.model_dump(), sort_keys=True)
    except Exception:
        return json.dumps({
            "profile": request.profile.model_dump() if hasattr(request.profile, "model_dump") else request.profile.dict(),
            "days": request.days
        }, sort_keys=True)

def _cache_get(key: str):
    item = _plan_cache.get(key)
    if not item:
        return None
    ts, value = item
    if (time.time() - ts) > PLAN_CACHE_TTL_SECONDS:
        _plan_cache.pop(key, None)
        return None
    return value

def _cache_set(key: str, value: GeneratePlanResponse):
    if len(_plan_cache) >= PLAN_CACHE_MAX_SIZE:
        oldest_key = min(_plan_cache.items(), key=lambda kv: kv[1][0])[0]
        _plan_cache.pop(oldest_key, None)
    _plan_cache[key] = (time.time(), value)

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(request: GeneratePlanRequest, user: Any = Depends(require_firebase_auth)):
    try:
        key = _cache_key(request)
        cached = _cache_get(key)
        if cached is not None:
            return cached

        all_recipes = database.get_all_recipes()
        result, msg = solve_meal_plan(request, all_recipes)
        if result:
            response = GeneratePlanResponse(weekLabel=f"PCOSINA {request.days}-Day Plan", days=result, status="success", message=msg)
            _cache_set(key, response)
            return response
        raise HTTPException(status_code=422, detail=f"Infeasible: {msg}")
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.get("/recipe/{recipe_id}", response_model=RecipeDetail)
async def get_recipe(recipe_id: str, user: Any = Depends(require_firebase_auth)):
    recipe = next((r for r in database.get_all_recipes() if r["id"] == recipe_id), None)
    if recipe: return RecipeDetail(**recipe)
    raise HTTPException(status_code=404, detail="Recipe not found")

@app.get("/health")
def health(): return {"status": "alive"}

@app.post("/feedback")
def feedback(payload: FeedbackRequest):
    try:
        # Minimal endpoint: log feedback for now (can be persisted later)
        print(f"Feedback received: {payload.message}")
        return {"status": "ok"}
    except Exception:
        raise HTTPException(status_code=500, detail="Failed to save feedback")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
