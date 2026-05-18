import sqlite3
import json
import os
import re
import time
import uuid
from contextlib import contextmanager
from typing import Any, Dict, Iterable, List, Optional

from db_url import is_postgres_database_url

try:
    import psycopg
    from psycopg.rows import dict_row
except Exception:
    psycopg = None
    dict_row = None

try:
    from psycopg_pool import ConnectionPool
except Exception:
    ConnectionPool = None

DB_NAME = os.getenv("PCOSINA_DB_NAME", "pcosina.db").strip() or "pcosina.db"
DATABASE_URL = os.getenv("DATABASE_URL", "").strip()
SCHEMA_MIGRATION_SCOPE = "app"
SCHEMA_BOOTSTRAP_LOCK_KEY = 2026032901
FEEDBACK_MESSAGE_MAX_CHARS = 2000
_POSTGRES_POOL = None
_POSTGRES_POOL_SIGNATURE: tuple[str, int, int, float] | None = None
_POSTGRES_IDENTIFIER_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def _is_production_env() -> bool:
    return os.getenv("PCOSINA_ENV", "development").strip().lower() in ("prod", "production")

ING_SYNONYMS = {
    "baboy": "pork",
    "liempo": "pork",
    "lechon": "pork",
    "litson": "pork",
    "baka": "beef",
    "bulalo": "beef",
    "tapa": "beef",
    "manok": "chicken",
    "isda": "fish",
    "hipon": "shrimp",
    "pusit": "squid",
    "gatas": "dairy",
    "keso": "cheese",
    "itlog": "egg",
    "tokwa": "tofu",
}

MEAT_TOKENS = {"pork", "beef", "chicken", "meat", "lamb", "goat", "duck"}
SEAFOOD_TOKENS = {"fish", "shrimp", "squid", "tuna", "salmon", "crab", "seafood"}
DAIRY_TOKENS = {"dairy", "milk", "cheese", "yogurt", "cream", "butter"}
EGG_TOKENS = {"egg"}

def _normalize_token(t: str) -> str:
    t = "".join(ch for ch in t.lower() if ch.isalnum() or ch in ("_", "-"))
    return ING_SYNONYMS.get(t, t)

def _normalize_ingredients(ings):
    tokens = []
    for ing in ings or []:
        name = ""
        if isinstance(ing, dict):
            name = str(ing.get("name", ""))
        else:
            name = str(ing)
        for raw in name.replace("/", " ").replace("-", " ").split():
            tok = _normalize_token(raw)
            if tok:
                tokens.append(tok)
    return tokens

def _infer_tags(recipe):
    tags = set([t.lower() for t in recipe.get("tags", []) if t])
    tokens = set(_normalize_ingredients(recipe.get("ingredients", [])))
    if tokens & MEAT_TOKENS: tags.add("contains_meat")
    if tokens & SEAFOOD_TOKENS: tags.add("contains_seafood")
    if tokens & DAIRY_TOKENS: tags.add("contains_dairy")
    if tokens & EGG_TOKENS: tags.add("contains_egg")

    nut = recipe.get("nutrition", {})
    p = nut.get("protein_g") or 0
    c = nut.get("carbs_g") or 0
    fiber = nut.get("fiber_g") or 0
    if p >= 25: tags.add("high_protein")
    if fiber >= 8: tags.add("high_fiber")
    if c <= 35: tags.add("low_carb")
    return list(tags)


def _median(values: list[int]) -> int:
    if not values:
        return 0
    sorted_vals = sorted(values)
    mid = len(sorted_vals) // 2
    if len(sorted_vals) % 2 == 1:
        return int(sorted_vals[mid])
    return int((sorted_vals[mid - 1] + sorted_vals[mid]) / 2)


def _compute_nutrition_medians(recipes: list[dict]) -> dict:
    calories = []
    protein = []
    carbs = []
    fats = []
    fiber = []
    for r in recipes:
        nut = r.get("nutrition", {}) or {}
        for key, bucket in [
            ("calories", calories),
            ("protein_g", protein),
            ("carbs_g", carbs),
            ("fat_g", fats),
            ("fiber_g", fiber),
        ]:
            raw = nut.get(key)
            if raw is not None and raw != 0:
                try:
                    bucket.append(int(raw))
                except Exception:
                    continue
    return {
        "calories": _median(calories) or 500,
        "protein_g": _median(protein) or 25,
        "carbs_g": _median(carbs) or 45,
        "fat_g": _median(fats) or 15,
        "fiber_g": _median(fiber) or 6,
    }


def _normalize_nutrition(nut: dict, medians: dict) -> tuple[int, int, int, int, int]:
    def pick(key: str, default: int) -> int:
        raw = nut.get(key)
        if raw is None or raw == 0:
            return default
        try:
            return max(0, int(raw))
        except Exception:
            return default
    cal = pick("calories", medians["calories"])
    prot = pick("protein_g", medians["protein_g"])
    carb = pick("carbs_g", medians["carbs_g"])
    fat = pick("fat_g", medians["fat_g"])
    fiber = pick("fiber_g", medians["fiber_g"])
    return cal, prot, carb, fat, fiber

def _use_postgres() -> bool:
    return is_postgres_database_url(DATABASE_URL)

def db_mode() -> str:
    return "postgres" if _use_postgres() else "sqlite"

def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name, "").strip().lower()
    if raw in ("1", "true", "yes", "on"):
        return True
    if raw in ("0", "false", "no", "off"):
        return False
    return default


def _postgres_pool_enabled() -> bool:
    return _env_bool("PCOSINA_DB_POOL_ENABLED", True)


def _postgres_pool_config() -> tuple[int, int, float]:
    min_size = max(0, int(os.getenv("PCOSINA_DB_POOL_MIN_SIZE", "1") or 1))
    max_size = max(1, int(os.getenv("PCOSINA_DB_POOL_MAX_SIZE", "5") or 5))
    if min_size > max_size:
        min_size = max_size
    timeout = max(1.0, float(os.getenv("PCOSINA_DB_POOL_TIMEOUT_SECONDS", "10") or 10))
    return min_size, max_size, timeout


class _PooledPostgresConnection:
    def __init__(self, pool, conn):
        self._pool = pool
        self._conn = conn
        self._returned = False

    def __getattr__(self, name: str):
        return getattr(self._conn, name)

    def close(self) -> None:
        if self._returned:
            return
        self._returned = True
        try:
            if not getattr(self._conn, "closed", False):
                self._conn.rollback()
        except Exception:
            pass
        self._pool.putconn(self._conn)


def _close_postgres_pool() -> None:
    global _POSTGRES_POOL, _POSTGRES_POOL_SIGNATURE
    if _POSTGRES_POOL is not None:
        try:
            _POSTGRES_POOL.close()
        except Exception:
            pass
    _POSTGRES_POOL = None
    _POSTGRES_POOL_SIGNATURE = None


def _get_postgres_pool():
    global _POSTGRES_POOL, _POSTGRES_POOL_SIGNATURE
    if ConnectionPool is None:
        return None
    min_size, max_size, timeout = _postgres_pool_config()
    signature = (DATABASE_URL, min_size, max_size, timeout)
    if _POSTGRES_POOL is not None and _POSTGRES_POOL_SIGNATURE == signature:
        return _POSTGRES_POOL
    _close_postgres_pool()
    _POSTGRES_POOL = ConnectionPool(
        conninfo=DATABASE_URL,
        min_size=min_size,
        max_size=max_size,
        timeout=timeout,
        open=True,
    )
    _POSTGRES_POOL_SIGNATURE = signature
    return _POSTGRES_POOL


def get_database_connection_pool_status(database_url: str | None = None) -> Dict[str, Any]:
    effective_url = DATABASE_URL if database_url is None else str(database_url or "").strip()
    mode = "postgres" if is_postgres_database_url(effective_url) else "sqlite"
    min_size, max_size, timeout = _postgres_pool_config()
    return {
        "mode": mode,
        "enabled": bool(mode == "postgres" and _postgres_pool_enabled()),
        "driverAvailable": bool(ConnectionPool is not None),
        "minSize": min_size,
        "maxSize": max_size,
        "timeoutSeconds": timeout,
        "open": bool(_POSTGRES_POOL is not None),
    }


def _connect():
    if _use_postgres():
        if psycopg is None:
            raise RuntimeError("psycopg is not installed. Add psycopg[binary] to requirements.")
        if _postgres_pool_enabled():
            pool = _get_postgres_pool()
            if pool is not None:
                return _PooledPostgresConnection(pool, pool.getconn())
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
    except Exception:
        conn.rollback()
        raise
    finally:
        cur.execute("SELECT pg_advisory_unlock(%s)", (SCHEMA_BOOTSTRAP_LOCK_KEY,))


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
        cur.execute(
            "SELECT migration_id FROM schema_migrations WHERE scope = %s",
            (scope,),
        )
        rows = cur.fetchall()
        return {str(row.get("migration_id")) for row in rows}
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    cur.execute(
        "SELECT migration_id FROM schema_migrations WHERE scope = ?",
        (scope,),
    )
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

def _create_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS recipes (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL CHECK (char_length(btrim(title)) BETWEEN 1 AND 160),
                meal_type TEXT NOT NULL CHECK (char_length(btrim(meal_type)) BETWEEN 1 AND 80),
                calories INTEGER CHECK (calories BETWEEN 1 AND 3000),
                protein INTEGER CHECK (protein BETWEEN 0 AND 300),
                carbs INTEGER CHECK (carbs BETWEEN 0 AND 500),
                fats INTEGER CHECK (fats BETWEEN 0 AND 250),
                fiber INTEGER CHECK (fiber BETWEEN 0 AND 120),
                tags TEXT CHECK (tags IS NULL OR char_length(tags) <= 4000),
                minutes INTEGER CHECK (minutes BETWEEN 1 AND 480),
                ingredients_json TEXT CHECK (ingredients_json IS NULL OR char_length(ingredients_json) <= 200000),
                steps_json TEXT CHECK (steps_json IS NULL OR char_length(steps_json) <= 200000),
                active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                source TEXT NOT NULL DEFAULT 'seed' CHECK (char_length(source) <= 40),
                source_version TEXT CHECK (source_version IS NULL OR char_length(source_version) <= 120),
                created_at BIGINT NOT NULL DEFAULT 0 CHECK (created_at >= 0),
                updated_at BIGINT NOT NULL DEFAULT 0 CHECK (updated_at >= 0),
                deleted_at BIGINT NOT NULL DEFAULT 0 CHECK (deleted_at >= 0)
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS recipes (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 160),
            meal_type TEXT NOT NULL CHECK (length(trim(meal_type)) BETWEEN 1 AND 80),
            calories INTEGER CHECK (calories BETWEEN 1 AND 3000),
            protein INTEGER CHECK (protein BETWEEN 0 AND 300),
            carbs INTEGER CHECK (carbs BETWEEN 0 AND 500),
            fats INTEGER CHECK (fats BETWEEN 0 AND 250),
            fiber INTEGER CHECK (fiber BETWEEN 0 AND 120),
            tags TEXT CHECK (tags IS NULL OR length(tags) <= 4000),
            minutes INTEGER CHECK (minutes BETWEEN 1 AND 480),
            ingredients_json TEXT CHECK (ingredients_json IS NULL OR length(ingredients_json) <= 200000),
            steps_json TEXT CHECK (steps_json IS NULL OR length(steps_json) <= 200000),
            active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
            source TEXT NOT NULL DEFAULT 'seed' CHECK (length(source) <= 40),
            source_version TEXT CHECK (source_version IS NULL OR length(source_version) <= 120),
            created_at INTEGER NOT NULL DEFAULT 0 CHECK (created_at >= 0),
            updated_at INTEGER NOT NULL DEFAULT 0 CHECK (updated_at >= 0),
            deleted_at INTEGER NOT NULL DEFAULT 0 CHECK (deleted_at >= 0)
        )
    """


def _create_ingredient_price_rules_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS ingredient_price_rules (
                id TEXT PRIMARY KEY,
                keywords_json TEXT NOT NULL,
                price_php INTEGER NOT NULL CHECK (price_php BETWEEN 1 AND 1000000),
                price_min_php INTEGER CHECK (price_min_php IS NULL OR price_min_php BETWEEN 0 AND 1000000),
                price_max_php INTEGER CHECK (price_max_php IS NULL OR price_max_php BETWEEN 0 AND 1000000),
                category TEXT NOT NULL CHECK (char_length(btrim(category)) BETWEEN 1 AND 80),
                unit TEXT CHECK (unit IS NULL OR char_length(unit) <= 80),
                active BOOLEAN NOT NULL DEFAULT TRUE,
                notes TEXT CHECK (notes IS NULL OR char_length(notes) <= 2000),
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                CHECK (price_min_php IS NULL OR price_max_php IS NULL OR price_min_php <= price_max_php),
                CHECK (price_min_php IS NULL OR price_php >= price_min_php),
                CHECK (price_max_php IS NULL OR price_php <= price_max_php)
            )
        """
    return """
            CREATE TABLE IF NOT EXISTS ingredient_price_rules (
                id TEXT PRIMARY KEY,
                keywords_json TEXT NOT NULL,
                price_php INTEGER NOT NULL CHECK (price_php BETWEEN 1 AND 1000000),
                price_min_php INTEGER CHECK (price_min_php IS NULL OR price_min_php BETWEEN 0 AND 1000000),
                price_max_php INTEGER CHECK (price_max_php IS NULL OR price_max_php BETWEEN 0 AND 1000000),
                category TEXT NOT NULL CHECK (length(trim(category)) BETWEEN 1 AND 80),
                unit TEXT CHECK (unit IS NULL OR length(unit) <= 80),
                active INTEGER NOT NULL DEFAULT 1,
                notes TEXT CHECK (notes IS NULL OR length(notes) <= 2000),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                CHECK (price_min_php IS NULL OR price_max_php IS NULL OR price_min_php <= price_max_php),
                CHECK (price_min_php IS NULL OR price_php >= price_min_php),
                CHECK (price_max_php IS NULL OR price_php <= price_max_php)
            )
        """


