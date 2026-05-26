from __future__ import annotations

import hashlib
import json
import os
import sqlite3
import time
import uuid
from copy import deepcopy
from contextlib import contextmanager
from typing import Any, Dict, List, Optional

from db_url import is_postgres_database_url
from policy_config import (
    POLICY_SCHEMA_VERSION,
    PRODUCTION_CANARY_BOOTSTRAP_PERCENT,
    PRODUCTION_SOLVER_MAX_SECONDS,
    PRODUCTION_SOLVER_RETRY_ATTEMPTS,
    PRODUCTION_SOLVER_TIME_LIMIT_SECONDS,
    PRODUCTION_SOLVER_WORKERS,
    PRODUCTION_STAGE1_MAX_CANDIDATES,
    PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE,
    PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER,
    PRODUCTION_TOTAL_SOLVER_SECONDS,
    PlannerPolicyConfig,
    default_policy,
    load_policy,
)

try:
    import psycopg
    from psycopg.rows import dict_row
except Exception:
    psycopg = None
    dict_row = None

DATABASE_URL = os.getenv("DATABASE_URL", "").strip()
DB_NAME = os.getenv("PCOSINA_DB_NAME", "pcosina.db").strip() or "pcosina.db"
SCHEMA_MIGRATION_SCOPE = "policy"
SCHEMA_BOOTSTRAP_LOCK_KEY = 2026032902
POLICY_WRITE_LOCK_KEY = 2026041301

def _is_production_env() -> bool:
    return os.getenv("PCOSINA_ENV", "development").strip().lower() in ("prod", "production")


def _use_postgres() -> bool:
    return is_postgres_database_url(DATABASE_URL)


def db_mode() -> str:
    return "postgres" if _use_postgres() else "sqlite"


def _connect():
    if _use_postgres():
        if psycopg is None:
            raise RuntimeError("psycopg is not installed. Add psycopg[binary] to requirements.")
        return psycopg.connect(DATABASE_URL)
    if _is_production_env():
        raise RuntimeError("Production requires a Postgres DATABASE_URL; SQLite fallback is disabled.")
    return sqlite3.connect(DB_NAME)


@contextmanager
def _schema_bootstrap_lock(conn):
    if not _use_postgres():
        yield
        return
    cur = conn.cursor()
    cur.execute("SELECT pg_advisory_lock(%s)", (SCHEMA_BOOTSTRAP_LOCK_KEY,))
    try:
        yield
    finally:
        cur.execute("SELECT pg_advisory_unlock(%s)", (SCHEMA_BOOTSTRAP_LOCK_KEY,))


@contextmanager
def _policy_write_lock(conn):
    if not _use_postgres():
        yield
        return
    cur = conn.cursor()
    cur.execute("SELECT pg_advisory_lock(%s)", (POLICY_WRITE_LOCK_KEY,))
    try:
        yield
    finally:
        cur.execute("SELECT pg_advisory_unlock(%s)", (POLICY_WRITE_LOCK_KEY,))


def _create_schema_migrations_table_sql() -> str:
    if _use_postgres():
        return """
        CREATE TABLE IF NOT EXISTS schema_migrations (
            scope TEXT NOT NULL,
            migration_id TEXT NOT NULL,
            description TEXT,
            applied_at BIGINT NOT NULL,
            PRIMARY KEY (scope, migration_id)
        )
        """
    return """
    CREATE TABLE IF NOT EXISTS schema_migrations (
        scope TEXT NOT NULL,
        migration_id TEXT NOT NULL,
        description TEXT,
        applied_at INTEGER NOT NULL,
        PRIMARY KEY (scope, migration_id)
    )
    """


def _ensure_schema_migration_table(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_schema_migrations_table_sql())


