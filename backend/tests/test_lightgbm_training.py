import csv
import importlib.util
import json
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


def _write_split(path: Path, rows: list[dict[str, object]]) -> None:
    fieldnames = list(rows[0].keys())
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def _request_rows(
    request_id: str,
    uid_hash: str,
    budget_weekly_norm: float,
    pantry_overlap_count: float,
    reason_events_total: int,
    generated_at_ms: int,
) -> list[dict[str, object]]:
    base = {
        "request_id": request_id,
        "uid_hash": uid_hash,
        "meal_bucket": "Lunch",
        "ranking_strategy": "stage1_heuristic_shadow_only",
        "model_version": "shadow_v0",
        "budget_weekly_norm": budget_weekly_norm,
        "pantry_overlap_count": pantry_overlap_count,
        "reason_events_total": reason_events_total,
        "recipe_minutes": 20,
        "recipe_fats": 12,
        "recipe_carbs": 45,
        "recipe_fiber": 8,
        "recipe_calories": 420,
        "recipe_protein": 28,
        "recipe_cost_est": 150,
        "restriction_count": 1,
    }
    return [
        {
            **base,
            "recipe_id": f"{request_id}-pos",
            "generated_at_ms": generated_at_ms,
            "selected_by_solver": 1,
            "model_score": 0.9,
            "heuristic_score": 0.7,
        },
        {
            **base,
            "recipe_id": f"{request_id}-neg",
            "generated_at_ms": generated_at_ms + 1,
            "selected_by_solver": 0,
            "model_score": 0.1,
            "heuristic_score": 0.3,
            "recipe_cost_est": 220,
            "recipe_calories": 510,
        },
    ]


def test_train_lightgbm_script_emits_cohort_metrics():
    base = REPO_ROOT / "backend" / "tests" / ".tmp_lightgbm_training"
    shutil.rmtree(base, ignore_errors=True)
    dataset_dir = base / "dataset"
    output_dir = base / "model"
    dataset_dir.mkdir(parents=True, exist_ok=True)

    train_rows = []
    train_rows += _request_rows("train-cold-low", "uid-a", 0.13, 0.4, 0, 1730000000000)
    train_rows += _request_rows("train-mid", "uid-b", 0.19, 1.4, 10, 1730000000100)
    train_rows += _request_rows("train-sparse", "uid-c", 0.17, 0.7, 5, 1730000000200)
    train_rows += _request_rows("train-high", "uid-d", 0.23, 1.9, 30, 1730000000300)

    val_rows = []
    val_rows += _request_rows("val-cold-low", "uid-e", 0.14, 0.3, 0, 1730000000400)
    val_rows += _request_rows("val-mid", "uid-f", 0.21, 1.7, 12, 1730000000500)
    val_rows += _request_rows("val-sparse", "uid-g", 0.18, 0.8, 4, 1730000000600)

    test_rows = []
    test_rows += _request_rows("test-cold-low", "uid-h", 0.13, 0.5, 0, 1730000000700)
    test_rows += _request_rows("test-budget", "uid-i", 0.15, 1.2, 6, 1730000000800)
    test_rows += _request_rows("test-high", "uid-j", 0.24, 1.8, 25, 1730000000900)

    _write_split(dataset_dir / "train.csv", train_rows)
    _write_split(dataset_dir / "val.csv", val_rows)
    _write_split(dataset_dir / "test.csv", test_rows)
    _write_split(dataset_dir / "dataset_full.csv", train_rows + val_rows + test_rows)
    (dataset_dir / "dataset_manifest.json").write_text(
        json.dumps(
            {
                "datasetVersion": "test-dataset",
                "quality": {
                    "rows": len(train_rows) + len(val_rows) + len(test_rows),
                    "positives": len(train_rows + val_rows + test_rows) // 2,
                    "negatives": len(train_rows + val_rows + test_rows) // 2,
                    "positive_rate": 0.5,
                    "unique_requests": 10,
                    "unique_users": 10,
                },
            },
            indent=2,
        ),
        encoding="utf-8",
    )

    script = REPO_ROOT / "ml" / "offline_training" / "train_lightgbm_v1.py"
    result = subprocess.run(
        [
            sys.executable,
            str(script),
            "--dataset-dir",
            str(dataset_dir),
            "--output-dir",
            str(output_dir),
            "--seed",
            "2026",
            "--n-estimators",
            "25",
            "--num-leaves",
            "7",
        ],
        check=False,
        capture_output=True,
        text=True,
        cwd=REPO_ROOT,
    )
    assert result.returncode == 0, result.stderr or result.stdout

    metrics = json.loads((output_dir / "training_metrics.json").read_text(encoding="utf-8"))
    assert metrics["artifact_files"]["request_confusion_csv"] == "request_confusion.csv"
    assert metrics["artifact_files"]["cohort_regression_request_confusion_csv"] == "cohort_regression_request_confusion.csv"
    assert "cohort_definitions" in metrics
    assert set(metrics["val_cohort_metrics"].keys()) == {"cold_start", "sparse_pantry", "budget_constrained"}
    assert set(metrics["test_cohort_metrics"].keys()) == {"cold_start", "sparse_pantry", "budget_constrained"}
    assert metrics["val_cohort_metrics"]["cold_start"]["request_count"] >= 1
    assert metrics["val_cohort_metrics"]["budget_constrained"]["request_count"] >= 1
    assert metrics["test_cohort_metrics"]["sparse_pantry"]["request_count"] >= 1
    assert "uplift_vs_baseline" in metrics["test_cohort_metrics"]["budget_constrained"]
    assert (output_dir / "cohort_metrics.csv").exists()
    request_confusion_path = output_dir / "request_confusion.csv"
    regression_confusion_path = output_dir / "cohort_regression_request_confusion.csv"
    assert request_confusion_path.exists()
    assert regression_confusion_path.exists()

    with request_confusion_path.open("r", newline="", encoding="utf-8") as fp:
        request_rows = list(csv.DictReader(fp))
    assert len(request_rows) == 6
    assert {row["split"] for row in request_rows} == {"val", "test"}
    assert {"model_top10_false_negatives", "uplift_ndcg@10", "cohorts"}.issubset(request_rows[0].keys())

    with regression_confusion_path.open("r", newline="", encoding="utf-8") as fp:
        regression_reader = csv.DictReader(fp)
        regression_rows = list(regression_reader)
    assert "cohort" in (regression_reader.fieldnames or [])
    assert isinstance(metrics["val_regressed_cohorts"], list)
    assert isinstance(metrics["test_regressed_cohorts"], list)


def test_regressed_cohort_request_confusion_rows_filters_non_regressions():
    module_path = REPO_ROOT / "ml" / "offline_training" / "train_lightgbm_v1.py"
    spec = importlib.util.spec_from_file_location("train_lightgbm_v1", module_path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    rows = [
        {
            "split": "val",
            "request_id": "req-a",
            "cohorts": "budget_constrained;cold_start",
            "uplift_ndcg@10": -0.10,
            "uplift_map@10": -0.20,
        },
        {
            "split": "test",
            "request_id": "req-b",
            "cohorts": "budget_constrained",
            "uplift_ndcg@10": 0.02,
            "uplift_map@10": 0.03,
        },
    ]
    filtered = module._regressed_cohort_request_confusion_rows(
        rows,
        {
            "val": ["budget_constrained"],
            "test": [],
        },
    )

    assert filtered == [
        {
            "split": "val",
            "request_id": "req-a",
            "cohorts": "budget_constrained;cold_start",
            "uplift_ndcg@10": -0.10,
            "uplift_map@10": -0.20,
            "cohort": "budget_constrained",
        }
    ]