def _create_recipe_nutrition_corrections_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS recipe_nutrition_corrections (
                id TEXT PRIMARY KEY,
                recipe_id TEXT NOT NULL UNIQUE,
                calories INTEGER CHECK (calories IS NULL OR calories BETWEEN 1 AND 3000),
                protein INTEGER CHECK (protein IS NULL OR protein BETWEEN 0 AND 300),
                carbs INTEGER CHECK (carbs IS NULL OR carbs BETWEEN 0 AND 500),
                fats INTEGER CHECK (fats IS NULL OR fats BETWEEN 0 AND 250),
                fiber INTEGER CHECK (fiber IS NULL OR fiber BETWEEN 0 AND 120),
                sodium_mg INTEGER CHECK (sodium_mg IS NULL OR sodium_mg BETWEEN 0 AND 10000),
                sugar_grams INTEGER CHECK (sugar_grams IS NULL OR sugar_grams BETWEEN 0 AND 250),
                active BOOLEAN NOT NULL DEFAULT TRUE,
                notes TEXT CHECK (notes IS NULL OR char_length(notes) <= 2000),
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                CHECK (
                    calories IS NOT NULL OR protein IS NOT NULL OR carbs IS NOT NULL OR
                    fats IS NOT NULL OR fiber IS NOT NULL OR sodium_mg IS NOT NULL OR sugar_grams IS NOT NULL
                )
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS recipe_nutrition_corrections (
                id TEXT PRIMARY KEY,
                recipe_id TEXT NOT NULL UNIQUE,
                calories INTEGER CHECK (calories IS NULL OR calories BETWEEN 1 AND 3000),
                protein INTEGER CHECK (protein IS NULL OR protein BETWEEN 0 AND 300),
                carbs INTEGER CHECK (carbs IS NULL OR carbs BETWEEN 0 AND 500),
                fats INTEGER CHECK (fats IS NULL OR fats BETWEEN 0 AND 250),
                fiber INTEGER CHECK (fiber IS NULL OR fiber BETWEEN 0 AND 120),
                sodium_mg INTEGER CHECK (sodium_mg IS NULL OR sodium_mg BETWEEN 0 AND 10000),
                sugar_grams INTEGER CHECK (sugar_grams IS NULL OR sugar_grams BETWEEN 0 AND 250),
                active INTEGER NOT NULL DEFAULT 1,
                notes TEXT CHECK (notes IS NULL OR length(notes) <= 2000),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                CHECK (
                    calories IS NOT NULL OR protein IS NOT NULL OR carbs IS NOT NULL OR
                    fats IS NOT NULL OR fiber IS NOT NULL OR sodium_mg IS NOT NULL OR sugar_grams IS NOT NULL
                )
            )
        """

def _ensure_recipe_columns(conn):
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS minutes INTEGER")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS ingredients_json TEXT")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS steps_json TEXT")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS active INTEGER NOT NULL DEFAULT 1")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'legacy'")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS source_version TEXT")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS updated_at BIGINT NOT NULL DEFAULT 0")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS deleted_at BIGINT NOT NULL DEFAULT 0")
    else:
        cur.execute("PRAGMA table_info(recipes)")
        cols = {row[1] for row in cur.fetchall()}
        if "minutes" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN minutes INTEGER")
        if "ingredients_json" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN ingredients_json TEXT")
        if "steps_json" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN steps_json TEXT")
        if "active" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
        if "source" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN source TEXT NOT NULL DEFAULT 'legacy'")
        if "source_version" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN source_version TEXT")
        if "created_at" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
        if "updated_at" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
        if "deleted_at" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0")
    cur.execute("UPDATE recipes SET minutes = COALESCE(minutes, 25)")
    cur.execute("UPDATE recipes SET minutes = 25 WHERE minutes < 1 OR minutes > 480")
    cur.execute("UPDATE recipes SET calories = 500 WHERE calories IS NULL OR calories < 1 OR calories > 3000")
    cur.execute("UPDATE recipes SET protein = 25 WHERE protein IS NULL OR protein < 0 OR protein > 300")
    cur.execute("UPDATE recipes SET carbs = 45 WHERE carbs IS NULL OR carbs < 0 OR carbs > 500")
    cur.execute("UPDATE recipes SET fats = 15 WHERE fats IS NULL OR fats < 0 OR fats > 250")
    cur.execute("UPDATE recipes SET fiber = 6 WHERE fiber IS NULL OR fiber < 0 OR fiber > 120")
    cur.execute("UPDATE recipes SET ingredients_json = COALESCE(ingredients_json, '[]')")
    cur.execute("UPDATE recipes SET steps_json = COALESCE(steps_json, '[]')")
    cur.execute("UPDATE recipes SET active = COALESCE(active, 1)")
    cur.execute("UPDATE recipes SET source = COALESCE(source, 'legacy')")
    cur.execute("UPDATE recipes SET created_at = COALESCE(created_at, 0)")
    cur.execute("UPDATE recipes SET updated_at = COALESCE(NULLIF(updated_at, 0), created_at, 0)")
    cur.execute("UPDATE recipes SET deleted_at = COALESCE(deleted_at, 0)")


def _ensure_plan_jobs_columns(conn):
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("ALTER TABLE plan_jobs ADD COLUMN IF NOT EXISTS request_json TEXT")
        cur.execute("ALTER TABLE plan_jobs ADD COLUMN IF NOT EXISTS idempotency_key TEXT")
        cur.execute("ALTER TABLE plan_jobs ADD COLUMN IF NOT EXISTS worker_id TEXT")
        cur.execute("ALTER TABLE plan_jobs ADD COLUMN IF NOT EXISTS owner_uid TEXT")
        cur.execute("ALTER TABLE plan_jobs ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0")
        cur.execute("ALTER TABLE plan_jobs ADD COLUMN IF NOT EXISTS next_attempt_at BIGINT NOT NULL DEFAULT 0")
    else:
        cur.execute("PRAGMA table_info(plan_jobs)")
        cols = {row[1] for row in cur.fetchall()}
        if "request_json" not in cols:
            cur.execute("ALTER TABLE plan_jobs ADD COLUMN request_json TEXT")
        if "idempotency_key" not in cols:
            cur.execute("ALTER TABLE plan_jobs ADD COLUMN idempotency_key TEXT")
        if "worker_id" not in cols:
            cur.execute("ALTER TABLE plan_jobs ADD COLUMN worker_id TEXT")
        if "owner_uid" not in cols:
            cur.execute("ALTER TABLE plan_jobs ADD COLUMN owner_uid TEXT")
        if "attempt_count" not in cols:
            cur.execute("ALTER TABLE plan_jobs ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0")
        if "next_attempt_at" not in cols:
            cur.execute("ALTER TABLE plan_jobs ADD COLUMN next_attempt_at INTEGER NOT NULL DEFAULT 0")
    cur.execute("UPDATE plan_jobs SET next_attempt_at = COALESCE(next_attempt_at, created_at, 0) WHERE next_attempt_at = 0")
    
def _create_feedback_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS feedback (
                id SERIAL PRIMARY KEY,
                message TEXT NOT NULL CHECK (char_length(message) <= 2000),
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS feedback (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message TEXT NOT NULL CHECK (length(message) <= 2000),
            created_at INTEGER NOT NULL
        )
    """


def _normalize_feedback_message(message: str) -> str:
    text = str(message or "").strip()
    if not text:
        raise ValueError("Feedback message is required")
    if len(text) > FEEDBACK_MESSAGE_MAX_CHARS:
        raise ValueError(f"Feedback message exceeds {FEEDBACK_MESSAGE_MAX_CHARS} characters")
    return text


def _trim_oversized_feedback_messages(conn) -> None:
    cur = conn.cursor()
    if _use_postgres():
        cur.execute(
            """
            UPDATE feedback
            SET message = LEFT(message, %s)
            WHERE char_length(message) > %s
            """,
            (FEEDBACK_MESSAGE_MAX_CHARS, FEEDBACK_MESSAGE_MAX_CHARS),
        )
        return
    cur.execute(
        """
        UPDATE feedback
        SET message = substr(message, 1, ?)
        WHERE length(message) > ?
        """,
        (FEEDBACK_MESSAGE_MAX_CHARS, FEEDBACK_MESSAGE_MAX_CHARS),
    )


def _ensure_feedback_constraints(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_feedback_table_sql())
    _trim_oversized_feedback_messages(conn)
    if _use_postgres():
        cur.execute(
            """
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conname = 'feedback_message_length_check'
                ) THEN
                    ALTER TABLE feedback
                    ADD CONSTRAINT feedback_message_length_check
                    CHECK (char_length(message) <= 2000);
                END IF;
            END $$;
            """
        )


def _create_admin_action_logs_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS admin_action_logs (
                id BIGSERIAL PRIMARY KEY,
                action TEXT NOT NULL,
                actor TEXT,
                resource_type TEXT NOT NULL,
                resource_id TEXT,
                details_json TEXT,
                created_at BIGINT NOT NULL
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS admin_action_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            action TEXT NOT NULL,
            actor TEXT,
            resource_type TEXT NOT NULL,
            resource_id TEXT,
            details_json TEXT,
            created_at INTEGER NOT NULL
        )
    """


def _create_admin_sessions_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS admin_sessions (
                id TEXT PRIMARY KEY,
                uid TEXT NOT NULL,
                email TEXT,
                actor TEXT NOT NULL,
                roles_json TEXT NOT NULL,
                auth_type TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                last_seen_at BIGINT NOT NULL,
                revoked_at BIGINT,
                revoked_by TEXT,
                revoke_reason TEXT
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS admin_sessions (
            id TEXT PRIMARY KEY,
            uid TEXT NOT NULL,
            email TEXT,
            actor TEXT NOT NULL,
            roles_json TEXT NOT NULL,
            auth_type TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            expires_at INTEGER NOT NULL,
            last_seen_at INTEGER NOT NULL,
            revoked_at INTEGER,
            revoked_by TEXT,
            revoke_reason TEXT
        )
    """


def _create_operator_access_overrides_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS operator_access_overrides (
                uid TEXT PRIMARY KEY,
                email TEXT,
                blocked BOOLEAN NOT NULL DEFAULT TRUE,
                reason TEXT,
                updated_by TEXT,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS operator_access_overrides (
            uid TEXT PRIMARY KEY,
            email TEXT,
            blocked INTEGER NOT NULL DEFAULT 1,
            reason TEXT,
            updated_by TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """


def _create_support_cases_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS support_cases (
                id TEXT PRIMARY KEY,
                user_uid TEXT NOT NULL,
                related_job_id TEXT,
                status TEXT NOT NULL,
                priority TEXT NOT NULL,
                assignee TEXT,
                escalated BOOLEAN NOT NULL DEFAULT FALSE,
                summary TEXT NOT NULL,
                notes_json TEXT NOT NULL,
                created_by TEXT,
                updated_by TEXT,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS support_cases (
            id TEXT PRIMARY KEY,
            user_uid TEXT NOT NULL,
            related_job_id TEXT,
            status TEXT NOT NULL,
            priority TEXT NOT NULL,
            assignee TEXT,
            escalated INTEGER NOT NULL DEFAULT 0,
            summary TEXT NOT NULL,
            notes_json TEXT NOT NULL,
            created_by TEXT,
            updated_by TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        )
    """

def _create_plan_jobs_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS plan_jobs (
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                request_json TEXT,
                result_json TEXT,
                error TEXT,
                idempotency_key TEXT,
                worker_id TEXT,
                owner_uid TEXT,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                next_attempt_at BIGINT NOT NULL DEFAULT 0
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS plan_jobs (
            id TEXT PRIMARY KEY,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            request_json TEXT,
            result_json TEXT,
            error TEXT,
            idempotency_key TEXT,
            worker_id TEXT,
            owner_uid TEXT,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            next_attempt_at INTEGER NOT NULL DEFAULT 0
        )
    """


def _create_plan_job_diagnostics_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS plan_job_diagnostics (
                metric_key TEXT PRIMARY KEY,
                metric_value BIGINT NOT NULL DEFAULT 0,
                updated_at BIGINT NOT NULL
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS plan_job_diagnostics (
            metric_key TEXT PRIMARY KEY,
            metric_value INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL
        )
    """


def _create_ml_events_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS ml_events (
                id TEXT PRIMARY KEY,
                event_name TEXT NOT NULL,
                uid_hash TEXT NOT NULL,
                request_id TEXT NOT NULL,
                plan_id TEXT,
                recipe_id TEXT,
                slot_index INTEGER,
                event_time_ms BIGINT NOT NULL,
                policy_version TEXT NOT NULL,
                schema_version TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                dedupe_key TEXT
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS ml_events (
            id TEXT PRIMARY KEY,
            event_name TEXT NOT NULL,
            uid_hash TEXT NOT NULL,
            request_id TEXT NOT NULL,
            plan_id TEXT,
            recipe_id TEXT,
            slot_index INTEGER,
            event_time_ms INTEGER NOT NULL,
            policy_version TEXT NOT NULL,
            schema_version TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            dedupe_key TEXT
        )
    """


def _create_ml_stage1_candidates_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS ml_stage1_candidate_features (
                id TEXT PRIMARY KEY,
                request_id TEXT NOT NULL,
                uid_hash TEXT NOT NULL,
                recipe_id TEXT NOT NULL,
                meal_bucket TEXT,
                generated_at_ms BIGINT NOT NULL,
                selected_by_solver INTEGER,
                model_score DOUBLE PRECISION,
                heuristic_score DOUBLE PRECISION,
                ranking_strategy TEXT NOT NULL,
                model_version TEXT,
                feature_json TEXT NOT NULL,
                created_at BIGINT NOT NULL
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS ml_stage1_candidate_features (
            id TEXT PRIMARY KEY,
            request_id TEXT NOT NULL,
            uid_hash TEXT NOT NULL,
            recipe_id TEXT NOT NULL,
            meal_bucket TEXT,
            generated_at_ms INTEGER NOT NULL,
            selected_by_solver INTEGER,
            model_score REAL,
            heuristic_score REAL,
            ranking_strategy TEXT NOT NULL,
            model_version TEXT,
            feature_json TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )
    """


def _ensure_ml_indexes(conn) -> None:
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_events_name_time ON ml_events(event_name, event_time_ms)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_events_request ON ml_events(request_id)")
        cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_ml_events_dedupe ON ml_events(dedupe_key)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_stage1_req ON ml_stage1_candidate_features(request_id)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_stage1_time ON ml_stage1_candidate_features(generated_at_ms)")
    else:
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_events_name_time ON ml_events(event_name, event_time_ms)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_events_request ON ml_events(request_id)")
        cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_ml_events_dedupe ON ml_events(dedupe_key)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_stage1_req ON ml_stage1_candidate_features(request_id)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_ml_stage1_time ON ml_stage1_candidate_features(generated_at_ms)")


def _ensure_plan_job_indexes(conn) -> None:
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("CREATE INDEX IF NOT EXISTS idx_plan_jobs_status_time ON plan_jobs(status, next_attempt_at, created_at)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_plan_jobs_owner ON plan_jobs(owner_uid, created_at)")
    else:
        cur.execute("CREATE INDEX IF NOT EXISTS idx_plan_jobs_status_time ON plan_jobs(status, next_attempt_at, created_at)")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_plan_jobs_owner ON plan_jobs(owner_uid, created_at)")


def _ensure_admin_action_log_indexes(conn) -> None:
    cur = conn.cursor()
    cur.execute("CREATE INDEX IF NOT EXISTS idx_admin_action_logs_created ON admin_action_logs(created_at)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_admin_action_logs_resource ON admin_action_logs(resource_type, resource_id)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_admin_action_logs_actor ON admin_action_logs(actor, created_at)")


def _ensure_admin_session_indexes(conn) -> None:
    cur = conn.cursor()
    cur.execute("CREATE INDEX IF NOT EXISTS idx_admin_sessions_uid_active ON admin_sessions(uid, revoked_at, expires_at)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_admin_sessions_actor_created ON admin_sessions(actor, created_at DESC)")


def _ensure_operator_access_override_indexes(conn) -> None:
    cur = conn.cursor()
    cur.execute("CREATE INDEX IF NOT EXISTS idx_operator_access_email ON operator_access_overrides(email)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_operator_access_blocked ON operator_access_overrides(blocked, updated_at DESC)")


def _ensure_support_case_indexes(conn) -> None:
    cur = conn.cursor()
    cur.execute("CREATE INDEX IF NOT EXISTS idx_support_cases_user_updated ON support_cases(user_uid, updated_at DESC)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_support_cases_status_updated ON support_cases(status, updated_at DESC)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_support_cases_related_job ON support_cases(related_job_id)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_support_cases_assignee_status ON support_cases(assignee, status, updated_at DESC)")


def _ensure_recipe_catalog_indexes(conn) -> None:
    cur = conn.cursor()
    cur.execute("CREATE INDEX IF NOT EXISTS idx_recipes_active_meal_type ON recipes(active, meal_type)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_recipes_updated_at ON recipes(updated_at)")


def _ensure_support_case_columns(conn) -> None:
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("ALTER TABLE support_cases ADD COLUMN IF NOT EXISTS assignee TEXT")
        cur.execute("ALTER TABLE support_cases ADD COLUMN IF NOT EXISTS escalated BOOLEAN NOT NULL DEFAULT FALSE")
        return

    cur.execute("PRAGMA table_info(support_cases)")
    columns = {str(row[1]).lower() for row in cur.fetchall()}
    if "assignee" not in columns:
        cur.execute("ALTER TABLE support_cases ADD COLUMN assignee TEXT")
    if "escalated" not in columns:
        cur.execute("ALTER TABLE support_cases ADD COLUMN escalated INTEGER NOT NULL DEFAULT 0")


def _migration_create_core_tables(conn) -> None:
    cursor = conn.cursor()
    cursor.execute(_create_table_sql())
    cursor.execute(_create_feedback_table_sql())
    cursor.execute(_create_admin_action_logs_table_sql())
    cursor.execute(_create_admin_sessions_table_sql())
    cursor.execute(_create_support_cases_table_sql())
    cursor.execute(_create_plan_jobs_table_sql())
    cursor.execute(_create_plan_job_diagnostics_table_sql())
    cursor.execute(_create_ml_events_table_sql())
    cursor.execute(_create_ml_stage1_candidates_table_sql())


def _migration_recipe_columns(conn) -> None:
    _ensure_recipe_columns(conn)
    _ensure_recipe_catalog_indexes(conn)


def _migration_plan_job_columns(conn) -> None:
    _ensure_plan_jobs_columns(conn)


def _migration_indexes(conn) -> None:
    _ensure_plan_job_indexes(conn)
    _ensure_admin_action_log_indexes(conn)
    _ensure_admin_session_indexes(conn)
    _ensure_support_case_indexes(conn)
    _ensure_ml_indexes(conn)


def _migration_price_rule_tables(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_ingredient_price_rules_table_sql())
    _ensure_price_rule_range_columns(conn)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_price_rules_active_updated ON ingredient_price_rules(active, updated_at DESC)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_price_rules_category ON ingredient_price_rules(category)")


def _ensure_price_rule_range_columns(conn) -> None:
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("ALTER TABLE ingredient_price_rules ADD COLUMN IF NOT EXISTS price_min_php INTEGER")
        cur.execute("ALTER TABLE ingredient_price_rules ADD COLUMN IF NOT EXISTS price_max_php INTEGER")
        return
    cur.execute("PRAGMA table_info(ingredient_price_rules)")
    columns = {row[1] for row in cur.fetchall()}
    if "price_min_php" not in columns:
        cur.execute("ALTER TABLE ingredient_price_rules ADD COLUMN price_min_php INTEGER")
    if "price_max_php" not in columns:
        cur.execute("ALTER TABLE ingredient_price_rules ADD COLUMN price_max_php INTEGER")


def _migration_price_rule_ranges(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_ingredient_price_rules_table_sql())
    _ensure_price_rule_range_columns(conn)


def _migration_feedback_constraints(conn) -> None:
    _ensure_feedback_constraints(conn)


def _migration_recipe_catalog_metadata(conn) -> None:
    _ensure_recipe_columns(conn)
    _ensure_recipe_catalog_indexes(conn)


def _quote_postgres_identifier(identifier: str) -> str:
    parts = str(identifier or "").split(".")
    if not parts or any(not _POSTGRES_IDENTIFIER_RE.fullmatch(part) for part in parts):
        raise ValueError(f"Unsafe PostgreSQL identifier: {identifier!r}")
    return ".".join(f'"{part}"' for part in parts)


def _add_postgres_check_constraint(cur, table_name: str, constraint_name: str, expression: str) -> None:
    cur.execute(
        """
        SELECT 1
        FROM pg_constraint
        WHERE conname = %s::name
          AND conrelid = %s::regclass
        LIMIT 1
        """,
        (constraint_name, table_name),
    )
    if cur.fetchone():
        return

    table_identifier = _quote_postgres_identifier(table_name)
    constraint_identifier = _quote_postgres_identifier(constraint_name)
    cur.execute(
        f"""
        ALTER TABLE {table_identifier}
        ADD CONSTRAINT {constraint_identifier}
        CHECK ({expression}) NOT VALID
        """
    )


def _migration_admin_content_constraints(conn) -> None:
    if not _use_postgres():
        return
    cur = conn.cursor()
    cur.execute("UPDATE recipes SET minutes = 25 WHERE minutes IS NULL OR minutes < 1 OR minutes > 480")
    cur.execute("UPDATE recipes SET calories = 500 WHERE calories IS NULL OR calories < 1 OR calories > 3000")
    cur.execute("UPDATE recipes SET protein = 25 WHERE protein IS NULL OR protein < 0 OR protein > 300")
    cur.execute("UPDATE recipes SET carbs = 45 WHERE carbs IS NULL OR carbs < 0 OR carbs > 500")
    cur.execute("UPDATE recipes SET fats = 15 WHERE fats IS NULL OR fats < 0 OR fats > 250")
    cur.execute("UPDATE recipes SET fiber = 6 WHERE fiber IS NULL OR fiber < 0 OR fiber > 120")
    for table_name, constraint_name, expression in [
        ("recipes", "recipes_title_bounds", "char_length(btrim(title)) BETWEEN 1 AND 160"),
        ("recipes", "recipes_meal_type_bounds", "char_length(btrim(meal_type)) BETWEEN 1 AND 80"),
        ("recipes", "recipes_calories_bounds", "calories BETWEEN 1 AND 3000"),
        ("recipes", "recipes_macro_bounds", "protein BETWEEN 0 AND 300 AND carbs BETWEEN 0 AND 500 AND fats BETWEEN 0 AND 250 AND fiber BETWEEN 0 AND 120"),
        ("recipes", "recipes_minutes_bounds", "minutes BETWEEN 1 AND 480"),
        ("recipes", "recipes_active_bool_int", "active IN (0, 1)"),
        ("ingredient_price_rules", "price_rules_price_bounds", "price_php BETWEEN 1 AND 1000000"),
        ("ingredient_price_rules", "price_rules_optional_range_bounds", "(price_min_php IS NULL OR price_min_php BETWEEN 0 AND 1000000) AND (price_max_php IS NULL OR price_max_php BETWEEN 0 AND 1000000)"),
        ("ingredient_price_rules", "price_rules_range_order", "price_min_php IS NULL OR price_max_php IS NULL OR price_min_php <= price_max_php"),
        ("ingredient_price_rules", "price_rules_price_in_range", "(price_min_php IS NULL OR price_php >= price_min_php) AND (price_max_php IS NULL OR price_php <= price_max_php)"),
        ("ingredient_price_rules", "price_rules_content_bounds", "char_length(btrim(category)) BETWEEN 1 AND 80 AND (unit IS NULL OR char_length(unit) <= 80) AND (notes IS NULL OR char_length(notes) <= 2000)"),
        ("recipe_nutrition_corrections", "nutrition_corrections_calorie_bounds", "calories IS NULL OR calories BETWEEN 1 AND 3000"),
        ("recipe_nutrition_corrections", "nutrition_corrections_macro_bounds", "(protein IS NULL OR protein BETWEEN 0 AND 300) AND (carbs IS NULL OR carbs BETWEEN 0 AND 500) AND (fats IS NULL OR fats BETWEEN 0 AND 250) AND (fiber IS NULL OR fiber BETWEEN 0 AND 120)"),
        ("recipe_nutrition_corrections", "nutrition_corrections_sodium_sugar_bounds", "(sodium_mg IS NULL OR sodium_mg BETWEEN 0 AND 10000) AND (sugar_grams IS NULL OR sugar_grams BETWEEN 0 AND 250)"),
        ("recipe_nutrition_corrections", "nutrition_corrections_notes_bounds", "notes IS NULL OR char_length(notes) <= 2000"),
        ("recipe_nutrition_corrections", "nutrition_corrections_has_value", "calories IS NOT NULL OR protein IS NOT NULL OR carbs IS NOT NULL OR fats IS NOT NULL OR fiber IS NOT NULL OR sodium_mg IS NOT NULL OR sugar_grams IS NOT NULL"),
    ]:
        _add_postgres_check_constraint(cur, table_name, constraint_name, expression)


def _migration_recipe_nutrition_corrections(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_recipe_nutrition_corrections_table_sql())
    cur.execute("CREATE INDEX IF NOT EXISTS idx_recipe_nutrition_recipe ON recipe_nutrition_corrections(recipe_id)")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_recipe_nutrition_active_updated ON recipe_nutrition_corrections(active, updated_at DESC)")


def _migration_support_cases(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_support_cases_table_sql())
    _ensure_support_case_columns(conn)
    _ensure_support_case_indexes(conn)


def _migration_support_case_handoff_columns(conn) -> None:
    _ensure_support_case_columns(conn)
    _ensure_support_case_indexes(conn)


def _migration_admin_sessions(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_admin_sessions_table_sql())
    _ensure_admin_session_indexes(conn)


def _create_market_seasonality_rules_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS market_seasonality_rules (
                id TEXT PRIMARY KEY,
                category TEXT NOT NULL,
                month_index INTEGER NOT NULL,
                multiplier FLOAT NOT NULL DEFAULT 1.0,
                notes TEXT,
                updated_at BIGINT NOT NULL
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS market_seasonality_rules (
            id TEXT PRIMARY KEY,
            category TEXT NOT NULL,
            month_index INTEGER NOT NULL,
            multiplier REAL NOT NULL DEFAULT 1.0,
            notes TEXT,
            updated_at INTEGER NOT NULL
        )
    """


def _migration_operator_access_overrides(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_operator_access_overrides_table_sql())
    _ensure_operator_access_override_indexes(conn)


def _migration_market_heuristics(conn) -> None:
    cur = conn.cursor()
    cur.execute(_create_market_seasonality_rules_table_sql())
    cur.execute("CREATE INDEX IF NOT EXISTS idx_market_seasonality_cat_month ON market_seasonality_rules(category, month_index)")


def _registered_schema_migrations():
    return [
        ("20260319_app_001_core_tables", "Create core application tables", _migration_create_core_tables),
        ("20260319_app_002_recipe_columns", "Ensure recipe artifact columns", _migration_recipe_columns),
        ("20260319_app_003_plan_job_columns", "Ensure plan job durability columns", _migration_plan_job_columns),
        ("20260319_app_004_indexes", "Ensure operational indexes", _migration_indexes),
        ("20260319_app_005_price_rules", "Create ingredient price rule overrides", _migration_price_rule_tables),
        ("20260319_app_006_recipe_nutrition_corrections", "Create recipe nutrition correction overrides", _migration_recipe_nutrition_corrections),
        ("20260319_app_007_support_cases", "Create support case workflow tables", _migration_support_cases),
        ("20260319_app_008_support_case_handoff", "Add support case assignee and escalation columns", _migration_support_case_handoff_columns),
        ("20260319_app_009_admin_sessions", "Create admin session registry and indexes", _migration_admin_sessions),
        ("20260319_app_010_operator_access_overrides", "Create operator access override registry", _migration_operator_access_overrides),
        ("20260319_app_011_market_heuristics", "Create market seasonality and volatility rules", _migration_market_heuristics),
        ("20260319_app_012_price_rule_ranges", "Add optional ingredient price range columns", _migration_price_rule_ranges),
        ("20260319_app_013_feedback_constraints", "Ensure feedback message length constraints", _migration_feedback_constraints),
        ("20260319_app_014_recipe_catalog_metadata", "Add recipe catalog metadata and soft-delete columns", _migration_recipe_catalog_metadata),
        ("20260319_app_015_admin_content_constraints", "Enforce admin content bounds for planner data", _migration_admin_content_constraints),
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

def cleanup_feedback(retention_days: int = 365) -> int:
    retention_days = max(1, int(retention_days or 365))
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                DELETE FROM feedback
                WHERE created_at < NOW() - (%s::int * INTERVAL '1 day')
                """,
                (retention_days,),
            )
        else:
            cutoff_ms = int(time.time() * 1000) - (retention_days * 24 * 60 * 60 * 1000)
            cur.execute("DELETE FROM feedback WHERE created_at < ?", (cutoff_ms,))
        conn.commit()
        return cur.rowcount or 0
    finally:
        conn.close()

def record_ml_event(event: Dict[str, Any]) -> None:
    event_id = str(event.get("event_id") or uuid.uuid4().hex)
    event_name = str(event.get("event_name") or "")
    uid_hash = str(event.get("uid_hash") or "anonymous")
    request_id = str(event.get("request_id") or "none")
    plan_id = event.get("plan_id")
    recipe_id = event.get("recipe_id")
    slot_index = event.get("slot_index")
    event_time_ms = int(event.get("event_time_ms") or int(time.time() * 1000))
    policy_version = str(event.get("policy_version") or "unknown")
    schema_version = str(event.get("event_schema_version") or "1.0.0")
    payload_json = json.dumps(event, sort_keys=True, ensure_ascii=True)
    created_at = int(time.time() * 1000)
    dedupe_key = str(event.get("dedupe_key") or f"{event_name}|{request_id}|{event_time_ms}")

    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO ml_events (
                    id, event_name, uid_hash, request_id, plan_id, recipe_id, slot_index,
                    event_time_ms, policy_version, schema_version, payload_json, created_at, dedupe_key
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (dedupe_key) DO NOTHING
                """,
                (
                    event_id,
                    event_name,
                    uid_hash,
                    request_id,
                    plan_id,
                    recipe_id,
                    slot_index,
                    event_time_ms,
                    policy_version,
                    schema_version,
                    payload_json,
                    created_at,
                    dedupe_key,
                ),
            )
        else:
            cur.execute(
                """
                INSERT OR IGNORE INTO ml_events (
                    id, event_name, uid_hash, request_id, plan_id, recipe_id, slot_index,
                    event_time_ms, policy_version, schema_version, payload_json, created_at, dedupe_key
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    event_id,
                    event_name,
                    uid_hash,
                    request_id,
                    plan_id,
                    recipe_id,
                    slot_index,
                    event_time_ms,
                    policy_version,
                    schema_version,
                    payload_json,
                    created_at,
                    dedupe_key,
                ),
            )
        conn.commit()
    finally:
        conn.close()


def get_reason_feedback_features(
    uid_hash_value: str,
    *,
    limit: int = 500,
    lookback_days: int = 180,
) -> Dict[str, float]:
    uid_token = str(uid_hash_value or "").strip()
    if not uid_token:
        return {}
    try:
        row_limit = max(1, min(int(limit or 500), 5000))
    except Exception:
        row_limit = 500
    try:
        days = max(1, min(int(lookback_days or 180), 3650))
    except Exception:
        days = 180
    start_time_ms = int(time.time() * 1000) - days * 24 * 60 * 60 * 1000

    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                SELECT payload_json
                FROM ml_events
                WHERE uid_hash = %s
                  AND event_name IN (%s, %s)
                  AND event_time_ms >= %s
                ORDER BY event_time_ms DESC
                LIMIT %s
                """,
                (uid_token, "why_replaced_submitted", "why_skipped_submitted", start_time_ms, row_limit),
            )
        else:
            cur.execute(
                """
                SELECT payload_json
                FROM ml_events
                WHERE uid_hash = ?
                  AND event_name IN (?, ?)
                  AND event_time_ms >= ?
                ORDER BY event_time_ms DESC
                LIMIT ?
                """,
                (uid_token, "why_replaced_submitted", "why_skipped_submitted", start_time_ms, row_limit),
            )
        events: list[Dict[str, Any]] = []
        for row in cur.fetchall():
            payload_json = row[0] if not isinstance(row, dict) else row.get("payload_json")
            try:
                payload = json.loads(payload_json or "{}")
            except Exception:
                continue
            if isinstance(payload, dict):
                events.append(payload)
        from services.ml_features import reason_feedback_features_from_events

        return reason_feedback_features_from_events(events)
    finally:
        conn.close()


def record_stage1_candidate_features(
    request_id: str,
    uid_hash: str,
    candidates: Iterable[Dict[str, Any]],
    selected_recipe_ids: Iterable[str],
    *,
    ranking_strategy: str = "heuristic",
    model_version: Optional[str] = None,
    generated_at_ms: Optional[int] = None,
) -> int:
    rows = list(candidates or [])
    if not rows:
        return 0
    selected_set = {str(rid) for rid in (selected_recipe_ids or [])}
    generated_at_ms = int(generated_at_ms or int(time.time() * 1000))
    created_at = int(time.time() * 1000)

    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("DELETE FROM ml_stage1_candidate_features WHERE request_id = %s", (request_id,))
        else:
            cur.execute("DELETE FROM ml_stage1_candidate_features WHERE request_id = ?", (request_id,))

        for row in rows:
            recipe_id = str(row.get("recipe_id") or "")
            if not recipe_id:
                continue
            meal_bucket = row.get("meal_bucket")
            model_score = row.get("model_score")
            heuristic_score = row.get("heuristic_score")
            feature_json = json.dumps(row.get("features") or {}, sort_keys=True, ensure_ascii=True)
            selected_by_solver = 1 if recipe_id in selected_set else 0
            entry_id = uuid.uuid4().hex
            if _use_postgres():
                cur.execute(
                    """
                    INSERT INTO ml_stage1_candidate_features (
                        id, request_id, uid_hash, recipe_id, meal_bucket, generated_at_ms,
                        selected_by_solver, model_score, heuristic_score, ranking_strategy, model_version,
                        feature_json, created_at
                    )
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        entry_id,
                        request_id,
                        uid_hash,
                        recipe_id,
                        meal_bucket,
                        generated_at_ms,
                        selected_by_solver,
                        model_score,
                        heuristic_score,
                        ranking_strategy,
                        model_version,
                        feature_json,
                        created_at,
                    ),
                )
            else:
                cur.execute(
                    """
                    INSERT INTO ml_stage1_candidate_features (
                        id, request_id, uid_hash, recipe_id, meal_bucket, generated_at_ms,
                        selected_by_solver, model_score, heuristic_score, ranking_strategy, model_version,
                        feature_json, created_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        entry_id,
                        request_id,
                        uid_hash,
                        recipe_id,
                        meal_bucket,
                        generated_at_ms,
                        selected_by_solver,
                        model_score,
                        heuristic_score,
                        ranking_strategy,
                        model_version,
                        feature_json,
                        created_at,
                    ),
                )
        conn.commit()
        return len(rows)
    finally:
        conn.close()


def get_stage1_candidate_features(limit: int = 10000) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 10000), 200000))
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT request_id, uid_hash, recipe_id, meal_bucket, generated_at_ms, selected_by_solver,
                       model_score, heuristic_score, ranking_strategy, model_version, feature_json
                FROM ml_stage1_candidate_features
                ORDER BY generated_at_ms DESC
                LIMIT %s
                """,
                (limit,),
            )
            rows = cur.fetchall()
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT request_id, uid_hash, recipe_id, meal_bucket, generated_at_ms, selected_by_solver,
                       model_score, heuristic_score, ranking_strategy, model_version, feature_json
                FROM ml_stage1_candidate_features
                ORDER BY generated_at_ms DESC
                LIMIT ?
                """,
                (limit,),
            )
            rows = cur.fetchall()
        out: List[Dict[str, Any]] = []
        for row in rows:
            if isinstance(row, dict):
                raw = dict(row)
            else:
                raw = {k: row[k] for k in row.keys()}
            try:
                raw["features"] = json.loads(raw.get("feature_json") or "{}")
            except Exception:
                raw["features"] = {}
            out.append(raw)
        return out
    finally:
        conn.close()


def clear_stage1_candidate_features(request_prefix: str | None = None) -> int:
    conn = _connect()
    try:
        cur = conn.cursor()
        if request_prefix:
            like_value = f"{request_prefix}%"
            if _use_postgres():
                cur.execute("DELETE FROM ml_stage1_candidate_features WHERE request_id LIKE %s", (like_value,))
            else:
                cur.execute("DELETE FROM ml_stage1_candidate_features WHERE request_id LIKE ?", (like_value,))
        else:
            cur.execute("DELETE FROM ml_stage1_candidate_features")
        deleted = int(cur.rowcount or 0)
        conn.commit()
        return deleted
    finally:
        conn.close()


def get_stage1_candidate_stats() -> Dict[str, Any]:
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT
                    COUNT(*) AS rows,
                    COUNT(DISTINCT request_id) AS unique_requests,
                    COUNT(DISTINCT uid_hash) AS unique_users,
                    SUM(CASE WHEN selected_by_solver = 1 THEN 1 ELSE 0 END) AS positives
                FROM ml_stage1_candidate_features
                """
            )
            row = cur.fetchone() or {}
            rows = int(row.get("rows") or 0)
            positives = int(row.get("positives") or 0)
            return {
                "rows": rows,
                "positives": positives,
                "negative": max(0, rows - positives),
                "positive_rate": (float(positives) / float(rows)) if rows > 0 else 0.0,
                "unique_requests": int(row.get("unique_requests") or 0),
                "unique_users": int(row.get("unique_users") or 0),
            }

        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        cur.execute(
            """
            SELECT
                COUNT(*) AS rows,
                COUNT(DISTINCT request_id) AS unique_requests,
                COUNT(DISTINCT uid_hash) AS unique_users,
                SUM(CASE WHEN selected_by_solver = 1 THEN 1 ELSE 0 END) AS positives
            FROM ml_stage1_candidate_features
            """
        )
        row = cur.fetchone()
        rows = int(row["rows"] or 0) if row else 0
        positives = int(row["positives"] or 0) if row else 0
        return {
            "rows": rows,
            "positives": positives,
            "negative": max(0, rows - positives),
            "positive_rate": (float(positives) / float(rows)) if rows > 0 else 0.0,
            "unique_requests": int(row["unique_requests"] or 0) if row else 0,
            "unique_users": int(row["unique_users"] or 0) if row else 0,
        }
    finally:
        conn.close()

def delete_feedback_by_id(feedback_id: int) -> int:
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("DELETE FROM feedback WHERE id = %s", (feedback_id,))
        else:
            cur.execute("DELETE FROM feedback WHERE id = ?", (feedback_id,))
        conn.commit()
        return cur.rowcount or 0
    finally:
        conn.close()


def log_admin_action(
    action: str,
    *,
    actor: str | None = None,
    resource_type: str,
    resource_id: str | None = None,
    details: Dict[str, Any] | None = None,
) -> None:
    conn = _connect()
    try:
        cur = conn.cursor()
        now = int(time.time() * 1000)
        details_json = json.dumps(details or {}, sort_keys=True, ensure_ascii=True)
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO admin_action_logs (action, actor, resource_type, resource_id, details_json, created_at)
                VALUES (%s, %s, %s, %s, %s, %s)
                """,
                (action, actor, resource_type, resource_id, details_json, now),
            )
        else:
            cur.execute(
                """
                INSERT INTO admin_action_logs (action, actor, resource_type, resource_id, details_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (action, actor, resource_type, resource_id, details_json, now),
            )
        conn.commit()
    finally:
        conn.close()


def list_admin_action_logs(
    limit: int = 100,
    resource_type: str | None = None,
    action: str | None = None,
    resource_id: str | None = None,
    actor: str | None = None,
) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 100), 500))
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT id, action, actor, resource_type, resource_id, details_json, created_at
                FROM admin_action_logs
                WHERE (%s IS NULL OR resource_type = %s)
                  AND (%s IS NULL OR action = %s)
                  AND (%s IS NULL OR resource_id = %s)
                  AND (%s IS NULL OR actor = %s)
                ORDER BY created_at DESC, id DESC
                LIMIT %s
                """,
                (resource_type, resource_type, action, action, resource_id, resource_id, actor, actor, limit),
            )
            rows = cur.fetchall()
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT id, action, actor, resource_type, resource_id, details_json, created_at
                FROM admin_action_logs
                WHERE (? IS NULL OR resource_type = ?)
                  AND (? IS NULL OR action = ?)
                  AND (? IS NULL OR resource_id = ?)
                  AND (? IS NULL OR actor = ?)
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                (resource_type, resource_type, action, action, resource_id, resource_id, actor, actor, limit),
            )
            rows = cur.fetchall()
        out: List[Dict[str, Any]] = []
        for row in rows:
            raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
            try:
                raw["details"] = json.loads(raw.get("details_json") or "{}")
            except Exception:
                raw["details"] = {}
            out.append(raw)
        return out
    finally:
        conn.close()


def _admin_session_row_to_dict(row: Any) -> Dict[str, Any]:
    raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
    try:
        roles = json.loads(raw.get("roles_json") or "[]")
    except Exception:
        roles = []
    if not isinstance(roles, list):
        roles = []
    return {
        "id": raw.get("id"),
        "uid": raw.get("uid"),
        "email": raw.get("email"),
        "actor": raw.get("actor"),
        "roles": [str(item) for item in roles if str(item).strip()],
        "authType": raw.get("auth_type"),
        "createdAt": int(raw.get("created_at") or 0),
        "expiresAt": int(raw.get("expires_at") or 0),
        "lastSeenAt": int(raw.get("last_seen_at") or 0),
        "revokedAt": int(raw.get("revoked_at") or 0) if raw.get("revoked_at") is not None else None,
        "revokedBy": raw.get("revoked_by"),
        "revokeReason": raw.get("revoke_reason"),
    }


def register_admin_session(
    *,
    session_id: str,
    uid: str,
    email: str | None,
    actor: str,
    roles: List[str],
    auth_type: str,
    created_at: int,
    expires_at: int,
) -> Dict[str, Any]:
    conn = _connect()
    try:
        cur = conn.cursor()
        roles_json = json.dumps(list(roles or []), ensure_ascii=True, sort_keys=True)
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO admin_sessions (
                    id, uid, email, actor, roles_json, auth_type, created_at, expires_at, last_seen_at, revoked_at, revoked_by, revoke_reason
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NULL, NULL, NULL)
                ON CONFLICT (id) DO UPDATE SET
                    uid = EXCLUDED.uid,
                    email = EXCLUDED.email,
                    actor = EXCLUDED.actor,
                    roles_json = EXCLUDED.roles_json,
                    auth_type = EXCLUDED.auth_type,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at,
                    last_seen_at = EXCLUDED.last_seen_at,
                    revoked_at = NULL,
                    revoked_by = NULL,
                    revoke_reason = NULL
                """,
                (session_id, uid, email, actor, roles_json, auth_type, created_at, expires_at, created_at),
            )
        else:
            cur.execute(
                """
                INSERT INTO admin_sessions (
                    id, uid, email, actor, roles_json, auth_type, created_at, expires_at, last_seen_at, revoked_at, revoked_by, revoke_reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL)
                ON CONFLICT(id) DO UPDATE SET
                    uid = excluded.uid,
                    email = excluded.email,
                    actor = excluded.actor,
                    roles_json = excluded.roles_json,
                    auth_type = excluded.auth_type,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at,
                    last_seen_at = excluded.last_seen_at,
                    revoked_at = NULL,
                    revoked_by = NULL,
                    revoke_reason = NULL
                """,
                (session_id, uid, email, actor, roles_json, auth_type, created_at, expires_at, created_at),
            )
        conn.commit()
    finally:
        conn.close()
    return get_admin_session(session_id, include_revoked=True) or {}


