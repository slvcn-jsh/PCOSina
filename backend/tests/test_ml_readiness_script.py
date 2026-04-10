import json
import os
import subprocess
import sys
import time
from pathlib import Path
from uuid import uuid4


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = PROJECT_ROOT / "scripts" / "check_ml_readiness.py"


def _case_dir(name: str) -> Path:
    root = Path(__file__).resolve().parent / ".tmp_ml_readiness"
    root.mkdir(parents=True, exist_ok=True)
    case_dir = root / f"{name}_{uuid4().hex}"
    case_dir.mkdir(parents=True, exist_ok=True)
    return case_dir


def _run(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, *args],
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True,
        check=False,
        env=dict(os.environ),
    )


def _base_manifest() -> dict:
    now_ms = int(time.time() * 1000)
    return {
        "generatedAtMs": now_ms,
        "sourceTelemetryWindow": {
            "rowCount": 1000,
            "minGeneratedAtMs": now_ms - 3600_000,
            "maxGeneratedAtMs": now_ms,
        },
        "reasonFeedbackWindow": {
            "rowCount": 120,
            "uniqueUsers": 12,
            "minEventTimeMs": now_ms - 1800_000,
            "maxEventTimeMs": now_ms,
        },
        "quality": {
            "rows": 1000,
            "positives": 120,
            "negatives": 880,
            "positive_rate": 0.12,
            "unique_requests": 30,
            "unique_users": 12,
        }
    }


def _cohort_metric(requests: int, ndcg_uplift: float, map_uplift: float) -> dict:
    return {
        "request_count": requests,
        "row_count": 100,
        "positive_rows": 10,
        "negative_rows": 90,
        "positive_rate": 0.1,
        "binary_metrics": {"auc": 0.9, "logloss": 0.2},
        "ranking_metrics": {"ndcg@10": 0.8, "map@10": 0.6},
        "baseline_ranking_metrics": {"ndcg@10": 0.7, "map@10": 0.5},
        "uplift_vs_baseline": {"ndcg@10": ndcg_uplift, "map@10": map_uplift},
    }


def _reason_feedback_summary(*, generated_at_ms: int, max_event_time_ms: int) -> dict:
    return {
        "generatedAtMs": generated_at_ms,
        "sourceEventWindow": {
            "minEventTimeMs": max_event_time_ms - 1000,
            "maxEventTimeMs": max_event_time_ms,
        },
        "rows": 240,
        "uniqueUsers": 6,
    }


