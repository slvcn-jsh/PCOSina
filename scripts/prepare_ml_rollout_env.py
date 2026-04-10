#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
from pathlib import Path


REQUIRED_CANARY_COHORTS = ("budget_constrained", "cold_start", "sparse_pantry")


def _render_shell_exports(*, shell: str, model_path: Path, metrics_path: Path) -> list[str]:
    if shell == "bash":
        return [
            f"export PCOSINA_ML_MODEL_PATH='{model_path.as_posix()}'",
            f"export PCOSINA_ML_METRICS_PATH='{metrics_path.as_posix()}'",
        ]
    return [
        f'$env:PCOSINA_ML_MODEL_PATH="{str(model_path)}"',
        f'$env:PCOSINA_ML_METRICS_PATH="{str(metrics_path)}"',
    ]


def _resolve_model_path(model_dir: Path, metrics: dict) -> Path:
    artifact_files = metrics.get("artifact_files") or {}
    candidate = artifact_files.get("model") or metrics.get("model") or "lightgbm_v1_model.txt"
    candidate_path = Path(str(candidate))
    if not candidate_path.is_absolute():
        candidate_path = model_dir / candidate_path
    return candidate_path


def _collect_canary_summary(metrics: dict, failures: list[str]) -> dict[str, dict[str, float | int]]:
    summary: dict[str, dict[str, float | int]] = {}
    test_cohorts = metrics.get("test_cohort_metrics") or {}
    for cohort_name in REQUIRED_CANARY_COHORTS:
        cohort_metrics = test_cohorts.get(cohort_name) or {}
        request_count = int(cohort_metrics.get("request_count") or 0)
        uplift = cohort_metrics.get("uplift_vs_baseline") or {}
        ndcg_uplift = uplift.get("ndcg@10")
        map_uplift = uplift.get("map@10")
        summary[cohort_name] = {
            "requestCount": request_count,
            "upliftNdcg10": float(ndcg_uplift or 0.0),
            "upliftMap10": float(map_uplift or 0.0),
        }
        if request_count <= 0:
            failures.append(f"test cohort {cohort_name} has no requests")
            continue
        if ndcg_uplift is None or map_uplift is None:
            failures.append(f"test cohort {cohort_name} missing uplift metrics")
            continue
        if float(ndcg_uplift) < 0 or float(map_uplift) < 0:
            failures.append(
                f"test cohort {cohort_name} regressed vs baseline: "
                f"ndcg@10={ndcg_uplift}, map@10={map_uplift}"
            )
    return summary


