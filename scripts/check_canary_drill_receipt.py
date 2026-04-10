#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _as_bool(value: Any) -> bool:
    return bool(value)


def _as_int(value: Any, default: int = -1) -> int:
    try:
        return int(value)
    except Exception:
        return int(default)


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate canary drill receipt contract.")
    parser.add_argument("--receipt", required=True, help="Path to canary drill receipt JSON.")
    parser.add_argument("--environment", choices=["staging", "production"], default="")
    parser.add_argument(
        "--require-webhook-delivery",
        action="store_true",
        help="Fail when configured webhook delivery was attempted but not successful.",
    )
    parser.add_argument("--output", default="", help="Optional path to write validation report JSON.")
    args = parser.parse_args()

    receipt_path = Path(args.receipt)
    if not receipt_path.exists():
        print(f"missing receipt: {receipt_path}")
        return 2
    receipt = _load_json(receipt_path)
    failures: List[str] = []

    if str(receipt.get("status") or "").strip().lower() != "ok":
        failures.append("receipt_status_not_ok")

    expected_env = str(args.environment or "").strip().lower()
    actual_env = str(receipt.get("environment") or "").strip().lower()
    if expected_env and actual_env != expected_env:
        failures.append(f"environment_mismatch_expected_{expected_env}_got_{actual_env or 'none'}")

    guard = receipt.get("guard") if isinstance(receipt.get("guard"), dict) else {}
    dashboard = receipt.get("dashboard") if isinstance(receipt.get("dashboard"), dict) else {}
    artifacts = receipt.get("generatedArtifacts") if isinstance(receipt.get("generatedArtifacts"), dict) else {}
    webhooks = receipt.get("webhooks") if isinstance(receipt.get("webhooks"), dict) else {}

    guard_exit = _as_int(guard.get("exitCode"), default=-1)
    dashboard_exit = _as_int(dashboard.get("exitCode"), default=-1)
    if guard_exit not in (0, 1):
        failures.append("guard_exitcode_invalid")
    if dashboard_exit != 0:
        failures.append("dashboard_exitcode_not_zero")

    guard_path = Path(str(artifacts.get("guardReport") or ""))
    panel_path = Path(str(artifacts.get("dashboardPanel") or ""))
    if not guard_path.exists():
        failures.append("guard_report_missing")
    if not panel_path.exists():
        failures.append("dashboard_panel_missing")

    if _as_bool(webhooks.get("alertConfigured")) and args.require_webhook_delivery:
        if not _as_bool(guard.get("alertAttempted")):
            failures.append("alert_configured_but_not_attempted")
        if guard.get("alertDeliveryOk") is not True:
            failures.append("alert_delivery_not_ok")

    validation = {
        "status": "ok" if not failures else "failed",
        "receiptPath": str(receipt_path),
        "environment": actual_env or None,
        "failures": failures,
        "checks": {
            "receiptStatusOk": str(receipt.get("status") or "").strip().lower() == "ok",
            "guardExitCode": guard_exit,
            "dashboardExitCode": dashboard_exit,
            "guardReportExists": guard_path.exists(),
            "dashboardPanelExists": panel_path.exists(),
            "alertConfigured": _as_bool(webhooks.get("alertConfigured")),
            "alertAttempted": _as_bool(guard.get("alertAttempted")),
            "alertDeliveryOk": guard.get("alertDeliveryOk"),
        },
    }

    if args.output:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(validation, indent=2, sort_keys=True), encoding="utf-8")

    if failures:
        print("CANARY DRILL RECEIPT CHECK FAILED")
        for item in failures:
            print(f"- {item}")
        return 1

    print("CANARY DRILL RECEIPT CHECK PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
