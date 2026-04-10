import json
import os
import subprocess
import sys
from pathlib import Path
from uuid import uuid4


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = PROJECT_ROOT / "scripts" / "prepare_ml_rollout_env.py"


def _case_dir(name: str) -> Path:
    root = Path(__file__).resolve().parent / ".tmp_ml_rollout_prep"
    root.mkdir(parents=True, exist_ok=True)
    case = root / f"{name}_{uuid4().hex}"
    case.mkdir(parents=True, exist_ok=True)
    return case


def _run(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, *args],
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True,
        check=False,
        env=dict(os.environ),
    )


def _write_metrics(case: Path, *, sparse_pantry_uplift: float) -> Path:
    model_dir = case / "model_v1"
    model_dir.mkdir(parents=True, exist_ok=True)
    (model_dir / "lightgbm_v1_model.txt").write_text("stub-model", encoding="utf-8")
    metrics = {
        "model_name": "lightgbm_stage1_ranker_v1",
        "trained_at_ms": 1774771039085,
        "feature_columns": ["recipe_calories", "budget_weekly_norm"],
        "val_binary_metrics": {"auc": 0.9267},
        "dataset_manifest": {
            "quality": {
                "rows": 74112,
                "positives": 4671,
                "negatives": 69441,
                "positive_rate": 0.06302623056994819,
                "unique_requests": 360,
                "unique_users": 60,
            }
        },
        "artifact_files": {"model": "lightgbm_v1_model.txt"},
        "test_cohort_metrics": {
            "budget_constrained": {
                "request_count": 19,
                "uplift_vs_baseline": {"ndcg@10": 0.0382, "map@10": 0.0520},
            },
            "cold_start": {
                "request_count": 30,
                "uplift_vs_baseline": {"ndcg@10": 0.0438, "map@10": 0.0568},
            },
            "sparse_pantry": {
                "request_count": 10,
                "uplift_vs_baseline": {"ndcg@10": sparse_pantry_uplift, "map@10": 0.0370},
            },
        },
    }
    (model_dir / "training_metrics.json").write_text(json.dumps(metrics), encoding="utf-8")
    return model_dir


def test_prepare_ml_rollout_env_emits_canary_exports_and_report():
    case = _case_dir("canary_ok")
    model_dir = _write_metrics(case, sparse_pantry_uplift=0.0267)
    output = case / "rollout_report.json"

    proc = _run(
        [
            str(SCRIPT),
            "--model-dir",
            str(model_dir),
            "--phase",
            "canary",
            "--output",
            str(output),
        ]
    )

    assert proc.returncode == 0, proc.stderr or proc.stdout
    assert "ML ROLLOUT PREP PASSED" in proc.stdout
    assert "PCOSINA_ML_MODEL_PATH" in proc.stdout
    report = json.loads(output.read_text(encoding="utf-8"))
    assert report["status"] == "ok"
    assert report["phase"] == "canary"
    assert report["policyRecommendation"]["stage1.ML_canary_enabled"] is True
    assert report["policyRecommendation"]["sre.canary_cohort_percent"] == 5.0
    assert report["canarySummary"]["sparse_pantry"]["upliftNdcg10"] == 0.0267


def test_prepare_ml_rollout_env_blocks_canary_regression():
    case = _case_dir("canary_fail")
    model_dir = _write_metrics(case, sparse_pantry_uplift=-0.0662)
    output = case / "rollout_report.json"

    proc = _run(
        [
            str(SCRIPT),
            "--model-dir",
            str(model_dir),
            "--phase",
            "canary",
            "--output",
            str(output),
        ]
    )

    assert proc.returncode == 1
    assert "ML ROLLOUT PREP FAILED" in proc.stdout
    report = json.loads(output.read_text(encoding="utf-8"))
    assert report["status"] == "failed"
    assert any("sparse_pantry" in item for item in report["failures"])
