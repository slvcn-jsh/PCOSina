from typing import List, Optional, Dict, Any
import json
import os
import random
import time

from ortools.sat.python import cp_model

from domain.models import GeneratePlanRequest, PlannedMeal, DayPlan, UserProfile
from price_catalog import estimate_recipe_cost


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if not raw:
        return default
    try:
        return int(raw)
    except Exception:
        return default


def _env_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if not raw:
        return default
    try:
        return float(raw)
    except Exception:
        return default


def _env_float_min(name: str, default: float) -> float:
    value = _env_float(name, default)
    return value if value >= default else default


def _env_int_list(name: str, default: List[int]) -> List[int]:
    raw = os.getenv(name)
    if not raw:
        return default
    values: List[int] = []
    for part in raw.split(","):
        part = part.strip()
        if not part:
            continue
        try:
            values.append(int(part))
        except Exception:
            continue
    return values if values else default


def _env_float_list(name: str, default: List[float]) -> List[float]:
    raw = os.getenv(name)
    if not raw:
        return default
    values: List[float] = []
    for part in raw.split(","):
        part = part.strip()
        if not part:
            continue
        try:
            values.append(float(part))
        except Exception:
            continue
    return values if values else default


def _env_bool(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return str(raw).strip().lower() in ("1", "true", "yes", "on")


# -------------------------
# Tagging + Normalization
# -------------------------
ING_SYNONYMS = {
    "baboy": "pork",
    "liempo": "pork",
    "lechon": "pork",
    "litson": "pork",
    "baka": "beef",
    "bulalo": "beef",
    "tapa": "beef",
    "manok": "chicken",
    "isda": "fish",
    "hipon": "shrimp",
    "pusit": "squid",
    "gatas": "dairy",
    "keso": "cheese",
    "itlog": "egg",
    "pechay": "bok_choy",
    "sitaw": "string_beans",
    "tokwa": "tofu",
}

ALLERGEN_SYNONYMS = {
    "peanut": "peanut",
    "peanuts": "peanut",
    "nuts": "nuts",
    "tree_nut": "nuts",
    "almond": "nuts",
    "cashew": "nuts",
    "walnut": "nuts",
    "hazelnut": "nuts",
    "dairy": "dairy",
    "milk": "dairy",
    "gatas": "dairy",
    "cheese": "dairy",
    "keso": "dairy",
    "egg": "egg",
    "itlog": "egg",
    "fish": "fish",
    "isda": "fish",
    "shellfish": "shellfish",
    "shrimp": "shellfish",
    "hipon": "shellfish",
    "crab": "shellfish",
    "soy": "soy",
    "toyo": "soy",
    "tofu": "soy",
    "wheat": "wheat",
    "gluten": "gluten",
    "sesame": "sesame",
}

MEAT_TOKENS = {"pork", "beef", "chicken", "meat", "lamb", "goat", "duck"}
SEAFOOD_TOKENS = {"fish", "shrimp", "squid", "tuna", "salmon", "crab", "seafood"}
DAIRY_TOKENS = {"dairy", "milk", "cheese", "yogurt", "cream", "butter"}
EGG_TOKENS = {"egg"}
PROTEIN_GROUP_TOKENS = {
    "pork": {"pork"},
    "beef": {"beef"},
    "chicken": {"chicken"},
    "fish": {"fish", "shrimp", "squid", "tuna", "salmon", "crab", "seafood"},
    "egg": {"egg"},
    "tofu": {"tofu"},
}
VEG_TOKENS = {
    "pechay", "sitaw", "ampalaya", "talong", "kamatis", "okra",
    "kalabasa", "sayote", "saluyot", "kangkong", "malunggay",
    "cabbage", "carrot", "onion", "garlic", "eggplant", "tomato",
    "string_beans", "bok_choy", "squash", "gourd", "bitter_gourd"
}

MEAL_LABELS = ["Breakfast", "Lunch", "Dinner"]


def _normalize_token(t: str) -> str:
    t = "".join(ch for ch in t.lower() if ch.isalnum() or ch in ("_", "-"))
    return ING_SYNONYMS.get(t, t)


def normalize_ingredients(ings: List[Any]) -> List[str]:
    tokens = []
    for ing in ings:
        name = ""
        if isinstance(ing, dict):
            name = str(ing.get("name", ""))
        else:
            name = str(ing)
        for raw in name.replace("/", " ").replace("-", " ").split():
            tok = _normalize_token(raw)
            if tok:
                tokens.append(tok)
    return tokens


def infer_veg_tokens(ing_tokens: List[str]) -> List[str]:
    return list(set(ing_tokens or []) & VEG_TOKENS)


def normalize_pantry(pantry: List[str]) -> List[str]:
    tokens = []
    for item in pantry or []:
        for raw in str(item).replace("/", " ").replace("-", " ").split():
            tok = _normalize_token(raw)
            if tok:
                tokens.append(tok)
    return tokens


def normalize_allergies(allergies: List[str]) -> List[str]:
    tokens = []
    for item in allergies or []:
        for raw in str(item).replace("/", " ").replace("-", " ").split():
            tok = _normalize_token(raw)
            if tok:
                tokens.append(ALLERGEN_SYNONYMS.get(tok, tok))
    return tokens


def infer_allowed_meals(meal_type: str | None) -> List[str]:
    if not meal_type:
        return MEAL_LABELS
    raw = meal_type.lower()
    if "universal" in raw:
        return MEAL_LABELS
    labels = []
    if "break" in raw:
        labels.append("Breakfast")
    if "lunch" in raw:
        labels.append("Lunch")
    if "dinner" in raw:
        labels.append("Dinner")
    return labels or MEAL_LABELS


def infer_tags(recipe: Dict[str, Any]) -> List[str]:
    tags = set([t.lower() for t in recipe.get("tags", []) if t])
    ing_tokens = set(normalize_ingredients(recipe.get("ingredients", [])))
    if ing_tokens & MEAT_TOKENS:
        tags.add("contains_meat")
    if ing_tokens & SEAFOOD_TOKENS:
        tags.add("contains_seafood")
    if ing_tokens & DAIRY_TOKENS:
        tags.add("contains_dairy")
    if ing_tokens & EGG_TOKENS:
        tags.add("contains_egg")

    p = recipe.get("proteinGrams") or 0
    c = recipe.get("carbsGrams") or 0
    fiber = recipe.get("fiberGrams") or 0
    if p >= 25:
        tags.add("high_protein")
    if fiber >= 8:
        tags.add("high_fiber")
    if c <= 35:
        tags.add("low_carb")
    return list(tags)


def infer_protein_group(ing_tokens: List[str]) -> str:
    toks = set(ing_tokens or [])
    for group, tokens in PROTEIN_GROUP_TOKENS.items():
        if toks & tokens:
            return group
    return "other"


def estimate_cost(recipe: Dict[str, Any]) -> int:
    ings = recipe.get("ingredients", [])
    catalog_cost = estimate_recipe_cost(ings)
    if catalog_cost > 0:
        return catalog_cost
    cal = recipe.get("calories") or 0
    rough = (len(ings) * 6) + (cal * 0.15)
    return int(max(30, min(450, rough)))


def _base_score(recipe: Dict[str, Any]) -> float:
    p = recipe.get("proteinGrams") or 0
    cals = recipe.get("calories") or 0
    pantry_bonus = (recipe.get("_pantry_match") or 0) * 1.5
    return (p * 2.0) - (recipe.get("_cost_est", 0) * 0.05) - abs(cals - 500) * 0.15 + pantry_bonus


def passes_restrictions(profile: UserProfile, tags: List[str], ing_tokens: List[str]) -> bool:
    restrictions = set(profile.dietaryRestrictions or [])
    tagset = set(tags)
    toks = set(ing_tokens)
    allergy_tokens = set(normalize_allergies(profile.allergies or []))
    if allergy_tokens and (toks & allergy_tokens):
        return False
    if "No Pork" in restrictions and "pork" in toks:
        return False
    if "No Beef" in restrictions and "beef" in toks:
        return False
    if "Vegetarian" in restrictions and (("contains_meat" in tagset) or ("contains_seafood" in tagset)):
        return False
    if "Pescatarian" in restrictions and ("contains_meat" in tagset):
        return False
    if "Lactose Intolerant" in restrictions and ("contains_dairy" in tagset):
        return False
    return True


def validate_profile(profile: UserProfile) -> Optional[str]:
    restrictions = set(profile.dietaryRestrictions or [])
    if "Pescatarian" in restrictions and ("No Seafood" in restrictions or "No Fish" in restrictions):
        return "Conflicting restrictions: Pescatarian + No Seafood."
    if "Vegetarian" in restrictions and ("No Eggs" in restrictions and "No Dairy" in restrictions):
        return "Very restrictive: Vegetarian + No Eggs + No Dairy."
    return None


def resolve_budget_weekly(profile: UserProfile) -> Optional[float]:
    if profile.weeklyBudgetPhp and profile.weeklyBudgetPhp > 0:
        return float(profile.weeklyBudgetPhp)
    if profile.budgetWeekly and profile.budgetWeekly > 0:
        return float(profile.budgetWeekly)
    if profile.budgetMonthly and profile.budgetMonthly > 0:
        return float(profile.budgetMonthly) / 4.33
    return None


def macro_ratios(insulin_level: str | None) -> tuple[float, float, float]:
    raw = (insulin_level or "").lower()
    if "severe" in raw:
        return (0.30, 0.30, 0.40)
    if "moderate" in raw:
        return (0.28, 0.35, 0.37)
    return (0.25, 0.40, 0.35)


def activity_multiplier(level: str | None) -> float:
    raw = (level or "").strip()
    if raw == "Sedentary":
        return 1.2
    if raw == "Moderately Active":
        return 1.55
    if raw == "Very Active":
        return 1.725
    return 1.375


def variety_weights(preference: str | None) -> Dict[str, int]:
    raw = (preference or "").lower()
    if "high" in raw:
        return {"repeat_weight": 7, "group_weight": 3, "diversity_weight": 2, "pantry_weight": 1}
    if "low" in raw:
        return {"repeat_weight": 3, "group_weight": 1, "diversity_weight": 1, "pantry_weight": 1}
    return {"repeat_weight": 5, "group_weight": 2, "diversity_weight": 1, "pantry_weight": 1}


def adjust_max_per_week(base: List[int], preference: str | None) -> List[int]:
    raw = (preference or "").lower()
    if "high" in raw:
        return [v for v in base if v <= 4] or [2, 3, 4]
    if "low" in raw:
        extended = sorted(set(base + [6, 8, 10]))
        return extended
    return base


def priority_overrides(priority: str | None) -> Dict[str, int]:
    raw = (priority or "").lower()
    if "budget" in raw:
        return {
            "budget_mult": 2,
            "repeat_weight": 3,
            "group_weight": 1,
            "diversity_weight": 1,
            "macro_mult": 1,
            "variety_mult": 1,
        }
    if "variety" in raw:
        return {
            "budget_mult": 1,
            "repeat_weight": 7,
            "group_weight": 3,
            "diversity_weight": 2,
            "macro_mult": 1,
            "variety_mult": 2,
        }
    if "nutrition" in raw or "tight" in raw:
        return {
            "budget_mult": 1,
            "macro_mult": 2,
            "variety_mult": 1,
        }
    return {"budget_mult": 1, "macro_mult": 1, "variety_mult": 1}


def shortlist_candidates(profile: UserProfile, recipes: List[Dict[str, Any]]) -> Dict[str, List[Dict[str, Any]]]:
    buckets = {"Breakfast": [], "Lunch": [], "Dinner": [], "Universal": []}
    restriction_count = len(profile.dietaryRestrictions or [])
    budget_weekly = resolve_budget_weekly(profile)
    max_cook = profile.maxCookingTimeMinutes if profile.maxCookingTimeMinutes and profile.maxCookingTimeMinutes > 0 else None
    pantry_tokens = set(normalize_pantry(profile.pantryItems or []))
    for r in recipes:
        tags = infer_tags(r)
        ing_tokens = normalize_ingredients(r.get("ingredients", []))
        if not passes_restrictions(profile, tags, ing_tokens):
            continue
        minutes = int(r.get("minutes") or 0)
        if max_cook is not None and minutes > max_cook:
            continue
        r["_tags"] = tags
        r["_ing_tokens"] = ing_tokens
        r["_cost_est"] = estimate_cost(r)
        r["_protein_group"] = infer_protein_group(ing_tokens)
        r["_veg_tokens"] = infer_veg_tokens(ing_tokens)
        r["_allowed_meals"] = infer_allowed_meals(r.get("mealType"))
        if pantry_tokens:
            r["_pantry_match"] = len(set(ing_tokens) & pantry_tokens)
        else:
            r["_pantry_match"] = 0
        meal_type = (r.get("mealType") or "Universal").lower()
        if "break" in meal_type:
            buckets["Breakfast"].append(r)
        elif "lunch" in meal_type:
            buckets["Lunch"].append(r)
        elif "dinner" in meal_type:
            buckets["Dinner"].append(r)
        else:
            buckets["Universal"].append(r)

    for k in buckets:
        buckets[k].sort(key=_base_score, reverse=True)
        limit_default = _env_int("PCOSINA_SHORTLIST_LIMIT", 80)
        limit_restricted = _env_int("PCOSINA_SHORTLIST_LIMIT_RESTRICTED", 120)
        limit = limit_default if restriction_count < 2 else limit_restricted
        buckets[k] = buckets[k][:limit]
        if budget_weekly:
            buckets[k].sort(key=lambda r: r.get("_cost_est", 0))
            keep_min = _env_int("PCOSINA_SHORTLIST_KEEP_MIN", 20)
            keep_ratio = _env_float("PCOSINA_SHORTLIST_KEEP_RATIO", 0.8)
            keep = int(max(keep_min, len(buckets[k]) * keep_ratio))
            buckets[k] = buckets[k][:keep]
    return buckets


def _calorie_bin(calories: int) -> str:
    if calories < 350:
        return "lt350"
    if calories < 500:
        return "350_499"
    if calories < 650:
        return "500_649"
    return "ge650"


def _cap_pool(pool: List[Dict[str, Any]], max_pool: int) -> List[Dict[str, Any]]:
    if len(pool) <= max_pool:
        return pool
    scored = []
    for r in pool:
        rid = str(r.get("id", ""))
        scored.append((_base_score(r), rid, r))
    scored.sort(key=lambda item: (-item[0], item[1]))
    top_k = max(1, int(max_pool * 0.6))
    selected = [r for _, _, r in scored[:top_k]]
    selected_ids = {str(r.get("id", "")) for r in selected}
    groups: Dict[str, List[tuple]] = {}
    for score, rid, r in scored[top_k:]:
        if rid in selected_ids:
            continue
        group = r.get("_protein_group") or "other"
        calories = int(r.get("calories") or 0)
        group = f"{group}:{_calorie_bin(calories)}"
        groups.setdefault(group, []).append((score, rid, r))
    group_keys = sorted(groups.keys())
    while len(selected) < max_pool and group_keys:
        progressed = False
        for group in list(group_keys):
            if len(selected) >= max_pool:
                break
            bucket = groups[group]
            if not bucket:
                group_keys.remove(group)
                continue
            _, rid, r = bucket.pop(0)
            if rid in selected_ids:
                continue
            selected.append(r)
            selected_ids.add(rid)
            progressed = True
        if not progressed:
            break
    if len(selected) < max_pool:
        for _, rid, r in scored:
            if len(selected) >= max_pool:
                break
            if rid in selected_ids:
                continue
            selected.append(r)
            selected_ids.add(rid)
    return selected


def _default_weight_set() -> Optional[Dict[str, int]]:
    raw = os.getenv("PCOSINA_MILP_WEIGHTS")
    if not raw:
        return None
    try:
        parsed = json.loads(raw)
        if isinstance(parsed, dict):
            return parsed
    except Exception:
        return None
    return None


def _build_explanation(
    selected: List[Dict[str, Any]],
    num_days: int,
    daily_targets: List[int],
    target: int,
    target_protein: int,
    target_carbs: int,
    target_fats: int,
    tol: float,
    max_per_week: int,
    profile: UserProfile,
    budget_weekly: Optional[float],
) -> Dict[str, Any]:
    if not selected or num_days <= 0:
        return {}
    daily_cals = []
    daily_pro = []
    daily_carb = []
    daily_fat = []
    for d in range(num_days):
        day_items = selected[d * 3:(d * 3 + 3)]
        daily_cals.append(sum(int(r.get("calories", 0)) for r in day_items))
        daily_pro.append(sum(int(r.get("proteinGrams", 0)) for r in day_items))
        daily_carb.append(sum(int(r.get("carbsGrams", 0)) for r in day_items))
        daily_fat.append(sum(int(r.get("fatsGrams", 0)) for r in day_items))
    avg_cal = int(sum(daily_cals) / num_days)
    avg_pro = int(sum(daily_pro) / num_days)
    avg_carb = int(sum(daily_carb) / num_days)
    avg_fat = int(sum(daily_fat) / num_days)
    avg_dev = int(sum(abs(daily_cals[i] - daily_targets[i]) for i in range(num_days)) / num_days)
    pantry_matches = sum(int(r.get("_pantry_match", 0)) for r in selected)
    unique_veg = len({t for r in selected for t in r.get("_veg_tokens", [])})
    est_cost = sum(int(r.get("_cost_est", 0)) for r in selected)
    confidence = 100
    confidence -= min(30, int(avg_dev / 10))
    confidence -= min(10, int(max(0.0, tol - 0.2) * 50))
    confidence -= max(0, int(max_per_week - 2) * 3)
    confidence -= min(20, int(len(profile.dietaryRestrictions or []) * 2))
    if budget_weekly and est_cost > budget_weekly:
        overshoot = (est_cost - budget_weekly) / max(1.0, budget_weekly)
        confidence -= min(15, int(overshoot * 50))
    confidence = max(0, min(100, confidence))
    return {
        "confidenceScore": confidence,
        "targetCalories": target,
        "avgCalories": avg_cal,
        "avgCaloriesDeviation": avg_dev,
        "targetProtein": target_protein,
        "avgProtein": avg_pro,
        "targetCarbs": target_carbs,
        "avgCarbs": avg_carb,
        "targetFats": target_fats,
        "avgFats": avg_fat,
        "toleranceUsed": tol,
        "maxPerWeek": max_per_week,
        "pantryMatches": pantry_matches,
        "uniqueVegTokens": unique_veg,
        "budgetWeekly": budget_weekly,
        "estimatedWeeklyCost": est_cost,
        "restrictionCount": len(profile.dietaryRestrictions or []),
    }


def _greedy_fallback_plan(
    pool: List[Dict[str, Any]],
    meal_to_allowed: Dict[str, set],
    slot_labels: List[str],
    num_days: int,
    base_scores: List[float],
    max_per_week: int,
) -> tuple[List[DayPlan], List[Dict[str, Any]]]:
    selected: List[Dict[str, Any]] = []
    res_plan: List[DayPlan] = []
    usage = [0] * len(pool)
    prev_idx = None
    day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    for d in range(num_days):
        meals = []
        total = 0
        for m in range(3):
            label = slot_labels[m]
            allowed = list(meal_to_allowed.get(label, set(range(len(pool)))))
            allowed.sort(key=lambda i: base_scores[i], reverse=True)
            pick = None
            for idx in allowed:
                if idx == prev_idx:
                    continue
                if usage[idx] >= max_per_week:
                    continue
                pick = idx
                break
            if pick is None:
                for idx in allowed:
                    if idx != prev_idx:
                        pick = idx
                        break
            if pick is None and allowed:
                pick = allowed[0]
            if pick is None:
                pick = 0
            usage[pick] += 1
            prev_idx = pick
            r = pool[pick]
            selected.append(r)
            meals.append(PlannedMeal(mealLabel=label, recipeId=r["id"], title=r["title"]))
            total += int(r.get("calories", 0))
        res_plan.append(DayPlan(dayLabel=day_names[d] if d < 7 else f"Day {d + 1}", meals=meals, totalCalories=total))
    return res_plan, selected


def solve_meal_plan(
    request: GeneratePlanRequest,
    recipes: List[Dict],
    weight_set: Optional[Dict[str, int]] = None
):
    profile = request.profile
    debug_solver = _env_bool("PCOSINA_DEBUG_SOLVER", False)
    debug_summary = {
        "pool": 0,
        "allowed_sizes": {},
        "attempts": [],
    }
    conflict = validate_profile(profile)
    if conflict:
        return None, conflict, None
    num_days = max(1, int(request.days or 7))
    slot_labels = MEAL_LABELS
    slot_count = num_days * len(slot_labels)
    w, h, a = (
        profile.weightKg if profile.weightKg > 0 else 65,
        profile.heightCm if profile.heightCm > 0 else 160,
        profile.age if profile.age > 0 else 25
    )
    bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
    target = int(bmr * activity_multiplier(profile.activityLevel))
    if "Weight Loss" in profile.goal:
        target -= 500
    target = max(1200, target)
    seed_salt = os.getenv("PCOSINA_SEED_SALT", "").strip()
    seed_key = f"{seed_salt}|{profile.displayName}_{profile.age}_{profile.heightCm}_{profile.weightKg}_{profile.activityLevel}_{profile.goal}_{profile.dietaryRestrictions}_{num_days}"
    rng = random.Random(seed_key)
    daily_targets = [target + rng.randint(-50, 50) for _ in range(num_days)]
    daily_targets = [max(1200, t) for t in daily_targets]
    # Macro targets based on calories, adjusted by insulin resistance level
    protein_ratio, carb_ratio, fat_ratio = macro_ratios(profile.insulinResistanceLevel)
    target_protein = int((target * protein_ratio) / 4)
    target_carbs = int((target * carb_ratio) / 4)
    target_fats = int((target * fat_ratio) / 9)
    tolerance_levels = _env_float_list("PCOSINA_TOLERANCE_LEVELS", [0.2, 0.3, 0.4])
    # Stage 1 pruning + shortlist
    buckets = shortlist_candidates(profile, recipes)
    candidates = list({r["id"]: r for r in (buckets["Breakfast"] + buckets["Lunch"] + buckets["Dinner"] + buckets["Universal"])}.values())
    if len(candidates) < 10:
        return None, "No safe recipes found.", None

    pool = candidates
    max_pool_size = 100
    if len(pool) > max_pool_size:
        if debug_solver:
            debug_summary["pool_pre_cap"] = len(pool)
        pool = _cap_pool(pool, max_pool_size)
    if debug_solver:
        debug_summary["pool"] = len(pool)
    # Enforce mealType where possible; Universal recipes are allowed everywhere.
    meal_to_allowed = {}
    for label in slot_labels:
        allowed = set(
            i for i, r in enumerate(pool)
            if label in (r.get("_allowed_meals") or MEAL_LABELS)
        )
        meal_to_allowed[label] = allowed
    if any(len(v) == 0 for v in meal_to_allowed.values()):
        meal_to_allowed = {label: set(range(len(pool))) for label in slot_labels}
    if debug_solver:
        debug_summary["allowed_sizes"] = {k: len(v) for k, v in meal_to_allowed.items()}
    base_scores = []
    for r in pool:
        base_scores.append(_base_score(r))

    # Render/free instances are CPU-limited; give the solver more time by default.
    total_time_limit = _env_float_min("PCOSINA_TOTAL_SOLVER_SECONDS", 25.0)
    started_at = time.time()
    max_per_week_list = adjust_max_per_week(
        _env_int_list("PCOSINA_MAX_PER_WEEK", [2, 3, 4, 10]),
        profile.varietyPreference
    )
    for tol in tolerance_levels:
        protein_bounds = (int(target_protein * (1 - tol)), int(target_protein * (1 + tol)))
        carbs_bounds = (int(target_carbs * (1 - tol)), int(target_carbs * (1 + tol)))
        fats_bounds = (int(target_fats * (1 - tol)), int(target_fats * (1 + tol)))
        for max_per_week in max_per_week_list:
            if (time.time() - started_at) >= total_time_limit:
                break
            model = cp_model.CpModel()
            x = {}
            for s in range(slot_count):
                for i in range(len(pool)):
                    x[s, i] = model.NewBoolVar(f"x_{s}_{i}")
            y = {}
            for i in range(len(pool)):
                y[i] = model.NewBoolVar(f"y_{i}")
                for s in range(slot_count):
                    model.Add(x[s, i] <= y[i])
            for s in range(slot_count):
                meal_label = slot_labels[s % 3]
                allowed = meal_to_allowed.get(meal_label, set(range(len(pool))))
                model.Add(sum(x[s, i] for i in allowed) == 1)
            # Greedy warm-start (hint)
            prev_idx = None
            for s in range(slot_count):
                meal_label = slot_labels[s % 3]
                allowed = list(meal_to_allowed.get(meal_label, set(range(len(pool)))))
                allowed.sort(key=lambda i: base_scores[i], reverse=True)
                pick = None
                for idx in allowed:
                    if idx != prev_idx:
                        pick = idx
                        break
                if pick is not None:
                    model.AddHint(x[s, pick], 1)
                    prev_idx = pick
            for s in range(slot_count - 1):
                for i in range(len(pool)):
                    model.Add(x[s, i] + x[s + 1, i] <= 1)
            for i in range(len(pool)):
                model.Add(sum(x[s, i] for s in range(slot_count)) <= max_per_week)
            repeat_over_vars = []
            for i in range(len(pool)):
                used_count = sum(x[s, i] for s in range(slot_count))
                repeat_over = model.NewIntVar(0, slot_count, f"repeat_over_{i}")
                model.Add(used_count - 1 <= repeat_over)
                model.Add(repeat_over >= 0)
                repeat_over_vars.append(repeat_over)
            # Protein group diversity (soft)
            group_over_vars = []
            group_limit = max(2, num_days)
            groups = {}
            for i in range(len(pool)):
                g = pool[i].get("_protein_group", "other")
                groups.setdefault(g, []).append(i)
            for g, idxs in groups.items():
                count = sum(x[s, i] for s in range(slot_count) for i in idxs)
                over = model.NewIntVar(0, slot_count, f"group_over_{g}")
                model.Add(count - group_limit <= over)
                model.Add(over >= 0)
                group_over_vars.append(over)
            # Ingredient diversity (soft) based on vegetable tokens
            veg_tokens = set()
            for r in pool:
                for t in r.get("_veg_tokens", []):
                    veg_tokens.add(t)
            veg_cov = {}
            for t in veg_tokens:
                veg_cov[t] = model.NewBoolVar(f"veg_{t}")
            for t in veg_tokens:
                # If any recipe containing token t is selected, veg_cov[t] can be 1
                related_idxs = [i for i, r in enumerate(pool) if t in r.get("_veg_tokens", [])]
                if related_idxs:
                    model.AddMaxEquality(veg_cov[t], [x[s, i] for s in range(slot_count) for i in related_idxs])
            min_diversity = min(5, len(veg_tokens)) if veg_tokens else 0
            diversity_slack = None
            if min_diversity > 0:
                diversity_slack = model.NewIntVar(0, min_diversity, "diversity_slack")
                model.Add(sum(veg_cov.values()) + diversity_slack >= min_diversity)
            pantry_bonus_vars = []
            pantry_match_total = None
            pantry = set(normalize_pantry(profile.pantryItems or []))
            if pantry:
                for i in range(len(pool)):
                    match_count = len(set(pool[i].get("_ing_tokens", [])) & pantry)
                    if match_count > 0:
                        pantry_bonus_vars.append(match_count * sum(x[s, i] for s in range(slot_count)))
                if pantry_bonus_vars:
                    pantry_match_total = sum(pantry_bonus_vars)
            budget_weekly = resolve_budget_weekly(profile)
            if budget_weekly:
                total_cost = sum(x[s, i] * int(pool[i].get("_cost_est", 0)) for s in range(slot_count) for i in range(len(pool)))
                budget_over = model.NewIntVar(0, 1000000, "budget_over")
                model.Add(total_cost - int(budget_weekly) <= budget_over)
                model.Add(budget_over >= 0)
            else:
                budget_over = None
            err_vars = []
            dev_pro_vars = []
            dev_carb_vars = []
            dev_fat_vars = []
            meal_err_vars = []
            for d in range(num_days):
                day_slots = range(d * 3, d * 3 + 3)
                day_cals = sum(x[s, i] * int(pool[i].get("calories", 0)) for s in day_slots for i in range(len(pool)))
                err = model.NewIntVar(0, 1500, f"err_{d}")
                model.Add(err >= day_cals - daily_targets[d])
                model.Add(err >= daily_targets[d] - day_cals)
                err_vars.append(err)
                day_pro = sum(x[s, i] * int(pool[i].get("proteinGrams", 0)) for s in day_slots for i in range(len(pool)))
                day_carb = sum(x[s, i] * int(pool[i].get("carbsGrams", 0)) for s in day_slots for i in range(len(pool)))
                day_fat = sum(x[s, i] * int(pool[i].get("fatsGrams", 0)) for s in day_slots for i in range(len(pool)))
                dev_pro = model.NewIntVar(0, 300, f"dev_pro_{d}")
                dev_carb = model.NewIntVar(0, 300, f"dev_carb_{d}")
                dev_fat = model.NewIntVar(0, 200, f"dev_fat_{d}")
                dev_pro_vars.append(dev_pro)
                dev_carb_vars.append(dev_carb)
                dev_fat_vars.append(dev_fat)
                model.Add(day_pro - protein_bounds[1] <= dev_pro)
                model.Add(protein_bounds[0] - day_pro <= dev_pro)
                model.Add(day_carb - carbs_bounds[1] <= dev_carb)
                model.Add(carbs_bounds[0] - day_carb <= dev_carb)
                model.Add(day_fat - fats_bounds[1] <= dev_fat)
                model.Add(fats_bounds[0] - day_fat <= dev_fat)
                # Meal-level calorie balance (soft)
                target_meal = max(300, int(daily_targets[d] / 3))
                for m in range(3):
                    slot = d * 3 + m
                    meal_cals = sum(x[slot, i] * int(pool[i].get("calories", 0)) for i in range(len(pool)))
                    meal_err = model.NewIntVar(0, 1200, f"meal_err_{slot}")
                    model.Add(meal_err >= meal_cals - target_meal)
                    model.Add(meal_err >= target_meal - meal_cals)
                    meal_err_vars.append(meal_err)
            total_err = sum(err_vars)
            total_dev_pro = sum(dev_pro_vars)
            total_dev_carb = sum(dev_carb_vars)
            total_dev_fat = sum(dev_fat_vars)
            total_meal_err = sum(meal_err_vars) if meal_err_vars else 0
            total_repeat_over = sum(repeat_over_vars)
            total_group_over = sum(group_over_vars) if group_over_vars else 0
            budget_penalty = budget_over if budget_over is not None else 0
            pantry_reward = sum(pantry_bonus_vars) if pantry_bonus_vars else 0
            diversity_reward = sum(veg_cov.values()) if veg_cov else 0
            diversity_penalty = (5 * diversity_slack) if diversity_slack is not None else 0
            pantry_min_slack = None
            pantry_min_penalty = 0
            if pantry and pantry_match_total is not None:
                pantry_min = min(4, len(pantry))
                pantry_min_slack = model.NewIntVar(0, pantry_min, "pantry_min_slack")
                model.Add(pantry_match_total + pantry_min_slack >= pantry_min)
                pantry_min_penalty = pantry_min_slack * 3
            priority = priority_overrides(profile.planningPriority)
            weights = variety_weights(profile.varietyPreference)
            weights.update(_default_weight_set() or {})
            if weight_set:
                weights.update(weight_set)
            if "repeat_weight" in priority:
                weights["repeat_weight"] = priority["repeat_weight"]
            if "group_weight" in priority:
                weights["group_weight"] = priority["group_weight"]
            if "diversity_weight" in priority:
                weights["diversity_weight"] = priority["diversity_weight"]
            repeat_w = int(weights.get("repeat_weight", 5)) * int(priority.get("variety_mult", 1))
            group_w = int(weights.get("group_weight", 2)) * int(priority.get("variety_mult", 1))
            diversity_w = int(weights.get("diversity_weight", 1)) * int(priority.get("variety_mult", 1))
            pantry_w = int(weights.get("pantry_weight", 1))
            macro_mult = int(priority.get("macro_mult", 1))
            budget_mult = int(priority.get("budget_mult", 1))
            model.Minimize(
                (macro_mult * total_err) + (macro_mult * total_meal_err) +
                (macro_mult * 2 * total_dev_pro) + (macro_mult * total_dev_carb) + (macro_mult * total_dev_fat) +
                (budget_mult * budget_penalty) + (repeat_w * total_repeat_over) + (group_w * total_group_over) +
                diversity_penalty - (pantry_w * pantry_reward) - (diversity_w * diversity_reward)
                + pantry_min_penalty
            )
            solver = cp_model.CpSolver()
            base_time = _env_float_min("PCOSINA_SOLVER_TIME_SECONDS", 6.0)
            max_time = _env_float_min("PCOSINA_SOLVER_MAX_SECONDS", 12.0)
            size_factor = max(0.0, (len(pool) - 60) / 40.0)
            restriction_factor = min(4.0, len(profile.dietaryRestrictions or []) / 2.0)
            adaptive_time = min(max_time, base_time + size_factor + restriction_factor)
            solver.parameters.max_time_in_seconds = adaptive_time
            cpu_count = os.cpu_count() or 1
            solver.parameters.num_search_workers = _env_int("PCOSINA_SOLVER_WORKERS", min(4, cpu_count))
            status = solver.Solve(model)
            if debug_solver:
                try:
                    status_name = solver.StatusName(status)
                except Exception:
                    status_name = str(status)
                debug_summary["attempts"].append({
                    "tol": tol,
                    "max_per_week": max_per_week,
                    "status": status_name,
                })
            if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
                res_plan = []
                day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
                selected = []
                for d in range(num_days):
                    meals = []
                    total = 0
                    for m in range(3):
                        idx = d * 3 + m
                        for i in range(len(pool)):
                            if solver.Value(x[idx, i]):
                                r = pool[i]
                                selected.append(r)
                                meals.append(PlannedMeal(mealLabel=slot_labels[m], recipeId=r["id"], title=r["title"]))
                                total += int(r.get("calories", 0))
                                break
                    res_plan.append(DayPlan(dayLabel=day_names[d] if d < 7 else f"Day {d + 1}", meals=meals, totalCalories=total))
                explanation = _build_explanation(
                    selected,
                    num_days,
                    daily_targets,
                    target,
                    target_protein,
                    target_carbs,
                    target_fats,
                    tol,
                    max_per_week,
                    profile,
                    budget_weekly,
                )
                return res_plan, "Success", explanation
        if (time.time() - started_at) >= total_time_limit:
            break
    if not _env_bool("PCOSINA_ALLOW_FALLBACK", False):
        if debug_solver:
            print("MILP_DEBUG", json.dumps(debug_summary))
            msg = json.dumps(debug_summary)[:1500]
            return None, f"Infeasible | debug={msg}", None
        return None, "Infeasible", None

    # Optional fallback: build a greedy plan to avoid hard failure if MILP cannot find a solution in time.
    fallback_max = _env_int("PCOSINA_FALLBACK_MAX_PER_WEEK", 10)
    res_plan, selected = _greedy_fallback_plan(
        pool,
        meal_to_allowed,
        slot_labels,
        num_days,
        base_scores,
        fallback_max,
    )
    explanation = _build_explanation(
        selected,
        num_days,
        daily_targets,
        target,
        target_protein,
        target_carbs,
        target_fats,
        tolerance_levels[-1] if tolerance_levels else 0.4,
        fallback_max,
        profile,
        budget_weekly,
    )
    if explanation is not None:
        explanation["fallbackUsed"] = True
        explanation["fallbackReason"] = "MILP infeasible or timed out"
    return res_plan, "Fallback: heuristic plan", explanation
