#!/usr/bin/env python
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]


def _ts() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _run(cmd: List[str], env: Dict[str, str]) -> Dict[str, Any]:
    proc = subprocess.run(cmd, cwd=str(ROOT), env=env, capture_output=True, text=True, check=False)
    return {
        "cmd": cmd,
        "exitCode": int(proc.returncode),
        "stdout": proc.stdout,
        "stderr": proc.stderr,
    }


def _read_json(path: Path) -> Dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _artifact_record(path_value: str | Path, *, label: str, step_name: str, required: bool) -> Dict[str, Any]:
    path = Path(path_value)
    exists = path.exists()
    record: Dict[str, Any] = {
        "label": label,
        "step": step_name,
        "path": str(path),
        "required": bool(required),
        "exists": bool(exists),
    }
    if exists:
        stat = path.stat()
        record["sizeBytes"] = int(stat.st_size)
        record["sha256"] = _sha256_file(path)
        record["modifiedAtUtc"] = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc).isoformat()
    return record


def _build_evidence_bundle(
    *,
    environment: str,
    stamp: str,
    steps: List[Dict[str, Any]],
    report_path: Path,
    overall_status: str,
) -> Dict[str, Any]:
    records: List[Dict[str, Any]] = []
    seen: set[tuple[str, str, str]] = set()

    for step in steps:
        step_name = str(step.get("name") or "")
        step_status = str(step.get("status") or "").lower()
        required = step_status not in ("skipped", "blocked")
        artifacts = step.get("artifacts") or {}
        if not isinstance(artifacts, dict):
            continue
        for label, path_value in artifacts.items():
            path_text = str(path_value or "").strip()
            if not path_text:
                continue
            key = (step_name, str(label), path_text)
            if key in seen:
                continue
            seen.add(key)
            records.append(
                _artifact_record(
                    path_text,
                    label=str(label),
                    step_name=step_name,
                    required=required,
                )
            )

    records.append(
        _artifact_record(
            report_path,
            label="rolloutReadinessReport",
            step_name="rollout_readiness_batch",
            required=True,
        )
    )

    missing_required = [item for item in records if item.get("required") and not item.get("exists")]
    bundle_status = "failed" if missing_required or overall_status != "ok" else "ok"
    return {
        "status": bundle_status,
        "environment": environment,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "stamp": stamp,
        "rolloutStatus": overall_status,
        "artifacts": records,
        "missingRequiredArtifacts": [item["path"] for item in missing_required],
    }


def _validate_redis_url(raw: str) -> tuple[bool, str]:
    text = str(raw or "").strip()
    if not text:
        return False, "missing"
    if "<" in text or ">" in text:
        return False, "placeholder_value"
    parsed = urlparse(text)
    scheme = (parsed.scheme or "").lower()
    if scheme not in ("redis", "rediss"):
        return False, f"invalid_scheme_{scheme or 'none'}"
    host = (parsed.hostname or "").strip().lower()
    if not host:
        return False, "missing_host"
    if host in ("host", "localhost.localdomain") or "your-" in host or "example.com" in host or "yourdomain.com" in host:
        return False, "placeholder_host"
    try:
        port = parsed.port
    except Exception:
        return False, "invalid_port"
    if port is None:
        return False, "missing_port"
    if int(port) <= 0:
        return False, "invalid_port"
    return True, ""