def get_admin_session(session_id: str, *, include_revoked: bool = False) -> Dict[str, Any] | None:
    token = str(session_id or "").strip()
    if not token:
        return None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT id, uid, email, actor, roles_json, auth_type, created_at, expires_at, last_seen_at, revoked_at, revoked_by, revoke_reason
                FROM admin_sessions
                WHERE id = %s AND (%s OR revoked_at IS NULL)
                """,
                (token, include_revoked),
            )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT id, uid, email, actor, roles_json, auth_type, created_at, expires_at, last_seen_at, revoked_at, revoked_by, revoke_reason
                FROM admin_sessions
                WHERE id = ? AND (? OR revoked_at IS NULL)
                """,
                (token, 1 if include_revoked else 0),
            )
        row = cur.fetchone()
        if not row:
            return None
        return _admin_session_row_to_dict(row)
    finally:
        conn.close()


def list_admin_sessions(uid: str | None = None, *, active_only: bool = True, limit: int = 100) -> List[Dict[str, Any]]:
    uid_token = str(uid or "").strip()
    limit = max(1, min(int(limit or 100), 500))
    now = int(time.time() * 1000)
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT id, uid, email, actor, roles_json, auth_type, created_at, expires_at, last_seen_at, revoked_at, revoked_by, revoke_reason
                FROM admin_sessions
                WHERE (%s = '' OR uid = %s)
                  AND (NOT %s OR (revoked_at IS NULL AND expires_at >= %s))
                ORDER BY created_at DESC
                LIMIT %s
                """,
                (uid_token, uid_token, active_only, now, limit),
            )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT id, uid, email, actor, roles_json, auth_type, created_at, expires_at, last_seen_at, revoked_at, revoked_by, revoke_reason
                FROM admin_sessions
                WHERE (? = '' OR uid = ?)
                  AND (NOT ? OR (revoked_at IS NULL AND expires_at >= ?))
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (uid_token, uid_token, 1 if active_only else 0, now, limit),
            )
        rows = cur.fetchall()
        return [_admin_session_row_to_dict(row) for row in rows]
    finally:
        conn.close()


def touch_admin_session(session_id: str, *, at_ms: int | None = None) -> Dict[str, Any] | None:
    token = str(session_id or "").strip()
    if not token:
        return None
    now = int(at_ms or int(time.time() * 1000))
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                UPDATE admin_sessions
                SET last_seen_at = %s
                WHERE id = %s AND revoked_at IS NULL
                """,
                (now, token),
            )
        else:
            cur.execute(
                """
                UPDATE admin_sessions
                SET last_seen_at = ?
                WHERE id = ? AND revoked_at IS NULL
                """,
                (now, token),
            )
        conn.commit()
    finally:
        conn.close()
    return get_admin_session(token, include_revoked=True)


