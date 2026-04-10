#!/usr/bin/env python
from __future__ import annotations

import argparse
import importlib.util
import json
import time
from pathlib import Path


def _has_module(name: str) -> bool:
    return importlib.util.find_spec(name) is not None


def _max_age_check(
    *,
    label: str,
    timestamp_ms: int | None,
    max_age_hours: float,
    now_ms: int,
    failures: list[str],
    info: list[str],
) -> None:
    if float(max_age_hours or 0.0) <= 0.0:
        return
    if not timestamp_ms or int(timestamp_ms) <= 0:
        failures.append(f"{label} timestamp missing")
        return
    age_hours = max(0.0, float(now_ms - int(timestamp_ms)) / 3_600_000.0)
    info.append(f"{label}_age_hours={age_hours:.2f}")
    if age_hours > float(max_age_hours):
        failures.append(f"{label} age exceeded gate: {age_hours:.2f}h > {float(max_age_hours):.2f}h")


def main() -> int:
    parser = argparse.ArgumentParser(description="Check PCOSINA ML readiness gates")
    parser.add_argument("--dataset-manifest", default="ml/offline_training/artifacts/dataset_v1/dataset_manifest.json")
    parser.add_argument("--model-metrics", default="ml/offline_training/artifacts/model_v1/training_metrics.json")
    parser.add_argument(
        "--reason-feedback-summary",
        default="ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json",
    )
    parser.add_argument("--mode", choices=["shadow", "canary"], default="shadow")
    parser.add_argument("--min-rows", type=int, default=500)
    parser.add_argument("--min-positive-rate", type=float, default=0.01)
    parser.add_argument("--min-unique-requests", type=int, default=10)
    parser.add_argument("--min-unique-users", type=int, default=3)
    parser.add_argument("--max-dataset-artifact-age-hours", type=float, default=0.0)
    parser.add_argument("--max-dataset-source-age-hours", type=float, default=0.0)
    parser.add_argument("--max-reason-feedback-artifact-age-hours", type=float, default=0.0)
    parser.add_argument("--max-reason-feedback-source-age-hours", type=float, default=0.0)
    args = parser.parse_args()

    failures: list[str] = []
    info: list[str] = []
    now_ms = int(time.time() * 1000)

    taxonomy = Path("shared-contracts/event_schemas/ml_event_taxonomy.v1.json")
    if not taxonomy.exists():
        failures.append("missing event taxonomy schema")

    manifest_path = Path(args.dataset_manifest)
    manifest_generated_at_ms = 0
    manifest_source_max_generated_at_ms = 0
    if not manifest_path.exists():
        failures.append(f"missing dataset manifest: {manifest_path}")
    else:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest_generated_at_ms = int(manifest.get("generatedAtMs") or 0)
        rows = int(((manifest.get("quality") or {}).get("rows")) or 0)
        positives = int(((manifest.get("quality") or {}).get("positives")) or 0)
        negatives = int(((manifest.get("quality") or {}).get("negatives")) or 0)
        positive_rate = float(((manifest.get("quality") or {}).get("positive_rate")) or 0.0)
        unique_requests = int(((manifest.get("quality") or {}).get("unique_requests")) or 0)
        unique_users = int(((manifest.get("quality") or {}).get("unique_users")) or 0)
        manifest_source_window = manifest.get("sourceTelemetryWindow") or {}
        manifest_source_max_generated_at_ms = int((manifest_source_window.get("maxGeneratedAtMs")) or 0)
        reason_feedback_window = manifest.get("reasonFeedbackWindow") or {}
        manifest_reason_feedback_max_event_time_ms = int((reason_feedback_window.get("maxEventTimeMs")) or 0)
        if rows <= 0:
            failures.append("dataset rows must be > 0")
        if rows < args.min_rows:
            failures.append(f"dataset rows below minimum gate: {rows} < {args.min_rows}")
        if positives <= 0 or negatives <= 0:
            failures.append("dataset must contain both positive and negative labels")
        if positive_rate < args.min_positive_rate:
            failures.append(
                f"dataset positive_rate below minimum gate: {positive_rate:.6f} < {args.min_positive_rate:.6f}"
            )
        if unique_requests < args.min_unique_requests:
            failures.append(
                f"dataset unique_requests below minimum gate: {unique_requests} < {args.min_unique_requests}"
            )
        if unique_users < args.min_unique_users:
            failures.append(f"dataset unique_users below minimum gate: {unique_users} < {args.min_unique_users}")
        info.append(f"dataset_rows={rows}")
        info.append(f"positive_rate={positive_rate}")
        info.append(f"unique_requests={unique_requests}")
        info.append(f"unique_users={unique_users}")
        if manifest_generated_at_ms > 0:
            info.append(f"dataset_generated_at_ms={manifest_generated_at_ms}")
        if manifest_source_max_generated_at_ms > 0:
            info.append(f"dataset_source_max_generated_at_ms={manifest_source_max_generated_at_ms}")
        if manifest_reason_feedback_max_event_time_ms > 0:
            info.append(f"dataset_reason_feedback_max_event_time_ms={manifest_reason_feedback_max_event_time_ms}")
        _max_age_check(
            label="dataset_artifact",
            timestamp_ms=manifest_generated_at_ms,
            max_age_hours=float(args.max_dataset_artifact_age_hours),
            now_ms=now_ms,
            failures=failures,
            info=info,
        )
        _max_age_check(
            label="dataset_source",
            timestamp_ms=manifest_source_max_generated_at_ms,
            max_age_hours=float(args.max_dataset_source_age_hours),
            now_ms=now_ms,
            failures=failures,
            info=info,
        )

    needs_reason_feedback_summary = (
        float(args.max_reason_feedback_artifact_age_hours) > 0.0
        or float(args.max_reason_feedback_source_age_hours) > 0.0
    )
    if needs_reason_feedback_summary:
        summary_path = Path(args.reason_feedback_summary)
        if not summary_path.exists():
            failures.append(f"missing reason feedback summary: {summary_path}")
        else:
            summary = json.loads(summary_path.read_text(encoding="utf-8"))
            reason_generated_at_ms = int(summary.get("generatedAtMs") or 0)
            reason_source_window = summary.get("sourceEventWindow") or {}
            reason_source_max_event_time_ms = int((reason_source_window.get("maxEventTimeMs")) or 0)
            info.append(f"reason_feedback_generated_at_ms={reason_generated_at_ms}")
            if reason_source_max_event_time_ms > 0:
                info.append(f"reason_feedback_source_max_event_time_ms={reason_source_max_event_time_ms}")
            _max_age_check(
                label="reason_feedback_artifact",
                timestamp_ms=reason_generated_at_ms,
                max_age_hours=float(args.max_reason_feedback_artifact_age_hours),
                now_ms=now_ms,
                failures=failures,
                info=info,
            )
            _max_age_check(
                label="reason_feedback_source",
                timestamp_ms=reason_source_max_event_time_ms,
                max_age_hours=float(args.max_reason_feedback_source_age_hours),
                now_ms=now_ms,
                failures=failures,
                info=info,
            )

    if not _has_module("lightgbm"):
        failures.append("python dependency missing: lightgbm")
    if not _has_module("sklearn"):
        failures.append("python dependency missing: scikit-learn")

    if args.mode == "canary":
        metrics_path = Path(args.model_metrics)
        if not metrics_path.exists():
            failures.append(f"missing model metrics: {metrics_path}")
        else:
            metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
            trained_at_ms = int(metrics.get("trained_at_ms") or 0)
            embedded_manifest = metrics.get("dataset_manifest") or {}
            embedded_manifest_generated_at_ms = int(embedded_manifest.get("generatedAtMs") or 0)
            auc = ((metrics.get("val_binary_metrics") or {}).get("auc"))
            if auc is None:
                failures.append("validation AUC missing")
            info.append(f"val_auc={auc}")
            if trained_at_ms > 0:
                info.append(f"model_trained_at_ms={trained_at_ms}")
            if embedded_manifest_generated_at_ms > 0:
                info.append(f"model_dataset_manifest_generated_at_ms={embedded_manifest_generated_at_ms}")
            if manifest_generated_at_ms > 0 and embedded_manifest_generated_at_ms > 0:
                if embedded_manifest_generated_at_ms != manifest_generated_at_ms:
                    failures.append(
                        "model metrics dataset manifest does not match current dataset manifest "
                        f"({embedded_manifest_generated_at_ms} != {manifest_generated_at_ms})"
                    )
            if trained_at_ms > 0 and manifest_generated_at_ms > 0 and trained_at_ms < manifest_generated_at_ms:
                failures.append(
                    "model trained_at_ms precedes dataset manifest generatedAtMs "
                    f"({trained_at_ms} < {manifest_generated_at_ms})"
                )
            required_cohorts = {"cold_start", "sparse_pantry", "budget_constrained"}
            for split_name in ("val_cohort_metrics", "test_cohort_metrics"):
                split_metrics = metrics.get(split_name) or {}
                missing = sorted(required_cohorts - set(split_metrics.keys()))
                if missing:
                    failures.append(f"{split_name} missing cohort metrics: {', '.join(missing)}")
            test_cohort_metrics = metrics.get("test_cohort_metrics") or {}
            for cohort_name in sorted(required_cohorts):
                cohort_metrics = test_cohort_metrics.get(cohort_name) or {}
                request_count = int(cohort_metrics.get("request_count") or 0)
                info.append(f"test_{cohort_name}_requests={request_count}")
                if request_count <= 0:
                    failures.append(f"test cohort {cohort_name} has no requests")
                    continue
                uplift = cohort_metrics.get("uplift_vs_baseline") or {}
                ndcg_uplift = uplift.get("ndcg@10")
                map_uplift = uplift.get("map@10")
                info.append(f"test_{cohort_name}_uplift_ndcg10={ndcg_uplift}")
                info.append(f"test_{cohort_name}_uplift_map10={map_uplift}")
                if ndcg_uplift is None or map_uplift is None:
                    failures.append(f"test cohort {cohort_name} missing uplift metrics")
                    continue
                if float(ndcg_uplift) < 0 or float(map_uplift) < 0:
                    failures.append(
                        f"test cohort {cohort_name} regressed vs baseline: "
                        f"ndcg@10={ndcg_uplift}, map@10={map_uplift}"
                    )
            val_cohort_metrics = metrics.get("val_cohort_metrics") or {}
            for cohort_name in sorted(required_cohorts):
                uplift = ((val_cohort_metrics.get(cohort_name) or {}).get("uplift_vs_baseline") or {})
                if uplift:
                    info.append(f"val_{cohort_name}_uplift_ndcg10={uplift.get('ndcg@10')}")
                    info.append(f"val_{cohort_name}_uplift_map10={uplift.get('map@10')}")

    if failures:
        print("ML READINESS CHECK FAILED")
        for item in failures:
            print(f"- {item}")
        for item in info:
            print(f"info: {item}")
        return 1

    print("ML READINESS CHECK PASSED")
    for item in info:
        print(f"info: {item}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
