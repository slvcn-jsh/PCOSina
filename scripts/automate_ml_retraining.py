#!/usr/bin/env python
"""Operator-safe orchestration for PCOSina offline ML retraining.

This script does not make ML authoritative. It only automates the Stage-1
ranking model pipeline; deterministic filtering and the solver remain the final
planning authority.
"""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Callable, Sequence


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DATASET_DIR = ROOT / "ml" / "offline_training" / "artifacts" / "dataset_v1"
DEFAULT_MODEL_DIR = ROOT / "ml" / "offline_training" / "artifacts" / "model_v1"
DEFAULT_CANDIDATE_MODEL_DIR = ROOT / "ml" / "offline_training" / "artifacts" / "model_v1_candidate"
DEFAULT_REPORT = ROOT / "ml" / "offline_training" / "artifacts" / "retraining_report.json"


Runner = Callable[[Sequence[str]], subprocess.CompletedProcess[str]]


@dataclass
class PipelineStep:
    name: str
    command: list[str]
    returncode: int
    durationSeconds: float
    stdout: str = ""
    stderr: str = ""


@dataclass
class RetrainingReport:
    status: str
    startedAtMs: int
    completedAtMs: int
    datasetDir: str
    candidateModelDir: str
    promotedModelDir: str | None
    dryRun: bool
    steps: list[PipelineStep]
    failure: str | None = None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Automate PCOSina Stage-1 ML retraining.")
    parser.add_argument("--db-path", default=str(ROOT / "pcosina.db"))
    parser.add_argument("--dataset-dir", type=Path, default=DEFAULT_DATASET_DIR)
    parser.add_argument("--candidate-model-dir", type=Path, default=DEFAULT_CANDIDATE_MODEL_DIR)
    parser.add_argument("--promoted-model-dir", type=Path, default=DEFAULT_MODEL_DIR)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--mode", choices=["shadow", "canary"], default="shadow")
    parser.add_argument("--min-rows", type=int, default=500)
    parser.add_argument("--min-unique-requests", type=int, default=10)
    parser.add_argument("--min-unique-users", type=int, default=3)
    parser.add_argument("--skip-readiness", action="store_true")
    parser.add_argument("--promote", action="store_true", help="Replace promoted model artifacts after validation passes.")
    parser.add_argument("--dry-run", action="store_true", help="Print commands and write report without running training.")
    return parser


def command_plan(args: argparse.Namespace) -> list[tuple[str, list[str]]]:
    python = sys.executable
    dataset_manifest = args.dataset_dir / "dataset_manifest.json"
    candidate_metrics = args.candidate_model_dir / "training_metrics.json"
    steps = [
        (
            "build_dataset",
            [
                python,
                str(ROOT / "ml" / "offline_training" / "build_training_dataset_v1.py"),
                "--db-path",
                str(args.db_path),
                "--output-dir",
                str(args.dataset_dir),
            ],
        ),
        (
            "train_candidate_model",
            [
                python,
                str(ROOT / "ml" / "offline_training" / "train_lightgbm_v1.py"),
                "--dataset-dir",
                str(args.dataset_dir),
                "--output-dir",
                str(args.candidate_model_dir),
                "--seed",
                str(args.seed),
            ],
        ),
    ]
    if not args.skip_readiness:
        steps.append(
            (
                "readiness_gate",
                [
                    python,
                    str(ROOT / "scripts" / "check_ml_readiness.py"),
                    "--mode",
                    str(args.mode),
                    "--dataset-manifest",
                    str(dataset_manifest),
                    "--model-metrics",
                    str(candidate_metrics),
                    "--min-rows",
                    str(args.min_rows),
                    "--min-unique-requests",
                    str(args.min_unique_requests),
                    "--min-unique-users",
                    str(args.min_unique_users),
                ],
            )
        )
    return steps


def run_pipeline(args: argparse.Namespace, runner: Runner | None = None) -> RetrainingReport:
    started = int(time.time() * 1000)
    runner = runner or _run_command
    steps: list[PipelineStep] = []
    failure: str | None = None
    status = "dry_run" if args.dry_run else "success"
    promoted_model_dir: str | None = None
    try:
        for name, command in command_plan(args):
            if args.dry_run:
                steps.append(PipelineStep(name=name, command=command, returncode=0, durationSeconds=0.0))
                continue
            before = time.monotonic()
            result = runner(command)
            duration = time.monotonic() - before
            step = PipelineStep(
                name=name,
                command=command,
                returncode=int(result.returncode),
                durationSeconds=round(duration, 3),
                stdout=(result.stdout or "")[-4000:],
                stderr=(result.stderr or "")[-4000:],
            )
            steps.append(step)
            if result.returncode != 0:
                failure = f"{name} failed with exit code {result.returncode}"
                status = "failed"
                break
        if status == "success":
            _verify_candidate_artifacts(args.candidate_model_dir)
            if args.promote:
                _promote_model(args.candidate_model_dir, args.promoted_model_dir)
                promoted_model_dir = str(args.promoted_model_dir)
            else:
                status = "validated"
    except Exception as exc:
        status = "failed"
        failure = str(exc)
    report = RetrainingReport(
        status=status,
        startedAtMs=started,
        completedAtMs=int(time.time() * 1000),
        datasetDir=str(args.dataset_dir),
        candidateModelDir=str(args.candidate_model_dir),
        promotedModelDir=promoted_model_dir,
        dryRun=bool(args.dry_run),
        steps=steps,
        failure=failure,
    )
    write_report(args.report, report)
    return report


def write_report(path: Path, report: RetrainingReport) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = asdict(report)
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")


def _run_command(command: Sequence[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=False)


def _verify_candidate_artifacts(candidate_dir: Path) -> None:
    required = [
        candidate_dir / "lightgbm_v1_model.txt",
        candidate_dir / "training_metrics.json",
        candidate_dir / "feature_importance.csv",
    ]
    missing = [str(path) for path in required if not path.exists()]
    if missing:
        raise FileNotFoundError(f"candidate model missing required artifact(s): {', '.join(missing)}")


def _promote_model(candidate_dir: Path, promoted_dir: Path) -> None:
    if not candidate_dir.exists():
        raise FileNotFoundError(f"candidate model directory not found: {candidate_dir}")
    if promoted_dir.exists():
        backup = promoted_dir.with_name(f"{promoted_dir.name}_backup_{int(time.time())}")
        shutil.move(str(promoted_dir), str(backup))
    shutil.copytree(candidate_dir, promoted_dir)


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    report = run_pipeline(args)
    print(f"ML retraining status: {report.status}")
    print(f"Report: {args.report}")
    if report.failure:
        print(f"Failure: {report.failure}", file=sys.stderr)
    return 0 if report.status in {"success", "validated", "dry_run"} else 1


if __name__ == "__main__":
    raise SystemExit(main())