def revoke_admin_session(session_id: str, *, revoked_by: str, reason: str | None = None) -> Dict[str, Any] | None:
    token = str(session_id or "").strip()
    if not token:
        return None
    now = int(time.time() * 1000)
    conn = _connect()
    try:
        cur = conn.cursor()
        reason_text = str(reason or "").strip() or "manual_revoke"
        if _use_postgres():
            cur.execute(
                """
                UPDATE admin_sessions
                SET revoked_at = %s, revoked_by = %s, revoke_reason = %s
                WHERE id = %s
                """,
                (now, revoked_by, reason_text, token),
            )
        else:
            cur.execute(
                """
                UPDATE admin_sessions
                SET revoked_at = ?, revoked_by = ?, revoke_reason = ?
                WHERE id = ?
                """,
                (now, revoked_by, reason_text, token),
            )
        conn.commit()
        if int(cur.rowcount or 0) <= 0:
            return None
    finally:
        conn.close()
    return get_admin_session(token, include_revoked=True)


def revoke_admin_sessions_for_uid(
    uid: str,
    *,
    revoked_by: str,
    reason: str | None = None,
    exclude_session_id: str | None = None,
) -> List[Dict[str, Any]]:
    uid_token = str(uid or "").strip()
    if not uid_token:
        return []
    reason_text = str(reason or "").strip() or "bulk_revoke"
    excluded = str(exclude_session_id or "").strip()
    now = int(time.time() * 1000)
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                UPDATE admin_sessions
                SET revoked_at = %s, revoked_by = %s, revoke_reason = %s
                WHERE uid = %s
                  AND revoked_at IS NULL
                  AND (%s = '' OR id <> %s)
                """,
                (now, revoked_by, reason_text, uid_token, excluded, excluded),
            )
        else:
            cur.execute(
                """
                UPDATE admin_sessions
                SET revoked_at = ?, revoked_by = ?, revoke_reason = ?
                WHERE uid = ?
                  AND revoked_at IS NULL
                  AND (? = '' OR id <> ?)
                """,
                (now, revoked_by, reason_text, uid_token, excluded, excluded),
            )
        conn.commit()
    finally:
        conn.close()
    return list_admin_sessions(uid=uid_token, active_only=False, limit=500)


def enforce_admin_session_limit(
    uid: str,
    *,
    max_active: int,
    keep_session_id: str | None = None,
    revoked_by: str,
    reason: str | None = None,
) -> List[Dict[str, Any]]:
    uid_token = str(uid or "").strip()
    limit_value = max(0, int(max_active or 0))
    preferred_session_id = str(keep_session_id or "").strip()
    if not uid_token or limit_value <= 0:
        return []

    active_sessions = list_admin_sessions(uid=uid_token, active_only=True, limit=500)
    if len(active_sessions) <= limit_value:
        return []

    chosen_ids: set[str] = set()
    if preferred_session_id:
        for item in active_sessions:
            if str(item.get("id") or "") == preferred_session_id:
                chosen_ids.add(preferred_session_id)
                break

    for item in active_sessions:
        session_id = str(item.get("id") or "").strip()
        if not session_id or session_id in chosen_ids:
            continue
        if len(chosen_ids) < limit_value:
            chosen_ids.add(session_id)
        else:
            break

    revoked_items: List[Dict[str, Any]] = []
    reason_text = str(reason or "").strip() or "session_limit"
    for item in active_sessions:
        session_id = str(item.get("id") or "").strip()
        if not session_id or session_id in chosen_ids:
            continue
        revoked = revoke_admin_session(session_id, revoked_by=revoked_by, reason=reason_text)
        if revoked:
            revoked_items.append(revoked)
    return revoked_items


def cleanup_admin_sessions(
    *,
    retention_days: int = 30,
    include_revoked: bool = True,
    include_expired: bool = True,
) -> Dict[str, Any]:
    retention_days = max(0, int(retention_days or 0))
    cutoff_ms = int(time.time() * 1000) - (retention_days * 24 * 60 * 60 * 1000)
    include_revoked = bool(include_revoked)
    include_expired = bool(include_expired)
    conn = _connect()
    deleted_count = 0
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                DELETE FROM admin_sessions
                WHERE (
                    (%s AND revoked_at IS NOT NULL AND revoked_at <= %s)
                    OR
                    (%s AND revoked_at IS NULL AND expires_at <= %s)
                )
                """,
                (include_revoked, cutoff_ms, include_expired, cutoff_ms),
            )
        else:
            cur.execute(
                """
                DELETE FROM admin_sessions
                WHERE (
                    (? AND revoked_at IS NOT NULL AND revoked_at <= ?)
                    OR
                    (? AND revoked_at IS NULL AND expires_at <= ?)
                )
                """,
                (1 if include_revoked else 0, cutoff_ms, 1 if include_expired else 0, cutoff_ms),
            )
        deleted_count = int(cur.rowcount or 0)
        conn.commit()
    finally:
        conn.close()
    return {
        "deletedCount": deleted_count,
        "retentionDays": retention_days,
        "cutoffMs": cutoff_ms,
        "includeRevoked": include_revoked,
        "includeExpired": include_expired,
    }


def _operator_access_override_row_to_dict(row: Any) -> Dict[str, Any]:
    raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
    return {
        "uid": str(raw.get("uid") or ""),
        "email": str(raw.get("email") or "").strip() or None,
        "blocked": bool(raw.get("blocked")),
        "reason": str(raw.get("reason") or "").strip() or None,
        "updatedBy": str(raw.get("updated_by") or "").strip() or None,
        "createdAt": int(raw.get("created_at") or 0),
        "updatedAt": int(raw.get("updated_at") or 0),
    }


def get_operator_access_override(uid: str) -> Dict[str, Any] | None:
    uid_token = str(uid or "").strip()
    if not uid_token:
        return None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT uid, email, blocked, reason, updated_by, created_at, updated_at
                FROM operator_access_overrides
                WHERE uid = %s
                """,
                (uid_token,),
            )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT uid, email, blocked, reason, updated_by, created_at, updated_at
                FROM operator_access_overrides
                WHERE uid = ?
                """,
                (uid_token,),
            )
        row = cur.fetchone()
        if not row:
            return None
        return _operator_access_override_row_to_dict(row)
    finally:
        conn.close()


def list_operator_access_overrides(*, blocked_only: bool = False, limit: int = 100) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 100), 500))
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT uid, email, blocked, reason, updated_by, created_at, updated_at
                FROM operator_access_overrides
                WHERE (NOT %s OR blocked = TRUE)
                ORDER BY updated_at DESC, uid ASC
                LIMIT %s
                """,
                (blocked_only, limit),
            )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT uid, email, blocked, reason, updated_by, created_at, updated_at
                FROM operator_access_overrides
                WHERE (NOT ? OR blocked = 1)
                ORDER BY updated_at DESC, uid ASC
                LIMIT ?
                """,
                (1 if blocked_only else 0, limit),
            )
        rows = cur.fetchall()
        return [_operator_access_override_row_to_dict(row) for row in rows]
    finally:
        conn.close()