def test_check_ml_readiness_passes_with_positive_test_cohort_uplift():
    case = _case_dir("pass")
    manifest_path = case / "dataset_manifest.json"
    metrics_path = case / "training_metrics.json"
    manifest_path.write_text(json.dumps(_base_manifest()), encoding="utf-8")
    metrics_path.write_text(
        json.dumps(
            {
                "val_binary_metrics": {"auc": 0.95},
                "val_cohort_metrics": {
                    "cold_start": _cohort_metric(2, 0.01, 0.01),
                    "sparse_pantry": _cohort_metric(2, 0.02, 0.02),
                    "budget_constrained": _cohort_metric(1, -0.01, -0.02),
                },
                "test_cohort_metrics": {
                    "cold_start": _cohort_metric(3, 0.03, 0.03),
                    "sparse_pantry": _cohort_metric(2, 0.04, 0.04),
                    "budget_constrained": _cohort_metric(2, 0.01, 0.02),
                },
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPT_PATH),
            "--dataset-manifest",
            str(manifest_path),
            "--model-metrics",
            str(metrics_path),
            "--mode",
            "canary",
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    assert "test_budget_constrained_uplift_ndcg10=0.01" in proc.stdout


def test_check_ml_readiness_fails_when_test_cohort_regresses():
    case = _case_dir("fail")
    manifest_path = case / "dataset_manifest.json"
    metrics_path = case / "training_metrics.json"
    manifest_path.write_text(json.dumps(_base_manifest()), encoding="utf-8")
    metrics_path.write_text(
        json.dumps(
            {
                "val_binary_metrics": {"auc": 0.95},
                "val_cohort_metrics": {
                    "cold_start": _cohort_metric(2, 0.01, 0.01),
                    "sparse_pantry": _cohort_metric(2, 0.02, 0.02),
                    "budget_constrained": _cohort_metric(1, 0.01, 0.02),
                },
                "test_cohort_metrics": {
                    "cold_start": _cohort_metric(3, 0.03, 0.03),
                    "sparse_pantry": _cohort_metric(2, 0.04, 0.04),
                    "budget_constrained": _cohort_metric(2, -0.01, 0.02),
                },
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPT_PATH),
            "--dataset-manifest",
            str(manifest_path),
            "--model-metrics",
            str(metrics_path),
            "--mode",
            "canary",
        ]
    )
    assert proc.returncode == 1
    assert "test cohort budget_constrained regressed vs baseline" in proc.stdout


def test_check_ml_readiness_fails_when_dataset_or_reason_feedback_is_stale():
    case = _case_dir("stale")
    manifest_path = case / "dataset_manifest.json"
    metrics_path = case / "training_metrics.json"
    reason_summary_path = case / "reason_feedback_summary.json"
    stale_ms = int(time.time() * 1000) - (800 * 3600_000)
    manifest = _base_manifest()
    manifest["generatedAtMs"] = stale_ms
    manifest["sourceTelemetryWindow"]["maxGeneratedAtMs"] = stale_ms
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    reason_summary_path.write_text(
        json.dumps(_reason_feedback_summary(generated_at_ms=stale_ms, max_event_time_ms=stale_ms)),
        encoding="utf-8",
    )
    metrics_path.write_text(
        json.dumps(
            {
                "trained_at_ms": stale_ms + 1,
                "dataset_manifest": {"generatedAtMs": stale_ms},
                "val_binary_metrics": {"auc": 0.95},
                "val_cohort_metrics": {
                    "cold_start": _cohort_metric(2, 0.01, 0.01),
                    "sparse_pantry": _cohort_metric(2, 0.02, 0.02),
                    "budget_constrained": _cohort_metric(1, 0.01, 0.02),
                },
                "test_cohort_metrics": {
                    "cold_start": _cohort_metric(3, 0.03, 0.03),
                    "sparse_pantry": _cohort_metric(2, 0.04, 0.04),
                    "budget_constrained": _cohort_metric(2, 0.01, 0.02),
                },
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPT_PATH),
            "--dataset-manifest",
            str(manifest_path),
            "--model-metrics",
            str(metrics_path),
            "--reason-feedback-summary",
            str(reason_summary_path),
            "--mode",
            "canary",
            "--max-dataset-artifact-age-hours",
            "720",
            "--max-dataset-source-age-hours",
            "720",
            "--max-reason-feedback-artifact-age-hours",
            "720",
            "--max-reason-feedback-source-age-hours",
            "720",
        ]
    )
    assert proc.returncode == 1
    assert "dataset_artifact age exceeded gate" in proc.stdout
    assert "reason_feedback_source age exceeded gate" in proc.stdout


def test_check_ml_readiness_fails_when_model_metrics_do_not_match_current_dataset_manifest():
    case = _case_dir("manifest_mismatch")
    manifest_path = case / "dataset_manifest.json"
    metrics_path = case / "training_metrics.json"
    manifest = _base_manifest()
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    metrics_path.write_text(
        json.dumps(
            {
                "trained_at_ms": int(time.time() * 1000),
                "dataset_manifest": {"generatedAtMs": int(manifest["generatedAtMs"]) - 1234},
                "val_binary_metrics": {"auc": 0.95},
                "val_cohort_metrics": {
                    "cold_start": _cohort_metric(2, 0.01, 0.01),
                    "sparse_pantry": _cohort_metric(2, 0.02, 0.02),
                    "budget_constrained": _cohort_metric(1, 0.01, 0.02),
                },
                "test_cohort_metrics": {
                    "cold_start": _cohort_metric(3, 0.03, 0.03),
                    "sparse_pantry": _cohort_metric(2, 0.04, 0.04),
                    "budget_constrained": _cohort_metric(2, 0.01, 0.02),
                },
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPT_PATH),
            "--dataset-manifest",
            str(manifest_path),
            "--model-metrics",
            str(metrics_path),
            "--mode",
            "canary",
        ]
    )
    assert proc.returncode == 1
    assert "model metrics dataset manifest does not match current dataset manifest" in proc.stdout