def _get_applied_migration_ids(conn, scope: str = SCHEMA_MIGRATION_SCOPE) -> set[str]:
    if _use_postgres() and dict_row is not None:
        cur = conn.cursor(row_factory=dict_row)
        cur.execute("SELECT migration_id FROM schema_migrations WHERE scope = %s", (scope,))
        rows = cur.fetchall()
        return {str(row.get("migration_id")) for row in rows}
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    cur.execute("SELECT migration_id FROM schema_migrations WHERE scope = ?", (scope,))
    rows = cur.fetchall()
    return {str(row["migration_id"]) for row in rows}


def _record_applied_migration(conn, migration_id: str, description: str, scope: str = SCHEMA_MIGRATION_SCOPE) -> None:
    cur = conn.cursor()
    now = int(time.time() * 1000)
    if _use_postgres():
        cur.execute(
            """
            INSERT INTO schema_migrations (scope, migration_id, description, applied_at)
            VALUES (%s, %s, %s, %s)
            ON CONFLICT (scope, migration_id) DO NOTHING
            """,
            (scope, migration_id, description, now),
        )
    else:
        cur.execute(
            """
            INSERT OR IGNORE INTO schema_migrations (scope, migration_id, description, applied_at)
            VALUES (?, ?, ?, ?)
            """,
            (scope, migration_id, description, now),
        )


