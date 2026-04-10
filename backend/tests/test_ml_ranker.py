import importlib.util
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services.ml_ranker import Stage1MLRanker


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

    assert state.ready is True
    assert state.model_version == "lightgbm_v1_unknown"
    assert len(state.feature_columns) > 0
