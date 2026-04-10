import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from canary_guard import evaluate_canary_metrics, extract_rollback_thresholds


def test_extract_rollback_thresholds_returns_numeric_map():
    policy = {
        "sre": {
            "rollback_trigger_thresholds": {
                "hard_violation_rate": 0,
                "latency_p95_ms": "7000",
                "api_error_rate": 0.01,
                "invalid": "x",
            }
        }
    }
    out = extract_rollback_thresholds(policy)
    assert out["hard_violation_rate"] == 0.0
    assert out["latency_p95_ms"] == 7000.0
    assert out["api_error_rate"] == 0.01
    assert "invalid" not in out


def test_extract_rollback_thresholds_falls_back_to_defaults_when_missing():
    out = extract_rollback_thresholds({})
    assert out["hard_violation_rate"] == 0.0
    assert out["latency_p95_ms"] == 7000.0
    assert out["api_error_rate"] == 0.01


def test_evaluate_canary_metrics_no_breach():
    thresholds = {
        "hard_violation_rate": 0.0,
        "latency_p95_ms": 7000.0,
        "api_error_rate": 0.01,
    }
    metrics = {
        "hard_violation_rate": 0.0,
        "latency_p95_ms": 6100.0,
        "api_error_rate": 0.008,
    }
    breaches = evaluate_canary_metrics(metrics=metrics, thresholds=thresholds)
    assert breaches == []


def test_evaluate_canary_metrics_detects_threshold_breach():
    thresholds = {
        "hard_violation_rate": 0.0,
        "latency_p95_ms": 7000.0,
        "api_error_rate": 0.01,
    }
    metrics = {
        "hard_violation_rate": 0.02,
        "latency_p95_ms": 8200.0,
        "api_error_rate": 0.008,
    }
    breaches = evaluate_canary_metrics(metrics=metrics, thresholds=thresholds)
    by_metric = {b["metric"]: b for b in breaches}
    assert by_metric["hard_violation_rate"]["reason"] == "threshold_exceeded"
    assert by_metric["latency_p95_ms"]["reason"] == "threshold_exceeded"
    assert "api_error_rate" not in by_metric