def upsert_operator_access_override(
    uid: str,
    *,
    email: str | None = None,
    blocked: bool = True,
    reason: str | None = None,
    updated_by: str | None = None,
) -> Dict[str, Any]:
    uid_token = str(uid or "").strip()
    if not uid_token:
        raise ValueError("uid is required")
    email_value = str(email or "").strip().lower() or None
    now = int(time.time() * 1000)
    existing = get_operator_access_override(uid_token)
    created_at = int(existing.get("createdAt") or now) if existing else now
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO operator_access_overrides (uid, email, blocked, reason, updated_by, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (uid) DO UPDATE SET
                    email = EXCLUDED.email,
                    blocked = EXCLUDED.blocked,
                    reason = EXCLUDED.reason,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = EXCLUDED.updated_at
                """,
                (uid_token, email_value, bool(blocked), str(reason or "").strip() or None, updated_by, created_at, now),
            )
        else:
            cur.execute(
                """
                INSERT INTO operator_access_overrides (uid, email, blocked, reason, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uid) DO UPDATE SET
                    email = excluded.email,
                    blocked = excluded.blocked,
                    reason = excluded.reason,
                    updated_by = excluded.updated_by,
                    updated_at = excluded.updated_at
                """,
                (uid_token, email_value, 1 if blocked else 0, str(reason or "").strip() or None, updated_by, created_at, now),
            )
        conn.commit()
    finally:
        conn.close()
    return get_operator_access_override(uid_token) or {}


def find_blocking_operator_access(*, uid: str | None = None, email: str | None = None) -> Dict[str, Any] | None:
    uid_token = str(uid or "").strip()
    email_token = str(email or "").strip().lower()
    if not uid_token and not email_token:
        return None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT uid, email, blocked, reason, updated_by, created_at, updated_at
                FROM operator_access_overrides
                WHERE blocked = TRUE
                  AND ((%s <> '' AND uid = %s) OR (%s <> '' AND LOWER(COALESCE(email, '')) = %s))
                ORDER BY updated_at DESC
                LIMIT 1
                """,
                (uid_token, uid_token, email_token, email_token),
            )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT uid, email, blocked, reason, updated_by, created_at, updated_at
                FROM operator_access_overrides
                WHERE blocked = 1
                  AND ((? <> '' AND uid = ?) OR (? <> '' AND LOWER(COALESCE(email, '')) = ?))
                ORDER BY updated_at DESC
                LIMIT 1
                """,
                (uid_token, uid_token, email_token, email_token),
            )
        row = cur.fetchone()
        if not row:
            return None
        return _operator_access_override_row_to_dict(row)
    finally:
        conn.close()


def _support_case_row_to_dict(row: Any) -> Dict[str, Any]:
    raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
    try:
        notes = json.loads(raw.get("notes_json") or "[]")
    except Exception:
        notes = []
    normalized_notes = []
    for item in notes or []:
        if not isinstance(item, dict):
            continue
        normalized_notes.append(
            {
                "author": str(item.get("author") or ""),
                "message": str(item.get("message") or ""),
                "createdAtMs": int(item.get("createdAtMs") or 0),
            }
        )
    return {
        "id": raw.get("id"),
        "userUid": raw.get("user_uid"),
        "relatedJobId": raw.get("related_job_id"),
        "status": raw.get("status"),
        "priority": raw.get("priority"),
        "assignee": raw.get("assignee"),
        "escalated": bool(raw.get("escalated")),
        "summary": raw.get("summary"),
        "notes": normalized_notes,
        "createdBy": raw.get("created_by"),
        "updatedBy": raw.get("updated_by"),
        "createdAt": int(raw.get("created_at") or 0),
        "updatedAt": int(raw.get("updated_at") or 0),
    }


def list_support_cases(
    user_uid: str | None = None,
    status: str | None = None,
    assignee: str | None = None,
    escalated: bool | None = None,
    q: str | None = None,
    limit: int = 100,
) -> List[Dict[str, Any]]:
    user_token = str(user_uid or "").strip()
    status_token = str(status or "").strip().lower()
    assignee_token = str(assignee or "").strip()
    query = str(q or "").strip()
    limit = max(1, min(int(limit or 100), 500))
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT id, user_uid, related_job_id, status, priority, assignee, escalated, summary, notes_json, created_by, updated_by, created_at, updated_at
                FROM support_cases
                WHERE (%s = '' OR user_uid = %s)
                  AND (%s = '' OR status = %s)
                  AND (%s = '' OR assignee = %s)
                  AND (%s IS NULL OR escalated = %s)
                  AND (%s = '' OR id ILIKE %s OR user_uid ILIKE %s OR summary ILIKE %s OR COALESCE(related_job_id, '') ILIKE %s OR COALESCE(assignee, '') ILIKE %s)
                ORDER BY updated_at DESC, created_at DESC
                LIMIT %s
                """,
                (
                    user_token, user_token,
                    status_token, status_token,
                    assignee_token, assignee_token,
                    escalated, escalated,
                    query, f"%{query}%", f"%{query}%", f"%{query}%", f"%{query}%", f"%{query}%",
                    limit,
                ),
            )
            rows = cur.fetchall()
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT id, user_uid, related_job_id, status, priority, assignee, escalated, summary, notes_json, created_by, updated_by, created_at, updated_at
                FROM support_cases
                WHERE (? = '' OR user_uid = ?)
                  AND (? = '' OR lower(status) = ?)
                  AND (? = '' OR assignee = ?)
                  AND (? IS NULL OR escalated = ?)
                  AND (? = '' OR lower(id) LIKE lower(?) OR lower(user_uid) LIKE lower(?) OR lower(summary) LIKE lower(?) OR lower(COALESCE(related_job_id, '')) LIKE lower(?) OR lower(COALESCE(assignee, '')) LIKE lower(?))
                ORDER BY updated_at DESC, created_at DESC
                LIMIT ?
                """,
                (
                    user_token, user_token,
                    status_token, status_token,
                    assignee_token, assignee_token,
                    escalated, escalated,
                    query, f"%{query}%", f"%{query}%", f"%{query}%", f"%{query}%", f"%{query}%",
                    limit,
                ),
            )
            rows = cur.fetchall()
        return [_support_case_row_to_dict(row) for row in rows]
    finally:
        conn.close()


def get_support_case(case_id: str) -> Dict[str, Any] | None:
    token = str(case_id or "").strip()
    if not token:
        return None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT id, user_uid, related_job_id, status, priority, assignee, escalated, summary, notes_json, created_by, updated_by, created_at, updated_at
                FROM support_cases
                WHERE id = %s
                """,
                (token,),
            )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT id, user_uid, related_job_id, status, priority, assignee, escalated, summary, notes_json, created_by, updated_by, created_at, updated_at
                FROM support_cases
                WHERE id = ?
                """,
                (token,),
            )
        row = cur.fetchone()
        if not row:
            return None
        return _support_case_row_to_dict(row)
    finally:
        conn.close()


def create_support_case(
    *,
    user_uid: str,
    summary: str,
    actor: str,
    related_job_id: str | None = None,
    priority: str = "normal",
    assignee: str | None = None,
    escalated: bool = False,
    initial_note: str | None = None,
) -> Dict[str, Any]:
    case_id = uuid.uuid4().hex
    now = int(time.time() * 1000)
    notes = []
    note_text = str(initial_note or "").strip()
    if note_text:
        notes.append({"author": actor, "message": note_text, "createdAtMs": now})
    user_token = str(user_uid or "").strip()
    related_job_token = str(related_job_id or "").strip() or None
    status = "open"
    priority_token = str(priority or "normal").strip().lower() or "normal"
    assignee_token = str(assignee or "").strip() or None
    summary_text = str(summary or "").strip() or "Untitled support case"
    escalated_value = bool(escalated)
    conn = _connect()
    try:
        cur = conn.cursor()
        notes_json = json.dumps(notes, ensure_ascii=True, sort_keys=True)
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO support_cases (
                    id, user_uid, related_job_id, status, priority, assignee, escalated, summary, notes_json, created_by, updated_by, created_at, updated_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (case_id, user_token, related_job_token, status, priority_token, assignee_token, escalated_value, summary_text, notes_json, actor, actor, now, now),
            )
        else:
            cur.execute(
                """
                INSERT INTO support_cases (
                    id, user_uid, related_job_id, status, priority, assignee, escalated, summary, notes_json, created_by, updated_by, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (case_id, user_token, related_job_token, status, priority_token, assignee_token, 1 if escalated_value else 0, summary_text, notes_json, actor, actor, now, now),
            )
        conn.commit()
    finally:
        conn.close()
    return get_support_case(case_id) or {}


def update_support_case(
    case_id: str,
    *,
    actor: str,
    status: str | None = None,
    priority: str | None = None,
    assignee: str | None = None,
    clear_assignee: bool = False,
    escalated: bool | None = None,
    summary: str | None = None,
) -> Dict[str, Any] | None:
    existing = get_support_case(case_id)
    if not existing:
        return None
    next_status = str(status or existing.get("status") or "open").strip().lower() or "open"
    next_priority = str(priority or existing.get("priority") or "normal").strip().lower() or "normal"
    if clear_assignee:
        next_assignee = None
    elif assignee is not None:
        next_assignee = str(assignee or "").strip() or None
    else:
        next_assignee = str(existing.get("assignee") or "").strip() or None
    next_escalated = bool(existing.get("escalated")) if escalated is None else bool(escalated)
    next_summary = str(summary or existing.get("summary") or "").strip() or "Untitled support case"
    now = int(time.time() * 1000)
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                UPDATE support_cases
                SET status = %s, priority = %s, assignee = %s, escalated = %s, summary = %s, updated_by = %s, updated_at = %s
                WHERE id = %s
                """,
                (next_status, next_priority, next_assignee, next_escalated, next_summary, actor, now, case_id),
            )
        else:
            cur.execute(
                """
                UPDATE support_cases
                SET status = ?, priority = ?, assignee = ?, escalated = ?, summary = ?, updated_by = ?, updated_at = ?
                WHERE id = ?
                """,
                (next_status, next_priority, next_assignee, 1 if next_escalated else 0, next_summary, actor, now, case_id),
            )
        conn.commit()
    finally:
        conn.close()
    return get_support_case(case_id)


def append_support_case_note(case_id: str, *, actor: str, message: str) -> Dict[str, Any] | None:
    existing = get_support_case(case_id)
    if not existing:
        return None
    notes = list(existing.get("notes") or [])
    notes.append({"author": actor, "message": str(message or "").strip(), "createdAtMs": int(time.time() * 1000)})
    notes_json = json.dumps(notes, ensure_ascii=True, sort_keys=True)
    now = int(time.time() * 1000)
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                UPDATE support_cases
                SET notes_json = %s, updated_by = %s, updated_at = %s
                WHERE id = %s
                """,
                (notes_json, actor, now, case_id),
            )
        else:
            cur.execute(
                """
                UPDATE support_cases
                SET notes_json = ?, updated_by = ?, updated_at = ?
                WHERE id = ?
                """,
                (notes_json, actor, now, case_id),
            )
        conn.commit()
    finally:
        conn.close()
    return get_support_case(case_id)

def get_market_multiplier(category: str, month_index: int) -> float:
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                "SELECT multiplier FROM market_seasonality_rules WHERE category = %s AND month_index = %s LIMIT 1",
                (category, month_index)
            )
        else:
            cur.execute(
                "SELECT multiplier FROM market_seasonality_rules WHERE category = ? AND month_index = ? LIMIT 1",
                (category, month_index)
            )
        row = cur.fetchone()
        if isinstance(row, dict):
            return float(row.get("multiplier") or 1.0)
        return float(row[0]) if row else 1.0
    finally:
        conn.close()


def list_market_multipliers_for_month(month_index: int) -> Dict[str, float]:
    try:
        month = int(month_index)
    except Exception:
        month = 0
    if month < 1 or month > 12:
        return {}
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT category, multiplier
                FROM market_seasonality_rules
                WHERE month_index = %s
                """,
                (month,),
            )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                SELECT category, multiplier
                FROM market_seasonality_rules
                WHERE month_index = ?
                """,
                (month,),
            )
        rows = cur.fetchall()
        result: Dict[str, float] = {}
        for row in rows:
            if isinstance(row, dict):
                category = str(row.get("category") or "").strip()
                multiplier = row.get("multiplier")
            elif hasattr(row, "keys"):
                category = str(row["category"] or "").strip()
                multiplier = row["multiplier"]
            else:
                category = str(row[0] or "").strip()
                multiplier = row[1]
            if not category:
                continue
            try:
                result[category] = float(multiplier or 1.0)
            except Exception:
                result[category] = 1.0
        return result
    finally:
        conn.close()


def init_db():
    conn = _connect()
    try:
        with _schema_bootstrap_lock(conn):
            _run_schema_migrations(conn)
        conn.commit()
    finally:
        conn.close()

def create_plan_job(
    job_id: str,
    request_json: str | None = None,
    idempotency_key: str | None = None,
    owner_uid: str | None = None,
):
    conn = _connect()
    try:
        now = int(time.time() * 1000)
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at,
                    request_json = COALESCE(EXCLUDED.request_json, plan_jobs.request_json),
                    idempotency_key = COALESCE(EXCLUDED.idempotency_key, plan_jobs.idempotency_key),
                    owner_uid = COALESCE(EXCLUDED.owner_uid, plan_jobs.owner_uid),
                    next_attempt_at = EXCLUDED.next_attempt_at
                """,
                (job_id, "queued", now, now, request_json, None, None, idempotency_key, None, owner_uid, 0, now)
            )
        else:
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    updated_at = excluded.updated_at,
                    request_json = COALESCE(excluded.request_json, plan_jobs.request_json),
                    idempotency_key = COALESCE(excluded.idempotency_key, plan_jobs.idempotency_key),
                    owner_uid = COALESCE(excluded.owner_uid, plan_jobs.owner_uid),
                    next_attempt_at = excluded.next_attempt_at
                """,
                (job_id, "queued", now, now, request_json, None, None, idempotency_key, None, owner_uid, 0, now)
            )
        conn.commit()
    finally:
        conn.close()

def update_plan_job(
    job_id: str,
    status: str,
    result_json: str | None = None,
    error: str | None = None,
    worker_id: str | None = None,
    increment_attempt: bool = False,
    next_attempt_at: int | None = None,
):
    conn = _connect()
    try:
        now = int(time.time() * 1000)
        cur = conn.cursor()
        effective_next_attempt = int(next_attempt_at) if next_attempt_at is not None else 0
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, attempt_count, next_attempt_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at,
                    result_json = COALESCE(EXCLUDED.result_json, plan_jobs.result_json),
                    error = COALESCE(EXCLUDED.error, plan_jobs.error),
                    worker_id = COALESCE(EXCLUDED.worker_id, plan_jobs.worker_id),
                    next_attempt_at = COALESCE(EXCLUDED.next_attempt_at, plan_jobs.next_attempt_at),
                    attempt_count = CASE
                        WHEN %s THEN plan_jobs.attempt_count + 1
                        ELSE plan_jobs.attempt_count
                    END
                """,
                (job_id, status, now, now, None, result_json, error, None, worker_id, 0, effective_next_attempt, increment_attempt)
            )
        else:
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, attempt_count, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    updated_at = excluded.updated_at,
                    result_json = COALESCE(excluded.result_json, plan_jobs.result_json),
                    error = COALESCE(excluded.error, plan_jobs.error),
                    worker_id = COALESCE(excluded.worker_id, plan_jobs.worker_id),
                    next_attempt_at = COALESCE(excluded.next_attempt_at, plan_jobs.next_attempt_at),
                    attempt_count = CASE
                        WHEN ? THEN plan_jobs.attempt_count + 1
                        ELSE plan_jobs.attempt_count
                    END
                """,
                (job_id, status, now, now, None, result_json, error, None, worker_id, 0, effective_next_attempt, 1 if increment_attempt else 0)
            )
        conn.commit()
    finally:
        conn.close()

def get_plan_job(job_id: str, owner_uid: str | None = None, *, any_owner: bool = False):
    conn = _connect()
    try:
        normalized_owner_uid = str(owner_uid or "").strip() or None
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
        if _use_postgres():
            if any_owner:
                cur.execute(
                    "SELECT id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at "
                    "FROM plan_jobs WHERE id = %s",
                    (job_id,),
                )
            elif normalized_owner_uid is None:
                cur.execute(
                    "SELECT id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at "
                    "FROM plan_jobs WHERE id = %s AND owner_uid IS NULL",
                    (job_id,),
                )
            else:
                cur.execute(
                    "SELECT id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at "
                    "FROM plan_jobs WHERE id = %s AND owner_uid = %s",
                    (job_id, normalized_owner_uid),
                )
        else:
            if any_owner:
                cur.execute(
                    "SELECT id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at "
                    "FROM plan_jobs WHERE id = ?",
                    (job_id,),
                )
            elif normalized_owner_uid is None:
                cur.execute(
                    "SELECT id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at "
                    "FROM plan_jobs WHERE id = ? AND owner_uid IS NULL",
                    (job_id,),
                )
            else:
                cur.execute(
                    "SELECT id, status, created_at, updated_at, request_json, result_json, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at "
                    "FROM plan_jobs WHERE id = ? AND owner_uid = ?",
                    (job_id, normalized_owner_uid),
                )
        row = cur.fetchone()
        if not row:
            return None
        if isinstance(row, dict):
            result_json = row.get("result_json")
            request_json = row.get("request_json")
            payload = {
                "id": row.get("id"),
                "status": row.get("status"),
                "createdAt": row.get("created_at"),
                "updatedAt": row.get("updated_at"),
                "error": row.get("error"),
                "idempotencyKey": row.get("idempotency_key"),
                "workerId": row.get("worker_id"),
                "ownerUid": row.get("owner_uid"),
                "attemptCount": row.get("attempt_count"),
                "nextAttemptAt": row.get("next_attempt_at"),
            }
        else:
            result_json = row["result_json"]
            request_json = row["request_json"]
            payload = {
                "id": row["id"],
                "status": row["status"],
                "createdAt": row["created_at"],
                "updatedAt": row["updated_at"],
                "error": row["error"],
                "idempotencyKey": row["idempotency_key"],
                "workerId": row["worker_id"],
                "ownerUid": row["owner_uid"],
                "attemptCount": row["attempt_count"],
                "nextAttemptAt": row["next_attempt_at"],
            }
        if request_json:
            try:
                payload["request"] = json.loads(request_json)
            except Exception:
                payload["request"] = request_json
        if result_json:
            try:
                payload["result"] = json.loads(result_json)
            except Exception:
                payload["result"] = result_json
        return payload
    finally:
        conn.close()


