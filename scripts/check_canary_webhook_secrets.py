#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from urllib.parse import urlparse


def _key(name: str, environment: str) -> str:
    suffix = "PRODUCTION" if environment == "production" else "STAGING"
    return f"PCOSINA_{name}_WEBHOOK_URL_{suffix}"


def _validate_webhook_url(raw: str, *, allow_http_localhost: bool) -> tuple[bool, str, str]:
    text = str(raw or "").strip()
    if not text:
        return False, "", "missing"
    parsed = urlparse(text)
    scheme = (parsed.scheme or "").lower()
    host = (parsed.hostname or "").strip().lower()
    if not host:
        return False, "", "missing_host"
    # Reject obvious template placeholders so drills fail fast with actionable errors.
    if (
        "<" in text
        or ">" in text
        or "your-" in host
        or "yourdomain.com" in host
        or "example.com" in host
        or host.startswith("real-alert-endpoint.")
        or host.startswith("real-dashboard-endpoint.")
    ):
        return False, host, "placeholder_value"
    if scheme == "https":
        return True, host, ""
    if allow_http_localhost and scheme == "http" and host in ("localhost", "127.0.0.1"):
        return True, host, ""
    return False, host, f"invalid_scheme_{scheme or 'none'}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate staging/production canary webhook env keys.")
    parser.add_argument("--environment", choices=["staging", "production"], required=True)
    parser.add_argument("--output", default="benchmarks/reports/canary_webhook_secrets_check.json")
    parser.add_argument(
        "--allow-http-localhost",
        action="store_true",
        help="Allow http://localhost or http://127.0.0.1 destinations for local drills.",
    )
    args = parser.parse_args()

    alert_key = _key("ALERT", args.environment)
    dashboard_key = _key("DASHBOARD", args.environment)
    alert_value = os.getenv(alert_key, "").strip()
    dashboard_value = os.getenv(dashboard_key, "").strip()
    alert_ok, alert_host, alert_error = _validate_webhook_url(
        alert_value,
        allow_http_localhost=bool(args.allow_http_localhost),
    )
    dashboard_ok, dashboard_host, dashboard_error = _validate_webhook_url(
        dashboard_value,
        allow_http_localhost=bool(args.allow_http_localhost),
    )
    status = "ok" if (alert_ok and dashboard_ok) else "missing_or_invalid"

    payload = {
        "environment": args.environment,
        "alertKey": alert_key,
        "dashboardKey": dashboard_key,
        "alertConfigured": bool(alert_value),
        "dashboardConfigured": bool(dashboard_value),
        "alertValid": bool(alert_ok),
        "dashboardValid": bool(dashboard_ok),
        "alertHost": alert_host or None,
        "dashboardHost": dashboard_host or None,
        "status": status,
        "errors": [
            {"key": alert_key, "reason": alert_error} if alert_error else None,
            {"key": dashboard_key, "reason": dashboard_error} if dashboard_error else None,
        ],
    }
    payload["errors"] = [entry for entry in payload["errors"] if entry]

    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")

    if payload["status"] != "ok":
        print("CANARY WEBHOOK SECRET CHECK FAILED")
        for err in payload["errors"]:
            print(f"- {err['key']}: {err['reason']}")
        return 1

    print("CANARY WEBHOOK SECRET CHECK PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
