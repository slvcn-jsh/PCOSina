#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
import os
from pathlib import Path
from typing import Any, Dict
from urllib import error as url_error
from urllib import request as url_request

import sys

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT / "backend") not in sys.path:
    sys.path.insert(0, str(ROOT / "backend"))

import policy_store  # type: ignore
from canary_guard import evaluate_canary_metrics, extract_rollback_thresholds


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")


def _resolve_webhook_url(cli_value: str, env_key: str) -> str:
    if cli_value.strip():
        return cli_value.strip()
    return os.getenv(env_key, "").strip()


def _send_alert(url: str, payload: Dict[str, Any], timeout_seconds: float) -> tuple[bool, str]:
    body = json.dumps(payload, sort_keys=True).encode("utf-8")
    req = url_request.Request(
        url=url,
        data=body,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    try:
        with url_request.urlopen(req, timeout=max(1.0, float(timeout_seconds))) as resp:
            status = getattr(resp, "status", None) or resp.getcode()
            if int(status) >= 400:
                return False, f"http_status_{status}"
            return True, ""
    except url_error.HTTPError as exc:
        return False, f"http_error_{exc.code}"
    except url_error.URLError as exc:
        return False, f"url_error_{exc.reason}"
    except Exception as exc:
        return False, f"alert_send_failed_{exc}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Monitor canary guardrails and optionally auto-rollback policy.")
    parser.add_argument(
        "--metrics",
        default="benchmarks/reports/go_live_metrics.json",
        help="Path to metrics JSON used for canary guard checks.",
    )
    parser.add_argument(
        "--policy-id",
        default="",
        help="Specific policy id to evaluate thresholds from (default: active policy).",
    )
    parser.add_argument("--actor", default="canary_guard", help="Audit actor for rollback actions.")
    parser.add_argument("--auto-rollback", action="store_true", help="Trigger policy rollback when a breach is found.")
    parser.add_argument(
        "--alert-webhook-url",
        default="",
        help="Alert webhook URL for breach notifications (fallback to --alert-webhook-env).",
    )
    parser.add_argument(
        "--alert-webhook-env",
        default="PCOSINA_ALERT_WEBHOOK_URL",
        help="Environment variable key used when --alert-webhook-url is not provided.",
    )
    parser.add_argument(
        "--alert-timeout-seconds",
        type=float,
        default=5.0,
        help="Timeout in seconds for webhook alert delivery.",
    )
    parser.add_argument(
        "--alert-on-ok",
        action="store_true",
        help="Also send webhook alerts when guard status is OK.",
    )
    parser.add_argument(
        "--output",
        default="benchmarks/reports/canary_guard_report.json",
        help="Report output path.",
    )
    args = parser.parse_args()

    metrics_path = Path(args.metrics)
    if not metrics_path.exists():
        print(f"missing metrics file: {metrics_path}")
        return 2

    metrics = _load_json(metrics_path)

    policy_store.init_policy_store()
    policy_store.ensure_default_policy(actor=args.actor)
    policy_record = (
        policy_store.get_policy(args.policy_id)
        if args.policy_id.strip()
        else policy_store.get_active_policy()
    )
    if not policy_record:
        print("no active policy found")
        return 2

    policy_payload = policy_record.get("policy") or {}
    thresholds = extract_rollback_thresholds(policy_payload)
    breaches = evaluate_canary_metrics(metrics=metrics, thresholds=thresholds)

    rollback_result: Dict[str, Any] | None = None
    rollback_error = ""
    if breaches and args.auto_rollback:
        notes = f"auto rollback by canary guard ({len(breaches)} breaches)"
        try:
            rollback_result = policy_store.rollback_policy(actor=args.actor, notes=notes)
        except Exception as exc:
            rollback_error = str(exc)

    alert_url = _resolve_webhook_url(args.alert_webhook_url, args.alert_webhook_env)
    should_alert = bool(alert_url) and (bool(breaches) or bool(args.alert_on_ok))
    alert_ok = True
    alert_error = ""
    if should_alert:
        alert_payload = {
            "source": "pcosina_canary_guard",
            "status": "breach" if breaches else "ok",
            "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
            "policyId": policy_record.get("id"),
            "policyVersionNumber": policy_record.get("version_number"),
            "breachCount": len(breaches),
            "breaches": breaches,
            "autoRollbackRequested": bool(args.auto_rollback),
            "autoRollbackPerformed": rollback_result is not None,
            "autoRollbackError": rollback_error or None,
            "rollbackPolicyId": rollback_result.get("id") if rollback_result else None,
        }
        alert_ok, alert_error = _send_alert(
            url=alert_url,
            payload=alert_payload,
            timeout_seconds=args.alert_timeout_seconds,
        )

    report = {
        "status": "breach" if breaches else "ok",
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "policyId": policy_record.get("id"),
        "policyVersionNumber": policy_record.get("version_number"),
        "thresholds": thresholds,
        "metricsPath": str(metrics_path),
        "breaches": breaches,
        "autoRollbackRequested": bool(args.auto_rollback),
        "autoRollbackPerformed": rollback_result is not None,
        "autoRollbackError": rollback_error or None,
        "rollbackResult": {
            "policyId": rollback_result.get("id"),
            "versionNumber": rollback_result.get("version_number"),
        }
        if rollback_result
        else None,
        "alertWebhookConfigured": bool(alert_url),
        "alertAttempted": should_alert,
        "alertDeliveryOk": alert_ok if should_alert else None,
        "alertDeliveryError": alert_error if should_alert and not alert_ok else None,
    }
    output_path = Path(args.output)
    _write_json(output_path, report)

    if breaches:
        print("CANARY GUARD BREACH DETECTED")
        for item in breaches:
            print(
                f"- {item.get('metric')}: observed={item.get('observed')} "
                f"threshold={item.get('threshold')} reason={item.get('reason')}"
            )
        if rollback_result is not None:
            print(f"AUTO ROLLBACK ACTIVATED -> policy {rollback_result.get('id')}")
        elif rollback_error:
            print(f"AUTO ROLLBACK FAILED -> {rollback_error}")
        if should_alert:
            if alert_ok:
                print("ALERT SENT")
            else:
                print(f"ALERT FAILED -> {alert_error}")
        return 1

    if should_alert:
        if alert_ok:
            print("ALERT SENT")
        else:
            print(f"ALERT FAILED -> {alert_error}")
    print("CANARY GUARD OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