def find_plan_job_by_idempotency(
    idempotency_key: str,
    *,
    request_json: str | None = None,
    owner_uid: str | None = None,
):
    normalized_key = str(idempotency_key or "").strip()
    if not normalized_key:
        return None
    normalized_owner_uid = str(owner_uid or "").strip() or None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            if normalized_owner_uid is None:
                cur.execute(
                    """
                    SELECT id
                    FROM plan_jobs
                    WHERE idempotency_key = %s
                      AND request_json = %s
                      AND owner_uid IS NULL
                    ORDER BY created_at DESC
                    LIMIT 1
                    """,
                    (normalized_key, request_json),
                )
            else:
                cur.execute(
                    """
                    SELECT id
                    FROM plan_jobs
                    WHERE idempotency_key = %s
                      AND request_json = %s
                      AND owner_uid = %s
                    ORDER BY created_at DESC
                    LIMIT 1
                    """,
                    (normalized_key, request_json, normalized_owner_uid),
                )
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            if normalized_owner_uid is None:
                cur.execute(
                    """
                    SELECT id
                    FROM plan_jobs
                    WHERE idempotency_key = ?
                      AND request_json = ?
                      AND owner_uid IS NULL
                    ORDER BY created_at DESC
                    LIMIT 1
                    """,
                    (normalized_key, request_json),
                )
            else:
                cur.execute(
                    """
                    SELECT id
                    FROM plan_jobs
                    WHERE idempotency_key = ?
                      AND request_json = ?
                      AND owner_uid = ?
                    ORDER BY created_at DESC
                    LIMIT 1
                    """,
                    (normalized_key, request_json, normalized_owner_uid),
                )
        row = cur.fetchone()
        if not row:
            return None
        job_id = row.get("id") if isinstance(row, dict) else row["id"]
    finally:
        conn.close()
    return get_plan_job(str(job_id), owner_uid=normalized_owner_uid)


def list_plan_jobs(
    status: str | None = None,
    owner_uid: str | None = None,
    q: str | None = None,
    limit: int = 100,
) -> List[Dict[str, Any]]:
    normalized_status = str(status or "").strip().lower()
    normalized_owner = str(owner_uid or "").strip()
    query = str(q or "").strip()
    limit = max(1, min(int(limit or 100), 500))
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute(
                """
                SELECT id, status, created_at, updated_at, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at,
                       CASE WHEN request_json IS NULL OR request_json = '' THEN FALSE ELSE TRUE END AS has_request,
                       CASE WHEN result_json IS NULL OR result_json = '' THEN FALSE ELSE TRUE END AS has_result
                FROM plan_jobs
                WHERE (%s = '' OR status = %s)
                  AND (%s = '' OR owner_uid = %s)
                  AND (%s = '' OR id ILIKE %s OR COALESCE(owner_uid, '') ILIKE %s)
                ORDER BY updated_at DESC, created_at DESC
                LIMIT %s
                """,
                (
                    normalized_status, normalized_status,
                    normalized_owner, normalized_owner,
                    query, f"%{query}%", f"%{query}%",
                    limit,
                ),
            )
            rows = cur.fetchall()
            return [
                {
                    "id": row.get("id"),
                    "status": row.get("status"),
                    "createdAt": row.get("created_at"),
                    "updatedAt": row.get("updated_at"),
                    "error": row.get("error"),
                    "idempotencyKey": row.get("idempotency_key"),
                    "workerId": row.get("worker_id"),
                    "ownerUid": row.get("owner_uid"),
                    "attemptCount": row.get("attempt_count"),
                    "nextAttemptAt": row.get("next_attempt_at"),
                    "hasRequest": bool(row.get("has_request")),
                    "hasResult": bool(row.get("has_result")),
                }
                for row in rows
            ]
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        cur.execute(
            """
            SELECT id, status, created_at, updated_at, error, idempotency_key, worker_id, owner_uid, attempt_count, next_attempt_at,
                   CASE WHEN request_json IS NULL OR request_json = '' THEN 0 ELSE 1 END AS has_request,
                   CASE WHEN result_json IS NULL OR result_json = '' THEN 0 ELSE 1 END AS has_result
            FROM plan_jobs
            WHERE (? = '' OR status = ?)
              AND (? = '' OR owner_uid = ?)
              AND (? = '' OR lower(id) LIKE lower(?) OR lower(COALESCE(owner_uid, '')) LIKE lower(?))
            ORDER BY updated_at DESC, created_at DESC
            LIMIT ?
            """,
            (
                normalized_status, normalized_status,
                normalized_owner, normalized_owner,
                query, f"%{query}%", f"%{query}%",
                limit,
            ),
        )
        rows = cur.fetchall()
        return [
            {
                "id": row["id"],
                "status": row["status"],
                "createdAt": row["created_at"],
                "updatedAt": row["updated_at"],
                "error": row["error"],
                "idempotencyKey": row["idempotency_key"],
                "workerId": row["worker_id"],
                "ownerUid": row["owner_uid"],
                "attemptCount": row["attempt_count"],
                "nextAttemptAt": row["next_attempt_at"],
                "hasRequest": bool(row["has_request"]),
                "hasResult": bool(row["has_result"]),
            }
            for row in rows
        ]
    finally:
        conn.close()


def requeue_plan_job(job_id: str, *, reset_attempt_count: bool = True) -> Dict[str, Any] | None:
    token = str(job_id or "").strip()
    if not token:
        return None
    conn = _connect()
    try:
        now = int(time.time() * 1000)
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                UPDATE plan_jobs
                SET status = 'queued',
                    updated_at = %s,
                    result_json = NULL,
                    error = NULL,
                    worker_id = NULL,
                    next_attempt_at = %s,
                    attempt_count = CASE WHEN %s THEN 0 ELSE attempt_count END
                WHERE id = %s
                  AND request_json IS NOT NULL
                  AND request_json <> ''
                RETURNING id
                """,
                (now, now, reset_attempt_count, token),
            )
            row = cur.fetchone()
            conn.commit()
            if not row:
                return None
        else:
            cur.execute(
                """
                UPDATE plan_jobs
                SET status = ?,
                    updated_at = ?,
                    result_json = NULL,
                    error = NULL,
                    worker_id = NULL,
                    next_attempt_at = ?,
                    attempt_count = CASE WHEN ? THEN 0 ELSE attempt_count END
                WHERE id = ?
                  AND request_json IS NOT NULL
                  AND request_json <> ''
                """,
                ("queued", now, now, 1 if reset_attempt_count else 0, token),
            )
            conn.commit()
            if cur.rowcount <= 0:
                return None
        return get_plan_job(token, any_owner=True)
    finally:
        conn.close()


def count_plan_jobs_by_status(status: str) -> int:
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute("SELECT COUNT(*) AS c FROM plan_jobs WHERE status = %s", (status,))
            row = cur.fetchone()
            return int(row.get("c") if isinstance(row, dict) else row[0])
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) AS c FROM plan_jobs WHERE status = ?", (status,))
        row = cur.fetchone()
        if not row:
            return 0
        return int(row["c"])
    finally:
        conn.close()


def claim_next_plan_job(worker_id: str):
    conn = _connect()
    try:
        now_ms = int(time.time() * 1000)
        if _use_postgres():
            if dict_row is not None:
                cur = conn.cursor(row_factory=dict_row)
            else:
                cur = conn.cursor()
            cur.execute(
                """
                WITH next_job AS (
                    SELECT id
                    FROM plan_jobs
                    WHERE status = 'queued'
                      AND COALESCE(next_attempt_at, 0) <= %s
                    ORDER BY created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE plan_jobs p
                SET status = 'running',
                    updated_at = %s,
                    worker_id = %s,
                    next_attempt_at = %s,
                    attempt_count = p.attempt_count + 1
                FROM next_job
                WHERE p.id = next_job.id
                RETURNING p.id, p.request_json, p.owner_uid, p.attempt_count, p.next_attempt_at
                """,
                (now_ms, now_ms, worker_id, now_ms),
            )
            row = cur.fetchone()
            conn.commit()
            if not row:
                return None
            if isinstance(row, dict):
                payload = dict(row)
            else:
                payload = {
                    "id": row[0],
                    "request_json": row[1],
                    "owner_uid": row[2],
                    "attempt_count": row[3],
                    "next_attempt_at": row[4],
                }
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                "SELECT id, request_json FROM plan_jobs "
                "WHERE status = 'queued' AND COALESCE(next_attempt_at, 0) <= ? "
                "ORDER BY next_attempt_at ASC, created_at ASC LIMIT 1",
                (now_ms,),
            )
            row = cur.fetchone()
            if not row:
                return None
            cur.execute(
                """
                UPDATE plan_jobs
                SET status = ?, updated_at = ?, worker_id = ?, next_attempt_at = ?, attempt_count = attempt_count + 1
                WHERE id = ? AND status = ?
                """,
                ("running", now_ms, worker_id, now_ms, row["id"], "queued"),
            )
            conn.commit()
            if cur.rowcount <= 0:
                return None
            cur.execute("SELECT id, request_json, owner_uid, attempt_count, next_attempt_at FROM plan_jobs WHERE id = ?", (row["id"],))
            row_after = cur.fetchone()
            if not row_after:
                return None
            payload = {
                "id": row_after["id"],
                "request_json": row_after["request_json"],
                "owner_uid": row_after["owner_uid"],
                "attempt_count": row_after["attempt_count"],
                "next_attempt_at": row_after["next_attempt_at"],
            }

        request_payload = None
        if payload.get("request_json"):
            try:
                request_payload = json.loads(payload["request_json"])
            except Exception:
                request_payload = None
        return {
            "id": payload.get("id"),
            "request": request_payload,
            "ownerUid": payload.get("owner_uid"),
            "attemptCount": int(payload.get("attempt_count") or 0),
            "nextAttemptAt": int(payload.get("next_attempt_at") or 0),
        }
    finally:
        conn.close()


def claim_plan_job_by_id(job_id: str, worker_id: str):
    token = str(job_id or "").strip()
    if not token:
        return None
    conn = _connect()
    try:
        now_ms = int(time.time() * 1000)
        if _use_postgres():
            if dict_row is not None:
                cur = conn.cursor(row_factory=dict_row)
            else:
                cur = conn.cursor()
            cur.execute(
                """
                UPDATE plan_jobs
                SET status = 'running',
                    updated_at = %s,
                    worker_id = %s,
                    next_attempt_at = %s,
                    attempt_count = attempt_count + 1
                WHERE id = %s
                  AND status = 'queued'
                  AND COALESCE(next_attempt_at, 0) <= %s
                RETURNING id, request_json, owner_uid, attempt_count, next_attempt_at
                """,
                (now_ms, worker_id, now_ms, token, now_ms),
            )
            row = cur.fetchone()
            conn.commit()
            if not row:
                return None
            if isinstance(row, dict):
                payload = dict(row)
            else:
                payload = {
                    "id": row[0],
                    "request_json": row[1],
                    "owner_uid": row[2],
                    "attempt_count": row[3],
                    "next_attempt_at": row[4],
                }
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute(
                """
                UPDATE plan_jobs
                SET status = ?, updated_at = ?, worker_id = ?, next_attempt_at = ?, attempt_count = attempt_count + 1
                WHERE id = ? AND status = ? AND COALESCE(next_attempt_at, 0) <= ?
                """,
                ("running", now_ms, worker_id, now_ms, token, "queued", now_ms),
            )
            conn.commit()
            if cur.rowcount <= 0:
                return None
            cur.execute("SELECT id, request_json, owner_uid, attempt_count, next_attempt_at FROM plan_jobs WHERE id = ?", (token,))
            row_after = cur.fetchone()
            if not row_after:
                return None
            payload = {
                "id": row_after["id"],
                "request_json": row_after["request_json"],
                "owner_uid": row_after["owner_uid"],
                "attempt_count": row_after["attempt_count"],
                "next_attempt_at": row_after["next_attempt_at"],
            }

        request_payload = None
        if payload.get("request_json"):
            try:
                request_payload = json.loads(payload["request_json"])
            except Exception:
                request_payload = None
        return {
            "id": payload.get("id"),
            "request": request_payload,
            "ownerUid": payload.get("owner_uid"),
            "attemptCount": int(payload.get("attempt_count") or 0),
            "nextAttemptAt": int(payload.get("next_attempt_at") or 0),
        }
    finally:
        conn.close()


def increment_plan_job_diagnostic(metric_key: str, delta: int = 1) -> None:
    key = str(metric_key or "").strip().lower()
    if not key:
        return
    inc = int(delta or 1)
    now = int(time.time() * 1000)
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO plan_job_diagnostics (metric_key, metric_value, updated_at)
                VALUES (%s, %s, %s)
                ON CONFLICT (metric_key) DO UPDATE SET
                    metric_value = plan_job_diagnostics.metric_value + EXCLUDED.metric_value,
                    updated_at = EXCLUDED.updated_at
                """,
                (key, inc, now),
            )
        else:
            cur.execute(
                """
                INSERT INTO plan_job_diagnostics (metric_key, metric_value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(metric_key) DO UPDATE SET
                    metric_value = plan_job_diagnostics.metric_value + excluded.metric_value,
                    updated_at = excluded.updated_at
                """,
                (key, inc, now),
            )
        conn.commit()
    finally:
        conn.close()


def get_plan_job_diagnostics() -> Dict[str, Any]:
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
            cur.execute("SELECT metric_key, metric_value, updated_at FROM plan_job_diagnostics ORDER BY metric_key ASC")
            rows = cur.fetchall()
            payload = {str(r.get("metric_key")): int(r.get("metric_value") or 0) for r in rows}
            updated_at = {str(r.get("metric_key")): int(r.get("updated_at") or 0) for r in rows}
            return {"metrics": payload, "updatedAtMsByKey": updated_at}
        conn.row_factory = sqlite3.Row
        cur = conn.cursor()
        cur.execute("SELECT metric_key, metric_value, updated_at FROM plan_job_diagnostics ORDER BY metric_key ASC")
        rows = cur.fetchall()
        payload = {str(r["metric_key"]): int(r["metric_value"] or 0) for r in rows}
        updated_at = {str(r["metric_key"]): int(r["updated_at"] or 0) for r in rows}
        return {"metrics": payload, "updatedAtMsByKey": updated_at}
    finally:
        conn.close()

def _recipe_count(conn, active_only: bool = True) -> int:
    cur = conn.cursor()
    if active_only:
        cur.execute("SELECT COUNT(*) FROM recipes WHERE COALESCE(active, 1) = 1")
    else:
        cur.execute("SELECT COUNT(*) FROM recipes")
    row = cur.fetchone()
    if isinstance(row, dict):
        return int(list(row.values())[0])
    return int(row[0])

def get_recipe_count() -> int:
    conn = _connect()
    try:
        return _recipe_count(conn)
    finally:
        conn.close()

def get_sample_recipes(limit: int = 3):
    conn = _connect()
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
    else:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
    try:
        cursor.execute(
            "SELECT id, title FROM recipes WHERE COALESCE(active, 1) = 1 ORDER BY id LIMIT ?",
            (limit,),
        ) if not _use_postgres() else cursor.execute(
            "SELECT id, title FROM recipes WHERE COALESCE(active, 1) = 1 ORDER BY id LIMIT %s",
            (limit,)
        )
        rows = cursor.fetchall()
        samples = []
        for row in rows:
            if isinstance(row, dict):
                samples.append({"id": row.get("id"), "title": row.get("title")})
            else:
                samples.append({"id": row["id"], "title": row["title"]})
        return samples
    finally:
        conn.close()


def _resolve_recipe_seed_path(source_path: str | None = None) -> str | None:
    candidates = []
    if source_path:
        candidates.append(source_path)
    else:
        candidates.extend([
            "recipes.json",
            os.path.join(os.path.dirname(__file__), "recipes.json"),
        ])
    for candidate in candidates:
        if candidate and os.path.exists(candidate):
            return candidate
    return None


