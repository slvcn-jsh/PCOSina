import importlib.util
import json
import subprocess
import sys
from pathlib import Path
from uuid import uuid4

REPO_ROOT = Path(__file__).resolve().parents[2]


def _load_module():
    module_path = REPO_ROOT / "scripts" / "automate_ml_retraining.py"
    spec = importlib.util.spec_from_file_location("automate_ml_retraining", module_path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def _temp_dir() -> Path:
    base = REPO_ROOT / "backend" / "tests" / ".tmp_ml_retraining_orchestrator"
    path = base / uuid4().hex
    path.mkdir(parents=True, exist_ok=True)
    return path


def test_retraining_orchestrator_dry_run_writes_command_report():
    module = _load_module()
    folder = _temp_dir()
    args = module.build_parser().parse_args(
        [
            "--db-path",
            str(folder / "pcosina.db"),
            "--dataset-dir",
            str(folder / "dataset"),
            "--candidate-model-dir",
            str(folder / "candidate"),
            "--report",
            str(folder / "report.json"),
            "--dry-run",
        ]
    )

    report = module.run_pipeline(args)

    assert report.status == "dry_run"
    assert [step.name for step in report.steps] == ["build_dataset", "train_candidate_model", "readiness_gate"]
    payload = json.loads((folder / "report.json").read_text(encoding="utf-8"))
    assert payload["dryRun"] is True
    assert payload["steps"][0]["name"] == "build_dataset"


def test_retraining_orchestrator_validates_candidate_artifacts():
    module = _load_module()
    folder = _temp_dir()
    candidate = folder / "candidate"
    candidate.mkdir(parents=True)
    (candidate / "lightgbm_v1_model.txt").write_text("model", encoding="utf-8")
    (candidate / "training_metrics.json").write_text("{}", encoding="utf-8")
    (candidate / "feature_importance.csv").write_text("feature,importance\n", encoding="utf-8")
    args = module.build_parser().parse_args(
        [
            "--db-path",
            str(folder / "pcosina.db"),
            "--dataset-dir",
            str(folder / "dataset"),
            "--candidate-model-dir",
            str(candidate),
            "--report",
            str(folder / "report.json"),
            "--skip-readiness",
        ]
    )

    calls = []

    def runner(command):
        calls.append(command)
        return subprocess.CompletedProcess(command, 0, stdout="ok", stderr="")

    report = module.run_pipeline(args, runner=runner)

    assert report.status == "validated"
    assert [step.name for step in report.steps] == ["build_dataset", "train_candidate_model"]
    assert len(calls) == 2


def test_retraining_orchestrator_stops_on_failed_step():
    module = _load_module()
    folder = _temp_dir()
    args = module.build_parser().parse_args(
        [
            "--db-path",
            str(folder / "pcosina.db"),
            "--dataset-dir",
            str(folder / "dataset"),
            "--candidate-model-dir",
            str(folder / "candidate"),
            "--report",
            str(folder / "report.json"),
            "--skip-readiness",
        ]
    )

    def runner(command):
        return subprocess.CompletedProcess(command, 2, stdout="", stderr="boom")

    report = module.run_pipeline(args, runner=runner)

    assert report.status == "failed"
    assert "build_dataset failed" in (report.failure or "")
    assert len(report.steps) == 1
