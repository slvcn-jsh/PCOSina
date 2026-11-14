#!/usr/bin/env python
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Dict


ROOT = Path(__file__).resolve().parents[1]
BACKEND_DIR = ROOT / "backend"
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))


def _write_json(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Check that tracked schema migrations can be applied and that no pending migrations remain."
    )
    parser.add_argument(
        "--database-name",
        help="Optional SQLite database file path used for local/dev migration checks. Ignored when DATABASE_URL points to Postgres.",
    )
    parser.add_argument(
        "--skip-apply",
        action="store_true",
        help="Do not run init_db/init_policy_store before evaluating migration status.",
    )
    parser.add_argument("--output", help="Optional JSON output path for the gate report.")
    args = parser.parse_args()

    if args.database_name:
        database_path = Path(args.database_name).resolve()
        database_path.parent.mkdir(parents=True, exist_ok=True)
        os.environ["PCOSINA_DB_NAME"] = str(database_path)

    import database  # noqa: WPS433
    import policy_store  # noqa: WPS433

    failures: list[str] = []
    app_status: Dict[str, Any] | None = None
    policy_status: Dict[str, Any] | None = None

    if not args.skip_apply:
        try:
            database.init_db()
        except Exception as exc:  # pragma: no cover - subprocess path exercised in script tests
            failures.append(f"application migration bootstrap failed: {exc}")
        try:
            policy_store.init_policy_store()
        except Exception as exc:  # pragma: no cover - subprocess path exercised in script tests
            failures.append(f"policy migration bootstrap failed: {exc}")

    try:
        app_status = database.get_schema_migration_status()
    except Exception as exc:  # pragma: no cover - subprocess path exercised in script tests
        failures.append(f"application migration status read failed: {exc}")
    try:
        policy_status = policy_store.get_schema_migration_status()
    except Exception as exc:  # pragma: no cover - subprocess path exercised in script tests
        failures.append(f"policy migration status read failed: {exc}")

    if app_status and app_status.get("pending"):
        failures.append(f"application pending migrations: {', '.join(app_status['pending'])}")
    if policy_status and policy_status.get("pending"):
        failures.append(f"policy pending migrations: {', '.join(policy_status['pending'])}")

    status = "ok" if not failures else "failed"
    app_backend = database.db_mode()
    policy_backend = policy_store.db_mode() if hasattr(policy_store, "db_mode") else app_backend
    payload = {
        "status": status,
        "databaseBackend": app_backend,
        "policyDatabaseBackend": policy_backend,
        "databaseName": (os.getenv("PCOSINA_DB_NAME", "").strip() or database.DB_NAME) if app_backend == "sqlite" else None,
        "skipApply": bool(args.skip_apply),
        "failures": failures,
        "application": app_status,
        "policy": policy_status,
    }

    if args.output:
        _write_json(Path(args.output), payload)

    if failures:
        print("SCHEMA MIGRATION GATE FAILED")
        for item in failures:
            print(f"- {item}")
        return 1

    print("SCHEMA MIGRATION GATE PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
