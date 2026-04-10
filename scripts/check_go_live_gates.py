#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
from pathlib import Path

GATES = {
    "hard_violation_rate": lambda v: float(v) == 0.0,
    "latency_p50_ms": lambda v: float(v) <= 2500,
    "latency_p95_ms": lambda v: float(v) <= 7000,
    "latency_p99_ms": lambda v: float(v) <= 12000,
    "offline_continuity_suite": lambda v: str(v).lower() == "pass",
    "reinstall_recovery_suite": lambda v: str(v).lower() == "pass",
    "backup_restore_suite": lambda v: str(v).lower() == "pass",
    "api_error_rate": lambda v: float(v) <= 0.01,
    "planner_timeout_rate": lambda v: float(v) <= 0.03,
    "crash_free_sessions": lambda v: float(v) >= 0.995,
    "admin_audit_logging_test": lambda v: str(v).lower() == "pass",
    "policy_validation_rejects_unsafe": lambda v: str(v).lower() == "pass",
    "secrets_rotation_check": lambda v: str(v).lower() == "pass",
}


def main() -> int:
    parser = argparse.ArgumentParser(description="Check PCOSINA go-live gates")
    parser.add_argument("--metrics", default="benchmarks/reports/go_live_metrics.json", help="Path to metrics JSON")
    args = parser.parse_args()

    metrics_path = Path(args.metrics)
    if not metrics_path.exists():
        print(f"missing metrics file: {metrics_path}")
        return 2

    metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
    failed = []
    for gate, validator in GATES.items():
        if gate not in metrics:
            failed.append((gate, "missing"))
            continue
        value = metrics.get(gate)
        try:
            ok = validator(value)
        except Exception as exc:
            failed.append((gate, f"invalid ({exc})"))
            continue
        if not ok:
            failed.append((gate, value))

    if failed:
        print("GO-LIVE GATE CHECK FAILED")
        for gate, value in failed:
            print(f"- {gate}: {value}")
        return 1

    print("GO-LIVE GATE CHECK PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
