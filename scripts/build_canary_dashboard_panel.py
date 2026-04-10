#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict
from urllib import error as url_error
from urllib import request as url_request


def _load_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _post_json(url: str, payload: Dict[str, Any], timeout_seconds: float) -> tuple[bool, str]:
    body = json.dumps(payload, sort_keys=True).encode("utf-8")
    req = url_request.Request(
        url=url,
        method="POST",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    try:
        with url_request.urlopen(req, timeout=max(1.0, float(timeout_seconds))) as resp:
            status = int(getattr(resp, "status", 200) or 200)
            if status >= 400:
                return False, f"http_status_{status}"
            return True, ""
    except url_error.HTTPError as exc:
        return False, f"http_error_{exc.code}"
    except url_error.URLError as exc:
        return False, f"url_error_{exc.reason}"
    except Exception as exc:
        return False, f"post_failed_{exc}"


def _to_number(value: Any) -> float | None:
    try:
        return float(value)
    except Exception:
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Build canary guard dashboard panel payload.")
    parser.add_argument(
        "--guard-report",
        default="benchmarks/reports/canary_guard_report.json",
        help="Path to canary guard report JSON.",
    )
    parser.add_argument(
        "--metrics",
        default="benchmarks/reports/go_live_metrics.json",
        help="Path to go-live metrics JSON.",
    )
    parser.add_argument(
        "--output",
        default="benchmarks/reports/ops_dashboard_canary_panel.json",
        help="Panel payload output path.",
    )
    parser.add_argument(
        "--webhook-url",
        default="",
        help="Optional dashboard ingest webhook URL.",
    )
    parser.add_argument(
        "--webhook-env",
        default="PCOSINA_DASHBOARD_WEBHOOK_URL",
        help="Env var key used when --webhook-url is not provided.",
    )
    parser.add_argument("--timeout-seconds", type=float, default=5.0)
    args = parser.parse_args()

    guard_path = Path(args.guard_report)
    if not guard_path.exists():
        print(f"missing guard report: {guard_path}")
        return 2
    guard = _load_json(guard_path)

    metrics_path = Path(args.metrics)
    metrics: Dict[str, Any] = {}
    if metrics_path.exists():
        metrics = _load_json(metrics_path)

    hard_v = _to_number(metrics.get("hard_violation_rate"))
    latency_p95 = _to_number(metrics.get("latency_p95_ms"))
    api_error = _to_number(metrics.get("api_error_rate"))

    panel = {
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "source": "pcosina_canary_guard",
        "status": str(guard.get("status") or "unknown"),
        "policyId": guard.get("policyId"),
        "policyVersionNumber": guard.get("policyVersionNumber"),
        "breachCount": len(guard.get("breaches") or []),
        "breaches": guard.get("breaches") or [],
        "kpis": {
            "hard_violation_rate": hard_v,
            "latency_p95_ms": latency_p95,
            "api_error_rate": api_error,
        },
        "thresholds": guard.get("thresholds") or {},
    }

    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(panel, indent=2, sort_keys=True), encoding="utf-8")

    webhook_url = str(args.webhook_url or "").strip() or os.getenv(args.webhook_env, "").strip()
    if webhook_url:
        ok, err = _post_json(webhook_url, panel, args.timeout_seconds)
        if ok:
            print("CANARY DASHBOARD PANEL SENT")
        else:
            print(f"CANARY DASHBOARD PANEL SEND FAILED: {err}")
            return 1

    print(f"CANARY DASHBOARD PANEL WRITTEN: {out_path}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
