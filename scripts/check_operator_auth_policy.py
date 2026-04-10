#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List


def _env_flag(name: str, *, default: bool) -> bool:
    raw = os.getenv(name, "").strip().lower()
    if raw in ("1", "true", "yes", "on"):
        return True
    if raw in ("0", "false", "no", "off"):
        return False
    return bool(default)


def _env_csv(name: str) -> List[str]:
    raw = str(os.getenv(name, "") or "")
    return [item.strip() for item in raw.split(",") if item.strip()]


def _write_json(path: Path, payload: Dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Check PCOSINA operator auth policy posture.")
    parser.add_argument("--environment", default=os.getenv("PCOSINA_ENV", "development"))
    parser.add_argument("--output", default="benchmarks/reports/operator_auth_policy_check.json")
    parser.add_argument("--max-admin-auth-age-seconds", type=int, default=1800)
    parser.add_argument("--max-admin-session-idle-timeout-seconds", type=int, default=3600)
    parser.add_argument("--max-admin-active-sessions-per-uid", type=int, default=5)
    args = parser.parse_args()

    environment = str(args.environment or "development").strip().lower()
    is_production = environment in ("prod", "production")
    require_verified_email = _env_flag("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL", default=is_production)
    require_operator_mfa = _env_flag("PCOSINA_REQUIRE_OPERATOR_MFA", default=is_production)
    require_recent_admin_auth = _env_flag("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", default=is_production)
    admin_session_secret = str(os.getenv("PCOSINA_ADMIN_SESSION_SECRET", "") or "").strip()
    try:
        admin_max_auth_age_seconds = max(60, int(str(os.getenv("PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS", "900") or "900").strip()))
    except Exception:
        admin_max_auth_age_seconds = 900
    try:
        default_idle = "1800" if is_production else "0"
        admin_session_idle_timeout_seconds = max(
            0,
            int(str(os.getenv("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS", default_idle) or default_idle).strip()),
        )
    except Exception:
        admin_session_idle_timeout_seconds = 1800 if is_production else 0
    try:
        default_active_session_cap = "3" if is_production else "0"
        admin_max_active_sessions_per_uid = max(
            0,
            int(str(os.getenv("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID", default_active_session_cap) or default_active_session_cap).strip()),
        )
    except Exception:
        admin_max_active_sessions_per_uid = 3 if is_production else 0

    findings: List[str] = []
    warnings: List[str] = []

    email_allowlists = {
        "admin": _env_csv("PCOSINA_ADMIN_EMAILS"),
        "policy_admin": _env_csv("PCOSINA_POLICY_ADMIN_EMAILS"),
        "ops_admin": _env_csv("PCOSINA_OPS_ADMIN_EMAILS"),
        "feedback_admin": _env_csv("PCOSINA_FEEDBACK_ADMIN_EMAILS"),
        "content_admin": _env_csv("PCOSINA_CONTENT_ADMIN_EMAILS"),
    }
    configured_email_allowlists = {role: items for role, items in email_allowlists.items() if items}

    if is_production and not require_verified_email:
        findings.append("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL must be enabled in production.")
    if is_production and not require_operator_mfa:
        findings.append("PCOSINA_REQUIRE_OPERATOR_MFA must be enabled in production.")
    if is_production and not require_recent_admin_auth:
        findings.append("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH must be enabled in production.")
    if is_production and admin_session_idle_timeout_seconds <= 0:
        findings.append("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS must be enabled in production.")
    if is_production and admin_max_active_sessions_per_uid <= 0:
        findings.append("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID must be enabled in production.")
    if is_production and not admin_session_secret:
        findings.append("PCOSINA_ADMIN_SESSION_SECRET must be configured in production.")
    if require_recent_admin_auth and admin_max_auth_age_seconds > int(args.max_admin_auth_age_seconds):
        findings.append(
            f"PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS={admin_max_auth_age_seconds} exceeds the policy limit of {int(args.max_admin_auth_age_seconds)} seconds."
        )
    if admin_session_idle_timeout_seconds > int(args.max_admin_session_idle_timeout_seconds):
        findings.append(
            f"PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS={admin_session_idle_timeout_seconds} exceeds the policy limit of {int(args.max_admin_session_idle_timeout_seconds)} seconds."
        )
    if admin_max_active_sessions_per_uid > int(args.max_admin_active_sessions_per_uid):
        findings.append(
            f"PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID={admin_max_active_sessions_per_uid} exceeds the policy limit of {int(args.max_admin_active_sessions_per_uid)} sessions."
        )
    if configured_email_allowlists and not require_verified_email:
        findings.append("Email-based operator allowlists require PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL=true.")

    if not configured_email_allowlists:
        warnings.append("No email-based operator allowlists are configured; operator access is expected to rely on UID allowlists and/or Firebase custom claims.")

    payload: Dict[str, object] = {
        "status": "ok" if not findings else "failed",
        "environment": environment,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "checks": {
            "requireVerifiedOperatorEmail": require_verified_email,
            "requireOperatorMfa": require_operator_mfa,
            "requireRecentAdminAuth": require_recent_admin_auth,
            "adminMaxAuthAgeSeconds": admin_max_auth_age_seconds,
            "adminSessionIdleTimeoutSeconds": admin_session_idle_timeout_seconds,
            "adminMaxActiveSessionsPerUid": admin_max_active_sessions_per_uid,
            "hasAdminSessionSecret": bool(admin_session_secret),
        },
        "configuredEmailAllowlists": {role: len(items) for role, items in configured_email_allowlists.items()},
        "findings": findings,
        "warnings": warnings,
    }

    _write_json(Path(args.output), payload)
    if payload["status"] != "ok":
        print("OPERATOR AUTH POLICY CHECK FAILED")
        for entry in findings:
            print(f"- {entry}")
        return 1

    print("OPERATOR AUTH POLICY CHECK PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
