import json
import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services.ml_ranker import Stage1MLRanker
from services.ml_features import (
    REASON_FEEDBACK_FEATURE_COLUMNS,
    STAGE1_LIGHTGBM_FEATURE_COLUMNS,
    reason_feedback_features_from_events,
)
from services.meal_planner import _stage1_ml_feature_vector
from domain.models import UserProfile


MODEL_ARTIFACT = ROOT.parent / "ml" / "offline_training" / "artifacts" / "model_v1" / "lightgbm_v1_model.txt"
METRICS_ARTIFACT = ROOT.parent / "ml" / "offline_training" / "artifacts" / "model_v1" / "training_metrics.json"


def _require_lightgbm() -> None:
    if importlib.util.find_spec("lightgbm") is None:
        pytest.skip("lightgbm not installed in this environment")


def test_ranker_gracefully_handles_missing_model_env(monkeypatch):
    monkeypatch.delenv("PCOSINA_ML_MODEL_PATH", raising=False)
    monkeypatch.delenv("PCOSINA_ML_METRICS_PATH", raising=False)

    ranker = Stage1MLRanker()
    state = ranker.state()
    assert state.ready is False
    assert "PCOSINA_ML_MODEL_PATH" in (state.error or "")
    score = ranker.score({"recipe_calories": 500.0})
    assert score is None


def test_ranker_loads_real_model_artifacts_and_scores(monkeypatch):
    _require_lightgbm()
    assert MODEL_ARTIFACT.exists()
    assert METRICS_ARTIFACT.exists()
    monkeypatch.setenv("PCOSINA_ML_MODEL_PATH", str(MODEL_ARTIFACT))
    monkeypatch.setenv("PCOSINA_ML_METRICS_PATH", str(METRICS_ARTIFACT))

    ranker = Stage1MLRanker()
    state = ranker.state()

    if not state.ready:
        assert "load" in (state.error or "").lower()
        assert ranker.score({"recipe_calories": 510.0}) is None
        return

    assert state.ready is True
    assert state.model_version == "lightgbm_stage1_ranker_v1"
    assert "recipe_calories" in state.feature_columns
    score = ranker.score(
        {
            "recipe_calories": 510.0,
            "recipe_protein": 29.0,
            "recipe_carbs": 42.0,
            "recipe_fats": 16.0,
            "recipe_fiber": 7.0,
            "recipe_minutes": 25.0,
            "recipe_cost_est": 155.0,
            "pantry_overlap_count": 2.0,
            "budget_weekly_norm": 0.18,
            "restriction_count": 1.0,
            "allergy_count": 0.0,
            "is_breakfast_candidate": 0.0,
            "is_lunch_candidate": 1.0,
            "is_dinner_candidate": 0.0,
        }
    )
    assert score is not None
    assert 0.0 <= score <= 1.0


def test_ranker_falls_back_to_booster_feature_names_when_metrics_path_missing(monkeypatch):
    _require_lightgbm()
    assert MODEL_ARTIFACT.exists()
    monkeypatch.setenv("PCOSINA_ML_MODEL_PATH", str(MODEL_ARTIFACT))
    monkeypatch.delenv("PCOSINA_ML_METRICS_PATH", raising=False)

    ranker = Stage1MLRanker()
    state = ranker.state()

    if not state.ready:
        assert "load" in (state.error or "").lower()
        return

    assert state.ready is True
    assert state.model_version == "lightgbm_v1_unknown"
    assert len(state.feature_columns) > 0


def test_live_stage1_feature_vector_matches_trained_lightgbm_columns():
    metrics = json.loads(METRICS_ARTIFACT.read_text(encoding="utf-8"))
    trained_columns = metrics["feature_columns"]
    recipe = {
        "id": "ph-test",
        "title": "Test Meal",
        "calories": 510,
        "proteinGrams": 29,
        "carbsGrams": 42,
        "fatsGrams": 16,
        "fiberGrams": 7,
        "minutes": 25,
        "_cost_est": 155,
        "_pantry_match": 2,
        "_allowed_meals": ["Lunch"],
    }
    profile = UserProfile(
        displayName="FeatureUser",
        weeklyBudgetPhp=1200,
        dietaryRestrictions=["No Pork"],
        allergies=[],
        goal="General Health",
        activityLevel="Lightly Active",
        maxCookingTimeMinutes=45,
    )
    features = _stage1_ml_feature_vector(
        recipe,
        profile,
        {
            "target_calories": 1500,
            "target_protein": 90,
            "target_carbs": 150,
            "target_fats": 50,
            "budget_weekly": 1200,
            "meals_per_day": 3,
        },
    )

    assert len(STAGE1_LIGHTGBM_FEATURE_COLUMNS) == 46
    assert sorted(features.keys()) == sorted(trained_columns)
    assert sorted(features.keys()) == sorted(STAGE1_LIGHTGBM_FEATURE_COLUMNS)
    assert features["model_score"] >= 0.0
    assert features["heuristic_score"] != 0.0
    assert features["calorie_distance_score"] == 990.0
    assert features["macro_distance_score"] == 203.0
    assert features["meals_per_day"] == 3.0
    assert features["profile_activity_lightly_active"] == 1.0
    assert features["profile_goal_general_health"] == 1.0
    for name in REASON_FEEDBACK_FEATURE_COLUMNS:
        assert features[name] == 0.0


def test_reason_feedback_features_from_events_counts_known_reason_tags():
    features = reason_feedback_features_from_events(
        [
            {
                "event_name": "why_replaced_submitted",
                "reason_tags": ["cost_too_high", "ingredient_unavailable"],
            },
            {
                "event_name": "why_skipped_submitted",
                "reason_primary_tag": "schedule_conflict",
            },
            {
                "event_name": "plan_viewed",
                "reason_tags": ["cost_too_high"],
            },
        ]
    )

    assert features["reason_events_total"] == 2.0
    assert features["replace_reason_events_total"] == 1.0
    assert features["replace_reason_tag_hist_cost_too_high"] == 1.0
    assert features["replace_reason_tag_hist_ingredient_unavailable"] == 1.0
    assert features["skip_reason_events_total"] == 1.0
    assert features["skip_reason_tag_hist_schedule_conflict"] == 1.0
