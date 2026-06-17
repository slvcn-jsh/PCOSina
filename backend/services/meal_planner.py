from typing import List, Optional, Dict, Any, Tuple
from datetime import date, timedelta
import json
import hashlib
import os
import random
import re
import time

from ortools.sat.python import cp_model

from canonical_ingredients import resolve_ingredient
from domain.models import GeneratePlanRequest, PlannedMeal, DayPlan, UserProfile
from price_catalog import PricingContext, create_pricing_context, estimate_recipe_cost
from services.grocery_aggregator import aggregate_grocery_list, price_grocery_buckets
from services.ml_features import complete_stage1_feature_vector, zero_reason_feedback_features
from services.ml_ranker import get_stage1_ranker


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


def _policy_get(policy: Optional[Dict[str, Any]], key: str, default: Any) -> Any:
    if not isinstance(policy, dict):
        return default
    current: Any = policy
    for part in key.split("."):
        if not isinstance(current, dict) or part not in current:
            return default
        current = current.get(part)
    if current is None:
        return default
    return current


def _policy_get_legacy_aware(
    policy: Optional[Dict[str, Any]],
    keys: List[str],
    default: Any,
) -> Any:
    for key in keys:
        value = _policy_get(policy, key, None)
        if value is not None:
            return value
    return default


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
    "bangus": "bangus",
    "milkfish": "milkfish",
    "tilapia": "tilapia",
    "galunggong": "galunggong",
    "tambakol": "tuna",
    "tulingan": "tuna",
    "tanigue": "fish",
    "salmon": "salmon",
    "tuna": "tuna",
    "hipon": "shrimp",
    "alimango": "crab",
    "alimasag": "crab",
    "pusit": "squid",
    "gatas": "dairy",
    "keso": "cheese",
    "itlog": "egg",
    "eggs": "egg",
    "patis": "fish",
    "pechay": "bok_choy",
    "sitaw": "string_beans",
    "tokwa": "tofu",
    "mani": "peanut",
    "peanuts": "peanut",
    "soybeans": "soy",
    "soybean": "soy",
    "soya": "soy",
    "tahong": "mussel",
}

ALLERGEN_SYNONYMS = {
    "peanut": "peanut",
    "peanuts": "peanut",
    "mani": "peanut",
    "nuts": "nuts",
    "nut": "nuts",
    "tree_nut": "nuts",
    "almond": "nuts",
    "almonds": "nuts",
    "cashew": "nuts",
    "cashews": "nuts",
    "walnut": "nuts",
    "walnuts": "nuts",
    "hazelnut": "nuts",
    "hazelnuts": "nuts",
    "pistachio": "nuts",
    "pistachios": "nuts",
    "pecan": "nuts",
    "pecans": "nuts",
    "dairy": "dairy",
    "milk": "dairy",
    "gatas": "dairy",
    "cheese": "dairy",
    "keso": "dairy",
    "yogurt": "dairy",
    "butter": "dairy",
    "cream": "dairy",
    "egg": "egg",
    "eggs": "egg",
    "itlog": "egg",
    "fish": "fish",
    "isda": "fish",
    "shellfish": "shellfish",
    "seafood": "fish",
    "shrimp": "shellfish",
    "hipon": "shellfish",
    "crab": "shellfish",
    "alimango": "shellfish",
    "alimasag": "shellfish",
    "squid": "shellfish",
    "pusit": "shellfish",
    "mussel": "shellfish",
    "mussels": "shellfish",
    "tahong": "shellfish",
    "clam": "shellfish",
    "clams": "shellfish",
    "oyster": "shellfish",
    "oysters": "shellfish",
    "lobster": "shellfish",
    "soy": "soy",
    "soya": "soy",
    "soybean": "soy",
    "soybeans": "soy",
    "toyo": "soy",
    "tofu": "soy",
    "wheat": "gluten",
    "gluten": "gluten",
    "sesame": "sesame",
}

