#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _safe_count(mapping: Dict[str, Any], key: str) -> int:
    try:
        return int(mapping.get(key) or 0)
    except Exception:
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Fail CI if reason-tag distribution quality drops below thresholds.")
    parser.add_argument(
        "--summary",
        default="ml/offline_training/artifacts/reason_feedback_v1/reason_feedback_summary.json",
        help="Path to reason_feedback_summary.json",
    )
    parser.add_argument("--min-rows", type=int, default=200)
    parser.add_argument("--min-unique-users", type=int, default=3)
    parser.add_argument("--min-unique-primary-tags", type=int, default=5)
    parser.add_argument("--max-dominant-primary-share", type=float, default=0.80)
    parser.add_argument("--max-event-imbalance-ratio", type=float, default=4.0)
    parser.add_argument(
        "--output",
        default="benchmarks/reports/reason_tag_distribution_gate_report.json",
        help="JSON report output path",
    )
    args = parser.parse_args()

    summary_path = Path(args.summary)
    if not summary_path.exists():
        print(f"REASON TAG GATE FAILED: missing summary file: {summary_path}")
        return 2

    summary = _load_json(summary_path)
    rows = int(summary.get("rows") or 0)
    unique_users = int(summary.get("uniqueUsers") or 0)
    primary_counts = summary.get("primaryTagCounts") if isinstance(summary.get("primaryTagCounts"), dict) else {}
    event_counts = summary.get("eventCounts") if isinstance(summary.get("eventCounts"), dict) else {}

    failures: list[str] = []

    if rows < int(args.min_rows):
        failures.append(f"rows_below_threshold:{rows}<{int(args.min_rows)}")
    if unique_users < int(args.min_unique_users):
        failures.append(f"unique_users_below_threshold:{unique_users}<{int(args.min_unique_users)}")

    unique_primary_tags = len([k for k, v in primary_counts.items() if int(v or 0) > 0])
    if unique_primary_tags < int(args.min_unique_primary_tags):
        failures.append(
            f"unique_primary_tags_below_threshold:{unique_primary_tags}<{int(args.min_unique_primary_tags)}"
        )

    dominant_primary_share = 1.0
    if rows > 0 and primary_counts:
        dominant_primary_share = max(int(v or 0) for v in primary_counts.values()) / float(rows)
    if dominant_primary_share > float(args.max_dominant_primary_share):
        failures.append(
            "dominant_primary_share_above_threshold:"
            f"{dominant_primary_share:.4f}>{float(args.max_dominant_primary_share):.4f}"
        )

    replace_count = _safe_count(event_counts, "why_replaced_submitted")
    skip_count = _safe_count(event_counts, "why_skipped_submitted")
    if replace_count <= 0 or skip_count <= 0:
        failures.append("missing_required_reason_event_type")
        imbalance_ratio = None
    else:
        imbalance_ratio = max(replace_count, skip_count) / float(min(replace_count, skip_count))
        if imbalance_ratio > float(args.max_event_imbalance_ratio):
            failures.append(
                "event_imbalance_ratio_above_threshold:"
                f"{imbalance_ratio:.4f}>{float(args.max_event_imbalance_ratio):.4f}"
            )

    report = {
        "status": "failed" if failures else "passed",
        "summaryPath": str(summary_path),
        "observed": {
            "rows": rows,
            "uniqueUsers": unique_users,
            "uniquePrimaryTags": unique_primary_tags,
            "dominantPrimaryShare": dominant_primary_share,
            "replaceCount": replace_count,
            "skipCount": skip_count,
            "eventImbalanceRatio": imbalance_ratio,
        },
        "thresholds": {
            "minRows": int(args.min_rows),
            "minUniqueUsers": int(args.min_unique_users),
            "minUniquePrimaryTags": int(args.min_unique_primary_tags),
            "maxDominantPrimaryShare": float(args.max_dominant_primary_share),
            "maxEventImbalanceRatio": float(args.max_event_imbalance_ratio),
        },
        "failures": failures,
    }
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")

    if failures:
        print("REASON TAG GATE FAILED")
        for item in failures:
            print(f"- {item}")
        return 1

    print("REASON TAG GATE PASSED")
    print(
        f"rows={rows} unique_users={unique_users} unique_primary_tags={unique_primary_tags} "
        f"dominant_share={dominant_primary_share:.4f}"
    )
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