def _load_seed_recipes(source_path: str | None = None) -> tuple[str | None, list[dict]]:
    recipes_path = _resolve_recipe_seed_path(source_path)
    if not recipes_path:
        return None, []
    with open(recipes_path, "r", encoding="utf-8") as f:
        recipes = json.load(f)
    if not isinstance(recipes, list):
        raise ValueError("Recipe seed file must contain a JSON array")
    return recipes_path, recipes


def _recipe_id_set(conn, active_only: bool = False) -> set[str]:
    cur = conn.cursor()
    if active_only:
        cur.execute("SELECT id FROM recipes WHERE COALESCE(active, 1) = 1")
    else:
        cur.execute("SELECT id FROM recipes")
    rows = cur.fetchall()
    ids: set[str] = set()
    for row in rows:
        raw_id = row.get("id") if isinstance(row, dict) else row[0]
        if raw_id:
            ids.add(str(raw_id))
    return ids


def get_recipe_catalog_status(source_path: str | None = None) -> Dict[str, Any]:
    recipes_path, seed_recipes_raw = _load_seed_recipes(source_path)
    seed_ids = {str(r.get("id") or "").strip() for r in seed_recipes_raw if str(r.get("id") or "").strip()}
    conn = _connect()
    try:
        db_ids = _recipe_id_set(conn)
        active_db_ids = _recipe_id_set(conn, active_only=True)
        return {
            "databaseCount": len(active_db_ids),
            "databaseActiveCount": len(active_db_ids),
            "databaseTotalCount": len(db_ids),
            "databaseInactiveCount": max(0, len(db_ids) - len(active_db_ids)),
            "seedSourcePath": recipes_path,
            "seedSourceCount": len(seed_recipes_raw),
            "seedSourceIdCount": len(seed_ids),
            "missingSeedCount": len(seed_ids - db_ids),
            "extraDatabaseCount": len(db_ids - seed_ids) if seed_ids else len(db_ids),
        }
    finally:
        conn.close()


def seed_recipes(source_path: str | None = None, force_reseed: bool | None = None) -> Dict[str, Any]:
    recipes_path, recipes = _load_seed_recipes(source_path)
    if not recipes_path:
        before_count = get_recipe_count()
        return {
            "sourcePath": None,
            "sourceCount": 0,
            "beforeCount": before_count,
            "afterCount": before_count,
            "insertedCount": 0,
            "updatedCount": 0,
            "skippedExistingCount": 0,
            "forceReseed": bool(force_reseed),
        }

    if force_reseed is None:
        force_reseed = os.getenv("PCOSINA_FORCE_RESEED", "").strip().lower() in ("1", "true", "yes")

    medians = _compute_nutrition_medians(recipes)

    conn = _connect()
    inserted_count = 0
    updated_count = 0
    skipped_existing_count = 0
    try:
        before_count = _recipe_count(conn)
        existing_ids = _recipe_id_set(conn)
        cursor = conn.cursor()
        now_ms = int(time.time() * 1000)
        source_label = "seed"
        source_version = os.path.basename(recipes_path)
        if _use_postgres():
            conflict_sql = """
                DO UPDATE SET
                    title = EXCLUDED.title,
                    meal_type = EXCLUDED.meal_type,
                    calories = EXCLUDED.calories,
                    protein = EXCLUDED.protein,
                    carbs = EXCLUDED.carbs,
                    fats = EXCLUDED.fats,
                    fiber = EXCLUDED.fiber,
                    tags = EXCLUDED.tags,
                    minutes = EXCLUDED.minutes,
                    ingredients_json = EXCLUDED.ingredients_json,
                    steps_json = EXCLUDED.steps_json,
                    active = 1,
                    source = EXCLUDED.source,
                    source_version = EXCLUDED.source_version,
                    updated_at = EXCLUDED.updated_at,
                    deleted_at = 0
            """ if force_reseed else "DO NOTHING"
            insert_sql = f'''
                INSERT INTO recipes (
                    id, title, meal_type, calories, protein, carbs, fats, fiber,
                    tags, minutes, ingredients_json, steps_json,
                    active, source, source_version, created_at, updated_at, deleted_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) {conflict_sql}
            '''
        else:
            insert_sql = f'''
                INSERT {"OR REPLACE" if force_reseed else "OR IGNORE"} INTO recipes (
                    id, title, meal_type, calories, protein, carbs, fats, fiber,
                    tags, minutes, ingredients_json, steps_json,
                    active, source, source_version, created_at, updated_at, deleted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            '''

        for r in recipes:
            recipe_id = str(r.get("id") or "").strip()
            if not recipe_id:
                continue
            already_exists = recipe_id in existing_ids
            if already_exists and not force_reseed:
                skipped_existing_count += 1
                continue

            nut = r.get("nutrition", {})
            cal, prot, carb, fat, fiber = _normalize_nutrition(nut, medians)
            minutes = max(1, min(int(r.get("minutes") or 25), 480))

            tags = _infer_tags(r)
            cursor.execute(insert_sql, (
                recipe_id,
                r.get("name") or r.get("title") or "Unnamed",
                r.get("mealType", "Universal"),
                cal, prot, carb, fat, fiber,
                ",".join(tags),
                minutes,
                json.dumps(r.get("ingredients", [])),
                json.dumps(r.get("instructions", []) or r.get("steps", [])),
                1,
                source_label,
                source_version,
                now_ms,
                now_ms,
                0,
            ))
            if already_exists:
                updated_count += 1
            else:
                inserted_count += 1
                existing_ids.add(recipe_id)

        conn.commit()
        after_count = _recipe_count(conn)
        summary = {
            "sourcePath": recipes_path,
            "sourceCount": len(recipes),
            "beforeCount": before_count,
            "afterCount": after_count,
            "insertedCount": inserted_count,
            "updatedCount": updated_count,
            "skippedExistingCount": skipped_existing_count,
            "forceReseed": bool(force_reseed),
        }
        print(
            "DATABASE SYNCED: "
            f"{after_count} recipes ready for MILP Brain "
            f"({inserted_count} inserted, {updated_count} updated, {skipped_existing_count} kept)."
        )
        return summary
    finally:
        conn.close()


def _nutrition_correction_row_to_dict(row: Any) -> Dict[str, Any]:
    raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
    return {
        "id": raw.get("id"),
        "recipeId": raw.get("recipe_id"),
        "calories": raw.get("calories"),
        "proteinGrams": raw.get("protein"),
        "carbsGrams": raw.get("carbs"),
        "fatsGrams": raw.get("fats"),
        "fiberGrams": raw.get("fiber"),
        "sodiumMg": raw.get("sodium_mg"),
        "sugarGrams": raw.get("sugar_grams"),
        "active": bool(raw.get("active")),
        "notes": raw.get("notes"),
        "updatedAt": int(raw.get("updated_at") or 0),
    }


def _parse_nutrition_correction_notes(notes: Optional[str]) -> Dict[str, str]:
    metadata: Dict[str, str] = {}
    for part in str(notes or "").split(";"):
        if "=" not in part:
            continue
        key, value = part.split("=", 1)
        key = key.strip().lower()
        value = value.strip()
        if key and value:
            metadata[key] = value
    return metadata


def _list_active_nutrition_corrections_map(conn, recipe_ids: Optional[Iterable[str]] = None) -> Dict[str, Dict[str, Any]]:
    ids = [str(recipe_id).strip() for recipe_id in (recipe_ids or []) if str(recipe_id).strip()]
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
        if ids:
            cursor.execute(
                """
                SELECT id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, updated_at
                FROM recipe_nutrition_corrections
                WHERE active = TRUE AND recipe_id = ANY(%s)
                ORDER BY updated_at DESC, id ASC
                """,
                (ids,),
            )
        else:
            cursor.execute(
                """
                SELECT id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, updated_at
                FROM recipe_nutrition_corrections
                WHERE active = TRUE
                ORDER BY updated_at DESC, id ASC
                """
            )
    else:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
        if ids:
            placeholders = ",".join("?" for _ in ids)
            cursor.execute(
                f"""
                SELECT id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, updated_at
                FROM recipe_nutrition_corrections
                WHERE active = 1 AND recipe_id IN ({placeholders})
                ORDER BY updated_at DESC, id ASC
                """,
                tuple(ids),
            )
        else:
            cursor.execute(
                """
                SELECT id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, updated_at
                FROM recipe_nutrition_corrections
                WHERE active = 1
                ORDER BY updated_at DESC, id ASC
                """
            )
    rows = cursor.fetchall()
    mapping: Dict[str, Dict[str, Any]] = {}
    for row in rows:
        item = _nutrition_correction_row_to_dict(row)
        recipe_id = str(item.get("recipeId") or "").strip()
        if recipe_id and recipe_id not in mapping:
            mapping[recipe_id] = item
    return mapping


def _apply_nutrition_correction(recipe: Dict[str, Any], correction: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if not correction or not correction.get("active", True):
        return recipe
    updated = dict(recipe)
    for key in ("calories", "proteinGrams", "carbsGrams", "fatsGrams", "fiberGrams", "sodiumMg", "sugarGrams"):
        value = correction.get(key)
        if value is not None:
            updated[key] = int(value)
    updated["nutritionCorrectionId"] = correction.get("id")
    metadata = _parse_nutrition_correction_notes(correction.get("notes"))
    updated["nutritionDataSource"] = metadata.get("source") or "manual_correction"
    updated["nutritionConfidence"] = metadata.get("confidence") or "reviewed"
    updated["nutritionReviewStatus"] = metadata.get("review_status") or metadata.get("reviewStatus") or "reviewed"
    if metadata.get("notes"):
        updated["nutritionNotes"] = metadata["notes"]
    return updated

def get_all_recipes():
    if not _use_postgres() and not os.path.exists(DB_NAME):
        return []
    conn = _connect()
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
    else:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
    try:
        cursor.execute("SELECT * FROM recipes WHERE COALESCE(active, 1) = 1")
        rows = cursor.fetchall()
        correction_map = _list_active_nutrition_corrections_map(conn)
        recipes = []
        for row in rows:
            recipe = {
                "id": row["id"], "title": row["title"], "mealType": row["meal_type"],
                "calories": row["calories"], "proteinGrams": row["protein"],
                "carbsGrams": row["carbs"], "fatsGrams": row["fats"],
                "fiberGrams": row["fiber"], "tags": row["tags"].split(",") if row["tags"] else [],
                "minutes": row["minutes"], "ingredients": json.loads(row["ingredients_json"]),
                "steps": json.loads(row["steps_json"])
            }
            recipes.append(_apply_nutrition_correction(recipe, correction_map.get(str(row["id"]))))
        return recipes
    finally:
        conn.close()


def get_recipe_by_id(recipe_id: str) -> Dict[str, Any] | None:
    token = str(recipe_id or "").strip()
    if not token:
        return None
    if not _use_postgres() and not os.path.exists(DB_NAME):
        return None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cursor = conn.cursor(row_factory=dict_row)
            cursor.execute("SELECT * FROM recipes WHERE id = %s AND COALESCE(active, 1) = 1", (token,))
        else:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM recipes WHERE id = ? AND COALESCE(active, 1) = 1", (token,))
        row = cursor.fetchone()
        if not row:
            return None
        correction_map = _list_active_nutrition_corrections_map(conn, [token])
        recipe = {
            "id": row["id"],
            "title": row["title"],
            "mealType": row["meal_type"],
            "calories": row["calories"],
            "proteinGrams": row["protein"],
            "carbsGrams": row["carbs"],
            "fatsGrams": row["fats"],
            "fiberGrams": row["fiber"],
            "tags": row["tags"].split(",") if row["tags"] else [],
            "minutes": row["minutes"],
            "ingredients": json.loads(row["ingredients_json"]),
            "steps": json.loads(row["steps_json"]),
        }
        return _apply_nutrition_correction(recipe, correction_map.get(token))
    finally:
        conn.close()


def list_admin_recipes(q: str | None = None, meal_type: str | None = None, limit: int = 100) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 100), 500))
    query = (q or "").strip()
    meal = (meal_type or "").strip()
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cursor = conn.cursor(row_factory=dict_row)
            cursor.execute(
                """
                SELECT id, title, meal_type, calories, protein, carbs, fats, fiber, tags, minutes,
                       active, source, source_version, created_at, updated_at, deleted_at
                FROM recipes
                WHERE COALESCE(active, 1) = 1
                  AND (%s = '' OR title ILIKE %s)
                  AND (%s = '' OR meal_type ILIKE %s)
                ORDER BY title ASC, id ASC
                LIMIT %s
                """,
                (query, f"%{query}%", meal, f"%{meal}%", limit),
            )
        else:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT id, title, meal_type, calories, protein, carbs, fats, fiber, tags, minutes,
                       active, source, source_version, created_at, updated_at, deleted_at
                FROM recipes
                WHERE COALESCE(active, 1) = 1
                  AND (? = '' OR lower(title) LIKE lower(?))
                  AND (? = '' OR lower(meal_type) LIKE lower(?))
                ORDER BY title ASC, id ASC
                LIMIT ?
                """,
                (query, f"%{query}%", meal, f"%{meal}%", limit),
            )
        rows = cursor.fetchall()
        recipe_ids = []
        for row in rows:
            raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
            recipe_ids.append(raw.get("id"))
        correction_map = _list_active_nutrition_corrections_map(conn, recipe_ids)
        items: List[Dict[str, Any]] = []
        for row in rows:
            raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
            item = {
                "id": raw.get("id"),
                "title": raw.get("title"),
                "mealType": raw.get("meal_type"),
                "calories": raw.get("calories"),
                "proteinGrams": raw.get("protein"),
                "carbsGrams": raw.get("carbs"),
                "fatsGrams": raw.get("fats"),
                "fiberGrams": raw.get("fiber"),
                "tags": str(raw.get("tags") or "").split(",") if raw.get("tags") else [],
                "minutes": raw.get("minutes"),
                "active": bool(raw.get("active", 1)),
                "source": raw.get("source"),
                "sourceVersion": raw.get("source_version"),
                "createdAt": int(raw.get("created_at") or 0),
                "updatedAt": int(raw.get("updated_at") or 0),
                "deletedAt": int(raw.get("deleted_at") or 0),
            }
            items.append(_apply_nutrition_correction(item, correction_map.get(str(raw.get("id") or ""))))
        return items
    finally:
        conn.close()


def upsert_recipe(recipe: Dict[str, Any]) -> Dict[str, Any]:
    recipe_id = str(recipe.get("id") or uuid.uuid4().hex).strip()
    title = str(recipe.get("title") or "").strip() or "Untitled Recipe"
    meal_type = str(recipe.get("mealType") or "Universal").strip() or "Universal"
    calories = int(recipe.get("calories") or 0)
    protein = int(recipe.get("proteinGrams") or 0)
    carbs = int(recipe.get("carbsGrams") or 0)
    fats = int(recipe.get("fatsGrams") or 0)
    fiber = int(recipe.get("fiberGrams") or 0)
    minutes = max(0, int(recipe.get("minutes") or 0))
    tags = [str(tag).strip() for tag in (recipe.get("tags") or []) if str(tag).strip()]
    ingredients_json = json.dumps(recipe.get("ingredients") or [], ensure_ascii=True)
    steps_json = json.dumps(recipe.get("steps") or [], ensure_ascii=True)
    source = str(recipe.get("source") or "admin").strip() or "admin"
    source_version = str(recipe.get("sourceVersion") or recipe.get("source_version") or "").strip() or None
    now_ms = int(time.time() * 1000)
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO recipes (
                    id, title, meal_type, calories, protein, carbs, fats, fiber, tags, minutes,
                    ingredients_json, steps_json, active, source, source_version, created_at, updated_at, deleted_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title,
                    meal_type = EXCLUDED.meal_type,
                    calories = EXCLUDED.calories,
                    protein = EXCLUDED.protein,
                    carbs = EXCLUDED.carbs,
                    fats = EXCLUDED.fats,
                    fiber = EXCLUDED.fiber,
                    tags = EXCLUDED.tags,
                    minutes = EXCLUDED.minutes,
                    ingredients_json = EXCLUDED.ingredients_json,
                    steps_json = EXCLUDED.steps_json,
                    active = 1,
                    source = EXCLUDED.source,
                    source_version = EXCLUDED.source_version,
                    updated_at = EXCLUDED.updated_at,
                    deleted_at = 0
                """,
                (
                    recipe_id, title, meal_type, calories, protein, carbs, fats, fiber, ",".join(tags), minutes,
                    ingredients_json, steps_json, 1, source, source_version, now_ms, now_ms, 0,
                ),
            )
        else:
            cur.execute(
                """
                INSERT INTO recipes (
                    id, title, meal_type, calories, protein, carbs, fats, fiber, tags, minutes,
                    ingredients_json, steps_json, active, source, source_version, created_at, updated_at, deleted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    meal_type = excluded.meal_type,
                    calories = excluded.calories,
                    protein = excluded.protein,
                    carbs = excluded.carbs,
                    fats = excluded.fats,
                    fiber = excluded.fiber,
                    tags = excluded.tags,
                    minutes = excluded.minutes,
                    ingredients_json = excluded.ingredients_json,
                    steps_json = excluded.steps_json,
                    active = 1,
                    source = excluded.source,
                    source_version = excluded.source_version,
                    updated_at = excluded.updated_at,
                    deleted_at = 0
                """,
                (
                    recipe_id, title, meal_type, calories, protein, carbs, fats, fiber, ",".join(tags), minutes,
                    ingredients_json, steps_json, 1, source, source_version, now_ms, now_ms, 0,
                ),
            )
        conn.commit()
    finally:
        conn.close()
    return get_recipe_by_id(recipe_id) or {
        "id": recipe_id,
        "title": title,
        "mealType": meal_type,
        "calories": calories,
        "proteinGrams": protein,
        "carbsGrams": carbs,
        "fatsGrams": fats,
        "fiberGrams": fiber,
        "tags": tags,
        "minutes": minutes,
        "ingredients": recipe.get("ingredients") or [],
        "steps": recipe.get("steps") or [],
        "active": True,
        "source": source,
        "sourceVersion": source_version,
        "createdAt": now_ms,
        "updatedAt": now_ms,
        "deletedAt": 0,
    }


