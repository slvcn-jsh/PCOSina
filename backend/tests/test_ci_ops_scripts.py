import json
import os
import subprocess
import sys
import threading
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from uuid import uuid4


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SCRIPTS_DIR = PROJECT_ROOT / "scripts"


def _case_dir(name: str) -> Path:
    root = Path(__file__).resolve().parent / ".tmp_ci_ops_scripts"
    root.mkdir(parents=True, exist_ok=True)
    case_dir = root / f"{name}_{uuid4().hex}"
    case_dir.mkdir(parents=True, exist_ok=True)
    return case_dir


def _run(args: list[str], *, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    run_env = dict(os.environ)
    if env:
        run_env.update(env)
    return subprocess.run(
        [sys.executable, *args],
        cwd=str(PROJECT_ROOT),
        capture_output=True,
        text=True,
        check=False,
        env=run_env,
    )


class _WebhookHandler(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"ok":true}')

    def log_message(self, format: str, *args):  # noqa: A003
        return


def _start_webhook_server() -> tuple[ThreadingHTTPServer, str]:
    server = ThreadingHTTPServer(("127.0.0.1", 0), _WebhookHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    host, port = server.server_address
    return server, f"http://{host}:{port}/hook"


def test_reason_tag_distribution_gate_passes_and_writes_report():
    case = _case_dir("reason_pass")
    summary_path = case / "reason_feedback_summary.json"
    report_path = case / "gate_report.json"

    summary = {
        "rows": 420,
        "uniqueUsers": 9,
        "primaryTagCounts": {
            "taste": 90,
            "budget": 80,
            "prep_time": 70,
            "availability": 90,
            "portion": 90,
        },
        "eventCounts": {
            "why_replaced_submitted": 220,
            "why_skipped_submitted": 200,
        },
    }
    summary_path.write_text(json.dumps(summary), encoding="utf-8")

    proc = _run(
        [
            str(SCRIPTS_DIR / "check_reason_tag_distribution.py"),
            "--summary",
            str(summary_path),
            "--output",
            str(report_path),
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["status"] == "passed"
    assert report["failures"] == []


def test_reason_tag_distribution_gate_fails_when_distribution_collapses():
    case = _case_dir("reason_fail")
    summary_path = case / "reason_feedback_summary.json"
    report_path = case / "gate_report.json"

    summary = {
        "rows": 120,
        "uniqueUsers": 1,
        "primaryTagCounts": {
            "taste": 118,
            "budget": 2,
        },
        "eventCounts": {
            "why_replaced_submitted": 118,
            "why_skipped_submitted": 2,
        },
    }
    summary_path.write_text(json.dumps(summary), encoding="utf-8")

    proc = _run(
        [
            str(SCRIPTS_DIR / "check_reason_tag_distribution.py"),
            "--summary",
            str(summary_path),
            "--output",
            str(report_path),
            "--min-rows",
            "200",
            "--min-unique-users",
            "3",
            "--min-unique-primary-tags",
            "5",
            "--max-dominant-primary-share",
            "0.80",
            "--max-event-imbalance-ratio",
            "4.0",
        ]
    )
    assert proc.returncode == 1
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["status"] == "failed"
    assert len(report["failures"]) >= 1


def test_build_canary_dashboard_panel_writes_expected_payload():
    case = _case_dir("panel")
    guard_report = case / "canary_guard_report.json"
    metrics = case / "go_live_metrics.json"
    output = case / "ops_dashboard_canary_panel.json"

    guard_report.write_text(
        json.dumps(
            {
                "status": "breach",
                "policyId": "pol-123",
                "policyVersionNumber": 4,
                "breaches": [{"name": "latency_p95_ms", "actual": 1900, "threshold": 1500}],
                "thresholds": {"latency_p95_ms": 1500},
            }
        ),
        encoding="utf-8",
    )
    metrics.write_text(
        json.dumps(
            {
                "hard_violation_rate": 0.0,
                "latency_p95_ms": 1900,
                "api_error_rate": 0.004,
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPTS_DIR / "build_canary_dashboard_panel.py"),
            "--guard-report",
            str(guard_report),
            "--metrics",
            str(metrics),
            "--output",
            str(output),
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    panel = json.loads(output.read_text(encoding="utf-8"))
    assert panel["status"] == "breach"
    assert panel["policyId"] == "pol-123"
    assert panel["breachCount"] == 1
    assert panel["kpis"]["latency_p95_ms"] == 1900


def test_run_canary_drill_writes_receipt():
    case = _case_dir("drill")
    metrics = case / "go_live_metrics.json"
    receipt = case / "canary_drill_receipt.json"
    metrics.write_text(
        json.dumps(
            {
                "hard_violation_rate": 0.0,
                "latency_p95_ms": 1200,
                "api_error_rate": 0.001,
            }
        ),
        encoding="utf-8",
    )

    server_alert, alert_url = _start_webhook_server()
    server_dash, dash_url = _start_webhook_server()
    try:
        proc = _run(
            [
                str(SCRIPTS_DIR / "run_canary_drill.py"),
                "--environment",
                "staging",
                "--metrics",
                str(metrics),
                "--expect-breach",
                "--artifacts-dir",
                str(case),
                "--require-webhooks",
                "--alert-on-ok",
                "--output",
                str(receipt),
                "--alert-webhook-url",
                alert_url,
                "--dashboard-webhook-url",
                dash_url,
            ],
            env={
                "PCOSINA_ALERT_WEBHOOK_URL_STAGING": alert_url,
                "PCOSINA_DASHBOARD_WEBHOOK_URL_STAGING": dash_url,
            },
        )
    finally:
        server_alert.shutdown()
        server_alert.server_close()
        server_dash.shutdown()
        server_dash.server_close()
    assert proc.returncode == 0, proc.stderr or proc.stdout
    payload = json.loads(receipt.read_text(encoding="utf-8"))
    assert payload["status"] == "ok"
    assert payload["environment"] == "staging"
    assert payload["guard"]["alertAttempted"] is True
    assert payload["guard"]["alertDeliveryOk"] is True
    assert str(payload["generatedArtifacts"]["guardReport"]).endswith(".json")
    assert str(payload["generatedArtifacts"]["dashboardPanel"]).endswith(".json")

    check_report = case / "receipt_check.json"
    check_proc = _run(
        [
            str(SCRIPTS_DIR / "check_canary_drill_receipt.py"),
            "--receipt",
            str(receipt),
            "--environment",
            "staging",
            "--require-webhook-delivery",
            "--output",
            str(check_report),
        ]
    )
    assert check_proc.returncode == 0, check_proc.stderr or check_proc.stdout
    check_payload = json.loads(check_report.read_text(encoding="utf-8"))
    assert check_payload["status"] == "ok"


def test_monitor_canary_guardrails_writes_report_even_when_auto_rollback_unavailable():
    case = _case_dir("guard_autorollback")
    metrics = case / "breach_metrics.json"
    output = case / "guard_report.json"
    metrics.write_text(
        json.dumps(
            {
                "hard_violation_rate": 0.5,
                "latency_p95_ms": 20000,
                "api_error_rate": 0.1,
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPTS_DIR / "monitor_canary_guardrails.py"),
            "--metrics",
            str(metrics),
            "--output",
            str(output),
            "--auto-rollback",
            "--actor",
            "ci-test",
        ]
    )
    # Breach is expected => non-zero exit is correct.
    assert proc.returncode == 1
    report = json.loads(output.read_text(encoding="utf-8"))
    assert report["status"] == "breach"
    assert report["autoRollbackRequested"] is True
    assert "autoRollbackError" in report


def test_queue_broker_throughput_memory_probe():
    case = _case_dir("broker_throughput")
    report = case / "queue_broker_throughput.json"
    proc = _run(
        [
            str(PROJECT_ROOT / "load-tests" / "queue_broker_throughput.py"),
            "--backend",
            "memory",
            "--jobs",
            "200",
            "--workers",
            "4",
            "--output",
            str(report),
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    payload = json.loads(report.read_text(encoding="utf-8"))
    assert payload["backend"] == "memory"
    assert payload["success"] is True
    assert int(payload["consume"]["uniquePopCount"]) == 200


def test_check_queue_broker_throughput_report_validates_external_redis_report():
    case = _case_dir("external_redis_report")
    report = case / "queue_broker_throughput.redis.json"
    check = case / "queue_broker_throughput.redis.check.json"
    report.write_text(
        json.dumps(
            {
                "backend": "redis",
                "jobs": 500,
                "publish": {"okCount": 500},
                "consume": {"uniquePopCount": 500},
                "success": True,
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPTS_DIR / "check_queue_broker_throughput_report.py"),
            "--report",
            str(report),
            "--backend",
            "redis",
            "--output",
            str(check),
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    payload = json.loads(check.read_text(encoding="utf-8"))
    assert payload["status"] == "ok"
    assert payload["backend"] == "redis"
    assert payload["checks"]["uniquePopCount"] == 500


def test_canary_webhook_secret_check_validates_scheme_and_localhost_override():
    case = _case_dir("webhook_secret_check")
    output = case / "secrets_check.json"
    env = {
        "PCOSINA_ALERT_WEBHOOK_URL_STAGING": "http://127.0.0.1:9001/hook",
        "PCOSINA_DASHBOARD_WEBHOOK_URL_STAGING": "https://ops.acme.internal/ingest",
    }
    fail_proc = _run(
        [
            str(SCRIPTS_DIR / "check_canary_webhook_secrets.py"),
            "--environment",
            "staging",
            "--output",
            str(output),
        ],
        env=env,
    )
    assert fail_proc.returncode == 1

    ok_proc = _run(
        [
            str(SCRIPTS_DIR / "check_canary_webhook_secrets.py"),
            "--environment",
            "staging",
            "--allow-http-localhost",
            "--output",
            str(output),
        ],
        env=env,
    )
    assert ok_proc.returncode == 0, ok_proc.stderr or ok_proc.stdout
    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["status"] == "ok"
    assert payload["alertValid"] is True
    assert payload["dashboardValid"] is True


def test_run_rollout_readiness_batch_writes_report():
    case = _case_dir("rollout_batch")
    metrics = case / "go_live_metrics.example.json"
    metrics.write_text(
        json.dumps(
            {
                "hard_violation_rate": 0.0,
                "latency_p95_ms": 1200,
                "api_error_rate": 0.001,
            }
        ),
        encoding="utf-8",
    )
    proc = _run(
        [
            str(SCRIPTS_DIR / "run_rollout_readiness_batch.py"),
            "--environment",
            "staging",
            "--metrics",
            str(metrics),
            "--artifacts-dir",
            str(case),
            "--skip-sync-replay-tests",
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    report_files = sorted(case.glob("rollout_readiness_batch.staging.*.json"))
    assert report_files, "expected rollout readiness report artifact"
    payload = json.loads(report_files[-1].read_text(encoding="utf-8"))
    assert payload["status"] == "ok"
    assert isinstance(payload.get("steps"), list) and len(payload["steps"]) >= 5
    assert any(step.get("name") == "schema_migration_gate" for step in payload["steps"])
    assert any(step.get("name") == "operator_auth_policy_check" for step in payload["steps"])
    bundle_files = sorted(case.glob("release_evidence_bundle.staging.*.json"))
    assert bundle_files, "expected release evidence bundle artifact"
    bundle = json.loads(bundle_files[-1].read_text(encoding="utf-8"))
    assert bundle["status"] == "ok"
    assert bundle["rolloutStatus"] == "ok"
    assert any(item.get("label") == "rolloutReadinessReport" for item in bundle["artifacts"])


def test_run_rollout_readiness_batch_validates_external_live_evidence():
    case = _case_dir("rollout_batch_external_live")
    metrics = case / "go_live_metrics.example.json"
    metrics.write_text(
        json.dumps(
            {
                "hard_violation_rate": 0.0,
                "latency_p95_ms": 1200,
                "api_error_rate": 0.001,
            }
        ),
        encoding="utf-8",
    )

    guard_report = case / "external_guard_report.json"
    dashboard_panel = case / "external_dashboard_panel.json"
    guard_report.write_text(json.dumps({"status": "ok"}), encoding="utf-8")
    dashboard_panel.write_text(json.dumps({"status": "ok"}), encoding="utf-8")

    external_receipt = case / "external_canary_drill_receipt.staging.json"
    external_receipt.write_text(
        json.dumps(
            {
                "status": "ok",
                "environment": "staging",
                "guard": {"exitCode": 0, "alertAttempted": True, "alertDeliveryOk": True},
                "dashboard": {"exitCode": 0},
                "generatedArtifacts": {
                    "guardReport": str(guard_report),
                    "dashboardPanel": str(dashboard_panel),
                },
                "webhooks": {"alertConfigured": True},
            }
        ),
        encoding="utf-8",
    )

    external_redis_report = case / "external_queue_broker_throughput.redis.json"
    external_redis_report.write_text(
        json.dumps(
            {
                "backend": "redis",
                "jobs": 750,
                "publish": {"okCount": 750},
                "consume": {"uniquePopCount": 750},
                "success": True,
            }
        ),
        encoding="utf-8",
    )

    proc = _run(
        [
            str(SCRIPTS_DIR / "run_rollout_readiness_batch.py"),
            "--environment",
            "staging",
            "--metrics",
            str(metrics),
            "--artifacts-dir",
            str(case),
            "--skip-sync-replay-tests",
            "--external-canary-receipt",
            str(external_receipt),
            "--external-queue-throughput-report",
            str(external_redis_report),
            "--require-external-canary-receipt",
            "--require-external-queue-throughput-report",
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout

    report_files = sorted(case.glob("rollout_readiness_batch.staging.*.json"))
    assert report_files
    payload = json.loads(report_files[-1].read_text(encoding="utf-8"))
    assert payload["status"] == "ok"
    assert any(step.get("name") == "external_canary_receipt_contract_check" and step.get("status") == "ok" for step in payload["steps"])
    assert any(step.get("name") == "external_queue_broker_throughput_check" and step.get("status") == "ok" for step in payload["steps"])

    bundle_files = sorted(case.glob("release_evidence_bundle.staging.*.json"))
    assert bundle_files
    bundle = json.loads(bundle_files[-1].read_text(encoding="utf-8"))
    labels = {item.get("label") for item in bundle["artifacts"]}
    assert "externalReceipt" in labels
    assert "externalRedisProbe" in labels


def test_check_operator_auth_policy_detects_disabled_controls():
    case = _case_dir("operator_auth_policy")
    output = case / "operator_auth_policy_check.json"
    env = {
        "PCOSINA_ENV": "production",
        "PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL": "false",
        "PCOSINA_REQUIRE_OPERATOR_MFA": "false",
        "PCOSINA_REQUIRE_RECENT_ADMIN_AUTH": "false",
        "PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS": "0",
        "PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID": "0",
        "PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS": "7200",
        "PCOSINA_ADMIN_EMAILS": "admin@example.com",
        "PCOSINA_ADMIN_SESSION_SECRET": "",
    }
    proc = _run(
        [
            str(SCRIPTS_DIR / "check_operator_auth_policy.py"),
            "--environment",
            "production",
            "--output",
            str(output),
            "--max-admin-auth-age-seconds",
            "1800",
        ],
        env=env,
    )
    assert proc.returncode == 1
    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["status"] == "failed"
    assert any("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL" in item for item in payload["findings"])
    assert any("PCOSINA_REQUIRE_OPERATOR_MFA" in item for item in payload["findings"])
    assert any("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH" in item for item in payload["findings"])
    assert any("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS" in item for item in payload["findings"])
    assert any("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID" in item for item in payload["findings"])
    assert any("PCOSINA_ADMIN_SESSION_SECRET" in item for item in payload["findings"])


def test_check_operator_auth_policy_passes_with_hardened_settings():
    case = _case_dir("operator_auth_policy_ok")
    output = case / "operator_auth_policy_check.json"
    env = {
        "PCOSINA_ENV": "production",
        "PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL": "true",
        "PCOSINA_REQUIRE_OPERATOR_MFA": "true",
        "PCOSINA_REQUIRE_RECENT_ADMIN_AUTH": "true",
        "PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS": "1200",
        "PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID": "3",
        "PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS": "900",
        "PCOSINA_CONTENT_ADMIN_EMAILS": "content@example.com",
        "PCOSINA_ADMIN_SESSION_SECRET": "prod-secret",
    }
    proc = _run(
        [
            str(SCRIPTS_DIR / "check_operator_auth_policy.py"),
            "--environment",
            "production",
            "--output",
            str(output),
            "--max-admin-auth-age-seconds",
            "1800",
        ],
        env=env,
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["status"] == "ok"
    assert payload["checks"]["requireVerifiedOperatorEmail"] is True
    assert payload["checks"]["requireOperatorMfa"] is True
    assert payload["checks"]["requireRecentAdminAuth"] is True
    assert payload["checks"]["adminSessionIdleTimeoutSeconds"] == 1200
    assert payload["checks"]["adminMaxActiveSessionsPerUid"] == 3


def test_check_schema_migrations_script_writes_gate_report():
    case = _case_dir("schema_gate")
    db_path = case / "missing" / "nested" / "schema_gate.sqlite3"
    output = case / "schema_gate_report.json"
    assert not db_path.parent.exists()

    proc = _run(
        [
            str(SCRIPTS_DIR / "check_schema_migrations.py"),
            "--database-name",
            str(db_path),
            "--output",
            str(output),
        ]
    )
    assert proc.returncode == 0, proc.stderr or proc.stdout
    assert db_path.is_file()
    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["status"] == "ok"
    assert payload["application"]["pending"] == []
    assert payload["policy"]["pending"] == []


def test_check_schema_migrations_script_fails_when_pending_remain():
    case = _case_dir("schema_gate_pending")
    db_path = case / "schema_gate_pending.sqlite3"
    output = case / "schema_gate_pending_report.json"

    proc = _run(
        [
            str(SCRIPTS_DIR / "check_schema_migrations.py"),
            "--database-name",
            str(db_path),
            "--skip-apply",
            "--output",
            str(output),
        ]
    )
    assert proc.returncode == 1
    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["status"] == "failed"
    assert payload["application"]["pending"]
    assert payload["policy"]["pending"]