def _policy_hash(policy: Dict[str, Any]) -> str:
    canonical = json.dumps(policy, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _deep_merge(base: Dict[str, Any], override: Dict[str, Any]) -> None:
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(base.get(key), dict):
            _deep_merge(base[key], value)
        else:
            base[key] = value


def _production_canary_bootstrap_overlay() -> Dict[str, Any]:
    return {
        "environment_overrides": {
            "production": {
                "stage1": {
                    "ML_shadow_enabled": True,
                    "ML_canary_enabled": True,
                    "max_candidates_per_slot": int(PRODUCTION_STAGE1_MAX_CANDIDATES),
                    "restricted_shortlist_multiplier": float(PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER),
                    "pool_cap_top_share": float(PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE),
                },
                "solver": {
                    "solver_time_limit_seconds": float(PRODUCTION_SOLVER_TIME_LIMIT_SECONDS),
                    "solver_max_seconds": float(PRODUCTION_SOLVER_MAX_SECONDS),
                    "total_solver_seconds": float(PRODUCTION_TOTAL_SOLVER_SECONDS),
                    "retry_attempts": int(PRODUCTION_SOLVER_RETRY_ATTEMPTS),
                    "solver_workers": int(PRODUCTION_SOLVER_WORKERS),
                },
                "sre": {
                    "canary_cohort_percent": float(PRODUCTION_CANARY_BOOTSTRAP_PERCENT),
                },
            }
        }
    }


def _staging_performance_bootstrap_overlay() -> Dict[str, Any]:
    return {
        "environment_overrides": {
            "staging": {
                "stage1": {
                    "max_candidates_per_slot": int(PRODUCTION_STAGE1_MAX_CANDIDATES),
                    "restricted_shortlist_multiplier": float(PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER),
                    "pool_cap_top_share": float(PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE),
                },
                "solver": {
                    "solver_time_limit_seconds": float(PRODUCTION_SOLVER_TIME_LIMIT_SECONDS),
                    "solver_max_seconds": float(PRODUCTION_SOLVER_MAX_SECONDS),
                    "total_solver_seconds": float(PRODUCTION_TOTAL_SOLVER_SECONDS),
                    "retry_attempts": int(PRODUCTION_SOLVER_RETRY_ATTEMPTS),
                    "solver_workers": int(PRODUCTION_SOLVER_WORKERS),
                },
            }
        }
    }


def _requires_production_canary_bootstrap(policy_payload: Dict[str, Any]) -> bool:
    validated = load_policy(policy_payload)
    resolved = validated.to_runtime_dict(environment="production")
    raw_policy_name = str((policy_payload or {}).get("policy_name") or "").strip().lower()
    stage1 = resolved.get("stage1") if isinstance(resolved.get("stage1"), dict) else {}
    sre = resolved.get("sre") if isinstance(resolved.get("sre"), dict) else {}
    solver = resolved.get("solver") if isinstance(resolved.get("solver"), dict) else {}
    try:
        canary_percent = float(sre.get("canary_cohort_percent"))
    except Exception:
        canary_percent = 0.0
    canary_bootstrap_missing = not (
        bool(stage1.get("ML_shadow_enabled"))
        and bool(stage1.get("ML_canary_enabled"))
        and canary_percent == float(PRODUCTION_CANARY_BOOTSTRAP_PERCENT)
    )
    if canary_bootstrap_missing:
        return True
    # Only auto-upgrade latency defaults when the active policy still resolves to the old
    # bootstrap/runtime defaults. Customized policies should keep their explicit tuning.
    should_bootstrap_latency = raw_policy_name == "default"
    if not should_bootstrap_latency:
        try:
            should_bootstrap_latency = (
                int(stage1.get("max_candidates_per_slot")) == 120
                and float(stage1.get("restricted_shortlist_multiplier")) == 1.25
                and float(stage1.get("pool_cap_top_share")) == 0.60
                and float(solver.get("solver_time_limit_seconds")) == 6.0
                and float(solver.get("solver_max_seconds")) == 12.0
                and float(solver.get("total_solver_seconds")) == 25.0
                and int(solver.get("retry_attempts")) == 2
                and int(solver.get("solver_workers")) == 4
            )
        except Exception:
            should_bootstrap_latency = False
    if not should_bootstrap_latency:
        return False
    try:
        return not (
            int(stage1.get("max_candidates_per_slot")) == int(PRODUCTION_STAGE1_MAX_CANDIDATES)
            and float(stage1.get("restricted_shortlist_multiplier")) == float(PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER)
            and float(stage1.get("pool_cap_top_share")) == float(PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE)
            and float(solver.get("solver_time_limit_seconds")) == float(PRODUCTION_SOLVER_TIME_LIMIT_SECONDS)
            and float(solver.get("solver_max_seconds")) == float(PRODUCTION_SOLVER_MAX_SECONDS)
            and float(solver.get("total_solver_seconds")) == float(PRODUCTION_TOTAL_SOLVER_SECONDS)
            and int(solver.get("retry_attempts")) == int(PRODUCTION_SOLVER_RETRY_ATTEMPTS)
            and int(solver.get("solver_workers")) == int(PRODUCTION_SOLVER_WORKERS)
        )
    except Exception:
        return True
 

def _requires_staging_performance_bootstrap(policy_payload: Dict[str, Any]) -> bool:
    validated = load_policy(policy_payload)
    resolved = validated.to_runtime_dict(environment="staging")
    raw_overrides = (policy_payload or {}).get("environment_overrides")
    staging_override = raw_overrides.get("staging") if isinstance(raw_overrides, dict) else None
    stage1 = resolved.get("stage1") if isinstance(resolved.get("stage1"), dict) else {}
    solver = resolved.get("solver") if isinstance(resolved.get("solver"), dict) else {}
    # Staging should be realistic for respondent testing, but explicit staging
    # overrides must remain under operator control.
    if isinstance(staging_override, dict) and staging_override:
        return False
    try:
        return not (
            int(stage1.get("max_candidates_per_slot")) == int(PRODUCTION_STAGE1_MAX_CANDIDATES)
            and float(stage1.get("restricted_shortlist_multiplier")) == float(PRODUCTION_STAGE1_RESTRICTED_MULTIPLIER)
            and float(stage1.get("pool_cap_top_share")) == float(PRODUCTION_STAGE1_POOL_CAP_TOP_SHARE)
            and float(solver.get("solver_time_limit_seconds")) == float(PRODUCTION_SOLVER_TIME_LIMIT_SECONDS)
            and float(solver.get("solver_max_seconds")) == float(PRODUCTION_SOLVER_MAX_SECONDS)
            and float(solver.get("total_solver_seconds")) == float(PRODUCTION_TOTAL_SOLVER_SECONDS)
            and int(solver.get("retry_attempts")) == int(PRODUCTION_SOLVER_RETRY_ATTEMPTS)
            and int(solver.get("solver_workers")) == int(PRODUCTION_SOLVER_WORKERS)
        )
    except Exception:
        return True


def _apply_production_canary_bootstrap(policy_payload: Dict[str, Any]) -> Dict[str, Any]:
    upgraded = deepcopy(load_policy(policy_payload).to_runtime_dict())
    _deep_merge(upgraded, _production_canary_bootstrap_overlay())
    return load_policy(upgraded).to_runtime_dict()


def _apply_runtime_bootstrap(policy_payload: Dict[str, Any]) -> Dict[str, Any]:
    upgraded = deepcopy(load_policy(policy_payload).to_runtime_dict())
    if _requires_production_canary_bootstrap(policy_payload):
        _deep_merge(upgraded, _production_canary_bootstrap_overlay())
    if _requires_staging_performance_bootstrap(policy_payload):
        _deep_merge(upgraded, _staging_performance_bootstrap_overlay())
    return load_policy(upgraded).to_runtime_dict()


def _create_policy_versions_table_sql() -> str:
    if _use_postgres():
        return """
        CREATE TABLE IF NOT EXISTS policy_versions (
            id TEXT PRIMARY KEY,
            version_number BIGINT NOT NULL UNIQUE,
            schema_version TEXT NOT NULL,
            policy_json TEXT NOT NULL,
            policy_hash TEXT NOT NULL,
            created_by TEXT,
            created_at BIGINT NOT NULL,
            notes TEXT,
            rollback_of TEXT,
            is_active INTEGER NOT NULL DEFAULT 0,
            activated_at BIGINT
        )
        """
    return """
    CREATE TABLE IF NOT EXISTS policy_versions (
        id TEXT PRIMARY KEY,
        version_number INTEGER NOT NULL UNIQUE,
        schema_version TEXT NOT NULL,
        policy_json TEXT NOT NULL,
        policy_hash TEXT NOT NULL,
        created_by TEXT,
        created_at INTEGER NOT NULL,
        notes TEXT,
        rollback_of TEXT,
        is_active INTEGER NOT NULL DEFAULT 0,
        activated_at INTEGER
    )
    """


def _create_policy_audit_table_sql() -> str:
    if _use_postgres():
        return """
        CREATE TABLE IF NOT EXISTS policy_audit_logs (
            id BIGSERIAL PRIMARY KEY,
            action TEXT NOT NULL,
            actor TEXT,
            policy_id TEXT,
            created_at BIGINT NOT NULL,
            details_json TEXT
        )
        """
    return """
    CREATE TABLE IF NOT EXISTS policy_audit_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        action TEXT NOT NULL,
        actor TEXT,
        policy_id TEXT,
        created_at INTEGER NOT NULL,
        details_json TEXT
    )
    """


def _ensure_policy_indexes(conn) -> None:
    cur = conn.cursor()
    cur.execute("CREATE INDEX IF NOT EXISTS idx_policy_versions_active ON policy_versions(is_active, version_number)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_policy_audit_created ON policy_audit_logs(created_at)")


def _migration_policy_tables(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_policy_versions_table_sql())
    cur.execute(_create_policy_audit_table_sql())


def _migration_policy_indexes(conn) -> None:
    _ensure_policy_indexes(conn)


def _registered_schema_migrations():
    return [
        ("20260319_policy_001_tables", "Create policy store tables", _migration_policy_tables),
        ("20260319_policy_002_indexes", "Ensure policy store indexes", _migration_policy_indexes),
    ]


def _run_schema_migrations(conn) -> list[str]:
    _ensure_schema_migration_table(conn)
    applied = _get_applied_migration_ids(conn)
    executed: list[str] = []
    for migration_id, description, callback in _registered_schema_migrations():
        if migration_id in applied:
            continue
        callback(conn)
        _record_applied_migration(conn, migration_id, description)
        conn.commit()
        executed.append(migration_id)
        applied.add(migration_id)
    return executed


def get_schema_migration_status() -> Dict[str, Any]:
    conn = _connect()
    try:
        _ensure_schema_migration_table(conn)
        applied = _get_applied_migration_ids(conn)
        registered = _registered_schema_migrations()
        items = []
        for migration_id, description, _callback in registered:
            items.append(
                {
                    "scope": SCHEMA_MIGRATION_SCOPE,
                    "migrationId": migration_id,
                    "description": description,
                    "applied": migration_id in applied,
                }
            )
        return {
            "scope": SCHEMA_MIGRATION_SCOPE,
            "registered": len(registered),
            "applied": sum(1 for item in items if item["applied"]),
            "pending": [item["migrationId"] for item in items if not item["applied"]],
            "items": items,
        }
    finally:
        conn.close()


def init_policy_store() -> None:
    conn = _connect()
    try:
        with _schema_bootstrap_lock(conn):
            _run_schema_migrations(conn)
        conn.commit()
    finally:
        conn.close()


def _audit(action: str, actor: str, policy_id: Optional[str], details: Dict[str, Any]) -> None:
    conn = _connect()
    try:
        cur = conn.cursor()
        now = int(time.time() * 1000)
        details_json = json.dumps(details, sort_keys=True)
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO policy_audit_logs (action, actor, policy_id, created_at, details_json)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (action, actor, policy_id, now, details_json),
            )
        else:
            cur.execute(
                """
                INSERT INTO policy_audit_logs (action, actor, policy_id, created_at, details_json)
                VALUES (?, ?, ?, ?, ?)
                """,
                (action, actor, policy_id, now, details_json),
            )
        conn.commit()
    finally:
        conn.close()