def _build_report(*, phase: str, canary_percent: float, shell: str, model_dir: Path) -> dict:
    failures: list[str] = []
    metrics_path = model_dir / "training_metrics.json"
    if not metrics_path.exists():
        failures.append(f"missing training metrics: {metrics_path}")
        return {
            "status": "failed",
            "phase": phase,
            "modelDir": str(model_dir.resolve()),
            "failures": failures,
        }

    metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
    model_path = _resolve_model_path(model_dir, metrics)
    if not model_path.exists():
        failures.append(f"missing model artifact: {model_path}")

    feature_columns = metrics.get("feature_columns") or metrics.get("featureColumns") or []
    if not isinstance(feature_columns, list) or not feature_columns:
        failures.append("feature column list missing from training metrics")

    val_auc = ((metrics.get("val_binary_metrics") or {}).get("auc"))
    if val_auc is None:
        failures.append("validation AUC missing from training metrics")

    dataset_manifest = metrics.get("dataset_manifest") or {}
    dataset_quality = dataset_manifest.get("quality") or {}
    if int(dataset_quality.get("rows") or 0) <= 0:
        failures.append("dataset quality rows missing from training metrics")

    canary_summary: dict[str, dict[str, float | int]] = {}
    if phase == "canary":
        canary_summary = _collect_canary_summary(metrics, failures)

    shell_exports = _render_shell_exports(
        shell=shell,
        model_path=model_path.resolve(),
        metrics_path=metrics_path.resolve(),
    )
    next_commands = []
    if phase == "canary":
        next_commands.append(
            "python scripts/monitor_canary_guardrails.py --metrics benchmarks/reports/go_live_metrics.json "
            "--output benchmarks/reports/canary_guard_report.json"
        )

    status = "ok" if not failures else "failed"
    return {
        "status": status,
        "phase": phase,
        "modelDir": str(model_dir.resolve()),
        "modelPath": str(model_path.resolve()),
        "metricsPath": str(metrics_path.resolve()),
        "modelName": str(metrics.get("model_name") or model_path.stem),
        "trainedAtMs": int(metrics.get("trained_at_ms") or 0),
        "valAuc": float(val_auc or 0.0),
        "featureCount": len(feature_columns),
        "dataset": {
            "rows": int(dataset_quality.get("rows") or 0),
            "positives": int(dataset_quality.get("positives") or 0),
            "negatives": int(dataset_quality.get("negatives") or 0),
            "positiveRate": float(dataset_quality.get("positive_rate") or 0.0),
            "uniqueRequests": int(dataset_quality.get("unique_requests") or 0),
            "uniqueUsers": int(dataset_quality.get("unique_users") or 0),
        },
        "canarySummary": canary_summary,
        "environment": {
            "PCOSINA_ML_MODEL_PATH": str(model_path.resolve()),
            "PCOSINA_ML_METRICS_PATH": str(metrics_path.resolve()),
        },
        "policyRecommendation": {
            "stage1.ML_shadow_enabled": True,
            "stage1.ML_canary_enabled": phase == "canary",
            "sre.canary_cohort_percent": float(canary_percent) if phase == "canary" else 0.0,
        },
        "shell": shell,
        "shellExports": shell_exports,
        "nextCommands": next_commands,
        "failures": failures,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Resolve, validate, and print runtime env exports for a PCOSINA ML rollout candidate."
    )
    parser.add_argument("--model-dir", default="ml/offline_training/artifacts/model_v1")
    parser.add_argument("--phase", choices=["shadow", "canary"], default="shadow")
    parser.add_argument("--shell", choices=["powershell", "bash"], default="powershell")
    parser.add_argument("--canary-percent", type=float, default=5.0)
    parser.add_argument("--output", help="Optional JSON report output path.")
    args = parser.parse_args()

    report = _build_report(
        phase=args.phase,
        canary_percent=float(args.canary_percent),
        shell=args.shell,
        model_dir=Path(args.model_dir),
    )

    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(report, indent=2), encoding="utf-8")

    if report["status"] != "ok":
        print("ML ROLLOUT PREP FAILED")
        for item in report["failures"]:
            print(f"- {item}")
        return 1

    print("ML ROLLOUT PREP PASSED")
    print(f"info: phase={report['phase']}")
    print(f"info: model_name={report['modelName']}")
    print(f"info: model_path={report['modelPath']}")
    print(f"info: metrics_path={report['metricsPath']}")
    print(f"info: val_auc={report['valAuc']}")
    print(f"info: dataset_rows={report['dataset']['rows']}")
    print(f"info: dataset_unique_requests={report['dataset']['uniqueRequests']}")
    print(f"info: dataset_unique_users={report['dataset']['uniqueUsers']}")
    if args.phase == "canary":
        for cohort_name in REQUIRED_CANARY_COHORTS:
            cohort = report["canarySummary"].get(cohort_name) or {}
            print(f"info: test_{cohort_name}_requests={cohort.get('requestCount')}")
            print(f"info: test_{cohort_name}_uplift_ndcg10={cohort.get('upliftNdcg10')}")
            print(f"info: test_{cohort_name}_uplift_map10={cohort.get('upliftMap10')}")
    print("shell:")
    for line in report["shellExports"]:
        print(line)
    print("policy:")
    print(f"stage1.ML_shadow_enabled={str(report['policyRecommendation']['stage1.ML_shadow_enabled']).lower()}")
    print(f"stage1.ML_canary_enabled={str(report['policyRecommendation']['stage1.ML_canary_enabled']).lower()}")
    print(f"sre.canary_cohort_percent={report['policyRecommendation']['sre.canary_cohort_percent']}")
    for command in report["nextCommands"]:
        print(f"next: {command}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
