from typing import List, Optional, Dict, Any
import json
import os
import random

from ortools.sat.python import cp_model

from domain.models import GeneratePlanRequest, PlannedMeal, DayPlan, UserProfile


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
    cal = recipe.get("calories") or 0
    return int(len(ings) * 8 + cal * 0.4)


def passes_restrictions(profile: UserProfile, tags: List[str], ing_tokens: List[str]) -> bool:
    restrictions = set(profile.dietaryRestrictions or [])
    tagset = set(tags)
    toks = set(ing_tokens)
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


def shortlist_candidates(profile: UserProfile, recipes: List[Dict[str, Any]]) -> Dict[str, List[Dict[str, Any]]]:
    buckets = {"Breakfast": [], "Lunch": [], "Dinner": [], "Universal": []}
    restriction_count = len(profile.dietaryRestrictions or [])
    budget_weekly = None
    if profile.budgetWeekly and profile.budgetWeekly > 0:
        budget_weekly = float(profile.budgetWeekly)
    elif profile.budgetMonthly and profile.budgetMonthly > 0:
        budget_weekly = float(profile.budgetMonthly) / 4.33
    pantry_tokens = set(normalize_pantry(profile.pantryItems or []))
    for r in recipes:
        tags = infer_tags(r)
        ing_tokens = normalize_ingredients(r.get("ingredients", []))
        if not passes_restrictions(profile, tags, ing_tokens):
            continue
        r["_tags"] = tags
        r["_ing_tokens"] = ing_tokens
        r["_cost_est"] = estimate_cost(r)
        r["_protein_group"] = infer_protein_group(ing_tokens)
        r["_veg_tokens"] = infer_veg_tokens(ing_tokens)
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

    def score(recipe: Dict[str, Any]) -> float:
        p = recipe.get("proteinGrams") or 0
        cals = recipe.get("calories") or 0
        pantry_bonus = (recipe.get("_pantry_match") or 0) * 1.5
        return (p * 2.0) - (recipe["_cost_est"] * 0.05) - abs(cals - 500) * 0.15 + pantry_bonus

    for k in buckets:
        buckets[k].sort(key=score, reverse=True)
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