def _next_version_number(conn) -> int:
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("SELECT COALESCE(MAX(version_number), 0) FROM policy_versions")
    else:
        cur.execute("SELECT COALESCE(MAX(version_number), 0) FROM policy_versions")
    row = cur.fetchone()
    current = row[0] if row else 0
    return int(current) + 1


def ensure_default_policy(actor: str = "system") -> Dict[str, Any]:
    active = get_active_policy()
    if active:
        active_payload = active.get("policy") if isinstance(active.get("policy"), dict) else {}
        if _requires_production_canary_bootstrap(active_payload) or _requires_staging_performance_bootstrap(active_payload):
            upgraded_payload = _apply_runtime_bootstrap(active_payload)
            upgraded_hash = _policy_hash(upgraded_payload)
            if upgraded_hash != str(active.get("policy_hash") or ""):
                return create_policy_version(
                    upgraded_payload,
                    actor=actor,
                    notes="bootstrap-runtime-performance-defaults",
                    activate=True,
                    rollback_of=str(active.get("id") or "") or None,
                )
        return active
    created = create_policy_version(
        _apply_runtime_bootstrap(default_policy().to_runtime_dict()),
        actor=actor,
        notes="bootstrap-default-policy",
        activate=True,
    )
    return created


def create_policy_version(
    policy_input: Dict[str, Any] | PlannerPolicyConfig,
    actor: str,
    notes: Optional[str] = None,
    activate: bool = False,
    rollback_of: Optional[str] = None,
) -> Dict[str, Any]:
    if isinstance(policy_input, PlannerPolicyConfig):
        policy = policy_input
    else:
        policy = load_policy(policy_input)
    policy_payload = policy.to_runtime_dict()
    policy_json = json.dumps(policy_payload, sort_keys=True)
    p_hash = _policy_hash(policy_payload)
    conn = _connect()
    try:
        with _policy_write_lock(conn):
            if activate and (notes in {"bootstrap-default-policy", "bootstrap-production-canary-defaults", "bootstrap-runtime-performance-defaults"} or actor == "system-bootstrap"):
                active = get_active_policy()
                if active and str(active.get("policy_hash") or "") == p_hash:
                    return active

            cur = conn.cursor()
            version_number = _next_version_number(conn)
            policy_id = uuid.uuid4().hex
            now = int(time.time() * 1000)
            if _use_postgres():
                cur.execute(
                    """
                    INSERT INTO policy_versions (
                        id, version_number, schema_version, policy_json, policy_hash,
                        created_by, created_at, notes, rollback_of, is_active, activated_at
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        policy_id,
                        version_number,
                        POLICY_SCHEMA_VERSION,
                        policy_json,
                        p_hash,
                        actor,
                        now,
                        notes,
                        rollback_of,
                        1 if activate else 0,
                        now if activate else None,
                    ),
                )
                if activate:
                    cur.execute("UPDATE policy_versions SET is_active = 0 WHERE id <> %s", (policy_id,))
            else:
                cur.execute(
                    """
                    INSERT INTO policy_versions (
                        id, version_number, schema_version, policy_json, policy_hash,
                        created_by, created_at, notes, rollback_of, is_active, activated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        policy_id,
                        version_number,
                        POLICY_SCHEMA_VERSION,
                        policy_json,
                        p_hash,
                        actor,
                        now,
                        notes,
                        rollback_of,
                        1 if activate else 0,
                        now if activate else None,
                    ),
                )
                if activate:
                    cur.execute("UPDATE policy_versions SET is_active = 0 WHERE id <> ?", (policy_id,))
            conn.commit()
    finally:
        conn.close()

    _audit(
        action="policy_create_and_activate" if activate else "policy_create",
        actor=actor,
        policy_id=policy_id,
        details={
            "version_number": version_number,
            "notes": notes,
            "rollback_of": rollback_of,
            "policy_hash": p_hash,
        },
    )
    return get_policy(policy_id) or {}


