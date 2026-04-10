import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from canary_guard import evaluate_canary_metrics


def test_breach_detection_for_all_primary_metrics():
    thresholds = {
        "hard_violation_rate": 0.0,
        "latency_p95_ms": 7000.0,
        "api_error_rate": 0.01,
    }
    metrics = {
        "hard_violation_rate": 0.05,
        "latency_p95_ms": 15000.0,
        "api_error_rate": 0.04,
    }
    breaches = evaluate_canary_metrics(metrics=metrics, thresholds=thresholds)
    names = sorted([item["metric"] for item in breaches])
    assert names == ["api_error_rate", "hard_violation_rate", "latency_p95_ms"]


def test_simulated_metrics_json_roundtrip():
    payload = {
        "hard_violation_rate": 0.03,
        "latency_p95_ms": 12000,
        "api_error_rate": 0.03,
    }
    encoded = json.dumps(payload)
    decoded = json.loads(encoded)
    assert decoded["hard_violation_rate"] > 0
    assert decoded["latency_p95_ms"] > 7000
    assert decoded["api_error_rate"] > 0.01