MEAT_TOKENS = {"pork", "beef", "chicken", "meat", "lamb", "goat", "duck"}
FISH_FAMILY_TOKENS = {
    "fish", "isda", "bangus", "milkfish", "tilapia", "galunggong",
    "salmon", "tuna", "tambakol", "tulingan", "tanigue", "seafood",
    "sardine", "sardines", "sardinas", "dilis", "dulong", "tinapa",
    "tuyo", "daing", "lapu", "maya",
}
SHELLFISH_FAMILY_TOKENS = {"shellfish", "shrimp", "hipon", "crab", "alimango", "alimasag"}
DAIRY_FAMILY_TOKENS = {"dairy", "milk", "gatas", "cheese", "keso", "yogurt", "butter", "cream"}
EGG_FAMILY_TOKENS = {"egg", "itlog"}
NUTS_FAMILY_TOKENS = {
    "nuts", "nut", "almond", "almonds", "cashew", "cashews",
    "walnut", "walnuts", "hazelnut", "hazelnuts",
    "pistachio", "pistachios", "pecan", "pecans",
}
PEANUT_FAMILY_TOKENS = {"peanut", "peanuts", "mani"}
SOY_FAMILY_TOKENS = {"soy", "soya", "soybean", "soybeans", "toyo", "tofu"}
GLUTEN_FAMILY_TOKENS = {"gluten", "wheat", "flour", "bread", "pasta", "noodle", "bihon", "miki", "pancit"}
SHELLFISH_FAMILY_TOKENS = SHELLFISH_FAMILY_TOKENS | {
    "squid", "pusit", "mussel", "mussels", "tahong",
    "clam", "clams", "oyster", "oysters", "lobster", "seafood",
}
SEAFOOD_TOKENS = FISH_FAMILY_TOKENS | SHELLFISH_FAMILY_TOKENS
DAIRY_TOKENS = {"dairy", "milk", "cheese", "yogurt", "cream", "butter"}
EGG_TOKENS = {"egg"}
ALLERGEN_FAMILY_TOKENS = {
    "fish": FISH_FAMILY_TOKENS,
    "shellfish": SHELLFISH_FAMILY_TOKENS,
    "dairy": DAIRY_FAMILY_TOKENS,
    "egg": EGG_FAMILY_TOKENS,
    "nuts": NUTS_FAMILY_TOKENS,
    "peanut": PEANUT_FAMILY_TOKENS,
    "soy": SOY_FAMILY_TOKENS,
    "gluten": GLUTEN_FAMILY_TOKENS,
}
ALLERGEN_TOKEN_TO_FAMILY = {
    token: family
    for family, tokens in ALLERGEN_FAMILY_TOKENS.items()
    for token in tokens
}
PROTEIN_GROUP_TOKENS = {
    "pork": {"pork"},
    "beef": {"beef"},
    "chicken": {"chicken"},
    "fish": FISH_FAMILY_TOKENS | SHELLFISH_FAMILY_TOKENS | {"squid"},
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
SNACK_LABELS = ["Snack1", "Snack2", "Snack3"]
ALL_SLOT_LABELS = MEAL_LABELS + SNACK_LABELS
GOAL_WEIGHT_LOSS = "weight loss"
GOAL_SYMPTOM_MANAGEMENT = "symptom management"
GOAL_GENERAL_HEALTH = "general health"
_RECIPE_STATIC_FEATURE_CACHE_MAX = 10000
_RECIPE_STATIC_FEATURE_CACHE: Dict[Tuple[Any, ...], Dict[str, Any]] = {}

SYMPTOM_ALIASES = {
    "irregular periods": "irregular_periods",
    "weight gain": "weight_gain",
    "acne": "acne",
    "hair loss": "hair_loss",
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


def normalize_allergies(allergies: List[str]) -> List[str]:
    families, custom_tokens = normalize_allergy_constraints(allergies)
    return sorted(families | custom_tokens)


def normalize_allergy_constraints(allergies: List[str]) -> tuple[set[str], set[str]]:
    families: set[str] = set()
    custom_tokens: set[str] = set()
    for item in allergies or []:
        raw_tokens = [
            _normalize_token(raw)
            for raw in str(item).replace("/", " ").replace("-", " ").split()
        ]
        raw_tokens = [token for token in raw_tokens if token]
        if not raw_tokens:
            continue

        item_families: set[str] = set()
        item_custom_tokens: set[str] = set()
        for token in raw_tokens:
            if token == "seafood":
                item_families.update({"fish", "shellfish"})
                continue
            normalized = ALLERGEN_SYNONYMS.get(token, token)
            family = ALLERGEN_TOKEN_TO_FAMILY.get(normalized)
            if family is not None:
                item_families.add(family)
            else:
                item_custom_tokens.add(normalized)

        families.update(item_families)
        if item_families:
            continue
        custom_tokens.update(item_custom_tokens)
    return families, custom_tokens


def normalize_goal_tokens(goal: str | None) -> List[str]:
    return [
        "".join(ch for ch in part.lower().strip() if ch.isalnum() or ch == " ").strip()
        for part in str(goal or "").replace("/", ",").split(",")
        if str(part or "").strip()
    ]


def goal_has(goal: str | None, expected: str) -> bool:
    target = str(expected or "").strip().lower()
    return any(target in token for token in normalize_goal_tokens(goal))


def normalize_symptoms(symptoms: List[str]) -> List[str]:
    normalized: List[str] = []
    for raw in symptoms or []:
        token = str(raw or "").strip().lower()
        if not token:
            continue
        normalized_token = SYMPTOM_ALIASES.get(token, token.replace(" ", "_"))
        normalized.append(normalized_token)
    return sorted(set(normalized))


def derive_allergen_exposures(tags: List[str], ing_tokens: List[str]) -> set[str]:
    toks = set(ing_tokens or [])
    exposures = {
        family
        for family, family_tokens in ALLERGEN_FAMILY_TOKENS.items()
        if toks & family_tokens
    }
    tagset = set(tags or [])
    if "contains_dairy" in tagset:
        exposures.add("dairy")
    if "contains_egg" in tagset:
        exposures.add("egg")
    if "contains_seafood" in tagset and not (exposures & {"fish", "shellfish"}):
        exposures.add("fish")
    return exposures


def symptom_adjustments(profile: UserProfile, goal_value: str | None = None) -> Dict[str, Any]:
    normalized_symptoms = normalize_symptoms(profile.symptoms or [])
    notes: List[str] = []
    adjustments = {
        "normalizedSymptoms": normalized_symptoms,
        "fiberMinBonus": 0,
        "proteinTargetBonus": 0,
        "sugarMaxDelta": 0,
        "carbTargetDelta": 0,
        "calorieTargetDelta": 0,
        "dairyPenalty": 0.0,
        "stage1HighFiberBonus": 0.0,
        "stage1HighProteinBonus": 0.0,
        "stage1LowerCalorieBonus": 0.0,
        "stage1SteadyCarbBonus": 0.0,
        "notes": notes,
    }

    if goal_has(goal_value, GOAL_SYMPTOM_MANAGEMENT):
        adjustments["fiberMinBonus"] += 4
        adjustments["sugarMaxDelta"] -= 8
        adjustments["carbTargetDelta"] -= 10
        adjustments["stage1HighFiberBonus"] += 1.5
        adjustments["stage1SteadyCarbBonus"] += 1.0
        notes.append("Symptom Management tightens fiber, sugar, and steadier-carb preferences.")
    if goal_has(goal_value, GOAL_WEIGHT_LOSS):
        adjustments["stage1LowerCalorieBonus"] += 0.75
        adjustments["stage1HighProteinBonus"] += 0.5
        notes.append("Weight Loss favors lighter, higher-protein meals after calorie target adjustment.")

    if "weight_gain" in normalized_symptoms:
        adjustments["calorieTargetDelta"] -= 120
        adjustments["fiberMinBonus"] += 2
        adjustments["stage1LowerCalorieBonus"] += 1.25
        adjustments["stage1HighFiberBonus"] += 0.75
        notes.append("Weight gain symptom nudges the plan toward lower-calorie, higher-fiber meals.")
    if "irregular_periods" in normalized_symptoms:
        adjustments["fiberMinBonus"] += 2
        adjustments["stage1HighFiberBonus"] += 0.75
        notes.append("Irregular periods nudges the plan toward higher-fiber meals.")
    if "acne" in normalized_symptoms:
        adjustments["sugarMaxDelta"] -= 6
        adjustments["dairyPenalty"] += 2.0
        adjustments["stage1SteadyCarbBonus"] += 0.5
        notes.append("Acne symptom reduces sugar allowance and softly penalizes dairy-heavy meals.")
    if "hair_loss" in normalized_symptoms:
        adjustments["proteinTargetBonus"] += 8
        adjustments["stage1HighProteinBonus"] += 1.25
        notes.append("Hair loss nudges the plan toward higher-protein meals.")

    return adjustments


def profile_rule_summary(profile: UserProfile, budget_weekly: Optional[float]) -> Dict[str, List[str]]:
    hard_filters: List[str] = []
    soft_drivers: List[str] = []
    shopping_factors: List[str] = []
    advisory_limits: List[str] = []
    tracking_only: List[str] = []

    allergies = normalize_allergies(profile.allergies or [])
    if allergies:
        hard_filters.append("Allergies exclude matching ingredient families.")
    if profile.dietaryRestrictions:
        hard_filters.append("Dietary restrictions exclude incompatible recipes.")
    if profile.maxCookingTimeMinutes and profile.maxCookingTimeMinutes > 0:
        hard_filters.append(f"Cooking time is capped at {int(profile.maxCookingTimeMinutes)} minutes.")
    if budget_weekly:
        hard_filters.append(f"Weekly budget is capped at ₱{int(budget_weekly)}.")

    soft_drivers.append(f"Activity level changes calorie target ({profile.activityLevel or 'Lightly Active'}).")
    soft_drivers.append("PCOS wellness nutrition policy uses a single macro target policy.")
    soft_drivers.append(f"Variety preference changes repeat pressure ({profile.varietyPreference or 'Balanced'}).")
    soft_drivers.append(f"Planning priority changes optimization weights ({profile.planningPriority or 'Balanced'}).")
    if str(profile.goal or "").strip():
        soft_drivers.append(f"Goal affects solver targets and stage-1 scoring ({profile.goal}).")
    if normalize_symptoms(profile.symptoms or []):
        soft_drivers.append("Selected symptoms add deterministic fiber, sugar, protein, or calorie nudges.")
    else:
        tracking_only.append("Symptoms are optional; no symptom-specific nudges are active.")
    if profile.targetWeightKg:
        tracking_only.append("Optional target weight supports progress review; it is not a solver hard constraint.")
    advisory_limits.append("Sodium and sugar are target limits minimized through overage penalties, not hard infeasibility gates.")

    shopping_factors.append("Grocery quantities and estimated cost are scoped to the primary user.")
    if profile.pantryItems:
        shopping_factors.append("Pantry items reward overlap; Android grocery coverage handles quantity-aware pantry marking after planning.")
    if not budget_weekly:
        tracking_only.append("Weekly budget is not set, so no hard budget cap is active.")

    return {
        "hardFilters": hard_filters,
        "softDrivers": soft_drivers,
        "shoppingFactors": shopping_factors,
        "advisoryLimits": advisory_limits,
        "trackingOnly": tracking_only,
    }


def planner_contract_summary(profile: UserProfile, budget_weekly: Optional[float]) -> List[Dict[str, Any]]:
    has_budget = budget_weekly is not None
    has_pantry = bool(profile.pantryItems)
    has_symptoms = bool(normalize_symptoms(profile.symptoms or []))
    return [
        {
            "field": "allergies",
            "classification": "hard",
            "enforcement": "Stage 1 excludes recipes with matching allergen families or custom excluded ingredients.",
            "active": bool(profile.allergies),
        },
        {
            "field": "dietaryRestrictions",
            "classification": "hard",
            "enforcement": "Stage 1 excludes recipes incompatible with supported restriction rules.",
            "active": bool(profile.dietaryRestrictions),
        },
        {
            "field": "weeklyBudgetPhp",
            "classification": "hard",
            "enforcement": "Stage 2 enforces total estimated plan cost <= weekly budget when a budget is set.",
            "active": has_budget,
        },
        {
            "field": "maxCookingTimeMinutes",
            "classification": "hard",
            "enforcement": "Stage 1 excludes recipes above the saved maximum cooking time.",
            "active": bool(profile.maxCookingTimeMinutes and profile.maxCookingTimeMinutes > 0),
        },
        {
            "field": "calories/protein/carbs/fats/fiber",
            "classification": "hard",
            "enforcement": "Stage 2 applies daily calorie, macro, and fiber bounds for each solve attempt.",
            "active": True,
        },
        {
            "field": "sodiumMg/sugarGrams",
            "classification": "advisory",
            "enforcement": "Stage 2 minimizes sodium and sugar overage variables instead of rejecting every overage.",
            "active": True,
        },
        {
            "field": "goal",
            "classification": "soft",
            "enforcement": "Goal changes calorie targets, nutrition nudges, and candidate scoring, while preserving hard filters.",
            "active": bool(str(profile.goal or "").strip()),
        },
        {
            "field": "targetWeightKg/targetDate/weeklyWeightChangeGoalKg",
            "classification": "tracking",
            "enforcement": "Stored as progress-support context; Android guardrails and opt-in feedback tags decide whether future plans are adjusted.",
            "active": bool(profile.targetWeightKg or profile.targetDate or profile.weeklyWeightChangeGoalKg is not None),
        },
        {
            "field": "symptoms",
            "classification": "soft",
            "enforcement": "Selected symptoms add deterministic nutrition and scoring nudges.",
            "active": has_symptoms,
        },
        {
            "field": "varietyPreference/planningPriority",
            "classification": "soft",
            "enforcement": "Preference values change optimization weights and retry ordering.",
            "active": True,
        },
        {
            "field": "pantryItems",
            "classification": "soft",
            "enforcement": "Pantry overlap is scored, optionally thresholded in Stage 1, and rewarded in Stage 2; Android grocery coverage evaluates saved pantry quantities after planning.",
            "active": has_pantry,
        },
        {
            "field": "displayName/comorbidities",
            "classification": "tracking",
            "enforcement": "Stored for profile context and continuity; not used as solver hard constraints.",
            "active": bool(str(profile.displayName or "").strip() or profile.comorbidities),
        },
    ]


def stage1_recipe_adjustments(
    recipe: Dict[str, Any],
    profile: UserProfile,
    symptom_state: Dict[str, Any],
    goal_value: str | None,
) -> tuple[float, List[str]]:
    tags = set(recipe.get("_tags") or [])
    calories = int(recipe.get("calories") or 0)
    protein = int(recipe.get("proteinGrams") or 0)
    carbs = int(recipe.get("carbsGrams") or 0)
    fiber = int(recipe.get("fiberGrams") or 0)
    sugar = int(recipe.get("sugarGrams") or 0)
    reasons: List[str] = []
    boost = 0.0

    if fiber >= 6 and symptom_state.get("stage1HighFiberBonus", 0.0) > 0.0:
        boost += float(symptom_state["stage1HighFiberBonus"])
        reasons.append("high_fiber_preferred")
    if protein >= 25 and symptom_state.get("stage1HighProteinBonus", 0.0) > 0.0:
        boost += float(symptom_state["stage1HighProteinBonus"])
        reasons.append("high_protein_preferred")
    if calories > 0 and calories <= 520 and symptom_state.get("stage1LowerCalorieBonus", 0.0) > 0.0:
        boost += float(symptom_state["stage1LowerCalorieBonus"])
        reasons.append("lower_calorie_preferred")
    if carbs > 0 and carbs <= 45 and symptom_state.get("stage1SteadyCarbBonus", 0.0) > 0.0:
        boost += float(symptom_state["stage1SteadyCarbBonus"])
        reasons.append("steady_carb_preferred")
    if symptom_state.get("dairyPenalty", 0.0) > 0.0 and "contains_dairy" in tags:
        boost -= float(symptom_state["dairyPenalty"])
        reasons.append("dairy_soft_penalty")
    if goal_has(goal_value, GOAL_GENERAL_HEALTH) and fiber >= 5 and protein >= 20:
        boost += 0.5
        reasons.append("balanced_goal_fit")
    if sugar and sugar <= 10 and goal_has(goal_value, GOAL_SYMPTOM_MANAGEMENT):
        boost += 0.5
        reasons.append("lower_sugar_goal_fit")

    return boost, reasons


def infer_allowed_meals(meal_type: str | None) -> List[str]:
    if not meal_type:
        return MEAL_LABELS
    raw = meal_type.lower()
    if "universal" in raw:
        return ALL_SLOT_LABELS
    labels = []
    if "break" in raw:
        labels.append("Breakfast")
    if "lunch" in raw:
        labels.append("Lunch")
    if "dinner" in raw:
        labels.append("Dinner")
    if "snack" in raw or "merienda" in raw:
        labels.extend(SNACK_LABELS)
    return labels or MEAL_LABELS


def infer_tags(recipe: Dict[str, Any], ing_tokens: Optional[List[str]] = None) -> List[str]:
    tags = set([t.lower() for t in recipe.get("tags", []) if t])
    token_set = set(ing_tokens if ing_tokens is not None else normalize_ingredients(recipe.get("ingredients", [])))
    if token_set & MEAT_TOKENS:
        tags.add("contains_meat")
    if token_set & SEAFOOD_TOKENS:
        tags.add("contains_seafood")
    if token_set & DAIRY_TOKENS:
        tags.add("contains_dairy")
    if token_set & EGG_TOKENS:
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


def _ingredient_cache_name(ingredient: Any) -> str:
    if isinstance(ingredient, dict):
        return str(ingredient.get("name", ""))
    return str(ingredient)


def _recipe_static_feature_cache_key(
    recipe: Dict[str, Any],
    canonical_features_enabled: bool = False,
) -> Tuple[Any, ...]:
    recipe_id = str(recipe.get("id") or "").strip()
    meal_type = str(recipe.get("mealType") or "").strip().lower()
    correction_id = str(recipe.get("nutritionCorrectionId") or "").strip()
    if recipe_id and correction_id:
        if canonical_features_enabled:
            canonical = recipe.get("_canonical_stage1") or {}
            canonical_ids = tuple(canonical.get("curatedIngredientIds") or [])
            return ("catalog_canonical", recipe_id, correction_id, meal_type, canonical_ids)
        return ("catalog", recipe_id, correction_id, meal_type)
    tags = tuple(str(tag or "").strip().lower() for tag in (recipe.get("tags") or []))
    ingredients = tuple(_ingredient_cache_name(item) for item in (recipe.get("ingredients") or []))
    return ("adhoc", recipe_id, meal_type, tags, ingredients, bool(canonical_features_enabled))


def _recipe_static_features(
    recipe: Dict[str, Any],
    canonical_features_enabled: bool = False,
) -> Dict[str, Any]:
    cache_key = _recipe_static_feature_cache_key(recipe, canonical_features_enabled)
    cached = _RECIPE_STATIC_FEATURE_CACHE.get(cache_key)
    if cached is not None:
        return cached

    ing_tokens = normalize_ingredients(recipe.get("ingredients", []))
    canonical = recipe.get("_canonical_stage1") or {}
    canonical_ids = set(canonical.get("curatedIngredientIds") or []) if canonical_features_enabled else set()
    canonical_names = list(canonical.get("curatedCanonicalNames") or []) if canonical_features_enabled else []
    canonical_tokens = normalize_ingredients(canonical_names) if canonical_names else []
    effective_tokens = sorted(set(ing_tokens) | set(canonical_tokens))
    canonical_allergens = (
        set(str(value).strip().lower() for value in (canonical.get("allergenFamilies") or []) if str(value).strip())
        if canonical_features_enabled
        else set()
    )
    features = {
        "tags": infer_tags(recipe, effective_tokens),
        "ing_tokens": effective_tokens,
        "legacy_ing_tokens": ing_tokens,
        "canonical_ingredient_ids": canonical_ids,
        "canonical_allergen_families": canonical_allergens,
        "canonical_feature_available": bool(canonical_ids),
        "protein_group": infer_protein_group(effective_tokens),
        "veg_tokens": infer_veg_tokens(effective_tokens),
        "allowed_meals": infer_allowed_meals(recipe.get("mealType")),
    }
    if len(_RECIPE_STATIC_FEATURE_CACHE) >= _RECIPE_STATIC_FEATURE_CACHE_MAX:
        _RECIPE_STATIC_FEATURE_CACHE.clear()
    _RECIPE_STATIC_FEATURE_CACHE[cache_key] = features
    return features


def parse_serving_count(value: Any) -> Optional[float]:
    if value in (None, ""):
        return None
    if isinstance(value, (int, float)):
        parsed = float(value)
        return parsed if parsed > 0 else None
    text = str(value or "").strip().lower()
    if not text:
        return None
    range_match = re.search(r"(\d+(?:\.\d+)?)\s*(?:-|to)\s*(\d+(?:\.\d+)?)", text)
    if range_match:
        low = float(range_match.group(1))
        high = float(range_match.group(2))
        if low > 0 and high > 0:
            return (low + high) / 2.0
    match = re.search(r"\d+(?:\.\d+)?", text)
    if not match:
        return None
    parsed = float(match.group(0))
    return parsed if parsed > 0 else None


def recipe_serving_count(recipe: Dict[str, Any]) -> Optional[float]:
    for key in ("sourceServings", "servings", "recipeYield"):
        parsed = parse_serving_count(recipe.get(key))
        if parsed is not None:
            return parsed
    notes = str(recipe.get("nutritionNotes") or "").strip()
    for key in ("source_servings", "source_recipe_yield", "sourceServings"):
        match = re.search(rf"{re.escape(key)}\s*=\s*([^;]+)", notes, flags=re.IGNORECASE)
        if match:
            parsed = parse_serving_count(match.group(1))
            if parsed is not None:
                return parsed
    return None


def serving_cost_multiplier(recipe: Dict[str, Any]) -> float:
    servings = recipe_serving_count(recipe)
    if servings is None or servings <= 0:
        return 1.0
    return 1.0 / max(1.0, float(servings))


def build_plan_day_labels(num_days: int, start_date_text: Optional[str] = None) -> List[str]:
    fallback = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    normalized_days = max(1, int(num_days or 0))
    start_value = str(start_date_text or "").strip()
    if not start_value:
        return [fallback[d] if d < 7 else f"Day {d + 1}" for d in range(normalized_days)]
    try:
        start_date = date.fromisoformat(start_value)
    except ValueError:
        return [fallback[d] if d < 7 else f"Day {d + 1}" for d in range(normalized_days)]
    return [
        fallback[(start_date.weekday() + d) % 7]
        if d < 7 else f"Day {d + 1}"
        for d in range(normalized_days)
    ]


def estimate_cost(
    recipe: Dict[str, Any],
    *,
    pricing_context: Optional[PricingContext] = None,
    cost_cache: Optional[Dict[Tuple[str, int, int], int]] = None,
    cost_cache_stats: Optional[Dict[str, int]] = None,
) -> int:
    cache_key: Optional[Tuple[str, int, int]] = None
    if cost_cache is not None:
        recipe_id = str(recipe.get("id") or "").strip()
        if not recipe_id:
            try:
                recipe_id = hashlib.sha256(
                    json.dumps(recipe.get("ingredients", []), sort_keys=True, separators=(",", ":")).encode("utf-8")
                ).hexdigest()
            except Exception:
                recipe_id = hashlib.sha256(str(recipe.get("ingredients", [])).encode("utf-8")).hexdigest()
        month_index = int(getattr(pricing_context, "month_index", 0) or 0)
        serving_key = int(round(float(recipe_serving_count(recipe) or 1.0) * 100))
        cache_key = (recipe_id, month_index, serving_key)
        cached = cost_cache.get(cache_key)
        if cached is not None:
            if cost_cache_stats is not None:
                cost_cache_stats["recipeCostCacheHits"] = int(cost_cache_stats.get("recipeCostCacheHits", 0)) + 1
            return int(cached)
        if cost_cache_stats is not None:
            cost_cache_stats["recipeCostCacheMisses"] = int(cost_cache_stats.get("recipeCostCacheMisses", 0)) + 1

    ings = recipe.get("ingredients", [])
    catalog_cost = estimate_recipe_cost(ings, pricing_context=pricing_context)
    serving_multiplier = serving_cost_multiplier(recipe)
    if catalog_cost > 0:
        resolved = int(max(1, round(float(catalog_cost) * serving_multiplier)))
        if cost_cache is not None and cache_key is not None:
            cost_cache[cache_key] = resolved
        return resolved
    cal = recipe.get("calories") or 0
    rough = (len(ings) * 6) + (cal * 0.15)
    resolved = int(max(1, round(max(30, min(450, rough)) * serving_multiplier)))
    if cost_cache is not None and cache_key is not None:
        cost_cache[cache_key] = resolved
    return resolved


def _base_score(recipe: Dict[str, Any]) -> float:
    p = recipe.get("proteinGrams") or 0
    cals = recipe.get("calories") or 0
    pantry_bonus = (recipe.get("_pantry_match") or 0) * 1.5
    stage1_boost = float(recipe.get("_stage1_score_boost") or 0.0)
    return (p * 2.0) - (recipe.get("_cost_est", 0) * 0.05) - abs(cals - 500) * 0.15 + pantry_bonus + stage1_boost


def _text_token_set(value: str) -> set[str]:
    parts = []
    for raw in str(value or "").replace("-", " ").replace("/", " ").split():
        token = "".join(ch for ch in raw.lower() if ch.isalnum())
        if token:
            parts.append(token)
    return set(parts)


def _token_jaccard(a: set[str], b: set[str]) -> float:
    if not a or not b:
        return 0.0
    inter = len(a & b)
    union = len(a | b)
    if union <= 0:
        return 0.0
    return float(inter) / float(union)


def _apply_similarity_dedup(candidates: List[Dict[str, Any]], threshold: float) -> List[Dict[str, Any]]:
    if not candidates:
        return []
    if threshold <= 0:
        return candidates
    normalized_threshold = min(1.0, max(0.0, threshold))
    if normalized_threshold >= 0.999:
        return candidates

    kept: List[Dict[str, Any]] = []
    kept_signatures: List[set[str]] = []
    for recipe in candidates:
        signature = _text_token_set(recipe.get("title", ""))
        if not signature:
            signature = {str(recipe.get("id", ""))}
        is_near_duplicate = False
        for prev in kept_signatures:
            if _token_jaccard(signature, prev) >= normalized_threshold:
                is_near_duplicate = True
                break
        if is_near_duplicate:
            continue
        kept.append(recipe)
        kept_signatures.append(signature)
    return kept


def _shadow_ml_score(recipe: Dict[str, Any], profile: UserProfile) -> float:
    # Deterministic bounded score [0,1] for shadow/canary ranking only.
    calories = float(recipe.get("calories") or 0)
    protein = float(recipe.get("proteinGrams") or 0)
    fiber = float(recipe.get("fiberGrams") or 0)
    minutes = float(recipe.get("minutes") or 0)
    pantry_match = float(recipe.get("_pantry_match") or 0)
    budget = float(profile.weeklyBudgetPhp or 0)

    macro_component = min(1.0, (protein / 45.0) * 0.5 + (fiber / 15.0) * 0.5)
    calorie_component = max(0.0, 1.0 - min(1.0, abs(calories - 500.0) / 500.0))
    prep_component = max(0.0, 1.0 - min(1.0, minutes / 90.0))
    pantry_component = min(1.0, pantry_match / 5.0)
    budget_component = 1.0 if budget <= 0 else max(0.0, 1.0 - min(1.0, (recipe.get("_cost_est", 0) or 0) / max(1.0, budget / 21.0)))

    blended = (
        0.30 * macro_component
        + 0.25 * calorie_component
        + 0.15 * prep_component
        + 0.20 * pantry_component
        + 0.10 * budget_component
    )
    return max(0.0, min(1.0, blended))


def _stage1_ml_feature_vector(
    recipe: Dict[str, Any],
    profile: UserProfile,
    ml_feature_context: Optional[Dict[str, Any]] = None,
) -> Dict[str, float]:
    context = ml_feature_context or {}
    budget_weekly = context.get("budget_weekly")
    if budget_weekly is None:
        budget_weekly = resolve_budget_weekly(profile) or 0.0
    calories = float(recipe.get("calories") or 0.0)
    protein = float(recipe.get("proteinGrams") or 0.0)
    carbs = float(recipe.get("carbsGrams") or 0.0)
    fats = float(recipe.get("fatsGrams") or 0.0)
    target_calories = float(context.get("target_calories") or 500.0)
    target_protein = context.get("target_protein")
    target_carbs = context.get("target_carbs")
    target_fats = context.get("target_fats")
    macro_distance = 0.0
    if target_protein is not None and target_carbs is not None and target_fats is not None:
        macro_distance = (
            abs(protein - float(target_protein))
            + abs(carbs - float(target_carbs))
            + abs(fats - float(target_fats))
        )
    reason_features = zero_reason_feedback_features()
    supplied_reason_features = context.get("reason_feedback_features")
    if isinstance(supplied_reason_features, dict):
        for key in reason_features.keys():
            try:
                reason_features[key] = float(supplied_reason_features.get(key, 0.0))
            except Exception:
                reason_features[key] = 0.0

    features = {
        "model_score": float(_shadow_ml_score(recipe, profile)),
        "heuristic_score": float(_base_score(recipe)),
        "restriction_count": float(len(profile.dietaryRestrictions or [])),
        "allergy_count": float(len(profile.allergies or [])),
        "budget_weekly_norm": float(float(budget_weekly or 0.0) / 7000.0),
        "max_cooking_time_minutes": float(profile.maxCookingTimeMinutes or 0),
        "recipe_calories": calories,
        "recipe_protein": protein,
        "recipe_carbs": carbs,
        "recipe_fats": fats,
        "recipe_fiber": float(recipe.get("fiberGrams") or 0.0),
        "recipe_minutes": float(recipe.get("minutes") or 0.0),
        "recipe_cost_est": float(recipe.get("_cost_est") or 0.0),
        "pantry_overlap_count": float(recipe.get("_pantry_match") or 0.0),
        "macro_distance_score": float(macro_distance),
        "calorie_distance_score": abs(calories - target_calories),
        "meals_per_day": float(context.get("meals_per_day") or 3),
        "profile_activity_lightly_active": 1.0 if str(profile.activityLevel or "") == "Lightly Active" else 0.0,
        "profile_goal_general_health": 1.0 if goal_has(profile.goal, GOAL_GENERAL_HEALTH) else 0.0,
        "profile_goal_weightloss": 1.0 if goal_has(profile.goal, GOAL_WEIGHT_LOSS) else 0.0,
        "is_breakfast_candidate": 1.0 if "Breakfast" in (recipe.get("_allowed_meals") or []) else 0.0,
        "is_lunch_candidate": 1.0 if "Lunch" in (recipe.get("_allowed_meals") or []) else 0.0,
        "is_dinner_candidate": 1.0 if "Dinner" in (recipe.get("_allowed_meals") or []) else 0.0,
    }
    features.update(reason_features)
    return complete_stage1_feature_vector(features)


def _is_profile_in_canary(profile: UserProfile, policy: Optional[Dict[str, Any]]) -> bool:
    canary_percent = float(_policy_get(policy, "sre.canary_cohort_percent", 0.0))
    if canary_percent <= 0.0:
        return False
    if canary_percent >= 100.0:
        return True
    key = f"{profile.displayName}|{profile.age}|{profile.goal}|{profile.activityLevel}"
    digest = hashlib.sha256(key.encode("utf-8")).hexdigest()
    bucket = int(digest[:8], 16) % 100
    return bucket < int(canary_percent)


def _stage1_ml_scoring_enabled(policy: Optional[Dict[str, Any]]) -> bool:
    ml_shadow_enabled = bool(_policy_get(policy, "stage1.ML_shadow_enabled", True))
    ml_canary_enabled = bool(_policy_get(policy, "stage1.ML_canary_enabled", False))
    return bool(ml_shadow_enabled or ml_canary_enabled)


def _stage1_ml_applies_to_ranking(profile: UserProfile, policy: Optional[Dict[str, Any]]) -> bool:
    ml_shadow_enabled = bool(_policy_get(policy, "stage1.ML_shadow_enabled", True))
    ml_canary_enabled = bool(_policy_get(policy, "stage1.ML_canary_enabled", False))
    # `ML_shadow_enabled` is kept for backward-compatible policy parsing, but now
    # acts as the main live-ranking switch rather than a score-only shadow path.
    return bool(ml_shadow_enabled or (ml_canary_enabled and _is_profile_in_canary(profile, policy)))


def _apply_stage1_scoring(
    recipe: Dict[str, Any],
    profile: UserProfile,
    policy: Optional[Dict[str, Any]],
    max_cook: Optional[int],
) -> None:
    penalty_weights = _policy_get(
        policy,
        "stage1.exclusion_penalty_weights",
        {"allergy": 1000.0, "restriction": 500.0, "prep_time": 50.0},
    )
    prep_penalty_weight = float((penalty_weights or {}).get("prep_time", 50.0))
    minutes = float(recipe.get("minutes") or 0)
    prep_penalty = 0.0
    if max_cook and max_cook > 0:
        prep_penalty = max(0.0, (minutes - max_cook) / float(max_cook)) * prep_penalty_weight

    ml_scoring_enabled = _stage1_ml_scoring_enabled(policy)
    ml_weight = float(_policy_get(policy, "stage1.ML_score_weight", 0.15))
    ml_cap = float(_policy_get(policy, "stage1.ML_score_cap", 0.30))
    ml_weight = max(0.0, min(ml_weight, ml_cap, 1.0))
    ml_score = 0.0
    ml_model_version = "shadow_v0"
    if ml_scoring_enabled:
        ranker = get_stage1_ranker()
        ranker_state = ranker.state()
        ml_model_version = ranker_state.model_version
        candidate_features = _stage1_ml_feature_vector(recipe, profile)
        model_score = ranker.score(candidate_features) if ranker_state.ready else None
        if model_score is None:
            ml_score = _shadow_ml_score(recipe, profile)
            ml_model_version = "shadow_v0"
        else:
            ml_score = max(0.0, min(1.0, float(model_score)))

    apply_ml_to_ranking = _stage1_ml_applies_to_ranking(profile, policy)
    effective_weight = ml_weight if apply_ml_to_ranking else 0.0
    existing_boost = float(recipe.get("_symptom_goal_boost") or 0.0)
    recipe["_ml_shadow_score"] = ml_score
    recipe["_ml_model_version"] = ml_model_version
    recipe["_ml_applied_to_ranking"] = apply_ml_to_ranking
    recipe["_stage1_score_boost"] = existing_boost + (ml_score * effective_weight * 10.0) - prep_penalty


def _finalize_stage1_scoring(
    recipe: Dict[str, Any],
    *,
    ml_score: float,
    ml_model_version: str,
    apply_ml_to_ranking: bool,
    ml_weight: float,
    prep_penalty: float,
) -> None:
    bounded_ml_score = max(0.0, min(1.0, float(ml_score or 0.0)))
    effective_weight = float(ml_weight or 0.0) if apply_ml_to_ranking else 0.0
    preserved_boost = float(recipe.get("_symptom_goal_boost") or (
        float(recipe.get("_stage1_score_boost") or 0.0) + float(prep_penalty or 0.0)
    ))
    recipe["_ml_shadow_score"] = bounded_ml_score
    recipe["_ml_model_version"] = str(ml_model_version or "shadow_v0")
    recipe["_ml_applied_to_ranking"] = bool(apply_ml_to_ranking)
    recipe["_stage1_score_boost"] = preserved_boost + (bounded_ml_score * effective_weight * 10.0) - float(prep_penalty or 0.0)


def restriction_failure_reasons(
    profile: UserProfile,
    tags: List[str],
    ing_tokens: List[str],
    canonical_allergen_families: Optional[set[str]] = None,
) -> List[str]:
    restrictions = set(profile.dietaryRestrictions or [])
    tagset = set(tags)
    toks = set(ing_tokens)
    allergy_families, custom_allergy_tokens = normalize_allergy_constraints(profile.allergies or [])
    allergen_exposures = derive_allergen_exposures(tags, ing_tokens)
    allergen_exposures.update(canonical_allergen_families or set())
    failures: List[str] = []
    matched_allergies = sorted(allergy_families & allergen_exposures)
    if matched_allergies:
        failures.extend(f"allergy:{token}" for token in matched_allergies)
    matched_custom_allergies = sorted(custom_allergy_tokens & toks)
    if matched_custom_allergies:
        failures.extend(f"allergy:{token}" for token in matched_custom_allergies)
    if "No Pork" in restrictions and "pork" in toks:
        failures.append("restriction:no_pork")
    if "No Beef" in restrictions and "beef" in toks:
        failures.append("restriction:no_beef")
    if "Vegetarian" in restrictions and (("contains_meat" in tagset) or ("contains_seafood" in tagset)):
        failures.append("restriction:vegetarian")
    if "Pescatarian" in restrictions and ("contains_meat" in tagset):
        failures.append("restriction:pescatarian")
    if "Lactose Intolerant" in restrictions and ("contains_dairy" in tagset):
        failures.append("restriction:lactose_intolerant")
    return failures


def passes_restrictions(profile: UserProfile, tags: List[str], ing_tokens: List[str]) -> bool:
    return not restriction_failure_reasons(profile, tags, ing_tokens)


def _increment_count(counter: Dict[str, int], key: str, amount: int = 1) -> None:
    if not key:
        return
    counter[key] = int(counter.get(key, 0)) + int(amount)


def validate_profile(profile: UserProfile) -> Optional[str]:
    restrictions = set(profile.dietaryRestrictions or [])
    allergy_tokens = set(normalize_allergies(profile.allergies or []))
    max_cook_raw = getattr(profile, "maxCookingTimeMinutes", 0)
    max_cook = int(0 if max_cook_raw is None else max_cook_raw)
    planning_priority = str(profile.planningPriority or "").strip().lower()
    variety_preference = str(profile.varietyPreference or "").strip().lower()
    if max_cook and not 10 <= max_cook <= 240:
        return "Max cooking time must stay between 10 and 240 minutes."
    if "Vegetarian" in restrictions and "Pescatarian" in restrictions:
        return "Conflicting restrictions: Vegetarian and Pescatarian cannot both be active."
    if "Pescatarian" in restrictions and ("No Seafood" in restrictions or "No Fish" in restrictions):
        return "Conflicting restrictions: Pescatarian + No Seafood."
    if "Pescatarian" in restrictions and {"fish", "shellfish"}.issubset(allergy_tokens):
        return "Conflicting profile: Pescatarian cannot be combined with both fish and shellfish allergies."
    if "Vegetarian" in restrictions and ("No Eggs" in restrictions and "No Dairy" in restrictions):
        return "Very restrictive: Vegetarian + No Eggs + No Dairy."
    if "budget" in planning_priority and resolve_budget_weekly(profile) is None:
        return "Budget First priority requires a weekly budget."
    if "variety" in planning_priority and "low" in variety_preference:
        return "Variety First priority conflicts with Low variety preference."
    return None


def resolve_budget_weekly(profile: UserProfile) -> Optional[float]:
    if profile.weeklyBudgetPhp and profile.weeklyBudgetPhp > 0:
        return float(profile.weeklyBudgetPhp)
    if profile.budgetWeekly and profile.budgetWeekly > 0:
        return float(profile.budgetWeekly)
    if profile.budgetMonthly and profile.budgetMonthly > 0:
        return float(profile.budgetMonthly) / 4.33
    return None


def _rough_budget_cap(
    budget_weekly: Optional[float],
    policy: Optional[Dict[str, Any]] = None,
) -> Optional[int]:
    if not budget_weekly or budget_weekly <= 0:
        return None
    try:
        multiplier = float(_policy_get(policy, "planning.rough_budget_cap_multiplier", 1.5))
    except Exception:
        multiplier = 1.5
    multiplier = max(1.0, multiplier)
    try:
        min_slack_php = float(_policy_get(policy, "planning.rough_budget_cap_min_slack_php", 0))
    except Exception:
        min_slack_php = 0.0
    cap = max(float(budget_weekly), float(budget_weekly) * multiplier, float(budget_weekly) + max(0.0, min_slack_php))
    return int(round(cap))


def _build_selected_grocery_output(
    selected: List[Dict[str, Any]],
    *,
    budget_weekly: Optional[float],
) -> Dict[str, Any]:
    plan_payload: List[Dict[str, Any]] = []
    for recipe in selected:
        serving_scale = serving_cost_multiplier(recipe)
        ingredients = []
        for ingredient in recipe.get("ingredients", []) or []:
            if isinstance(ingredient, dict):
                scaled = dict(ingredient)
            else:
                scaled = {"name": str(ingredient), "quantity": ""}
            scaled["scale"] = serving_scale
            ingredients.append(scaled)
        plan_payload.append({"meals": [{"ingredients": ingredients}]})
    buckets = aggregate_grocery_list(plan_payload)
    output = price_grocery_buckets(buckets, weekly_budget_php=budget_weekly)
    output["selectedMealCount"] = len(selected)
    return output


def _budget_authority_diagnostics(
    *,
    budget_weekly: Optional[float],
    solver_budget_estimate_php: Optional[float],
    final_grocery_estimate_php: Optional[float],
    budget_gap_php: Optional[float],
) -> Dict[str, Any]:
    return {
        "userBudgetPhp": int(round(float(budget_weekly))) if budget_weekly else None,
        "solverBudgetEstimatePhp": int(round(float(solver_budget_estimate_php or 0))),
        "finalGroceryEstimatePhp": int(round(float(final_grocery_estimate_php or 0))),
        "displayedEstimateSource": "backend_aggregated_grocery",
        "budgetAuthority": "backend_aggregated_grocery",
        "budgetGapPhp": int(round(float(budget_gap_php))) if budget_gap_php is not None else None,
    }


def macro_ratios(_: str | None = None) -> tuple[float, float, float]:
    """Return the final PCOS wellness macro policy.

    The optional argument is retained for legacy callers during the transition.
    """
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
        extended = sorted(set([v for v in base if v >= 3] + [3, 4, 6, 8, 10]))
        return extended
    return base


def repeat_sequence_for_profile(
    base: List[int],
    preference: str | None,
    priority: str | None,
    *,
    hard_filter_count: int = 0,
    safe_candidate_count: Optional[int] = None,
) -> List[int]:
    sequence = adjust_max_per_week(base, preference)
    raw_preference = str(preference or "").strip().lower()
    raw_priority = str(priority or "").strip().lower()
    restricted_catalog = int(hard_filter_count or 0) >= 6 or (
        safe_candidate_count is not None and int(safe_candidate_count or 0) <= 96
    )
    if restricted_catalog:
        return sorted(set([value for value in sequence if value >= 6] + [6, 8, 10]))
    if "budget" in raw_priority and "high" not in raw_preference:
        return sorted(set([value for value in sequence if value >= 6] + [6, 8, 10]))
    return sequence


def _ordered_values_by_preference(values: List[Any], preferred: List[Any]) -> List[Any]:
    ordered: List[Any] = []
    for target in preferred:
        for value in values:
            if value in ordered:
                continue
            try:
                matches = abs(float(value) - float(target)) < 0.0001
            except Exception:
                matches = value == target
            if matches:
                ordered.append(value)
    for value in values:
        if value not in ordered:
            ordered.append(value)
    return ordered


def solve_pair_sequence_for_profile(
    tolerance_levels: List[float],
    max_per_week_list: List[int],
    relaxation_order: List[Any],
    *,
    restricted_catalog: bool = False,
    preferred_tolerances: Optional[List[float]] = None,
    preferred_repeats: Optional[List[int]] = None,
) -> List[Tuple[float, int]]:
    tol_sequence = [float(v) for v in tolerance_levels]
    repeat_sequence = [int(v) for v in max_per_week_list]
    if preferred_tolerances or preferred_repeats:
        if preferred_tolerances:
            tol_sequence = _ordered_values_by_preference(tol_sequence, preferred_tolerances)
        if preferred_repeats:
            repeat_sequence = _ordered_values_by_preference(repeat_sequence, preferred_repeats)
        return [(tol, max_repeat) for tol in tol_sequence for max_repeat in repeat_sequence]
    if restricted_catalog:
        tol_sequence = _ordered_values_by_preference(tol_sequence, [0.4, 0.6, 0.8, 0.3, 0.2])
        repeat_sequence = _ordered_values_by_preference(repeat_sequence, [10, 8, 6, 4, 3, 2])
        return [(tol, max_repeat) for tol in tol_sequence for max_repeat in repeat_sequence]

    outer_key = (str(relaxation_order[0]).strip().lower() if relaxation_order else "daily_tolerance_percent")
    if "recipe_repeat_limits" in outer_key:
        return [(tol, max_repeat) for max_repeat in repeat_sequence for tol in tol_sequence]
    return [(tol, max_repeat) for tol in tol_sequence for max_repeat in repeat_sequence]


def _solve_pair_preferences_for_profile(
    profile: UserProfile,
    *,
    restricted_catalog: bool = False,
) -> Dict[str, Any]:
    restrictions = {str(item or "").strip().lower() for item in (profile.dietaryRestrictions or [])}
    allergies = [str(item or "").strip() for item in (profile.allergies or []) if str(item or "").strip()]
    goal_tokens = normalize_goal_tokens(profile.goal)
    symptom_count = len(normalize_symptoms(profile.symptoms or []))
    priority = str(profile.planningPriority or "").strip().lower()
    major_diet = bool(restrictions & {"vegetarian", "pescatarian"})
    has_weekly_budget = resolve_budget_weekly(profile) is not None
    budget_weekly = resolve_budget_weekly(profile)
    tight_budget_profile = bool(budget_weekly and float(budget_weekly) < 2500.0)
    strict_time_limit = int(profile.maxCookingTimeMinutes or 0) > 0 and int(profile.maxCookingTimeMinutes or 0) <= 25

    if restricted_catalog or major_diet:
        return {
            "strategy": "restricted_or_major_diet",
            "preferredTolerances": [0.4, 0.6, 0.3, 0.8, 0.2],
            "preferredRepeats": [10, 8, 6, 4, 3, 2],
        }
    if strict_time_limit:
        return {
            "strategy": "strict_time_tolerance_first",
            "preferredTolerances": [0.3, 0.4, 0.2, 0.6, 0.8],
            "preferredRepeats": [4, 3, 6, 8, 10, 2],
        }
    if allergies:
        return {
            "strategy": "allergy_repeat_first",
            "preferredTolerances": [0.3, 0.2, 0.4, 0.6, 0.8],
            "preferredRepeats": [4, 10, 3, 2, 6, 8],
        }
    if "budget" in priority:
        return {
            "strategy": "budget_tolerance_first",
            "preferredTolerances": [0.3, 0.4, 0.2, 0.6, 0.8],
            "preferredRepeats": [6, 8, 10, 4, 3, 2],
        }
    if "quick" in priority or "prep" in priority:
        return {
            "strategy": "quick_prep_tolerance_first",
            "preferredTolerances": [0.3, 0.4, 0.2, 0.6, 0.8],
            "preferredRepeats": [4, 3, 6, 8, 10, 2],
        }
    if restrictions and "budget" not in priority:
        return {
            "strategy": "dietary_restriction_repeat_first",
            "preferredTolerances": [0.3, 0.2, 0.4, 0.6, 0.8],
            "preferredRepeats": [3, 4, 10, 2, 6, 8],
        }
    high_nutrition_pressure = (
        "nutrition" in priority
        or "tight" in priority
        or (GOAL_WEIGHT_LOSS.lower() in goal_tokens and GOAL_SYMPTOM_MANAGEMENT.lower() in goal_tokens)
        or symptom_count >= 2
    )
    if high_nutrition_pressure:
        if tight_budget_profile:
            return {
                "strategy": "nutrition_pressure_tight_budget_relaxed_first",
                "preferredTolerances": [0.6, 0.8, 0.4, 0.3, 0.2],
                "preferredRepeats": [10, 8, 6, 4, 3, 2],
            }
        return {
            "strategy": "nutrition_pressure_tolerance_first",
            "preferredTolerances": [0.3, 0.4, 0.2, 0.6, 0.8],
            "preferredRepeats": [3, 4, 10, 2, 6, 8],
        }
    if not has_weekly_budget:
        return {
            "strategy": "broad_no_budget_repeat_four_first",
            "preferredTolerances": [0.3, 0.4, 0.2, 0.6, 0.8],
            "preferredRepeats": [4, 3, 2, 10, 6, 8],
        }
    return {
        "strategy": "default_repeat_three_first",
        "preferredTolerances": None,
        "preferredRepeats": [3, 2, 4, 10],
    }


def priority_overrides(priority: str | None) -> Dict[str, int]:
    raw = (priority or "").lower()
    if "budget" in raw and ("quick" in raw or "prep" in raw):
        return {
            "budget_mult": 2,
            "repeat_weight": 3,
            "group_weight": 1,
            "diversity_weight": 1,
            "macro_mult": 1,
            "variety_mult": 1,
            "prep_time_mult": 3,
        }
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
    if "quick" in raw or "prep" in raw:
        return {
            "budget_mult": 1,
            "macro_mult": 1,
            "variety_mult": 1,
            "prep_time_mult": 3,
        }
    return {"budget_mult": 1, "macro_mult": 1, "variety_mult": 1}


def _should_optimize_cost(profile: UserProfile) -> bool:
    return "budget" in str(profile.planningPriority or "").strip().lower()


def _cheap_pre_price_score(recipe: Dict[str, Any]) -> float:
    protein = float(recipe.get("proteinGrams") or 0.0)
    calories = float(recipe.get("calories") or 0.0)
    fiber = float(recipe.get("fiberGrams") or 0.0)
    minutes = float(recipe.get("minutes") or 0.0)
    pantry_bonus = float(recipe.get("_pantry_match") or 0.0) * 1.5
    stage1_boost = float(recipe.get("_stage1_score_boost") or 0.0)
    prep_nudge = max(0.0, minutes - 30.0) * 0.03
    return (protein * 2.0) + (fiber * 1.2) - (abs(calories - 500.0) * 0.10) + pantry_bonus + stage1_boost - prep_nudge


def _nutrition_anchor_score(recipe: Dict[str, Any]) -> float:
    fiber = float(recipe.get("fiberGrams") or 0.0)
    carbs = float(recipe.get("carbsGrams") or 0.0)
    protein = float(recipe.get("proteinGrams") or 0.0)
    calories = float(recipe.get("calories") or 0.0)
    sodium = float(recipe.get("sodiumMg") or 0.0)
    sugar = float(recipe.get("sugarGrams") or 0.0)
    cost = float(recipe.get("_cost_est") or 0.0)
    excess_sodium = max(0.0, sodium - 1800.0)
    excess_sugar = max(0.0, sugar - 35.0)
    excess_calories = max(0.0, calories - 900.0)
    return (
        (fiber * 8.0)
        + (min(carbs, 90.0) * 0.35)
        + (min(protein, 55.0) * 0.20)
        - (excess_sodium * 0.004)
        - (excess_sugar * 0.35)
        - (excess_calories * 0.03)
        - (cost * 0.01)
    )


def _is_restricted_nutrition_anchor(recipe: Dict[str, Any]) -> bool:
    calories = int(recipe.get("calories") or 0)
    protein = int(recipe.get("proteinGrams") or 0)
    carbs = int(recipe.get("carbsGrams") or 0)
    fats = int(recipe.get("fatsGrams") or 0)
    fiber = int(recipe.get("fiberGrams") or 0)
    return (
        400 <= calories <= 650
        and 18 <= protein <= 30
        and 45 <= carbs <= 90
        and 8 <= fats <= 25
        and fiber >= 8
    )


def _add_unique_recipe(selected: List[Dict[str, Any]], selected_ids: set[str], recipe: Dict[str, Any], limit: int) -> bool:
    if len(selected) >= max(1, int(limit or 1)):
        return False
    recipe_key = str(recipe.get("id") or id(recipe))
    if recipe_key in selected_ids:
        return False
    selected_ids.add(recipe_key)
    selected.append(recipe)
    return True


def _apply_restricted_nutrition_trim(bucket: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
    normalized_limit = max(1, int(limit or 1))
    if len(bucket) <= normalized_limit:
        return bucket
    selected: List[Dict[str, Any]] = []
    selected_ids: set[str] = set()
    anchors = sorted(
        [recipe for recipe in bucket if _is_restricted_nutrition_anchor(recipe)],
        key=lambda r: (
            _nutrition_anchor_score(r),
            float(r.get("proteinGrams") or 0.0),
            float(r.get("fiberGrams") or 0.0),
            -float(r.get("_cost_est") or 0.0),
            str(r.get("id") or ""),
        ),
        reverse=True,
    )
    anchor_reserve = min(normalized_limit, max(6, int(normalized_limit * 0.75)))
    for recipe in anchors[:anchor_reserve]:
        _add_unique_recipe(selected, selected_ids, recipe, normalized_limit)
    for recipe in sorted(bucket, key=_base_score, reverse=True):
        if len(selected) >= normalized_limit:
            break
        _add_unique_recipe(selected, selected_ids, recipe, normalized_limit)
    return selected


def _apply_anchor_preserving_similarity_dedup(
    bucket: List[Dict[str, Any]],
    threshold: float,
) -> List[Dict[str, Any]]:
    selected: List[Dict[str, Any]] = []
    selected_ids: set[str] = set()
    for recipe in bucket:
        if _is_restricted_nutrition_anchor(recipe):
            _add_unique_recipe(selected, selected_ids, recipe, len(bucket))
    for recipe in _apply_similarity_dedup(bucket, threshold):
        _add_unique_recipe(selected, selected_ids, recipe, len(bucket))
    return selected


def _reinsert_restricted_nutrition_anchors(
    buckets: Dict[str, List[Dict[str, Any]]],
    anchors: List[Dict[str, Any]],
) -> None:
    present_ids = {
        str(recipe.get("id") or "")
        for bucket in buckets.values()
        for recipe in bucket
        if str(recipe.get("id") or "")
    }
    for recipe in anchors:
        recipe_id = str(recipe.get("id") or "")
        if not recipe_id or recipe_id in present_ids:
            continue
        bucket_key = _pre_pricing_bucket_key(recipe)
        if bucket_key not in buckets:
            bucket_key = "Universal"
        buckets[bucket_key].append(recipe)
        present_ids.add(recipe_id)


def _pre_pricing_bucket_key(recipe: Dict[str, Any]) -> str:
    allowed = [str(label) for label in (recipe.get("_allowed_meals") or []) if str(label) in MEAL_LABELS]
    if len(allowed) == 1:
        return allowed[0]
    return "Universal"


def _apply_pre_pricing_prune(
    candidates: List[Dict[str, Any]],
    *,
    cap: int,
    bucket_reserve: int,
) -> Tuple[List[Dict[str, Any]], bool]:
    normalized_cap = max(1, int(cap or 1))
    if len(candidates) <= normalized_cap:
        return candidates, False
    reserve = max(0, int(bucket_reserve or 0))
    ranked = sorted(
        candidates,
        key=lambda r: (
            float(r.get("_pre_price_score") or 0.0),
            float(r.get("proteinGrams") or 0.0),
            float(r.get("fiberGrams") or 0.0),
        ),
        reverse=True,
    )
    selected: List[Dict[str, Any]] = []
    selected_ids: set[str] = set()

    def add_recipe(recipe: Dict[str, Any]) -> None:
        _add_unique_recipe(selected, selected_ids, recipe, normalized_cap)

    top_count = max(1, int(normalized_cap * 0.55))
    for recipe in ranked[:top_count]:
        add_recipe(recipe)

    nutrition_anchor_reserve = max(4, int(normalized_cap * 0.25))
    nutrition_anchor_per_bucket = max(1, nutrition_anchor_reserve // max(1, len(MEAL_LABELS) + 1))
    nutrition_ranked = sorted(
        candidates,
        key=lambda r: (
            _nutrition_anchor_score(r),
            float(r.get("fiberGrams") or 0.0),
            float(r.get("carbsGrams") or 0.0),
            float(r.get("proteinGrams") or 0.0),
        ),
        reverse=True,
    )
    for bucket in [*MEAL_LABELS, "Universal"]:
        added = 0
        for recipe in nutrition_ranked:
            if _pre_pricing_bucket_key(recipe) != bucket:
                continue
            before = len(selected)
            add_recipe(recipe)
            if len(selected) > before:
                added += 1
            if added >= nutrition_anchor_per_bucket or len(selected) >= normalized_cap:
                break

    if reserve > 0:
        for bucket in [*MEAL_LABELS, "Universal"]:
            added = 0
            for recipe in ranked:
                if _pre_pricing_bucket_key(recipe) != bucket:
                    continue
                before = len(selected)
                add_recipe(recipe)
                if len(selected) > before:
                    added += 1
                if added >= reserve or len(selected) >= normalized_cap:
                    break

    for recipe in ranked:
        add_recipe(recipe)
        if len(selected) >= normalized_cap:
            break

    return selected, True


def _apply_budget_nutrition_trim(bucket: List[Dict[str, Any]], keep: int) -> List[Dict[str, Any]]:
    normalized_keep = max(1, int(keep or 1))
    if len(bucket) <= normalized_keep:
        return bucket

    by_cost = sorted(
        bucket,
        key=lambda r: (
            int(r.get("_cost_est") or 0),
            -_base_score(r),
            str(r.get("id") or ""),
        ),
    )
    by_nutrition = sorted(
        bucket,
        key=lambda r: (
            _nutrition_anchor_score(r),
            float(r.get("fiberGrams") or 0.0),
            float(r.get("carbsGrams") or 0.0),
            float(r.get("proteinGrams") or 0.0),
            -float(r.get("sodiumMg") or 0.0),
            -float(r.get("sugarGrams") or 0.0),
        ),
        reverse=True,
    )
    nutrition_reserve = min(max(2, normalized_keep // 4), max(2, normalized_keep - 1))
    budget_count = max(1, normalized_keep - nutrition_reserve)
    selected: List[Dict[str, Any]] = []
    selected_ids: set[str] = set()
    for recipe in by_cost[:budget_count]:
        _add_unique_recipe(selected, selected_ids, recipe, normalized_keep)
    for recipe in by_nutrition:
        if len(selected) >= normalized_keep:
            break
        _add_unique_recipe(selected, selected_ids, recipe, normalized_keep)
    for recipe in by_cost:
        if len(selected) >= normalized_keep:
            break
        _add_unique_recipe(selected, selected_ids, recipe, normalized_keep)
    return sorted(
        selected,
        key=lambda r: (
            int(r.get("_cost_est") or 0),
            -_nutrition_anchor_score(r),
            str(r.get("id") or ""),
        ),
    )


def shortlist_candidates(
    profile: UserProfile,
    recipes: List[Dict[str, Any]],
    policy: Optional[Dict[str, Any]] = None,
    stage1_diag: Optional[Dict[str, Any]] = None,
    pricing_context: Optional[PricingContext] = None,
    deadline_at: Optional[float] = None,
    ml_feature_context: Optional[Dict[str, Any]] = None,
) -> Dict[str, List[Dict[str, Any]]]:
    pricing_context = pricing_context or create_pricing_context()
    buckets = {"Breakfast": [], "Lunch": [], "Dinner": [], "Universal": []}
    restriction_count = len(profile.dietaryRestrictions or [])
    allergy_count = len(profile.allergies or [])
    budget_weekly = resolve_budget_weekly(profile)
    max_cook = profile.maxCookingTimeMinutes if profile.maxCookingTimeMinutes and profile.maxCookingTimeMinutes > 0 else None
    pantry_tokens = set(normalize_pantry(profile.pantryItems or []))
    canonical_features_enabled = bool(_policy_get(policy, "stage1.canonical_features_enabled", False))
    canonical_pantry_ids: set[str] = set()
    if canonical_features_enabled:
        for pantry_item in profile.pantryItems or []:
            resolution = resolve_ingredient(str(pantry_item or ""))
            if resolution.status == "mapped" and resolution.ingredient_id:
                canonical_pantry_ids.add(resolution.ingredient_id)
    stage1_max = int(
        _policy_get_legacy_aware(
            policy,
            ["stage1.max_candidates_per_slot", "max_pool_size", "shortlist_limit_restricted", "shortlist_limit"],
            120,
        )
    )
    ranking_cutoff = float(
        _policy_get_legacy_aware(policy, ["stage1.ranking_cutoff", "shortlist_keep_ratio"], 0.8)
    )
    similarity_threshold = float(_policy_get(policy, "stage1.similarity_threshold", 0.85))
    restricted_shortlist_multiplier = float(_policy_get(policy, "stage1.restricted_shortlist_multiplier", 1.25))
    budget_keep_min_count = int(_policy_get(policy, "stage1.budget_keep_min_count", 10))
    budget_keep_min_ratio = float(_policy_get(policy, "stage1.budget_keep_min_ratio", 0.25))
    pantry_match_threshold = int(_policy_get(policy, "stage1.pantry_match_threshold", 0))
    pre_pricing_enabled = bool(_policy_get(policy, "stage1.pre_pricing_pruning_enabled", True))
    pre_pricing_multiplier = float(_policy_get(policy, "stage1.pre_pricing_candidate_multiplier", 5.0))
    pre_pricing_cap_config = _policy_get(policy, "stage1.pre_pricing_candidate_cap", None)
    if pre_pricing_cap_config is None:
        pre_pricing_cap = max(240, int(stage1_max * max(1.0, pre_pricing_multiplier)))
    else:
        pre_pricing_cap = max(1, int(pre_pricing_cap_config))
    pre_pricing_bucket_reserve = int(_policy_get(policy, "stage1.pre_pricing_bucket_reserve", 32))
    pre_pricing_restricted_enabled = bool(_policy_get(policy, "stage1.pre_pricing_restricted_enabled", False))
    symptom_state = symptom_adjustments(profile, profile.goal)
    ml_scoring_enabled = _stage1_ml_scoring_enabled(policy)
    ml_weight = float(_policy_get(policy, "stage1.ML_score_weight", 0.15))
    ml_cap = float(_policy_get(policy, "stage1.ML_score_cap", 0.30))
    ml_weight = max(0.0, min(ml_weight, ml_cap, 1.0))
    apply_ml_to_ranking = _stage1_ml_applies_to_ranking(profile, policy)
    ranker = get_stage1_ranker() if ml_scoring_enabled else None
    ranker_state = ranker.state() if ranker is not None else None
    ml_model_version = str(ranker_state.model_version) if ranker_state is not None else "shadow_v0"
    ml_scored_recipes: List[Dict[str, Any]] = []
    ml_feature_vectors: List[Dict[str, float]] = []
    exclusion_summary = {
        "allergy": 0,
        "restriction": 0,
        "prep_time": 0,
        "pantry": 0,
    }
    exclusion_detail_counts: Dict[str, int] = {}
    phase_started_at = time.time()
    processed_recipe_count = 0
    safe_candidates: List[Dict[str, Any]] = []
    for r in recipes:
        processed_recipe_count += 1
        if deadline_at is not None and (processed_recipe_count == 1 or processed_recipe_count % 16 == 0):
            if time.time() >= deadline_at:
                if stage1_diag is not None:
                    stage1_diag["preprocess_ms"] = max(0, int((time.time() - phase_started_at) * 1000))
                    stage1_diag["pricing_diagnostics"] = pricing_context.snapshot()
                    stage1_diag["cost_estimation_ms"] = int(pricing_context.price_cost_estimation_ms)
                    stage1_diag["processed_recipe_count"] = processed_recipe_count
                    stage1_diag["timeout_stage"] = "stage1_price_estimation"
                raise _PlannerBudgetExceeded("stage1_price_estimation")
        static_features = _recipe_static_features(r, canonical_features_enabled)
        tags = static_features["tags"]
        ing_tokens = static_features["ing_tokens"]
        restriction_failures = restriction_failure_reasons(
            profile,
            tags,
            ing_tokens,
            static_features["canonical_allergen_families"],
        )
        if restriction_failures:
            for reason in sorted(set(restriction_failures)):
                _increment_count(exclusion_detail_counts, reason)
            if any(reason.startswith("allergy:") for reason in restriction_failures):
                exclusion_summary["allergy"] += 1
            else:
                exclusion_summary["restriction"] += 1
            continue
        minutes = int(r.get("minutes") or 0)
        if max_cook is not None and minutes > max_cook:
            exclusion_summary["prep_time"] += 1
            _increment_count(exclusion_detail_counts, "prep_time:over_limit")
            continue
        penalty_weights = _policy_get(
            policy,
            "stage1.exclusion_penalty_weights",
            {"allergy": 1000.0, "restriction": 500.0, "prep_time": 50.0},
        )
        prep_penalty_weight = float((penalty_weights or {}).get("prep_time", 50.0))
        prep_penalty = 0.0
        if max_cook and max_cook > 0:
            prep_penalty = max(0.0, (float(minutes) - max_cook) / float(max_cook)) * prep_penalty_weight
        r["_tags"] = tags
        r["_ing_tokens"] = ing_tokens
        r["_canonical_ingredient_ids"] = sorted(static_features["canonical_ingredient_ids"])
        r["_canonical_feature_available"] = bool(static_features["canonical_feature_available"])
        r["_protein_group"] = static_features["protein_group"]
        r["_veg_tokens"] = static_features["veg_tokens"]
        r["_allowed_meals"] = static_features["allowed_meals"]
        if pantry_tokens:
            canonical_match = len(static_features["canonical_ingredient_ids"] & canonical_pantry_ids)
            legacy_match = len(set(static_features["legacy_ing_tokens"]) & pantry_tokens)
            if canonical_features_enabled and canonical_pantry_ids and static_features["canonical_feature_available"]:
                r["_pantry_match"] = max(canonical_match, legacy_match)
                r["_pantry_match_method"] = (
                    "canonical_plus_legacy"
                    if canonical_match != legacy_match
                    else "canonical"
                )
            else:
                r["_pantry_match"] = legacy_match
                r["_pantry_match_method"] = "legacy_tokens"
        else:
            r["_pantry_match"] = 0
            r["_pantry_match_method"] = "none"
        r["_stage1_prep_penalty"] = prep_penalty
        r["_ml_shadow_score"] = 0.0
        r["_ml_model_version"] = ml_model_version
        r["_ml_applied_to_ranking"] = apply_ml_to_ranking
        symptom_boost, symptom_reasons = stage1_recipe_adjustments(r, profile, symptom_state, profile.goal)
        r["_symptom_goal_boost"] = symptom_boost
        r["_selection_reasons"] = symptom_reasons
        r["_stage1_score_boost"] = symptom_boost - prep_penalty
        if pantry_match_threshold > 0 and pantry_tokens and r["_pantry_match"] < pantry_match_threshold:
            exclusion_summary["pantry"] += 1
            _increment_count(exclusion_detail_counts, "pantry:below_threshold")
            continue
        r["_pre_price_score"] = _cheap_pre_price_score(r)
        safe_candidates.append(r)

    safe_candidates_count = len(safe_candidates)
    hard_filter_count = restriction_count + allergy_count
    tight_budget_profile = bool(budget_weekly and float(budget_weekly) < 2500.0)
    budget_sensitive_profile = bool(budget_weekly and (_should_optimize_cost(profile) or tight_budget_profile))
    should_pre_prune = (
        pre_pricing_enabled
        and safe_candidates_count > pre_pricing_cap
        and (pre_pricing_restricted_enabled or hard_filter_count == 0)
    )
    if should_pre_prune:
        safe_candidates, pre_pruned = _apply_pre_pricing_prune(
            safe_candidates,
            cap=pre_pricing_cap,
            bucket_reserve=pre_pricing_bucket_reserve,
        )
    else:
        pre_pruned = False

    cost_cache: Dict[Tuple[str, int, int], int] = {}
    cost_cache_stats: Dict[str, int] = {"recipeCostCacheHits": 0, "recipeCostCacheMisses": 0}
    cost_estimated_recipe_count = 0
    for r in safe_candidates:
        cost_estimated_recipe_count += 1
        r["_cost_est"] = estimate_cost(
            r,
            pricing_context=pricing_context,
            cost_cache=cost_cache,
            cost_cache_stats=cost_cache_stats,
        )
        if deadline_at is not None and cost_estimated_recipe_count % 8 == 0 and time.time() >= deadline_at:
            if stage1_diag is not None:
                stage1_diag["preprocess_ms"] = max(0, int((time.time() - phase_started_at) * 1000))
                stage1_diag["pricing_diagnostics"] = pricing_context.snapshot()
                stage1_diag["cost_estimation_ms"] = int(pricing_context.price_cost_estimation_ms)
                stage1_diag["processed_recipe_count"] = processed_recipe_count
                stage1_diag["safe_recipe_count_pre_pricing"] = safe_candidates_count
                stage1_diag["pre_pricing_budget_sensitive"] = bool(budget_sensitive_profile)
                stage1_diag["pre_pricing_pruned"] = bool(pre_pruned)
                stage1_diag["pre_pricing_candidate_cap"] = int(pre_pricing_cap)
                stage1_diag["pre_pricing_retained_count"] = len(safe_candidates)
                stage1_diag["cost_estimated_recipe_count"] = cost_estimated_recipe_count
                stage1_diag["recipe_cost_cache"] = dict(cost_cache_stats)
                stage1_diag["timeout_stage"] = "stage1_price_estimation"
            raise _PlannerBudgetExceeded("stage1_price_estimation")
        if ml_scoring_enabled:
            ml_scored_recipes.append(r)
            ml_feature_vectors.append(_stage1_ml_feature_vector(r, profile, ml_feature_context))
        meal_type = (r.get("mealType") or "Universal").lower()
        if "break" in meal_type:
            buckets["Breakfast"].append(r)
        elif "lunch" in meal_type:
            buckets["Lunch"].append(r)
        elif "dinner" in meal_type:
            buckets["Dinner"].append(r)
        else:
            buckets["Universal"].append(r)
    restricted_anchor_recipes = [recipe for recipe in safe_candidates if _is_restricted_nutrition_anchor(recipe)]
    if stage1_diag is not None:
        stage1_diag["preprocess_ms"] = max(0, int((time.time() - phase_started_at) * 1000))
        stage1_diag["pricing_diagnostics"] = pricing_context.snapshot()
        stage1_diag["cost_estimation_ms"] = int(pricing_context.price_cost_estimation_ms)
        stage1_diag["processed_recipe_count"] = processed_recipe_count
        stage1_diag["safe_recipe_count_pre_pricing"] = safe_candidates_count
        stage1_diag["restricted_nutrition_anchor_count_safe"] = len(restricted_anchor_recipes)
        stage1_diag["pre_pricing_pruning_enabled"] = bool(pre_pricing_enabled)
        stage1_diag["pre_pricing_restricted_enabled"] = bool(pre_pricing_restricted_enabled)
        stage1_diag["pre_pricing_budget_sensitive"] = bool(budget_sensitive_profile)
        stage1_diag["pre_pricing_tight_budget"] = bool(tight_budget_profile)
        stage1_diag["pre_pricing_pruned"] = bool(pre_pruned)
        stage1_diag["pre_pricing_candidate_cap"] = int(pre_pricing_cap)
        stage1_diag["pre_pricing_retained_count"] = len(safe_candidates)
        stage1_diag["cost_estimated_recipe_count"] = int(cost_estimated_recipe_count)
        stage1_diag["recipe_cost_cache"] = dict(cost_cache_stats)

    if ml_scored_recipes:
        ml_started_at = time.time()
        if ranker_state is not None and ranker_state.ready and ranker is not None:
            ml_scores = ranker.score_many(ml_feature_vectors)
        else:
            ml_scores = [None for _ in ml_feature_vectors]
        for recipe, candidate_features, model_score in zip(ml_scored_recipes, ml_feature_vectors, ml_scores):
            resolved_model_score = model_score
            resolved_model_version = ml_model_version
            if resolved_model_score is None:
                resolved_model_score = _shadow_ml_score(recipe, profile)
                resolved_model_version = "shadow_v0"
            _finalize_stage1_scoring(
                recipe,
                ml_score=float(resolved_model_score or 0.0),
                ml_model_version=resolved_model_version,
                apply_ml_to_ranking=apply_ml_to_ranking,
                ml_weight=ml_weight,
                prep_penalty=float(recipe.get("_stage1_prep_penalty") or 0.0),
            )
        if stage1_diag is not None:
            stage1_diag["ml_score_ms"] = max(0, int((time.time() - ml_started_at) * 1000))

    finalize_started_at = time.time()
    restricted_catalog = hard_filter_count >= 6 or safe_candidates_count <= 96
    for k in buckets:
        buckets[k].sort(key=_base_score, reverse=True)
        if restricted_catalog:
            buckets[k] = _apply_anchor_preserving_similarity_dedup(buckets[k], similarity_threshold)
        else:
            buckets[k] = _apply_similarity_dedup(buckets[k], similarity_threshold)
        limit = stage1_max if restriction_count < 2 else int(stage1_max * max(1.0, restricted_shortlist_multiplier))
        if restricted_catalog:
            buckets[k] = _apply_restricted_nutrition_trim(buckets[k], limit)
        else:
            buckets[k] = buckets[k][:limit]
        if budget_weekly and not restricted_catalog:
            keep_min = max(max(1, budget_keep_min_count), int(len(buckets[k]) * max(0.0, budget_keep_min_ratio)))
            keep = int(max(keep_min, len(buckets[k]) * ranking_cutoff))
            buckets[k] = _apply_budget_nutrition_trim(buckets[k], keep)
        for recipe in buckets[k]:
            recipe["_stage1_bucket"] = k
    if restricted_catalog and restricted_anchor_recipes:
        _reinsert_restricted_nutrition_anchors(buckets, restricted_anchor_recipes)
        for k in buckets:
            for recipe in buckets[k]:
                recipe["_stage1_bucket"] = k
    restricted_anchor_count_post_trim = sum(
        1 for recipe in (buckets["Breakfast"] + buckets["Lunch"] + buckets["Dinner"] + buckets["Universal"])
        if _is_restricted_nutrition_anchor(recipe)
    )
    if stage1_diag is not None:
        stage1_diag["bucket_finalize_ms"] = max(0, int((time.time() - finalize_started_at) * 1000))
        stage1_diag["pricing_diagnostics"] = pricing_context.snapshot()
        stage1_diag["cost_estimation_ms"] = int(pricing_context.price_cost_estimation_ms)
        stage1_diag["ml_candidate_count"] = len(ml_scored_recipes)
        stage1_diag["ranker_ready"] = bool(ranker_state.ready) if ranker_state is not None else False
        stage1_diag["exclusion_summary"] = dict(exclusion_summary)
        stage1_diag["exclusion_detail_counts"] = dict(exclusion_detail_counts)
        stage1_diag["restricted_nutrition_anchor_reserve"] = bool(restricted_catalog)
        stage1_diag["restricted_nutrition_anchor_count_post_trim"] = int(restricted_anchor_count_post_trim)
        stage1_diag["goal_symptom_strategy"] = list(symptom_state.get("notes") or [])
        stage1_diag["canonical_features_enabled"] = canonical_features_enabled
        stage1_diag["canonical_feature_recipe_count"] = sum(
            1 for recipe in safe_candidates if recipe.get("_canonical_feature_available")
        )
    return buckets


def build_swap_candidates(
    profile: UserProfile,
    recipes: List[Dict[str, Any]],
    *,
    meal_label: str,
    current_recipe_id: Optional[str] = None,
    active_recipe_ids: Optional[List[str]] = None,
    limit: int = 20,
    policy: Optional[Dict[str, Any]] = None,
) -> List[Dict[str, Any]]:
    normalized_meal_label = str(meal_label or "").strip().title()
    if normalized_meal_label not in MEAL_LABELS:
        return []

    shortlisted = shortlist_candidates(profile, recipes, policy=policy)
    pool = shortlisted.get(normalized_meal_label, []) + shortlisted.get("Universal", [])
    if not pool:
        return []

    current_counts: Dict[str, int] = {}
    for recipe_id in active_recipe_ids or []:
        normalized_id = str(recipe_id or "").strip()
        if not normalized_id:
            continue
        current_counts[normalized_id] = current_counts.get(normalized_id, 0) + 1

    repeat_limit_candidates = adjust_max_per_week(
        [
            int(v)
            for v in _policy_get_legacy_aware(
                policy,
                ["planning.recipe_repeat_limits", "max_per_week"],
                _env_int_list("PCOSINA_MAX_PER_WEEK", [2, 3, 4, 10]),
            )
        ],
        profile.varietyPreference,
    )
    baseline_repeat_limit = min(repeat_limit_candidates) if repeat_limit_candidates else 2
    max_repeat_limit = max(repeat_limit_candidates or [baseline_repeat_limit])

    budget_weekly = resolve_budget_weekly(profile)
    current_total_cost = 0.0
    if budget_weekly:
        current_total_cost = sum(
            float(estimate_cost(recipe))
            for recipe in recipes
            for _ in range(current_counts.get(str(recipe.get("id") or ""), 0))
        )

    filtered: List[Dict[str, Any]] = []
    for recipe in pool:
        recipe_id = str(recipe.get("id") or "").strip()
        if not recipe_id or recipe_id == str(current_recipe_id or "").strip():
            continue

        next_repeat_count = current_counts.get(recipe_id, 0) + 1
        if next_repeat_count > max_repeat_limit:
            continue

        if budget_weekly:
            current_recipe_cost = 0.0
            if current_recipe_id:
                current_recipe_cost = float(
                    next(
                        (
                            estimate_cost(item)
                            for item in recipes
                            if str(item.get("id") or "") == str(current_recipe_id)
                        ),
                        0.0,
                    )
                )
            candidate_total_cost = current_total_cost - current_recipe_cost + float(
                recipe.get("_cost_est") or estimate_cost(recipe)
            )
            if candidate_total_cost > float(budget_weekly):
                continue

        filtered.append(recipe)

    filtered.sort(key=_base_score, reverse=True)
    deduped: List[Dict[str, Any]] = []
    seen_ids: set[str] = set()
    for recipe in filtered:
        recipe_id = str(recipe.get("id") or "").strip()
        if not recipe_id or recipe_id in seen_ids:
            continue
        seen_ids.add(recipe_id)
        deduped.append(recipe)
        if len(deduped) >= max(1, int(limit or 20)):
            break
    return deduped


def _safe_div(num: float, den: float) -> float:
    if den == 0:
        return 0.0
    return float(num) / float(den)


def _build_stage1_feature_rows(
    pool: List[Dict[str, Any]],
    profile: UserProfile,
    *,
    target_calories: int,
    target_protein: int,
    target_carbs: int,
    target_fats: int,
    budget_weekly: Optional[float],
    meals_per_day: int,
) -> List[Dict[str, Any]]:
    rows: List[Dict[str, Any]] = []
    budget_norm = _safe_div(float(budget_weekly or 0.0), 7000.0)
    ml_feature_context = {
        "target_calories": float(target_calories),
        "target_protein": float(target_protein),
        "target_carbs": float(target_carbs),
        "target_fats": float(target_fats),
        "budget_weekly": float(budget_weekly or 0.0),
        "meals_per_day": float(meals_per_day),
    }
    for recipe in pool:
        features = _stage1_ml_feature_vector(recipe, profile, ml_feature_context)
        feature_payload = {
            key: value
            for key, value in features.items()
            if key not in {"model_score", "heuristic_score"}
        }
        feature_payload["budget_weekly_norm"] = budget_norm
        rows.append(
            {
                "recipe_id": str(recipe.get("id") or ""),
                "meal_bucket": str(recipe.get("_stage1_bucket") or "Universal"),
                "model_score": float(recipe.get("_ml_shadow_score") or 0.0),
                "heuristic_score": float(_base_score(recipe)),
                "features": feature_payload,
            }
        )
    return rows


def _calorie_bin(calories: int) -> str:
    if calories < 350:
        return "lt350"
    if calories < 500:
        return "350_499"
    if calories < 650:
        return "500_649"
    return "ge650"


def _cap_pool(pool: List[Dict[str, Any]], max_pool: int, top_share: float = 0.6) -> List[Dict[str, Any]]:
    if len(pool) <= max_pool:
        return pool
    scored = []
    for r in pool:
        rid = str(r.get("id", ""))
        scored.append((_base_score(r), rid, r))
    scored.sort(key=lambda item: (-item[0], item[1]))
    bounded_top_share = min(0.95, max(0.05, float(top_share)))
    selected: List[Dict[str, Any]] = []
    selected_ids: set[str] = set()
    anchor_limit = min(max_pool, max(8, int(max_pool * 0.40)))
    anchors = sorted(
        [r for _, _, r in scored if _is_restricted_nutrition_anchor(r)],
        key=lambda r: (
            _nutrition_anchor_score(r),
            float(r.get("proteinGrams") or 0.0),
            float(r.get("fiberGrams") or 0.0),
            -float(r.get("_cost_est") or 0.0),
            str(r.get("id") or ""),
        ),
        reverse=True,
    )
    for recipe in anchors[:anchor_limit]:
        _add_unique_recipe(selected, selected_ids, recipe, max_pool)
    top_k = max(1, int(max_pool * bounded_top_share))
    for _, _, recipe in scored[:top_k]:
        _add_unique_recipe(selected, selected_ids, recipe, max_pool)
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


def _restricted_solver_anchor_core(pool: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    anchors = [recipe for recipe in pool if _is_restricted_nutrition_anchor(recipe)]
    if len(anchors) < 3:
        return pool
    return sorted(
        anchors,
        key=lambda recipe: (
            _nutrition_anchor_score(recipe),
            float(recipe.get("proteinGrams") or 0.0),
            float(recipe.get("fiberGrams") or 0.0),
            -float(recipe.get("_cost_est") or 0.0),
            str(recipe.get("id") or ""),
        ),
        reverse=True,
    )


def _nutrition_coverage_gap(
    pool: List[Dict[str, Any]],
    meal_to_allowed: Dict[str, set],
    slot_labels: List[str],
    *,
    calorie_min: int,
    protein_min: int,
    carb_min: int,
    fat_min: int,
    fiber_min: int,
    sodium_max: int,
    sugar_max: int,
) -> Dict[str, Any]:
    nutrient_fields = {
        "calories": "calories",
        "proteinGrams": "protein",
        "carbsGrams": "carbs",
        "fatsGrams": "fats",
        "fiberGrams": "fiber",
        "sodiumMg": "sodium",
        "sugarGrams": "sugar",
    }
    required_min = {
        "calories": int(calorie_min),
        "proteinGrams": int(protein_min),
        "carbsGrams": int(carb_min),
        "fatsGrams": int(fat_min),
        "fiberGrams": int(fiber_min),
    }
    required_max = {
        "sodiumMg": int(sodium_max),
        "sugarGrams": int(sugar_max),
    }
    slot_best: Dict[str, Dict[str, int]] = {}
    slot_floor: Dict[str, Dict[str, int]] = {}
    possible_daily_max = {field: 0 for field in nutrient_fields}
    possible_daily_min = {field: 0 for field in nutrient_fields}
    for label in slot_labels:
        allowed = list(meal_to_allowed.get(label, set(range(len(pool)))))
        if not allowed:
            return {
                "ok": False,
                "gaps": [{"nutrient": "meal_slot", "slot": label, "reason": "no_allowed_candidates"}],
                "slotBest": slot_best,
                "slotFloor": slot_floor,
            }
        best_for_slot: Dict[str, int] = {}
        floor_for_slot: Dict[str, int] = {}
        for field in nutrient_fields:
            values = [int(pool[idx].get(field) or 0) for idx in allowed]
            best_for_slot[field] = max(values)
            floor_for_slot[field] = min(values)
            possible_daily_max[field] += best_for_slot[field]
            possible_daily_min[field] += floor_for_slot[field]
        slot_best[label] = best_for_slot
        slot_floor[label] = floor_for_slot

    gaps: List[Dict[str, Any]] = []
    advisory_gaps: List[Dict[str, Any]] = []
    for field, minimum in required_min.items():
        possible = int(possible_daily_max.get(field) or 0)
        if possible < minimum:
            gaps.append(
                {
                    "nutrient": nutrient_fields[field],
                    "requiredMin": int(minimum),
                    "possibleDailyMax": possible,
                }
            )
    for field, maximum in required_max.items():
        possible = int(possible_daily_min.get(field) or 0)
        if possible > maximum:
            advisory_gaps.append(
                {
                    "nutrient": nutrient_fields[field],
                    "advisoryMax": int(maximum),
                    "possibleDailyMin": possible,
                }
            )
    return {
        "ok": len(gaps) == 0,
        "gaps": gaps,
        "advisoryGaps": advisory_gaps,
        "requiredMin": required_min,
        "advisoryMax": required_max,
        "possibleDailyMax": possible_daily_max,
        "possibleDailyMin": possible_daily_min,
        "slotBest": slot_best,
        "slotFloor": slot_floor,
    }


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


def _selection_reason_payload(selected: List[Dict[str, Any]]) -> tuple[Dict[str, List[str]], Dict[str, int]]:
    by_recipe: Dict[str, List[str]] = {}
    counts: Dict[str, int] = {}
    for recipe in selected:
        recipe_id = str(recipe.get("id") or "").strip()
        if not recipe_id:
            continue
        reasons = [
            str(reason).strip()
            for reason in (recipe.get("_selection_reasons") or [])
            if str(reason).strip()
        ]
        if not reasons:
            continue
        deduped = sorted(set(reasons))
        by_recipe[recipe_id] = deduped
        for reason in deduped:
            counts[reason] = counts.get(reason, 0) + 1
    return by_recipe, counts


def _build_explanation(
    selected: List[Dict[str, Any]],
    num_days: int,
    meals_per_day: int,
    daily_targets: List[int],
    target: int,
    target_protein: int,
    target_carbs: int,
    target_fats: int,
    tol: float,
    max_per_week: int,
    profile: UserProfile,
    budget_weekly: Optional[float],
    candidate_pool_size: int = 0,
    *,
    profile_rule_effects: Optional[Dict[str, List[str]]] = None,
    symptom_state: Optional[Dict[str, Any]] = None,
    candidate_exclusion_summary: Optional[Dict[str, int]] = None,
    budget_hard_cap_applied: bool = False,
    fiber_min_target: Optional[int] = None,
    sugar_max_target: Optional[int] = None,
) -> Dict[str, Any]:
    if not selected or num_days <= 0:
        return {}
    daily_cals = []
    daily_pro = []
    daily_carb = []
    daily_fat = []
    for d in range(num_days):
        day_items = selected[d * meals_per_day:(d * meals_per_day + meals_per_day)]
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
    selection_reasons_by_recipe, selection_reason_counts = _selection_reason_payload(selected)
    return {
        "fallbackUsed": False,
        "authority": "cp-sat",
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
        "candidatePoolSize": int(candidate_pool_size),
        "selectedMeals": len(selected),
        "budgetHardCapApplied": bool(budget_hard_cap_applied),
        "goalValue": str(profile.goal or "").strip(),
        "symptomSelections": list(normalize_symptoms(profile.symptoms or [])),
        "profileRuleEffects": profile_rule_effects or profile_rule_summary(profile, budget_weekly),
        "plannerContract": planner_contract_summary(profile, budget_weekly),
        "symptomStrategy": list((symptom_state or {}).get("notes") or []),
        "candidateExclusionSummary": candidate_exclusion_summary or {},
        "selectionReasonsByRecipeId": selection_reasons_by_recipe,
        "selectionReasonCounts": selection_reason_counts,
        "fiberMinTarget": fiber_min_target,
        "sugarMaxTarget": sugar_max_target,
        "goalStrategy": [
            note
            for note in [
                "Weight Loss lowers calorie target." if goal_has(profile.goal, GOAL_WEIGHT_LOSS) else None,
                "Symptom Management tightens fiber, sugar, and steadier-carb preferences."
                if goal_has(profile.goal, GOAL_SYMPTOM_MANAGEMENT) else None,
                "General Health keeps balanced default targets."
                if goal_has(profile.goal, GOAL_GENERAL_HEALTH) else None,
            ]
            if note is not None
        ],
    }


def _greedy_fallback_plan(
    pool: List[Dict[str, Any]],
    meal_to_allowed: Dict[str, set],
    slot_labels: List[str],
    num_days: int,
    base_scores: List[float],
    max_per_week: int,
) -> tuple[List[DayPlan], List[Dict[str, Any]]]:
    # Deprecated non-authoritative helper retained only for benchmark baselines.
    # Production planning must never return this path as authoritative output.
    selected: List[Dict[str, Any]] = []
    res_plan: List[DayPlan] = []
    usage = [0] * len(pool)
    prev_idx = None
    day_names = build_plan_day_labels(num_days)
    meals_per_day = max(1, len(slot_labels))
    for d in range(num_days):
        meals = []
        total = 0
        for m in range(meals_per_day):
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
        res_plan.append(DayPlan(dayLabel=day_names[d], meals=meals, totalCalories=total))
    return res_plan, selected


class _PlannerBudgetExceeded(RuntimeError):
    def __init__(self, stage: str) -> None:
        super().__init__(stage)
        self.stage = str(stage or "unknown")


def _record_phase_timing(phase_timings_ms: Dict[str, int], phase: str, started_at: float) -> None:
    phase_timings_ms[str(phase)] = max(0, int((time.time() - started_at) * 1000))


def _check_planner_budget(
    deadline_at: float,
    stage: str,
    *,
    telemetry_out: Optional[Dict[str, Any]] = None,
) -> None:
    if time.time() < deadline_at:
        return
    if telemetry_out is not None:
        telemetry_out["budget_exceeded_stage"] = str(stage or "unknown")
    raise _PlannerBudgetExceeded(stage)


def _budget_aware_pool_limit(
    *,
    max_pool_size: int,
    slot_count: int,
    total_time_limit: float,
    minimum_candidates_required: int,
) -> int:
    normalized_max_pool = max(1, int(max_pool_size or 1))
    normalized_slots = max(1, int(slot_count or 1))
    minimum_candidates = max(1, int(minimum_candidates_required or 1))
    minimum_assignments = normalized_slots * minimum_candidates
    # Tight hosted-worker budgets cannot afford unbounded slot x recipe assignment
    # growth. For a 21-slot, 14-second production solve, 60 candidates still
    # left CP-SAT spending the whole deadline in UNKNOWN on Render starter.
    # About 33 candidates was the fastest reliable point in the 20-profile
    # benchmark while preserving hard-rule validation.
    assignment_budget = max(minimum_assignments, int(max(1.0, float(total_time_limit or 0.0)) * 50.0))
    budget_limited_pool = max(minimum_candidates, assignment_budget // normalized_slots)
    return min(normalized_max_pool, budget_limited_pool)


def solve_meal_plan(
    request: GeneratePlanRequest,
    recipes: List[Dict],
    weight_set: Optional[Dict[str, int]] = None,
    policy: Optional[Dict[str, Any]] = None,
    telemetry_out: Optional[Dict[str, Any]] = None,
    ml_feature_context: Optional[Dict[str, Any]] = None,
):
    profile = request.profile
    if _stage1_ml_scoring_enabled(policy):
        # First-use artifact loading is initialization work, not solver search.
        # Warm it before the planner deadline starts so a cold model load does
        # not consume the narrow solve budget on tiny requests.
        try:
            get_stage1_ranker().state()
        except Exception:
            pass
    planner_started_at = time.time()
    cold_start_defaults = _policy_get(
        policy,
        "stage1.cold_start_defaults",
        {
            "activityLevel": "Lightly Active",
            "goal": "General Health",
        },
    )
    activity_level = (profile.activityLevel or cold_start_defaults.get("activityLevel") or "Lightly Active")
    goal_value = (profile.goal or cold_start_defaults.get("goal") or "General Health")
    symptom_state = symptom_adjustments(profile, goal_value)
    debug_solver = _env_bool("PCOSINA_DEBUG_SOLVER", False)
    debug_summary = {
        "pool": 0,
        "allowed_sizes": {},
        "attempts": [],
    }
    conflict = validate_profile(profile)
    if conflict:
        return None, conflict, None
    configured_meals_per_day = int(
        _policy_get_legacy_aware(policy, ["planning.meals_per_day", "meals_per_day"], int(request.mealsPerDay or 3))
    )
    if int(request.mealsPerDay or configured_meals_per_day) != configured_meals_per_day:
        return None, f"Only mealsPerDay={configured_meals_per_day} is supported by active policy.", None
    configured_horizon = int(
        _policy_get_legacy_aware(policy, ["planning.planning_horizon_days", "planning_horizon_days"], int(request.days or 7))
    )
    snack_rules = _policy_get(policy, "planning.snack_rules", {})
    snack_enabled = bool((snack_rules or {}).get("enabled", False))
    num_days = max(1, int(request.days or configured_horizon))
    if num_days != configured_horizon:
        return None, f"Only planning_horizon_days={configured_horizon} is supported by active policy.", None
    base_meal_labels = ALL_SLOT_LABELS
    slot_labels = base_meal_labels[:configured_meals_per_day]
    if configured_meals_per_day > len(MEAL_LABELS) and not snack_enabled:
        return None, "Snacks are disabled by active policy.", None
    if len(slot_labels) != configured_meals_per_day:
        return None, "Configured meals_per_day exceeds supported label mapping.", None
    slot_count = num_days * len(slot_labels)
    total_time_limit = float(
        _policy_get_legacy_aware(
            policy,
            ["solver.total_solver_seconds", "total_solver_seconds"],
            _env_float_min("PCOSINA_TOTAL_SOLVER_SECONDS", 25.0),
        )
    )
    timeout_ms = int(_policy_get(policy, "solver.timeout_ms", int(total_time_limit * 1000)))
    total_time_limit = min(total_time_limit, max(0.5, timeout_ms / 1000.0))
    deadline_at = planner_started_at + total_time_limit
    retry_attempts = int(_policy_get(policy, "solver.retry_attempts", 2))
    retry_attempts = max(0, min(retry_attempts, 20))
    phase_timings_ms: Dict[str, int] = {}
    solve_pair_diagnostics: List[Dict[str, Any]] = []
    solver_budget = {
        "totalTimeLimitSeconds": round(float(total_time_limit), 3),
        "timeoutMs": int(timeout_ms),
        "retryAttempts": int(retry_attempts),
        "slotCount": int(slot_count),
    }
    if telemetry_out is not None:
        telemetry_out.clear()
        telemetry_out["solver_budget"] = dict(solver_budget)

    w, h, a = (
        profile.weightKg if profile.weightKg > 0 else 65,
        profile.heightCm if profile.heightCm > 0 else 160,
        profile.age if profile.age > 0 else 25
    )
    bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
    target = int(bmr * activity_multiplier(activity_level))
    if goal_has(goal_value, GOAL_WEIGHT_LOSS):
        target -= 500
    target += int(symptom_state.get("calorieTargetDelta") or 0)
    calorie_min = int(_policy_get_legacy_aware(policy, ["nutrition.calorie_min", "calorie_min"], 1200))
    calorie_max = int(_policy_get_legacy_aware(policy, ["nutrition.calorie_max", "calorie_max"], 3200))
    target = max(calorie_min, min(target, calorie_max))
    seed_salt = os.getenv("PCOSINA_SEED_SALT", "").strip()
    seed_key = f"{seed_salt}|{profile.displayName}_{profile.age}_{profile.heightCm}_{profile.weightKg}_{profile.activityLevel}_{profile.goal}_{profile.dietaryRestrictions}_{num_days}"
    rng = random.Random(seed_key)
    daily_targets = [target + rng.randint(-50, 50) for _ in range(num_days)]
    daily_targets = [max(calorie_min, min(t, calorie_max)) for t in daily_targets]
    # Macro targets use the final PCOS wellness policy.
    protein_ratio, carb_ratio, fat_ratio = macro_ratios()
    target_protein = int((target * protein_ratio) / 4)
    target_carbs = int((target * carb_ratio) / 4)
    target_fats = int((target * fat_ratio) / 9)
    protein_min = int(_policy_get_legacy_aware(policy, ["nutrition.protein_min", "protein_min"], 45))
    protein_max = int(_policy_get_legacy_aware(policy, ["nutrition.protein_max", "protein_max"], 220))
    carb_min = int(_policy_get_legacy_aware(policy, ["nutrition.carb_min", "carb_min"], 120))
    carb_max = int(_policy_get_legacy_aware(policy, ["nutrition.carb_max", "carb_max"], 420))
    fat_min = int(_policy_get_legacy_aware(policy, ["nutrition.fat_min", "fat_min"], 35))
    fat_max = int(_policy_get_legacy_aware(policy, ["nutrition.fat_max", "fat_max"], 140))
    target_protein += int(symptom_state.get("proteinTargetBonus") or 0)
    target_carbs += int(symptom_state.get("carbTargetDelta") or 0)
    target_protein = max(protein_min, min(target_protein, protein_max))
    target_carbs = max(carb_min, min(target_carbs, carb_max))
    target_fats = max(fat_min, min(target_fats, fat_max))

    daily_tolerance = float(
        _policy_get_legacy_aware(
            policy,
            ["nutrition.daily_tolerance_percent", "daily_tolerance_percent", "tolerance_levels.0"],
            0.2,
        )
    )
    weekly_tolerance = float(_policy_get(policy, "nutrition.weekly_tolerance_percent", 0.10))
    tolerance_levels = [
        daily_tolerance,
        min(0.8, daily_tolerance + max(0.05, weekly_tolerance)),
        min(0.8, daily_tolerance + max(0.10, weekly_tolerance * 2.0)),
        min(0.8, daily_tolerance + max(0.20, weekly_tolerance * 4.0)),
        min(0.8, daily_tolerance + max(0.40, weekly_tolerance * 6.0)),
    ]
    tolerance_levels = list(dict.fromkeys(float(level) for level in tolerance_levels))
    # Stage 1 pruning + shortlist
    shortlist_started_at = time.time()
    stage1_diag: Dict[str, Any] = {}
    pricing_context = create_pricing_context()
    caller_ml_feature_context = ml_feature_context if isinstance(ml_feature_context, dict) else {}
    stage1_ml_feature_context = {
        "target_calories": float(target),
        "target_protein": float(target_protein),
        "target_carbs": float(target_carbs),
        "target_fats": float(target_fats),
        "budget_weekly": float(resolve_budget_weekly(profile) or 0.0),
        "meals_per_day": float(configured_meals_per_day),
    }
    if isinstance(caller_ml_feature_context.get("reason_feedback_features"), dict):
        stage1_ml_feature_context["reason_feedback_features"] = caller_ml_feature_context["reason_feedback_features"]
    minimum_candidates_required = int(
        _policy_get(policy, "stage1.minimum_candidates_required", 10)
    )
    configured_stage1_max = int(
        _policy_get_legacy_aware(
            policy,
            ["stage1.max_candidates_per_slot", "max_pool_size", "shortlist_limit_restricted"],
            300,
        )
    )
    budget_pool_limit = _budget_aware_pool_limit(
        max_pool_size=configured_stage1_max * max(1, configured_meals_per_day),
        slot_count=slot_count,
        total_time_limit=total_time_limit,
        minimum_candidates_required=minimum_candidates_required,
    )
    budget_stage1_max = max(
        1,
        (int(budget_pool_limit) + max(1, configured_meals_per_day) - 1) // max(1, configured_meals_per_day),
    )
    stage1_policy = policy
    if budget_stage1_max < configured_stage1_max:
        stage1_policy = dict(policy or {})
        stage1_settings = dict(stage1_policy.get("stage1") or {})
        stage1_settings["max_candidates_per_slot"] = int(budget_stage1_max)
        stage1_policy["stage1"] = stage1_settings
    try:
        buckets = shortlist_candidates(
            profile,
            recipes,
            policy=stage1_policy,
            stage1_diag=stage1_diag,
            pricing_context=pricing_context,
            deadline_at=deadline_at,
            ml_feature_context=stage1_ml_feature_context,
        )
    except _PlannerBudgetExceeded as exc:
        _record_phase_timing(phase_timings_ms, "stage1_shortlist", shortlist_started_at)
        phase_timings_ms["stage1_preprocess"] = int(stage1_diag.get("preprocess_ms") or 0)
        phase_timings_ms["stage1_price_estimation"] = int(stage1_diag.get("cost_estimation_ms") or 0)
        phase_timings_ms["price_cost_estimation"] = int(stage1_diag.get("cost_estimation_ms") or 0)
        _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
        pricing_diagnostics = dict(stage1_diag.get("pricing_diagnostics") or pricing_context.snapshot())
        if telemetry_out is not None:
            telemetry_out["budget_exceeded_stage"] = exc.stage
            telemetry_out["selected_recipe_ids"] = []
            telemetry_out["status"] = "no-safe-plan"
            telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
            telemetry_out["stage1_diag"] = dict(stage1_diag)
            telemetry_out["pricing_diagnostics"] = pricing_diagnostics
            telemetry_out["solve_pair_diagnostics"] = []
        return None, "Planner timed out while pricing, filtering, or optimizing recipes.", None
    _record_phase_timing(phase_timings_ms, "stage1_shortlist", shortlist_started_at)
    if stage1_diag:
        phase_timings_ms["stage1_preprocess"] = int(stage1_diag.get("preprocess_ms") or 0)
        phase_timings_ms["stage1_price_estimation"] = int(stage1_diag.get("cost_estimation_ms") or 0)
        phase_timings_ms["price_cost_estimation"] = int(stage1_diag.get("cost_estimation_ms") or 0)
        phase_timings_ms["stage1_ml_score"] = int(stage1_diag.get("ml_score_ms") or 0)
        phase_timings_ms["stage1_bucket_finalize"] = int(stage1_diag.get("bucket_finalize_ms") or 0)
    pricing_diagnostics = dict(stage1_diag.get("pricing_diagnostics") or pricing_context.snapshot())
    if telemetry_out is not None:
        telemetry_out["stage1_diag"] = dict(stage1_diag)
        telemetry_out["pricing_diagnostics"] = pricing_diagnostics
    candidates = list({r["id"]: r for r in (buckets["Breakfast"] + buckets["Lunch"] + buckets["Dinner"] + buckets["Universal"])}.values())
    if telemetry_out is not None:
        telemetry_out["candidate_count_pre"] = len(candidates)
    if time.time() >= deadline_at:
        if telemetry_out is not None:
            telemetry_out["budget_exceeded_stage"] = "stage1_shortlist"
            telemetry_out["candidate_count_post"] = None
            telemetry_out["selected_recipe_ids"] = []
            telemetry_out["status"] = "no-safe-plan"
            _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
            telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
            telemetry_out["stage1_diag"] = dict(stage1_diag)
            telemetry_out["solve_pair_diagnostics"] = []
        return None, "Planner timed out while pricing, filtering, or optimizing recipes.", None
    if len(candidates) < max(1, minimum_candidates_required):
        return None, "No safe recipes found.", None

    pool = candidates
    restricted_solver_catalog = int(len(profile.dietaryRestrictions or []) + len(profile.allergies or [])) >= 6 or (
        int(stage1_diag.get("safe_recipe_count_pre_pricing") or 0) <= 96
    )
    if restricted_solver_catalog:
        anchor_core_pool = _restricted_solver_anchor_core(pool)
        if len(anchor_core_pool) < len(pool):
            pool = anchor_core_pool
            stage1_diag["restricted_solver_anchor_core"] = True
            stage1_diag["restricted_solver_anchor_core_count"] = len(pool)
            stage1_diag["restricted_solver_anchor_core_ids"] = [str(recipe.get("id") or "") for recipe in pool]
    if telemetry_out is not None:
        telemetry_out["candidate_count_pre"] = len(candidates)
        telemetry_out["ranking_strategy"] = "stage1_heuristic_with_ml_shadow"
        telemetry_out["ml_score_enabled"] = _stage1_ml_scoring_enabled(policy)
        telemetry_out["ml_model_version"] = "shadow_v0"
        telemetry_out["stage1_diag"] = dict(stage1_diag)
        telemetry_out["profile_rule_effects"] = profile_rule_summary(profile, resolve_budget_weekly(profile))
    max_pool_size = int(
        _policy_get_legacy_aware(
            policy,
            ["stage1.max_candidates_per_slot", "max_pool_size", "shortlist_limit_restricted"],
            300,
        )
    ) * max(1, configured_meals_per_day)
    cap_top_share = float(_policy_get(policy, "stage1.pool_cap_top_share", 0.6))
    max_pool_size = _budget_aware_pool_limit(
        max_pool_size=max_pool_size,
        slot_count=slot_count,
        total_time_limit=total_time_limit,
        minimum_candidates_required=minimum_candidates_required,
    )
    solver_budget["effectivePoolCap"] = int(max_pool_size)
    if len(pool) > max_pool_size:
        if debug_solver:
            debug_summary["pool_pre_cap"] = len(pool)
        pool = _cap_pool(pool, max_pool_size, top_share=cap_top_share)
    if telemetry_out is not None:
        telemetry_out["candidate_count_post"] = len(pool)
        if pool:
            telemetry_out["ml_model_version"] = str(pool[0].get("_ml_model_version") or "shadow_v0")
            applied = bool(pool[0].get("_ml_applied_to_ranking", False))
            telemetry_out["ranking_strategy"] = (
                "stage1_ml_canary_plus_heuristic" if applied else "stage1_heuristic_shadow_only"
            )
    if debug_solver:
        debug_summary["pool"] = len(pool)
    # Enforce mealType where possible; Universal recipes are allowed everywhere.
    pool_prepare_started_at = time.time()
    meal_to_allowed = {}
    for label in slot_labels:
        allowed = set(
            i for i, r in enumerate(pool)
            if label in (r.get("_allowed_meals") or MEAL_LABELS)
        )
        meal_to_allowed[label] = allowed
    if debug_solver:
        debug_summary["allowed_sizes"] = {k: len(v) for k, v in meal_to_allowed.items()}
    base_scores = []
    for r in pool:
        base_scores.append(_base_score(r))
    _record_phase_timing(phase_timings_ms, "stage1_pool_prepare", pool_prepare_started_at)
    if time.time() >= deadline_at:
        if telemetry_out is not None:
            telemetry_out["budget_exceeded_stage"] = "stage1_pool_prepare"
            telemetry_out["selected_recipe_ids"] = []
            telemetry_out["status"] = "no-safe-plan"
            _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
            telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
            telemetry_out["stage1_diag"] = dict(stage1_diag)
            telemetry_out["solve_pair_diagnostics"] = []
        return None, "Planner timed out while pricing, filtering, or optimizing recipes.", None
    fiber_min_target = int(_policy_get_legacy_aware(policy, ["nutrition.fiber_min", "fiber_min"], 20))
    sodium_max_target = int(_policy_get_legacy_aware(policy, ["nutrition.sodium_max", "sodium_max"], 2300))
    sugar_max_target = int(_policy_get_legacy_aware(policy, ["nutrition.sugar_max", "sugar_max"], 50))
    fiber_min_target = max(0, fiber_min_target + int(symptom_state.get("fiberMinBonus") or 0))
    sugar_max_target = max(5, sugar_max_target + int(symptom_state.get("sugarMaxDelta") or 0))
    nutrition_feasibility = _nutrition_coverage_gap(
        pool,
        meal_to_allowed,
        slot_labels,
        calorie_min=calorie_min,
        protein_min=protein_min,
        carb_min=carb_min,
        fat_min=fat_min,
        fiber_min=fiber_min_target,
        sodium_max=sodium_max_target,
        sugar_max=sugar_max_target,
    )
    stage1_diag["nutrition_feasibility"] = nutrition_feasibility
    if not nutrition_feasibility.get("ok"):
        if telemetry_out is not None:
            telemetry_out["selected_recipe_ids"] = []
            telemetry_out["status"] = "no-safe-plan"
            _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
            telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
            telemetry_out["solver_budget"] = dict(solver_budget)
            telemetry_out["stage1_diag"] = dict(stage1_diag)
            telemetry_out["solve_pair_diagnostics"] = []
        return None, "Catalog nutrition coverage is insufficient for this profile.", None
    budget_weekly = resolve_budget_weekly(profile)
    rough_budget_cap = _rough_budget_cap(budget_weekly, policy)
    if budget_weekly:
        solver_budget["budgetWeeklyPhp"] = int(round(float(budget_weekly)))
        solver_budget["roughMealBudgetCapPhp"] = int(rough_budget_cap or budget_weekly)
        solver_budget["finalBudgetAuthority"] = "backend_aggregated_grocery"
        solver_budget["budgetAuthority"] = "backend_aggregated_grocery"
        solver_budget["roughBudgetHardCapApplied"] = bool(
            _policy_get(policy, "planning.enforce_rough_budget_cap", False)
        )
    rule_effects = profile_rule_summary(profile, budget_weekly)
    max_per_week_list = repeat_sequence_for_profile(
        [
            int(v)
            for v in _policy_get_legacy_aware(
                policy,
                ["planning.recipe_repeat_limits", "max_per_week"],
                _env_int_list("PCOSINA_MAX_PER_WEEK", [2, 3, 4, 10]),
            )
        ],
        profile.varietyPreference,
        profile.planningPriority,
        hard_filter_count=len(profile.dietaryRestrictions or []) + len(profile.allergies or []),
        safe_candidate_count=int(stage1_diag.get("safe_recipe_count_pre_pricing") or 0),
    )
    stage1_diag["repeat_sequence"] = list(max_per_week_list)
    relaxation_order = _policy_get(
        policy,
        "planning.infeasibility_relaxation_order",
        ["daily_tolerance_percent", "recipe_repeat_limits"],
    )
    if not isinstance(relaxation_order, list):
        relaxation_order = ["daily_tolerance_percent", "recipe_repeat_limits"]
    solve_pair_preferences = _solve_pair_preferences_for_profile(
        profile,
        restricted_catalog=bool(restricted_solver_catalog),
    )
    solve_pairs = solve_pair_sequence_for_profile(
        tolerance_levels,
        max_per_week_list,
        relaxation_order,
        restricted_catalog=bool(restricted_solver_catalog),
        preferred_tolerances=solve_pair_preferences.get("preferredTolerances"),
        preferred_repeats=solve_pair_preferences.get("preferredRepeats"),
    )
    stage1_diag["solve_pair_strategy"] = str(solve_pair_preferences.get("strategy") or "default")
    if restricted_solver_catalog:
        stage1_diag["restricted_solver_pair_priority"] = True

    feature_rows_started_at = time.time()
    if telemetry_out is not None:
        telemetry_out["stage1_candidates"] = _build_stage1_feature_rows(
            pool,
            profile,
            target_calories=target,
            target_protein=target_protein,
            target_carbs=target_carbs,
            target_fats=target_fats,
            budget_weekly=budget_weekly,
            meals_per_day=configured_meals_per_day,
        )
    _record_phase_timing(phase_timings_ms, "stage1_feature_rows", feature_rows_started_at)
    if time.time() >= deadline_at:
        if telemetry_out is not None:
            telemetry_out["budget_exceeded_stage"] = "stage1_feature_rows"
            telemetry_out["selected_recipe_ids"] = []
            telemetry_out["status"] = "no-safe-plan"
            _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
            telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
            telemetry_out["stage1_diag"] = dict(stage1_diag)
            telemetry_out["solve_pair_diagnostics"] = []
        return None, "Planner timed out while pricing, filtering, or optimizing recipes.", None

    solver_started_at = time.time()
    budget_exceeded_stage: Optional[str] = None
    final_grocery_budget_rejected = False
    for pair_index, (tol, max_per_week) in enumerate(solve_pairs):
        pair_diag: Dict[str, Any] = {
            "tol": round(float(tol), 4),
            "maxPerWeek": int(max_per_week),
        }
        protein_bounds = (
            max(protein_min, int(target_protein * (1 - tol))),
            min(protein_max, int(target_protein * (1 + tol))),
        )
        carbs_bounds = (
            max(carb_min, int(target_carbs * (1 - tol))),
            min(carb_max, int(target_carbs * (1 + tol))),
        )
        fats_bounds = (
            max(fat_min, int(target_fats * (1 - tol))),
            min(fat_max, int(target_fats * (1 + tol))),
        )
        if time.time() >= deadline_at:
            break
        model_build_started_at = time.time()
        try:
            model = cp_model.CpModel()
            x = {}
            for s in range(slot_count):
                _check_planner_budget(deadline_at, "solver_model_x_vars", telemetry_out=telemetry_out)
                for i in range(len(pool)):
                    x[s, i] = model.NewBoolVar(f"x_{s}_{i}")
                    if (i & 31) == 0:
                        _check_planner_budget(deadline_at, "solver_model_x_vars", telemetry_out=telemetry_out)
            for s in range(slot_count):
                _check_planner_budget(deadline_at, "solver_model_allowed", telemetry_out=telemetry_out)
                meal_label = slot_labels[s % configured_meals_per_day]
                allowed = meal_to_allowed.get(meal_label, set(range(len(pool))))
                model.Add(sum(x[s, i] for i in allowed) == 1)
                # Force non-allowed meal-type assignments to zero. Without this,
                # disallowed binaries remain free and bloat CP-SAT search.
                for i in range(len(pool)):
                    if i not in allowed:
                        model.Add(x[s, i] == 0)
                        if (i & 31) == 0:
                            _check_planner_budget(deadline_at, "solver_model_allowed", telemetry_out=telemetry_out)
            # Greedy warm-start (hint)
            prev_idx = None
            for s in range(slot_count):
                _check_planner_budget(deadline_at, "solver_model_hints", telemetry_out=telemetry_out)
                meal_label = slot_labels[s % configured_meals_per_day]
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
                _check_planner_budget(deadline_at, "solver_model_adjacent", telemetry_out=telemetry_out)
                for i in range(len(pool)):
                    model.Add(x[s, i] + x[s + 1, i] <= 1)
                    if (i & 31) == 0:
                        _check_planner_budget(deadline_at, "solver_model_adjacent", telemetry_out=telemetry_out)
            for i in range(len(pool)):
                if (i & 15) == 0:
                    _check_planner_budget(deadline_at, "solver_model_repeat", telemetry_out=telemetry_out)
                model.Add(sum(x[s, i] for s in range(slot_count)) <= max_per_week)
        except _PlannerBudgetExceeded as exc:
            budget_exceeded_stage = exc.stage
            pair_diag["buildMs"] = max(0, int((time.time() - model_build_started_at) * 1000))
            pair_diag["status"] = "budget_exceeded"
            pair_diag["budgetExceededStage"] = exc.stage
            solve_pair_diagnostics.append(pair_diag)
            break
        pair_diag["buildMs"] = max(0, int((time.time() - model_build_started_at) * 1000))

        repeat_over_vars = []
        for i in range(len(pool)):
            used_count = sum(x[s, i] for s in range(slot_count))
            repeat_over = model.NewIntVar(0, slot_count, f"repeat_over_{i}")
            model.Add(used_count - 1 <= repeat_over)
            model.Add(repeat_over >= 0)
            repeat_over_vars.append(repeat_over)

        # Protein group diversity (soft)
        group_over_vars = []
        group_floor = int(_policy_get(policy, "planning.group_limit_floor", 2))
        group_limit = max(group_floor, num_days)
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
            related_idxs = [i for i, r in enumerate(pool) if t in r.get("_veg_tokens", [])]
            if related_idxs:
                model.AddMaxEquality(veg_cov[t], [x[s, i] for s in range(slot_count) for i in related_idxs])
        diversity_min_target = int(_policy_get(policy, "planning.diversity_min_token_target", 5))
        min_diversity = min(diversity_min_target, len(veg_tokens)) if veg_tokens else 0
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

        total_cost = sum(x[s, i] * int(pool[i].get("_cost_est", 0)) for s in range(slot_count) for i in range(len(pool)))
        if budget_weekly and bool(_policy_get(policy, "planning.enforce_rough_budget_cap", False)):
            model.Add(total_cost <= int(rough_budget_cap or budget_weekly))

        err_vars = []
        sodium_over_vars = []
        sugar_over_vars = []
        meal_err_vars = []
        meal_distribution = _policy_get(policy, "nutrition.meal_distribution_targets", None)
        if not isinstance(meal_distribution, list) or len(meal_distribution) < configured_meals_per_day:
            meal_distribution = [1.0 / configured_meals_per_day for _ in range(configured_meals_per_day)]
        meal_min_target = int(_policy_get(policy, "planning.meal_min_calorie_target", 180))

        for d in range(num_days):
            day_slots = range(d * configured_meals_per_day, d * configured_meals_per_day + configured_meals_per_day)
            day_cals = sum(x[s, i] * int(pool[i].get("calories", 0)) for s in day_slots for i in range(len(pool)))
            err = model.NewIntVar(0, 1500, f"err_{d}")
            model.Add(day_cals >= calorie_min)
            model.Add(day_cals <= calorie_max)
            model.Add(err >= day_cals - daily_targets[d])
            model.Add(err >= daily_targets[d] - day_cals)
            err_vars.append(err)
            day_pro = sum(x[s, i] * int(pool[i].get("proteinGrams", 0)) for s in day_slots for i in range(len(pool)))
            day_carb = sum(x[s, i] * int(pool[i].get("carbsGrams", 0)) for s in day_slots for i in range(len(pool)))
            day_fat = sum(x[s, i] * int(pool[i].get("fatsGrams", 0)) for s in day_slots for i in range(len(pool)))
            day_fiber = sum(x[s, i] * int(pool[i].get("fiberGrams", 0)) for s in day_slots for i in range(len(pool)))
            day_sodium = sum(x[s, i] * int(pool[i].get("sodiumMg", 0) or 0) for s in day_slots for i in range(len(pool)))
            day_sugar = sum(x[s, i] * int(pool[i].get("sugarGrams", 0) or 0) for s in day_slots for i in range(len(pool)))
            sodium_over = model.NewIntVar(0, 200000, f"sodium_over_{d}")
            sugar_over = model.NewIntVar(0, 10000, f"sugar_over_{d}")
            sodium_over_vars.append(sodium_over)
            sugar_over_vars.append(sugar_over)
            model.Add(day_pro >= protein_bounds[0])
            model.Add(day_pro <= protein_bounds[1])
            model.Add(day_carb >= carbs_bounds[0])
            model.Add(day_carb <= carbs_bounds[1])
            model.Add(day_fat >= fats_bounds[0])
            model.Add(day_fat <= fats_bounds[1])
            model.Add(day_fiber >= fiber_min_target)
            model.Add(day_sodium - sodium_max_target <= sodium_over)
            model.Add(day_sugar - sugar_max_target <= sugar_over)

            for m in range(configured_meals_per_day):
                slot = d * configured_meals_per_day + m
                target_meal = max(meal_min_target, int(daily_targets[d] * float(meal_distribution[m])))
                meal_cals = sum(x[slot, i] * int(pool[i].get("calories", 0)) for i in range(len(pool)))
                meal_err = model.NewIntVar(0, 1200, f"meal_err_{slot}")
                model.Add(meal_err >= meal_cals - target_meal)
                model.Add(meal_err >= target_meal - meal_cals)
                meal_err_vars.append(meal_err)

        total_err = sum(err_vars)
        total_sodium_over = sum(sodium_over_vars) if sodium_over_vars else 0
        total_sugar_over = sum(sugar_over_vars) if sugar_over_vars else 0
        total_meal_err = sum(meal_err_vars) if meal_err_vars else 0
        total_repeat_over = sum(repeat_over_vars)
        total_group_over = sum(group_over_vars) if group_over_vars else 0
        budget_penalty = 0
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
        policy_weights = _policy_get(policy, "milp_weights", None)
        if isinstance(policy_weights, dict):
            weights.update(policy_weights)
        weights.update(_default_weight_set() or {})
        if weight_set:
            weights.update(weight_set)
        if "repeat_weight" in priority:
            weights["repeat_weight"] = priority["repeat_weight"]
        if "group_weight" in priority:
            weights["group_weight"] = priority["group_weight"]
        if "diversity_weight" in priority:
            weights["diversity_weight"] = priority["diversity_weight"]
        repeat_w = int(_policy_get(policy, "planning.substitution_penalty", weights.get("repeat_weight", 5))) * int(priority.get("variety_mult", 1))
        group_w = int(_policy_get(policy, "planning.cuisine_diversity_weight", weights.get("group_weight", 2))) * int(priority.get("variety_mult", 1))
        diversity_w = int(_policy_get(policy, "planning.cuisine_diversity_weight", weights.get("diversity_weight", 1))) * int(priority.get("variety_mult", 1))
        pantry_w = int(_policy_get(policy, "planning.pantry_utilization_weight", weights.get("pantry_weight", 1)))
        prep_time_w = int(priority.get("prep_time_mult", 1)) * int(_policy_get(policy, "planning.prep_time_weight", 1))
        cost_w = int(_policy_get(policy, "planning.grocery_cost_weight", 1))
        acceptance_w = int(_policy_get(policy, "planning.acceptance_score_weight", 1))
        macro_mult = int(priority.get("macro_mult", 1))
        budget_mult = int(priority.get("budget_mult", 1))
        cost_objective = total_cost if (budget_weekly or _should_optimize_cost(profile)) else 0
        prep_time_penalty = sum(x[s, i] * int(pool[i].get("minutes", 0)) for s in range(slot_count) for i in range(len(pool)))
        model.Minimize(
            (macro_mult * total_err) + (macro_mult * total_meal_err) +
            (macro_mult * total_sodium_over) + (macro_mult * total_sugar_over) +
            (budget_mult * cost_w * cost_objective) + (budget_mult * cost_w * budget_penalty) + (prep_time_w * prep_time_penalty) +
            (repeat_w * total_repeat_over) + (group_w * total_group_over) + (acceptance_w * total_meal_err) +
            diversity_penalty - (pantry_w * pantry_reward) - (diversity_w * diversity_reward)
            + pantry_min_penalty
        )
        pair_diag["buildMs"] = max(0, int((time.time() - model_build_started_at) * 1000))
        if time.time() >= deadline_at:
            budget_exceeded_stage = "solver_model_finalize"
            if telemetry_out is not None:
                telemetry_out["budget_exceeded_stage"] = budget_exceeded_stage
            pair_diag["status"] = "budget_exceeded"
            pair_diag["budgetExceededStage"] = budget_exceeded_stage
            solve_pair_diagnostics.append(pair_diag)
            break

        solver = cp_model.CpSolver()
        base_time = float(
            _policy_get_legacy_aware(
                policy,
                ["solver.solver_time_limit_seconds", "solver_time_seconds"],
                _env_float_min("PCOSINA_SOLVER_TIME_SECONDS", 6.0),
            )
        )
        max_time = float(
            _policy_get_legacy_aware(
                policy,
                ["solver.solver_max_seconds", "solver_max_seconds"],
                _env_float_min("PCOSINA_SOLVER_MAX_SECONDS", 12.0),
            )
        )
        size_factor = max(0.0, (len(pool) - 60) / 40.0)
        restriction_factor = min(4.0, len(profile.dietaryRestrictions or []) / 2.0)
        budget_factor = 2.0 if budget_weekly and _should_optimize_cost(profile) else 0.0
        adaptive_time = min(max_time, base_time + size_factor + restriction_factor + budget_factor)
        solver.parameters.relative_gap_limit = float(
            _policy_get_legacy_aware(policy, ["solver.optimality_gap_target", "optimality_gap_target"], 0.05)
        )
        cpu_count = os.cpu_count() or 1
        workers_default = _env_int("PCOSINA_SOLVER_WORKERS", min(4, cpu_count))
        solver.parameters.num_search_workers = int(
            _policy_get_legacy_aware(policy, ["solver.solver_workers", "solver_workers"], workers_default)
        )
        memory_limit = int(_policy_get(policy, "solver.worker_memory_limit", 1024))
        if hasattr(solver.parameters, "max_memory_in_mb"):
            solver.parameters.max_memory_in_mb = memory_limit
        max_solution_count = int(_policy_get(policy, "solver.max_solution_count", 1))
        stop_after_first_solution = max_solution_count <= 1
        if hasattr(solver.parameters, "stop_after_first_solution"):
            solver.parameters.stop_after_first_solution = bool(stop_after_first_solution)

        status = cp_model.UNKNOWN
        status_name = "UNKNOWN"
        attempts_used = 0
        solve_started_at = time.time()
        pair_attempts: List[Dict[str, Any]] = []
        for retry_idx in range(retry_attempts + 1):
            attempts_used = retry_idx + 1
            elapsed = time.time() - planner_started_at
            remaining = total_time_limit - elapsed
            if remaining <= 0:
                break
            retry_scale = 1.0 + (0.15 * retry_idx)
            attempt_time = min(adaptive_time * retry_scale, max_time, remaining)
            solver.parameters.max_time_in_seconds = max(0.2, attempt_time)
            attempt_started_at = time.time()
            status = solver.Solve(model)
            solve_elapsed_ms = max(0, int((time.time() - attempt_started_at) * 1000))
            try:
                status_name = solver.StatusName(status)
            except Exception:
                status_name = str(status)
            pair_attempts.append(
                {
                    "retry": retry_idx,
                    "attemptTimeSeconds": round(float(attempt_time), 3),
                    "solveMs": solve_elapsed_ms,
                    "status": status_name,
                    "stopAfterFirstSolution": bool(stop_after_first_solution),
                }
            )

            if debug_solver:
                diag_depth = int(
                    _policy_get_legacy_aware(
                        policy,
                        ["solver.infeasibility_diagnostic_depth", "infeasibility_diagnostic_depth"],
                        30,
                    )
                )
                debug_summary["attempts"].append(
                    {
                        "tol": tol,
                        "max_per_week": max_per_week,
                        "retry": retry_idx,
                        "attempt_time": round(float(attempt_time), 3),
                        "status": status_name,
                    }
                )
                if len(debug_summary["attempts"]) > max(1, diag_depth):
                    debug_summary["attempts"] = debug_summary["attempts"][-diag_depth:]

            if status in (cp_model.OPTIMAL, cp_model.FEASIBLE):
                break
            if status == cp_model.INFEASIBLE:
                break
            if status == cp_model.UNKNOWN and pair_index < len(solve_pairs) - 1:
                break
        pair_diag["solveMs"] = max(0, int((time.time() - solve_started_at) * 1000))
        pair_diag["attemptsUsed"] = attempts_used
        pair_diag["status"] = status_name
        pair_diag["attempts"] = pair_attempts
        solve_pair_diagnostics.append(pair_diag)

        if status in [cp_model.OPTIMAL, cp_model.FEASIBLE]:
            final_attempt_limit = max(1, int(_policy_get(policy, "planning.final_grocery_validation_attempts", 6)))
            final_attempts: List[Dict[str, Any]] = []
            final_attempt_index = 0
            day_names = build_plan_day_labels(num_days, getattr(request, "startDate", None))
            while status in [cp_model.OPTIMAL, cp_model.FEASIBLE] and final_attempt_index < final_attempt_limit:
                res_plan = []
                selected = []
                selected_indices: List[Tuple[int, int]] = []
                for d in range(num_days):
                    meals = []
                    total = 0
                    for m in range(configured_meals_per_day):
                        idx = d * configured_meals_per_day + m
                        for i in range(len(pool)):
                            if solver.Value(x[idx, i]):
                                r = pool[i]
                                selected.append(r)
                                selected_indices.append((idx, i))
                                meals.append(PlannedMeal(mealLabel=slot_labels[m], recipeId=r["id"], title=r["title"]))
                                total += int(r.get("calories", 0))
                                break
                    res_plan.append(DayPlan(dayLabel=day_names[d], meals=meals, totalCalories=total))
                rough_est_cost = sum(int(r.get("_cost_est", 0)) for r in selected)
                grocery_output = _build_selected_grocery_output(selected, budget_weekly=budget_weekly)
                grocery_total = int(grocery_output.get("estimatedTotalPhp") or 0)
                budget_diag = _budget_authority_diagnostics(
                    budget_weekly=budget_weekly,
                    solver_budget_estimate_php=rough_est_cost,
                    final_grocery_estimate_php=grocery_total,
                    budget_gap_php=grocery_output.get("budgetDeltaPhp"),
                )
                grocery_output.update(budget_diag)
                grocery_output["plannerMealEstimatePhp"] = int(rough_est_cost)
                grocery_output["solverBudgetEstimatePhp"] = int(rough_est_cost)
                grocery_output["roughMealBudgetCapPhp"] = int(rough_budget_cap or budget_weekly or 0) or None
                final_attempt = {
                    "attempt": final_attempt_index + 1,
                    "estimatedTotalPhp": grocery_total,
                    "weeklyBudgetPhp": int(round(float(budget_weekly))) if budget_weekly else None,
                    "withinBudget": grocery_output.get("withinBudget"),
                    "plannerMealEstimatePhp": int(rough_est_cost),
                    "budgetGapPhp": budget_diag.get("budgetGapPhp"),
                }
                final_attempts.append(final_attempt)
                pair_diag["finalGroceryAttempts"] = final_attempts[-6:]
                pair_diag["finalGroceryBudget"] = final_attempt
                if budget_weekly and grocery_total > int(round(float(budget_weekly))):
                    final_grocery_budget_rejected = True
                    pair_diag["solverStatus"] = status_name
                    pair_diag["status"] = "GROCERY_BUDGET_EXCEEDED"
                    if telemetry_out is not None:
                        telemetry_out["grocery_output"] = grocery_output
                        telemetry_out["budget_diagnostics"] = budget_diag
                        telemetry_out["budget_exceeded_stage"] = "final_grocery_budget"
                    if final_attempt_index >= final_attempt_limit - 1 or time.time() >= deadline_at:
                        break
                    if len(selected_indices) != slot_count:
                        break
                    model.Add(sum(x[slot_idx, recipe_idx] for slot_idx, recipe_idx in selected_indices) <= slot_count - 1)
                    elapsed = time.time() - planner_started_at
                    remaining = total_time_limit - elapsed
                    if remaining <= 0:
                        break
                    final_retry_started_at = time.time()
                    solver.parameters.max_time_in_seconds = max(0.2, min(adaptive_time, max_time, remaining))
                    status = solver.Solve(model)
                    solve_elapsed_ms = max(0, int((time.time() - final_retry_started_at) * 1000))
                    try:
                        status_name = solver.StatusName(status)
                    except Exception:
                        status_name = str(status)
                    attempts_used += 1
                    pair_attempts.append(
                        {
                            "retry": attempts_used - 1,
                            "attemptTimeSeconds": round(float(solver.parameters.max_time_in_seconds), 3),
                            "solveMs": solve_elapsed_ms,
                            "status": status_name,
                            "stopAfterFirstSolution": bool(stop_after_first_solution),
                            "finalGroceryRetry": final_attempt_index + 1,
                        }
                    )
                    pair_diag["solveMs"] = max(0, int((time.time() - solve_started_at) * 1000))
                    pair_diag["attemptsUsed"] = attempts_used
                    pair_diag["attempts"] = pair_attempts
                    final_attempt_index += 1
                    continue
                explanation = _build_explanation(
                    selected,
                    num_days,
                    configured_meals_per_day,
                    daily_targets,
                    target,
                    target_protein,
                    target_carbs,
                    target_fats,
                    tol,
                    max_per_week,
                    profile,
                    budget_weekly,
                    candidate_pool_size=len(pool),
                    profile_rule_effects=rule_effects,
                    symptom_state=symptom_state,
                    candidate_exclusion_summary=dict(stage1_diag.get("exclusion_summary") or {}),
                    budget_hard_cap_applied=bool(budget_weekly),
                    fiber_min_target=fiber_min_target,
                    sugar_max_target=sugar_max_target,
                )
                explanation["roughMealEstimatedWeeklyCost"] = int(rough_est_cost)
                explanation["estimatedWeeklyCost"] = int(grocery_total)
                explanation["estimatedWeeklyCostSource"] = "backend_aggregated_grocery"
                explanation["roughMealBudgetCapPhp"] = int(rough_budget_cap or budget_weekly or 0) or None
                explanation["groceryBudgetAuthority"] = {
                    "authority": grocery_output.get("authority"),
                    "estimatedTotalPhp": int(grocery_total),
                    "weeklyBudgetPhp": grocery_output.get("weeklyBudgetPhp"),
                    "withinBudget": grocery_output.get("withinBudget"),
                    "budgetDeltaPhp": grocery_output.get("budgetDeltaPhp"),
                    "plannerMealEstimatePhp": int(rough_est_cost),
                    **budget_diag,
                }
                explanation.update(budget_diag)
                explanation["solverStatus"] = status_name
                explanation["retryAttemptsUsed"] = attempts_used
                _record_phase_timing(phase_timings_ms, "solver", solver_started_at)
                _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
                explanation["phaseTimingsMs"] = dict(phase_timings_ms)
                explanation["solverBudget"] = dict(solver_budget)
                explanation["solvePairDiagnostics"] = solve_pair_diagnostics[-6:]
                if telemetry_out is not None:
                    telemetry_out["selected_recipe_ids"] = [str(r.get("id") or "") for r in selected]
                    telemetry_out["status"] = "success"
                    telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
                    telemetry_out["solver_budget"] = dict(solver_budget)
                    telemetry_out["stage1_diag"] = dict(stage1_diag)
                    telemetry_out["solve_pair_diagnostics"] = solve_pair_diagnostics[-6:]
                    telemetry_out["grocery_output"] = grocery_output
                    telemetry_out["budget_diagnostics"] = budget_diag
                    telemetry_out.pop("budget_exceeded_stage", None)
                return res_plan, "Success", explanation
        if (time.time() - planner_started_at) >= total_time_limit:
            if status == cp_model.UNKNOWN:
                budget_exceeded_stage = "solver_search"
                if telemetry_out is not None:
                    telemetry_out["budget_exceeded_stage"] = budget_exceeded_stage
            break
    if debug_solver:
        print("MILP_DEBUG", json.dumps(debug_summary))
        msg = json.dumps(debug_summary)[:1500]
        if telemetry_out is not None:
            telemetry_out["selected_recipe_ids"] = []
            telemetry_out["status"] = "no-safe-plan"
            _record_phase_timing(phase_timings_ms, "solver", solver_started_at)
            _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
            telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
            telemetry_out["solver_budget"] = dict(solver_budget)
            telemetry_out["stage1_diag"] = dict(stage1_diag)
            telemetry_out["solve_pair_diagnostics"] = solve_pair_diagnostics[-6:]
        return None, f"Infeasible | debug={msg}", None
    _record_phase_timing(phase_timings_ms, "solver", solver_started_at)
    _record_phase_timing(phase_timings_ms, "planner_total", planner_started_at)
    if telemetry_out is not None:
        telemetry_out["selected_recipe_ids"] = []
        telemetry_out["status"] = "no-safe-plan"
        telemetry_out["phase_timings_ms"] = dict(phase_timings_ms)
        telemetry_out["solver_budget"] = dict(solver_budget)
        telemetry_out["stage1_diag"] = dict(stage1_diag)
        telemetry_out["solve_pair_diagnostics"] = solve_pair_diagnostics[-6:]
    if final_grocery_budget_rejected:
        return None, "Final grocery estimate exceeds weekly budget.", None
    if budget_exceeded_stage:
        return None, "Planner timed out while pricing, filtering, or optimizing recipes.", None
    return None, "Infeasible", None