def _fetch_one(query: str, params: tuple) -> Optional[Dict[str, Any]]:
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
        cur.execute(query, params)
        row = cur.fetchone()
        if not row:
            return None
        if isinstance(row, dict):
            return dict(row)
        return {k: row[k] for k in row.keys()}
    finally:
        conn.close()


def _fetch_many(query: str, params: tuple = ()) -> List[Dict[str, Any]]:
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
        cur.execute(query, params)
        rows = cur.fetchall()
        out: List[Dict[str, Any]] = []
        for row in rows:
            if isinstance(row, dict):
                out.append(dict(row))
            else:
                out.append({k: row[k] for k in row.keys()})
        return out
    finally:
        conn.close()


def _normalize_record(row: Dict[str, Any]) -> Dict[str, Any]:
    payload = dict(row)
    raw_policy = payload.get("policy_json")
    if raw_policy:
        try:
            payload["policy"] = json.loads(raw_policy)
        except Exception:
            payload["policy"] = raw_policy
    payload["is_active"] = bool(payload.get("is_active"))
    return payload


def get_policy(policy_id: str) -> Optional[Dict[str, Any]]:
    query = (
        "SELECT id, version_number, schema_version, policy_json, policy_hash, created_by, created_at, notes, rollback_of, is_active, activated_at "
        "FROM policy_versions WHERE id = %s"
        if _use_postgres()
        else "SELECT id, version_number, schema_version, policy_json, policy_hash, created_by, created_at, notes, rollback_of, is_active, activated_at FROM policy_versions WHERE id = ?"
    )
    row = _fetch_one(query, (policy_id,))
    if not row:
        return None
    return _normalize_record(row)


