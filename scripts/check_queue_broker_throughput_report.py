#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _as_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except Exception:
        return int(default)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate queue broker throughput/load report.")
    parser.add_argument("--report", required=True, help="Path to queue broker throughput report JSON.")
    parser.add_argument("--backend", choices=["memory", "redis"], default="", help="Optional expected backend.")
    parser.add_argument("--min-jobs", type=int, default=1)
    parser.add_argument("--output", default="", help="Optional path to write validation report JSON.")
    args = parser.parse_args()

    report_path = Path(args.report)
    if not report_path.exists():
        print(f"missing report: {report_path}")
        return 2

    report = _load_json(report_path)
    failures: List[str] = []

    backend = str(report.get("backend") or "").strip().lower()
    if args.backend and backend != str(args.backend).strip().lower():
        failures.append(f"backend_mismatch_expected_{args.backend}_got_{backend or 'none'}")

    jobs = _as_int(report.get("jobs"), default=-1)
    if jobs < max(1, int(args.min_jobs)):
        failures.append(f"jobs_below_minimum_{jobs}")

    publish = report.get("publish") if isinstance(report.get("publish"), dict) else {}
    consume = report.get("consume") if isinstance(report.get("consume"), dict) else {}
    publish_ok = _as_int(publish.get("okCount"), default=-1)
    unique_pop = _as_int(consume.get("uniquePopCount"), default=-1)
    success = bool(report.get("success"))

    if publish_ok < jobs:
        failures.append("publish_okcount_below_jobs")
    if unique_pop < jobs:
        failures.append("consume_unique_popcount_below_jobs")
    if not success:
        failures.append("probe_success_false")

    validation = {
        "status": "ok" if not failures else "failed",
        "reportPath": str(report_path),
        "backend": backend or None,
        "failures": failures,
        "checks": {
            "jobs": jobs,
            "success": success,
            "publishOkCount": publish_ok,
            "uniquePopCount": unique_pop,
        },
    }

    if args.output:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(validation, indent=2, sort_keys=True), encoding="utf-8")

    if failures:
        print("QUEUE BROKER THROUGHPUT REPORT CHECK FAILED")
        for item in failures:
            print(f"- {item}")
        return 1

    print("QUEUE BROKER THROUGHPUT REPORT CHECK PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
