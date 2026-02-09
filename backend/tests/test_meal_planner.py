import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from domain.models import UserProfile
from services import meal_planner


def test_macro_ratios_moderate():
    assert meal_planner.macro_ratios("Moderate") == (0.28, 0.35, 0.37)


def test_macro_ratios_severe():
    assert meal_planner.macro_ratios("Severe") == (0.30, 0.30, 0.40)


def test_resolve_budget_weekly_prefers_weekly_php():
    profile = UserProfile(weeklyBudgetPhp=3000, budgetWeekly=2000, budgetMonthly=8000)
    assert meal_planner.resolve_budget_weekly(profile) == 3000.0


def test_allergy_filter_blocks_recipe():
    profile = UserProfile(allergies=["peanut"])
    recipes = [
        {
            "id": "r1",
            "title": "Peanut Dish",
            "mealType": "Lunch",
            "calories": 500,
            "proteinGrams": 20,
            "carbsGrams": 40,
            "fatsGrams": 15,
            "fiberGrams": 5,
            "ingredients": [{"name": "peanut oil", "quantity": "1 tbsp"}],
        }
    ]
    buckets = meal_planner.shortlist_candidates(profile, recipes)
    assert all(len(v) == 0 for v in buckets.values())


def test_infer_allowed_meals():
    assert meal_planner.infer_allowed_meals("Breakfast") == ["Breakfast"]
    assert set(meal_planner.infer_allowed_meals("Universal")) == set(meal_planner.MEAL_LABELS)