def get_active_policy() -> Optional[Dict[str, Any]]:
    query = (
        "SELECT id, version_number, schema_version, policy_json, policy_hash, created_by, created_at, notes, rollback_of, is_active, activated_at "
        "FROM policy_versions WHERE is_active = 1 ORDER BY activated_at DESC LIMIT 1"
    )
    rows = _fetch_many(query)
    if not rows:
        return None
    return _normalize_record(rows[0])


def list_policy_versions(limit: int = 50) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 50), 250))
    query = (
        "SELECT id, version_number, schema_version, policy_json, policy_hash, created_by, created_at, notes, rollback_of, is_active, activated_at "
        "FROM policy_versions ORDER BY version_number DESC LIMIT %s"
        if _use_postgres()
        else "SELECT id, version_number, schema_version, policy_json, policy_hash, created_by, created_at, notes, rollback_of, is_active, activated_at FROM policy_versions ORDER BY version_number DESC LIMIT ?"
    )
    rows = _fetch_many(query, (limit,))
    return [_normalize_record(r) for r in rows]


def activate_policy(policy_id: str, actor: str, notes: Optional[str] = None) -> Dict[str, Any]:
    current = get_policy(policy_id)
    if not current:
        raise ValueError("Policy not found")
    conn = _connect()
    try:
        cur = conn.cursor()
        now = int(time.time() * 1000)
        if _use_postgres():
            cur.execute("UPDATE policy_versions SET is_active = 0")
            cur.execute("UPDATE policy_versions SET is_active = 1, activated_at = %s WHERE id = %s", (now, policy_id))
        else:
            cur.execute("UPDATE policy_versions SET is_active = 0")
            cur.execute("UPDATE policy_versions SET is_active = 1, activated_at = ? WHERE id = ?", (now, policy_id))
        conn.commit()
    finally:
        conn.close()

    _audit(
        action="policy_activate",
        actor=actor,
        policy_id=policy_id,
        details={"notes": notes, "version_number": current.get("version_number")},
    )
    return get_policy(policy_id) or {}