def delete_recipe(recipe_id: str) -> int:
    token = str(recipe_id or "").strip()
    if not token:
        return 0
    conn = _connect()
    try:
        cur = conn.cursor()
        now_ms = int(time.time() * 1000)
        if _use_postgres():
            cur.execute(
                """
                UPDATE recipes
                SET active = 0, deleted_at = %s, updated_at = %s
                WHERE id = %s AND COALESCE(active, 1) = 1
                """,
                (now_ms, now_ms, token),
            )
        else:
            cur.execute(
                """
                UPDATE recipes
                SET active = 0, deleted_at = ?, updated_at = ?
                WHERE id = ? AND COALESCE(active, 1) = 1
                """,
                (now_ms, now_ms, token),
            )
        conn.commit()
        return int(cur.rowcount or 0)
    finally:
        conn.close()


def _price_rule_row_to_dict(row: Any) -> Dict[str, Any]:
    raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
    keywords_json = raw.get("keywords_json") or "[]"
    try:
        keywords = json.loads(keywords_json)
    except Exception:
        keywords = []
    return {
        "id": raw.get("id"),
        "keywords": [str(item).strip() for item in (keywords or []) if str(item).strip()],
        "pricePhp": int(raw.get("price_php") or 0),
        "priceMinPhp": int(raw["price_min_php"]) if raw.get("price_min_php") is not None else None,
        "priceMaxPhp": int(raw["price_max_php"]) if raw.get("price_max_php") is not None else None,
        "category": str(raw.get("category") or "Others"),
        "unit": raw.get("unit"),
        "active": bool(raw.get("active")),
        "notes": raw.get("notes"),
        "updatedAt": int(raw.get("updated_at") or 0),
    }


def list_admin_price_rules(
    q: str | None = None,
    category: str | None = None,
    limit: int = 100,
    active_only: bool = False,
) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 100), 500))
    query = (q or "").strip()
    category_value = (category or "").strip()
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cursor = conn.cursor(row_factory=dict_row)
            cursor.execute(
                """
                SELECT id, keywords_json, price_php, price_min_php, price_max_php, category, unit, active, notes, updated_at
                FROM ingredient_price_rules
                WHERE (%s = '' OR keywords_json ILIKE %s OR category ILIKE %s)
                  AND (%s = '' OR category ILIKE %s)
                  AND (%s = FALSE OR active = TRUE)
                ORDER BY updated_at DESC, id ASC
                LIMIT %s
                """,
                (query, f"%{query}%", f"%{query}%", category_value, f"%{category_value}%", active_only, limit),
            )
        else:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT id, keywords_json, price_php, price_min_php, price_max_php, category, unit, active, notes, updated_at
                FROM ingredient_price_rules
                WHERE (? = '' OR lower(keywords_json) LIKE lower(?) OR lower(category) LIKE lower(?))
                  AND (? = '' OR lower(category) LIKE lower(?))
                  AND (? = 0 OR active = 1)
                ORDER BY updated_at DESC, id ASC
                LIMIT ?
                """,
                (query, f"%{query}%", f"%{query}%", category_value, f"%{category_value}%", 1 if active_only else 0, limit),
            )
        rows = cursor.fetchall()
        return [_price_rule_row_to_dict(row) for row in rows]
    finally:
        conn.close()


def list_active_price_rules(limit: int = 500) -> List[Dict[str, Any]]:
    return list_admin_price_rules(limit=limit, active_only=True)


def get_price_rule_by_id(rule_id: str) -> Dict[str, Any] | None:
    token = str(rule_id or "").strip()
    if not token:
        return None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cursor = conn.cursor(row_factory=dict_row)
            cursor.execute(
                """
                SELECT id, keywords_json, price_php, price_min_php, price_max_php, category, unit, active, notes, updated_at
                FROM ingredient_price_rules
                WHERE id = %s
                """,
                (token,),
            )
        else:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT id, keywords_json, price_php, price_min_php, price_max_php, category, unit, active, notes, updated_at
                FROM ingredient_price_rules
                WHERE id = ?
                """,
                (token,),
            )
        row = cursor.fetchone()
        if not row:
            return None
        return _price_rule_row_to_dict(row)
    finally:
        conn.close()


def _optional_positive_int(value: Any) -> int | None:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    try:
        parsed = int(text)
    except (TypeError, ValueError):
        return None
    return max(1, parsed)


def upsert_price_rule(rule: Dict[str, Any]) -> Dict[str, Any]:
    rule_id = str(rule.get("id") or uuid.uuid4().hex).strip()
    keywords = [str(item).strip().lower() for item in (rule.get("keywords") or []) if str(item).strip()]
    price_php = max(1, int(rule.get("pricePhp") or 0))
    price_min_php = _optional_positive_int(rule.get("priceMinPhp"))
    price_max_php = _optional_positive_int(rule.get("priceMaxPhp"))
    if price_min_php is not None and price_max_php is not None and price_max_php < price_min_php:
        price_min_php, price_max_php = price_max_php, price_min_php
    category = str(rule.get("category") or "Others").strip() or "Others"
    unit_raw = str(rule.get("unit") or "").strip()
    unit = unit_raw or None
    active = bool(rule.get("active", True))
    notes = str(rule.get("notes") or "").strip() or None
    now = int(time.time() * 1000)
    keywords_json = json.dumps(keywords, ensure_ascii=True)
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO ingredient_price_rules (
                    id, keywords_json, price_php, price_min_php, price_max_php, category, unit, active, notes, created_at, updated_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    keywords_json = EXCLUDED.keywords_json,
                    price_php = EXCLUDED.price_php,
                    price_min_php = EXCLUDED.price_min_php,
                    price_max_php = EXCLUDED.price_max_php,
                    category = EXCLUDED.category,
                    unit = EXCLUDED.unit,
                    active = EXCLUDED.active,
                    notes = EXCLUDED.notes,
                    updated_at = EXCLUDED.updated_at
                """,
                (rule_id, keywords_json, price_php, price_min_php, price_max_php, category, unit, active, notes, now, now),
            )
        else:
            cur.execute(
                """
                INSERT OR REPLACE INTO ingredient_price_rules (
                    id, keywords_json, price_php, price_min_php, price_max_php, category, unit, active, notes, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM ingredient_price_rules WHERE id = ?), ?), ?)
                """,
                (rule_id, keywords_json, price_php, price_min_php, price_max_php, category, unit, 1 if active else 0, notes, rule_id, now, now),
            )
        conn.commit()
    finally:
        conn.close()
    return get_price_rule_by_id(rule_id) or {
        "id": rule_id,
        "keywords": keywords,
        "pricePhp": price_php,
        "priceMinPhp": price_min_php,
        "priceMaxPhp": price_max_php,
        "category": category,
        "unit": unit,
        "active": active,
        "notes": notes,
        "updatedAt": now,
    }


def delete_price_rule(rule_id: str) -> int:
    token = str(rule_id or "").strip()
    if not token:
        return 0
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("DELETE FROM ingredient_price_rules WHERE id = %s", (token,))
        else:
            cur.execute("DELETE FROM ingredient_price_rules WHERE id = ?", (token,))
        conn.commit()
        return int(cur.rowcount or 0)
    finally:
        conn.close()


def list_admin_nutrition_corrections(q: str | None = None, limit: int = 100) -> List[Dict[str, Any]]:
    limit = max(1, min(int(limit or 100), 500))
    query = (q or "").strip()
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cursor = conn.cursor(row_factory=dict_row)
            cursor.execute(
                """
                SELECT c.id, c.recipe_id, c.calories, c.protein, c.carbs, c.fats, c.fiber, c.sodium_mg, c.sugar_grams, c.active, c.notes, c.updated_at,
                       r.title AS recipe_title
                FROM recipe_nutrition_corrections c
                LEFT JOIN recipes r ON r.id = c.recipe_id
                WHERE (%s = '' OR c.recipe_id ILIKE %s OR COALESCE(r.title, '') ILIKE %s)
                ORDER BY c.updated_at DESC, c.recipe_id ASC
                LIMIT %s
                """,
                (query, f"%{query}%", f"%{query}%", limit),
            )
        else:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT c.id, c.recipe_id, c.calories, c.protein, c.carbs, c.fats, c.fiber, c.sodium_mg, c.sugar_grams, c.active, c.notes, c.updated_at,
                       r.title AS recipe_title
                FROM recipe_nutrition_corrections c
                LEFT JOIN recipes r ON r.id = c.recipe_id
                WHERE (? = '' OR lower(c.recipe_id) LIKE lower(?) OR lower(COALESCE(r.title, '')) LIKE lower(?))
                ORDER BY c.updated_at DESC, c.recipe_id ASC
                LIMIT ?
                """,
                (query, f"%{query}%", f"%{query}%", limit),
            )
        rows = cursor.fetchall()
        items = []
        for row in rows:
            item = _nutrition_correction_row_to_dict(row)
            raw = dict(row) if isinstance(row, dict) else {k: row[k] for k in row.keys()}
            item["recipeTitle"] = raw.get("recipe_title")
            items.append(item)
        return items
    finally:
        conn.close()


def get_nutrition_correction_by_recipe_id(recipe_id: str) -> Dict[str, Any] | None:
    token = str(recipe_id or "").strip()
    if not token:
        return None
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cursor = conn.cursor(row_factory=dict_row)
            cursor.execute(
                """
                SELECT id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, updated_at
                FROM recipe_nutrition_corrections
                WHERE recipe_id = %s
                """,
                (token,),
            )
        else:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute(
                """
                SELECT id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, updated_at
                FROM recipe_nutrition_corrections
                WHERE recipe_id = ?
                """,
                (token,),
            )
        row = cursor.fetchone()
        if not row:
            return None
        return _nutrition_correction_row_to_dict(row)
    finally:
        conn.close()


def upsert_nutrition_correction(recipe_id: str, correction: Dict[str, Any]) -> Dict[str, Any]:
    recipe_token = str(recipe_id or "").strip()
    if not recipe_token:
        raise ValueError("recipe_id is required")
    existing = get_nutrition_correction_by_recipe_id(recipe_token)
    correction_id = str((existing or {}).get("id") or uuid.uuid4().hex).strip()
    now = int(time.time() * 1000)
    calories = correction.get("calories")
    protein = correction.get("proteinGrams")
    carbs = correction.get("carbsGrams")
    fats = correction.get("fatsGrams")
    fiber = correction.get("fiberGrams")
    sodium = correction.get("sodiumMg")
    sugar = correction.get("sugarGrams")
    active = bool(correction.get("active", True))
    notes = str(correction.get("notes") or "").strip() or None
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO recipe_nutrition_corrections (
                    id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, created_at, updated_at
                )
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (recipe_id) DO UPDATE SET
                    calories = EXCLUDED.calories,
                    protein = EXCLUDED.protein,
                    carbs = EXCLUDED.carbs,
                    fats = EXCLUDED.fats,
                    fiber = EXCLUDED.fiber,
                    sodium_mg = EXCLUDED.sodium_mg,
                    sugar_grams = EXCLUDED.sugar_grams,
                    active = EXCLUDED.active,
                    notes = EXCLUDED.notes,
                    updated_at = EXCLUDED.updated_at
                """,
                (
                    correction_id, recipe_token,
                    calories, protein, carbs, fats, fiber, sodium, sugar,
                    active, notes, now, now,
                ),
            )
        else:
            cur.execute(
                """
                INSERT OR REPLACE INTO recipe_nutrition_corrections (
                    id, recipe_id, calories, protein, carbs, fats, fiber, sodium_mg, sugar_grams, active, notes, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE((SELECT created_at FROM recipe_nutrition_corrections WHERE recipe_id = ?), ?), ?)
                """,
                (
                    correction_id, recipe_token,
                    calories, protein, carbs, fats, fiber, sodium, sugar,
                    1 if active else 0, notes, recipe_token, now, now,
                ),
            )
        conn.commit()
    finally:
        conn.close()
    return get_nutrition_correction_by_recipe_id(recipe_token) or {
        "id": correction_id,
        "recipeId": recipe_token,
        "calories": calories,
        "proteinGrams": protein,
        "carbsGrams": carbs,
        "fatsGrams": fats,
        "fiberGrams": fiber,
        "sodiumMg": sodium,
        "sugarGrams": sugar,
        "active": active,
        "notes": notes,
        "updatedAt": now,
    }


def delete_nutrition_correction(recipe_id: str) -> int:
    token = str(recipe_id or "").strip()
    if not token:
        return 0
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("DELETE FROM recipe_nutrition_corrections WHERE recipe_id = %s", (token,))
        else:
            cur.execute("DELETE FROM recipe_nutrition_corrections WHERE recipe_id = ?", (token,))
        conn.commit()
        return int(cur.rowcount or 0)
    finally:
        conn.close()


def get_recipe_summaries(meal_type: str | None = None, limit: int = 50):
    if not _use_postgres() and not os.path.exists(DB_NAME):
        return []
    limit = max(1, min(int(limit or 50), 200))
    conn = _connect()
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
    else:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
    try:
        if meal_type:
            if _use_postgres():
                cursor.execute(
                    """
                    SELECT id, title, meal_type, minutes
                    FROM recipes
                    WHERE COALESCE(active, 1) = 1
                      AND (meal_type ILIKE %s OR meal_type ILIKE '%%universal%%')
                    ORDER BY id
                    LIMIT %s
                    """,
                    (f"%{meal_type}%", limit)
                )
            else:
                cursor.execute(
                    """
                    SELECT id, title, meal_type, minutes
                    FROM recipes
                    WHERE COALESCE(active, 1) = 1
                      AND (lower(meal_type) LIKE lower(?) OR lower(meal_type) LIKE '%universal%')
                    ORDER BY id
                    LIMIT ?
                    """,
                    (f"%{meal_type}%", limit)
                )
        else:
            cursor.execute(
                "SELECT id, title, meal_type, minutes FROM recipes WHERE COALESCE(active, 1) = 1 ORDER BY id LIMIT ?",
                (limit,)
            ) if not _use_postgres() else cursor.execute(
                "SELECT id, title, meal_type, minutes FROM recipes WHERE COALESCE(active, 1) = 1 ORDER BY id LIMIT %s",
                (limit,)
            )
        rows = cursor.fetchall()
        summaries = []
        for row in rows:
            if isinstance(row, dict):
                summaries.append({
                    "id": row.get("id"),
                    "title": row.get("title"),
                    "mealType": row.get("meal_type"),
                    "minutes": row.get("minutes"),
                })
            else:
                summaries.append({
                    "id": row["id"],
                    "title": row["title"],
                    "mealType": row["meal_type"],
                    "minutes": row["minutes"],
                })
        return summaries
    finally:
        conn.close()

def save_feedback(message: str):
    message = _normalize_feedback_message(message)
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("INSERT INTO feedback (message) VALUES (%s)", (message,))
        else:
            cur.execute(
                "INSERT INTO feedback (message, created_at) VALUES (?, ?)",
                (message, int(time.time() * 1000))
            )
        conn.commit()
    finally:
        conn.close()

def get_recent_feedback(limit: int = 50, order: str = "desc"):
    conn = _connect()
    try:
        cur = conn.cursor()
        order_dir = "ASC" if str(order).lower() == "asc" else "DESC"
        if _use_postgres():
            cur.execute(f"SELECT id, message, created_at FROM feedback ORDER BY id {order_dir} LIMIT %s", (limit,))
            rows = cur.fetchall()
            return [{"id": r[0], "message": r[1], "created_at": str(r[2])} for r in rows]
        else:
            cur.execute(f"SELECT id, message, created_at FROM feedback ORDER BY id {order_dir} LIMIT ?", (limit,))
            rows = cur.fetchall()
            return [{"id": r[0], "message": r[1], "created_at": r[2]} for r in rows]
    finally:
        conn.close()

if __name__ == "__main__":
    init_db()
    seed_recipes()