def _step_result(
    *,
    name: str,
    run: Dict[str, Any] | None = None,
    status: str | None = None,
    reason: str | None = None,
    artifacts: Dict[str, str] | None = None,
) -> Dict[str, Any]:
    resolved_status = status or ("ok" if (run and int(run.get("exitCode") or 1) == 0) else "failed")
    return {
        "name": name,
        "status": resolved_status,
        "reason": reason,
        "artifacts": artifacts or {},
        "run": run,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Run PCOSINA rollout-readiness batch and emit a single evidence report.")
    parser.add_argument("--environment", choices=["staging", "production"], default="staging")
    parser.add_argument("--metrics", default="benchmarks/reports/go_live_metrics.json")
    parser.add_argument("--artifacts-dir", default="benchmarks/reports")
    parser.add_argument("--external-canary-receipt", default="", help="Optional path to an externally generated live canary drill receipt.")
    parser.add_argument("--external-queue-throughput-report", default="", help="Optional path to an externally generated queue broker throughput report.")
    parser.add_argument(
        "--require-external-canary-receipt",
        action="store_true",
        help="Fail batch if no external canary receipt is supplied or if supplied receipt validation fails.",
    )
    parser.add_argument(
        "--require-external-queue-throughput-report",
        action="store_true",
        help="Fail batch if no external queue throughput report is supplied or if supplied report validation fails.",
    )
    parser.add_argument(
        "--schema-db-name",
        default="",
        help="Optional SQLite database path used for the schema migration gate when DATABASE_URL is not set.",
    )
    parser.add_argument(
        "--allow-http-localhost",
        action="store_true",
        help="Allow localhost http webhook URLs for local drill runs.",
    )
    parser.add_argument(
        "--require-live-webhooks",
        action="store_true",
        help="Fail batch if environment webhook keys are missing/invalid.",
    )
    parser.add_argument(
        "--require-redis",
        action="store_true",
        help="Fail batch if Redis URL is missing or Redis throughput probe fails.",
    )
    parser.add_argument("--redis-jobs", type=int, default=5000, help="Redis probe publish target count.")
    parser.add_argument("--redis-workers", type=int, default=8, help="Redis probe consumer worker count.")
    parser.add_argument(
        "--redis-consume-timeout-seconds",
        type=float,
        default=180.0,
        help="Redis probe consume timeout window.",
    )
    parser.add_argument(
        "--skip-sync-replay-tests",
        action="store_true",
        help="Skip sync replay pytest execution.",
    )
    args = parser.parse_args()

    stamp = _ts()
    reports_dir = Path(args.artifacts_dir)
    if not reports_dir.is_absolute():
        reports_dir = ROOT / reports_dir
    reports_dir.mkdir(parents=True, exist_ok=True)

    env = dict(os.environ)
    steps: List[Dict[str, Any]] = []
    blockers: List[str] = []

    schema_gate_path = reports_dir / f"schema_migration_gate.{args.environment}.{stamp}.json"
    schema_env = dict(env)
    schema_db_name = args.schema_db_name.strip()
    if not schema_db_name and not str(env.get("DATABASE_URL") or "").strip():
        schema_db_name = str(reports_dir / f"schema_gate.{args.environment}.{stamp}.sqlite3")
    schema_cmd = [
        sys.executable,
        "scripts/check_schema_migrations.py",
        "--output",
        str(schema_gate_path),
    ]
    if schema_db_name:
        schema_env["PCOSINA_DB_NAME"] = str(Path(schema_db_name).resolve())
        schema_cmd.extend(["--database-name", schema_env["PCOSINA_DB_NAME"]])
    schema_run = _run(schema_cmd, env=schema_env)
    schema_ok = schema_run["exitCode"] == 0
    schema_artifacts = {"schemaGate": str(schema_gate_path)}
    if schema_db_name:
        schema_artifacts["schemaDb"] = schema_env["PCOSINA_DB_NAME"]
    steps.append(
        _step_result(
            name="schema_migration_gate",
            run=schema_run,
            status="ok" if schema_ok else "failed",
            reason=None if schema_ok else "schema_migration_gate_failed",
            artifacts=schema_artifacts,
        )
    )
    if not schema_ok:
        blockers.append("Schema migration gate failed.")

    webhook_check_path = reports_dir / f"canary_webhook_secrets_check.{args.environment}.{stamp}.json"
    webhook_cmd = [
        sys.executable,
        "scripts/check_canary_webhook_secrets.py",
        "--environment",
        args.environment,
        "--output",
        str(webhook_check_path),
    ]
    if args.allow_http_localhost:
        webhook_cmd.append("--allow-http-localhost")
    webhook_run = _run(webhook_cmd, env=env)
    webhook_ok = webhook_run["exitCode"] == 0
    steps.append(
        _step_result(
            name="webhook_secret_validation",
            run=webhook_run,
            status="ok" if webhook_ok else ("skipped" if not args.require_live_webhooks else "failed"),
            reason=None if webhook_ok else "webhook_keys_missing_or_invalid",
            artifacts={"secretCheck": str(webhook_check_path)},
        )
    )
    if not webhook_ok and args.require_live_webhooks:
        blockers.append("Missing/invalid environment webhook keys for required live drill.")

    operator_auth_path = reports_dir / f"operator_auth_policy_check.{args.environment}.{stamp}.json"
    operator_auth_cmd = [
        sys.executable,
        "scripts/check_operator_auth_policy.py",
        "--environment",
        args.environment,
        "--output",
        str(operator_auth_path),
    ]
    operator_auth_run = _run(operator_auth_cmd, env=env)
    operator_auth_ok = operator_auth_run["exitCode"] == 0
    steps.append(
        _step_result(
            name="operator_auth_policy_check",
            run=operator_auth_run,
            status="ok" if operator_auth_ok else "failed",
            reason=None if operator_auth_ok else "operator_auth_policy_check_failed",
            artifacts={"operatorAuthPolicy": str(operator_auth_path)},
        )
    )
    if not operator_auth_ok:
        blockers.append("Operator auth policy check failed.")

    external_canary_receipt = str(args.external_canary_receipt or "").strip()
    external_canary_check = reports_dir / f"external_canary_drill_receipt_check.{args.environment}.{stamp}.json"
    if external_canary_receipt:
        external_canary_cmd = [
            sys.executable,
            "scripts/check_canary_drill_receipt.py",
            "--receipt",
            external_canary_receipt,
            "--environment",
            args.environment,
            "--require-webhook-delivery",
            "--output",
            str(external_canary_check),
        ]
        external_canary_run = _run(external_canary_cmd, env=env)
        external_canary_ok = external_canary_run["exitCode"] == 0
        steps.append(
            _step_result(
                name="external_canary_receipt_contract_check",
                run=external_canary_run,
                status="ok" if external_canary_ok else "failed",
                reason=None if external_canary_ok else "external_canary_receipt_invalid",
                artifacts={
                    "externalReceipt": external_canary_receipt,
                    "externalReceiptCheck": str(external_canary_check),
                },
            )
        )
        if not external_canary_ok:
            blockers.append("External canary receipt contract check failed.")
    else:
        steps.append(
            _step_result(
                name="external_canary_receipt_contract_check",
                status="blocked" if args.require_external_canary_receipt else "skipped",
                reason="external_canary_receipt_not_supplied",
                artifacts={"externalReceiptCheck": str(external_canary_check)},
            )
        )
        if args.require_external_canary_receipt:
            blockers.append("External canary receipt is required but was not supplied.")

    canary_receipt = reports_dir / f"canary_drill_receipt.{args.environment}.{stamp}.json"
    canary_check = reports_dir / f"canary_drill_receipt_check.{args.environment}.{stamp}.json"
    canary_ran = False
    if webhook_ok or not args.require_live_webhooks:
        canary_cmd = [
            sys.executable,
            "scripts/run_canary_drill.py",
            "--environment",
            args.environment,
            "--metrics",
            args.metrics,
            "--output",
            str(canary_receipt),
            "--alert-on-ok",
        ]
        if args.require_live_webhooks:
            canary_cmd.append("--require-webhooks")
        canary_run = _run(canary_cmd, env=env)
        canary_ok = canary_run["exitCode"] == 0
        canary_ran = True
        steps.append(
            _step_result(
                name="canary_drill",
                run=canary_run,
                status="ok" if canary_ok else "failed",
                reason=None if canary_ok else "canary_drill_failed",
                artifacts={"receipt": str(canary_receipt)},
            )
        )
        if canary_ok:
            receipt_check_cmd = [
                sys.executable,
                "scripts/check_canary_drill_receipt.py",
                "--receipt",
                str(canary_receipt),
                "--environment",
                args.environment,
                "--require-webhook-delivery",
                "--output",
                str(canary_check),
            ]
            receipt_check_run = _run(receipt_check_cmd, env=env)
            receipt_ok = receipt_check_run["exitCode"] == 0
            steps.append(
                _step_result(
                    name="canary_receipt_contract_check",
                    run=receipt_check_run,
                    status="ok" if receipt_ok else "failed",
                    reason=None if receipt_ok else "receipt_contract_check_failed",
                    artifacts={"receiptCheck": str(canary_check)},
                )
            )
            if not receipt_ok:
                blockers.append("Canary receipt contract check failed.")
        else:
            blockers.append("Canary drill failed.")
    else:
        steps.append(
            _step_result(
                name="canary_drill",
                status="blocked",
                reason="webhook_validation_failed",
                artifacts={"receipt": str(canary_receipt)},
            )
        )
        steps.append(
            _step_result(
                name="canary_receipt_contract_check",
                status="blocked",
                reason="canary_drill_not_executed",
                artifacts={"receiptCheck": str(canary_check)},
            )
        )

    queue_memory_path = reports_dir / f"queue_broker_throughput.memory.{stamp}.json"
    queue_memory_cmd = [
        sys.executable,
        "load-tests/queue_broker_throughput.py",
        "--backend",
        "memory",
        "--jobs",
        "1000",
        "--workers",
        "4",
        "--output",
        str(queue_memory_path),
    ]
    queue_memory_run = _run(queue_memory_cmd, env=env)
    queue_memory_ok = queue_memory_run["exitCode"] == 0
    steps.append(
        _step_result(
            name="queue_broker_throughput_memory",
            run=queue_memory_run,
            status="ok" if queue_memory_ok else "failed",
            reason=None if queue_memory_ok else "memory_probe_failed",
            artifacts={"memoryProbe": str(queue_memory_path)},
        )
    )
    if not queue_memory_ok:
        blockers.append("Memory queue throughput probe failed.")

    redis_url = str(env.get("PCOSINA_REDIS_URL") or "").strip()
    queue_redis_path = reports_dir / f"queue_broker_throughput.redis.{stamp}.json"
    redis_ok, redis_error = _validate_redis_url(redis_url) if redis_url else (False, "missing")
    if redis_url and redis_ok:
        queue_redis_cmd = [
            sys.executable,
            "load-tests/queue_broker_throughput.py",
            "--backend",
            "redis",
            "--jobs",
            str(max(1, int(args.redis_jobs))),
            "--workers",
            str(max(1, int(args.redis_workers))),
            "--consume-timeout-seconds",
            str(max(1.0, float(args.redis_consume_timeout_seconds))),
            "--output",
            str(queue_redis_path),
        ]
        queue_redis_run = _run(queue_redis_cmd, env=env)
        queue_redis_ok = queue_redis_run["exitCode"] == 0
        steps.append(
            _step_result(
                name="queue_broker_throughput_redis",
                run=queue_redis_run,
                status="ok" if queue_redis_ok else "failed",
                reason=None if queue_redis_ok else "redis_probe_failed",
                artifacts={"redisProbe": str(queue_redis_path)},
            )
        )
        if not queue_redis_ok:
            blockers.append("Redis throughput probe failed.")
    else:
        steps.append(
            _step_result(
                name="queue_broker_throughput_redis",
                status="blocked" if args.require_redis else "skipped",
                reason=f"redis_url_invalid_{redis_error}",
                artifacts={"redisProbe": str(queue_redis_path)},
            )
        )
        if args.require_redis:
            blockers.append(
                "Redis probe required but PCOSINA_REDIS_URL is missing/invalid."
            )

    external_queue_report = str(args.external_queue_throughput_report or "").strip()
    external_queue_check = reports_dir / f"external_queue_broker_throughput_check.{args.environment}.{stamp}.json"
    if external_queue_report:
        external_queue_cmd = [
            sys.executable,
            "scripts/check_queue_broker_throughput_report.py",
            "--report",
            external_queue_report,
            "--backend",
            "redis",
            "--output",
            str(external_queue_check),
        ]
        external_queue_run = _run(external_queue_cmd, env=env)
        external_queue_ok = external_queue_run["exitCode"] == 0
        steps.append(
            _step_result(
                name="external_queue_broker_throughput_check",
                run=external_queue_run,
                status="ok" if external_queue_ok else "failed",
                reason=None if external_queue_ok else "external_queue_throughput_invalid",
                artifacts={
                    "externalRedisProbe": external_queue_report,
                    "externalRedisProbeCheck": str(external_queue_check),
                },
            )
        )
        if not external_queue_ok:
            blockers.append("External queue throughput report check failed.")
    else:
        steps.append(
            _step_result(
                name="external_queue_broker_throughput_check",
                status="blocked" if args.require_external_queue_throughput_report else "skipped",
                reason="external_queue_throughput_report_not_supplied",
                artifacts={"externalRedisProbeCheck": str(external_queue_check)},
            )
        )
        if args.require_external_queue_throughput_report:
            blockers.append("External queue throughput report is required but was not supplied.")

    sync_step_artifacts = {}
    if not args.skip_sync_replay_tests:
        sync_cmd = [
            sys.executable,
            "-m",
            "pytest",
            "backend/tests/test_sync_recovery_e2e.py",
            "backend/tests/test_sync_replay_pack.py",
            "-q",
        ]
        sync_run = _run(sync_cmd, env=env)
        sync_ok = sync_run["exitCode"] == 0
        sync_out_path = reports_dir / f"sync_replay_pytest.{stamp}.txt"
        sync_out_path.write_text(
            (sync_run.get("stdout") or "") + "\n" + (sync_run.get("stderr") or ""),
            encoding="utf-8",
        )
        sync_step_artifacts = {"syncReplayPytestLog": str(sync_out_path)}
        steps.append(
            _step_result(
                name="sync_replay_e2e",
                run=sync_run,
                status="ok" if sync_ok else "failed",
                reason=None if sync_ok else "sync_replay_tests_failed",
                artifacts=sync_step_artifacts,
            )
        )
        if not sync_ok:
            blockers.append("Sync replay E2E tests failed.")
    else:
        steps.append(
            _step_result(
                name="sync_replay_e2e",
                status="skipped",
                reason="flag_skip_sync_replay_tests",
                artifacts=sync_step_artifacts,
            )
        )

    statuses = [str(step.get("status") or "").lower() for step in steps]
    has_failed = any(s == "failed" for s in statuses)
    has_blocked = any(s == "blocked" for s in statuses)
    overall_status = "failed" if has_failed else ("blocked" if has_blocked else "ok")

    report = {
        "status": overall_status,
        "environment": args.environment,
        "generatedAtUtc": datetime.now(timezone.utc).isoformat(),
        "inputs": {
            "metrics": args.metrics,
            "externalCanaryReceipt": external_canary_receipt or None,
            "externalQueueThroughputReport": external_queue_report or None,
            "requireExternalCanaryReceipt": bool(args.require_external_canary_receipt),
            "requireExternalQueueThroughputReport": bool(args.require_external_queue_throughput_report),
            "requireLiveWebhooks": bool(args.require_live_webhooks),
            "requireRedis": bool(args.require_redis),
            "allowHttpLocalhost": bool(args.allow_http_localhost),
            "skipSyncReplayTests": bool(args.skip_sync_replay_tests),
        },
        "steps": steps,
        "blockers": blockers,
    }

    report_path = reports_dir / f"rollout_readiness_batch.{args.environment}.{stamp}.json"
    _write_json(report_path, report)
    bundle = _build_evidence_bundle(
        environment=args.environment,
        stamp=stamp,
        steps=steps,
        report_path=report_path,
        overall_status=overall_status,
    )
    bundle_path = reports_dir / f"release_evidence_bundle.{args.environment}.{stamp}.json"
    _write_json(bundle_path, bundle)
    print(f"ROLLOUT READINESS REPORT WRITTEN: {report_path}")
    print(f"RELEASE EVIDENCE BUNDLE WRITTEN: {bundle_path}")
    print(f"STATUS: {overall_status.upper()}")
    return 0 if bundle["status"] == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