def rollback_policy(actor: str, target_policy_id: Optional[str] = None, notes: Optional[str] = None) -> Dict[str, Any]:
    active = get_active_policy()
    if target_policy_id:
        target = get_policy(target_policy_id)
    else:
        query = (
            "SELECT id, version_number, schema_version, policy_json, policy_hash, created_by, created_at, notes, rollback_of, is_active, activated_at "
            "FROM policy_versions WHERE id <> %s ORDER BY version_number DESC LIMIT 1"
            if _use_postgres()
            else "SELECT id, version_number, schema_version, policy_json, policy_hash, created_by, created_at, notes, rollback_of, is_active, activated_at FROM policy_versions WHERE id <> ? ORDER BY version_number DESC LIMIT 1"
        )
        if active:
            rows = _fetch_many(query, (active.get("id"),))
            target = _normalize_record(rows[0]) if rows else None
        else:
            versions = list_policy_versions(limit=1)
            target = versions[0] if versions else None

    if not target:
        raise ValueError("No rollback target policy available")

    # Create explicit rollback version so history is immutable and auditable.
    created = create_policy_version(
        policy_input=target["policy"],
        actor=actor,
        notes=notes or "rollback",
        activate=True,
        rollback_of=active.get("id") if active else None,
    )
    _audit(
        action="policy_rollback",
        actor=actor,
        policy_id=created.get("id"),
        details={
            "source_policy_id": target.get("id"),
            "replaced_active_policy_id": active.get("id") if active else None,
            "notes": notes,
        },
    )
    return created


def list_policy_audit(limit: int = 100) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 100), 500))
    query = (
        "SELECT id, action, actor, policy_id, created_at, details_json FROM policy_audit_logs ORDER BY id DESC LIMIT %s"
        if _use_postgres()
        else "SELECT id, action, actor, policy_id, created_at, details_json FROM policy_audit_logs ORDER BY id DESC LIMIT ?"
    )
    rows = _fetch_many(query, (limit,))
    for row in rows:
        raw = row.get("details_json")
        if raw:
            try:
                row["details"] = json.loads(raw)
            except Exception:
                row["details"] = raw
    return rows