def solve_meal_plan(
    request: GeneratePlanRequest,
    recipes: List[Dict],
    weight_set: Optional[Dict[str, int]] = None
):
    profile = request.profile
    conflict = validate_profile(profile)
    if conflict:
        return None, conflict, None
    num_days = max(1, int(request.days or 7))
    slot_count = num_days * 3
    slot_labels = ["Breakfast", "Lunch", "Dinner"]
    w, h, a = (
        profile.weightKg if profile.weightKg > 0 else 65,
        profile.heightCm if profile.heightCm > 0 else 160,
        profile.age if profile.age > 0 else 25
    )
    bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
    target = int(bmr * 1.375)
    if "Weight Loss" in profile.goal:
        target -= 500
    target = max(1200, target)
    seed_salt = os.getenv("PCOSINA_SEED_SALT", "").strip()
    seed_key = f"{seed_salt}|{profile.displayName}_{profile.age}_{profile.heightCm}_{profile.weightKg}_{profile.activityLevel}_{profile.goal}_{profile.dietaryRestrictions}_{num_days}"
    rng = random.Random(seed_key)
    daily_targets = [target + rng.randint(-50, 50) for _ in range(num_days)]
    daily_targets = [max(1200, t) for t in daily_targets]
    # Macro targets based on calories (PCOS-friendly balanced)
    target_protein = int((target * 0.25) / 4)
    target_carbs = int((target * 0.40) / 4)
    target_fats = int((target * 0.35) / 9)
    tolerance_levels = _env_float_list("PCOSINA_TOLERANCE_LEVELS", [0.2, 0.3, 0.4])
    # Stage 1 pruning + shortlist
    buckets = shortlist_candidates(profile, recipes)
    candidates = list({r["id"]: r for r in (buckets["Breakfast"] + buckets["Lunch"] + buckets["Dinner"] + buckets["Universal"])}.values())
    if len(candidates) < 10:
        return None, "No safe recipes found.", None

    pool = candidates
    meal_to_allowed = {"Breakfast": set(), "Lunch": set(), "Dinner": set()}
    for i, r in enumerate(pool):
        mt = (r.get("mealType") or "Universal").lower()
        if "break" in mt:
            meal_to_allowed["Breakfast"].add(i)
        if "lunch" in mt:
            meal_to_allowed["Lunch"].add(i)
        if "dinner" in mt:
            meal_to_allowed["Dinner"].add(i)
        if mt == "universal":
            meal_to_allowed["Breakfast"].add(i)
            meal_to_allowed["Lunch"].add(i)
            meal_to_allowed["Dinner"].add(i)
    for tol in tolerance_levels:
        protein_bounds = (int(target_protein * (1 - tol)), int(target_protein * (1 + tol)))
        carbs_bounds = (int(target_carbs * (1 - tol)), int(target_carbs * (1 + tol)))
        fats_bounds = (int(target_fats * (1 - tol)), int(target_fats * (1 + tol)))
        for max_per_week in _env_int_list("PCOSINA_MAX_PER_WEEK", [2, 3, 4, 10]):
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
            base_scores = []
            for r in pool:
                p = r.get("proteinGrams") or 0
                cals = r.get("calories") or 0
                pantry_bonus = (r.get("_pantry_match") or 0) * 1.5
                base_scores.append((p * 2.0) - (r.get("_cost_est", 0) * 0.05) - abs(cals - 500) * 0.15 + pantry_bonus)
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
            pantry = set(normalize_pantry(profile.pantryItems or []))
            if pantry:
                for i in range(len(pool)):
                    match_count = len(set(pool[i].get("_ing_tokens", [])) & pantry)
                    if match_count > 0:
                        pantry_bonus_vars.append(match_count * sum(x[s, i] for s in range(slot_count)))
            budget_weekly = None
            if profile.budgetWeekly and profile.budgetWeekly > 0:
                budget_weekly = float(profile.budgetWeekly)
            elif profile.budgetMonthly and profile.budgetMonthly > 0:
                budget_weekly = float(profile.budgetMonthly) / 4.33
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
            total_err = sum(err_vars)
            total_dev_pro = sum(dev_pro_vars)
            total_dev_carb = sum(dev_carb_vars)
            total_dev_fat = sum(dev_fat_vars)
            total_repeat_over = sum(repeat_over_vars)
            total_group_over = sum(group_over_vars) if group_over_vars else 0
            budget_penalty = budget_over if budget_over is not None else 0
            pantry_reward = sum(pantry_bonus_vars) if pantry_bonus_vars else 0
            diversity_reward = sum(veg_cov.values()) if veg_cov else 0
            diversity_penalty = (5 * diversity_slack) if diversity_slack is not None else 0
            weights = weight_set or _default_weight_set() or {}
            repeat_w = int(weights.get("repeat_weight", 5))
            group_w = int(weights.get("group_weight", 2))
            diversity_w = int(weights.get("diversity_weight", 1))
            pantry_w = int(weights.get("pantry_weight", 1))
            model.Minimize(
                total_err + (2 * total_dev_pro) + total_dev_carb + total_dev_fat +
                budget_penalty + (repeat_w * total_repeat_over) + (group_w * total_group_over) +
                diversity_penalty - (pantry_w * pantry_reward) - (diversity_w * diversity_reward)
            )
            solver = cp_model.CpSolver()
            solver.parameters.max_time_in_seconds = _env_float("PCOSINA_SOLVER_TIME_SECONDS", 3.0)
            solver.parameters.num_search_workers = _env_int("PCOSINA_SOLVER_WORKERS", 8)
            status = solver.Solve(model)
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
                # Explanation payload for transparency & evaluation
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
                avg_cal = int(sum(daily_cals) / num_days) if num_days else 0
                avg_pro = int(sum(daily_pro) / num_days) if num_days else 0
                avg_carb = int(sum(daily_carb) / num_days) if num_days else 0
                avg_fat = int(sum(daily_fat) / num_days) if num_days else 0
                avg_dev = int(sum(abs(daily_cals[i] - daily_targets[i]) for i in range(num_days)) / num_days) if num_days else 0
                pantry_matches = sum(int(r.get("_pantry_match", 0)) for r in selected)
                unique_veg = len({t for r in selected for t in r.get("_veg_tokens", [])})
                est_cost = sum(int(r.get("_cost_est", 0)) for r in selected)
                # Confidence score: heuristic based on deviation, tolerance, repeats, and restrictions.
                confidence = 100
                confidence -= min(30, int(avg_dev / 10))
                confidence -= min(10, int(max(0.0, tol - 0.2) * 50))
                confidence -= max(0, int(max_per_week - 2) * 3)
                confidence -= min(20, int(len(profile.dietaryRestrictions or []) * 2))
                if budget_weekly and est_cost > budget_weekly:
                    overshoot = (est_cost - budget_weekly) / max(1.0, budget_weekly)
                    confidence -= min(15, int(overshoot * 50))
                confidence = max(0, min(100, confidence))
                explanation = {
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
                return res_plan, "Success", explanation
    return None, "Infeasible", None
