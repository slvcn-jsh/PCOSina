#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List


ROOT = Path(__file__).resolve().parents[1]


def _now_slug() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _run(cmd: List[str], env: Dict[str, str]) -> Dict[str, Any]:
    proc = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True, check=False, env=env)
    return {
        "cmd": cmd,
        "exitCode": int(proc.returncode),
        "stdout": proc.stdout,
        "stderr": proc.stderr,
    }


def _resolve_env_key(kind: str, environment: str) -> str:
    suffix = "PRODUCTION" if environment == "production" else "STAGING"
    if kind == "alert":
        return f"PCOSINA_ALERT_WEBHOOK_URL_{suffix}"
    return f"PCOSINA_DASHBOARD_WEBHOOK_URL_{suffix}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Run a full canary drill and write receipt evidence.")
    parser.add_argument("--environment", choices=["staging", "production"], default="staging")
    parser.add_argument("--metrics", default="benchmarks/reports/go_live_metrics.json")
    parser.add_argument("--simulate-breach", action="store_true")
    parser.add_argument("--expect-breach", action="store_true")
    parser.add_argument("--auto-rollback", action="store_true")
    parser.add_argument(
        "--alert-on-ok",
        action="store_true",
        help="Forward to guard monitor so alert webhook is also exercised on non-breach runs.",
    )
    parser.add_argument("--alert-webhook-url", default="")
    parser.add_argument("--dashboard-webhook-url", default="")
    parser.add_argument("--artifacts-dir", default="benchmarks/reports")
    parser.add_argument("--output", default="")
    parser.add_argument(
        "--require-webhooks",
        action="store_true",
        help="Fail early when environment-scoped webhook destinations are not configured.",
    )
    args = parser.parse_args()

    reports_dir = Path(args.artifacts_dir)
    if not reports_dir.is_absolute():
        reports_dir = ROOT / reports_dir
    reports_dir.mkdir(parents=True, exist_ok=True)
    stamp = _now_slug()

    metrics_path = Path(args.metrics)
    if not metrics_path.exists():
        fallback = ROOT / "benchmarks" / "reports" / "go_live_metrics.example.json"
        if fallback.exists():
            metrics_path = fallback
        else:
            print(f"missing metrics file: {metrics_path}")
            return 2

    run_env = dict(os.environ)
    alert_url = str(args.alert_webhook_url or "").strip() or run_env.get(_resolve_env_key("alert", args.environment), "").strip()
    dashboard_url = str(args.dashboard_webhook_url or "").strip() or run_env.get(
        _resolve_env_key("dashboard", args.environment), ""
    ).strip()
    if alert_url:
        run_env["PCOSINA_ALERT_WEBHOOK_URL"] = alert_url
    if dashboard_url:
        run_env["PCOSINA_DASHBOARD_WEBHOOK_URL"] = dashboard_url
    if args.require_webhooks and (not alert_url or not dashboard_url):
        missing = []
        if not alert_url:
            missing.append(_resolve_env_key("alert", args.environment))
        if not dashboard_url:
            missing.append(_resolve_env_key("dashboard", args.environment))
        receipt = {
            "status": "failed",
            "environment": args.environment,
            "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
            "reason": "missing_required_webhooks",
            "missingWebhookKeys": missing,
            "steps": [],
        }
        output = Path(args.output) if args.output else reports_dir / f"canary_drill_receipt.{args.environment}.{stamp}.json"
        output.write_text(json.dumps(receipt, indent=2, sort_keys=True), encoding="utf-8")
        print(f"CANARY DRILL FAILED. Receipt: {output}")
        return 1

    effective_metrics = metrics_path
    steps: list[Dict[str, Any]] = []
    generated: Dict[str, str] = {}

    if args.simulate_breach:
        breached_metrics = reports_dir / f"go_live_metrics.drill.{args.environment}.{stamp}.json"
        step = _run(
            [
                sys.executable,
                "scripts/simulate_canary_breach_metrics.py",
                "--input",
                str(metrics_path),
                "--output",
                str(breached_metrics),
                "--scenario",
                "all",
            ],
            env=run_env,
        )
        steps.append({"name": "simulate_breach_metrics", **step})
        if step["exitCode"] != 0:
            return_code = 1
            receipt = {
                "status": "failed",
                "environment": args.environment,
                "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
                "reason": "simulate_breach_failed",
                "steps": steps,
            }
            output = Path(args.output) if args.output else reports_dir / f"canary_drill_receipt.{args.environment}.{stamp}.json"
            output.write_text(json.dumps(receipt, indent=2, sort_keys=True), encoding="utf-8")
            print(f"CANARY DRILL FAILED. Receipt: {output}")
            return return_code
        effective_metrics = breached_metrics
        generated["breachMetrics"] = str(breached_metrics)

    guard_report = reports_dir / f"canary_guard_report.{args.environment}.{stamp}.json"
    guard_cmd = [
        sys.executable,
        "scripts/monitor_canary_guardrails.py",
        "--metrics",
        str(effective_metrics),
        "--output",
        str(guard_report),
        "--alert-webhook-env",
        "PCOSINA_ALERT_WEBHOOK_URL",
    ]
    if args.alert_on_ok:
        guard_cmd.append("--alert-on-ok")
    if args.auto_rollback:
        guard_cmd.extend(["--auto-rollback", "--actor", f"canary_drill_{args.environment}"])

    guard_step = _run(guard_cmd, env=run_env)
    steps.append({"name": "canary_guard_eval", **guard_step})
    generated["guardReport"] = str(guard_report)

    dashboard_panel = reports_dir / f"ops_dashboard_canary_panel.{args.environment}.{stamp}.json"
    panel_step = _run(
        [
            sys.executable,
            "scripts/build_canary_dashboard_panel.py",
            "--guard-report",
            str(guard_report),
            "--metrics",
            str(effective_metrics),
            "--output",
            str(dashboard_panel),
            "--webhook-env",
            "PCOSINA_DASHBOARD_WEBHOOK_URL",
        ],
        env=run_env,
    )
    steps.append({"name": "dashboard_panel_publish", **panel_step})
    generated["dashboardPanel"] = str(dashboard_panel)

    guard_payload = _load_json(guard_report) if guard_report.exists() else {}
    panel_payload = _load_json(dashboard_panel) if dashboard_panel.exists() else {}

    guard_exit = int(guard_step["exitCode"])
    if args.expect_breach:
        guard_ok = guard_exit in (0, 1)
    else:
        guard_ok = guard_exit == 0
    panel_ok = int(panel_step["exitCode"]) == 0

    receipt = {
        "status": "ok" if guard_ok and panel_ok else "failed",
        "environment": args.environment,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "metricsUsed": str(effective_metrics),
        "webhooks": {
            "alertConfigured": bool(alert_url),
            "dashboardConfigured": bool(dashboard_url),
            "alertEnvKey": _resolve_env_key("alert", args.environment),
            "dashboardEnvKey": _resolve_env_key("dashboard", args.environment),
        },
        "generatedArtifacts": generated,
        "guard": {
            "exitCode": guard_exit,
            "status": guard_payload.get("status"),
            "breachCount": len(guard_payload.get("breaches") or []),
            "alertAttempted": guard_payload.get("alertAttempted"),
            "alertDeliveryOk": guard_payload.get("alertDeliveryOk"),
            "alertDeliveryError": guard_payload.get("alertDeliveryError"),
            "autoRollbackPerformed": guard_payload.get("autoRollbackPerformed"),
        },
        "dashboard": {
            "exitCode": int(panel_step["exitCode"]),
            "status": panel_payload.get("status"),
            "breachCount": panel_payload.get("breachCount"),
        },
        "steps": steps,
    }

    output_path = Path(args.output) if args.output else reports_dir / f"canary_drill_receipt.{args.environment}.{stamp}.json"
    output_path.write_text(json.dumps(receipt, indent=2, sort_keys=True), encoding="utf-8")
    print(f"CANARY DRILL RECEIPT WRITTEN: {output_path}")

    return 0 if receipt["status"] == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
