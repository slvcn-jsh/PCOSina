"""Shared PostgreSQL coordination for application and policy migrations."""

from __future__ import annotations

from typing import Any


SCHEMA_MIGRATION_TABLE_NAME = "schema_migrations"
POSTGRES_SCHEMA_BOOTSTRAP_LOCK_KEY = 2026032901


def postgres_schema_migration_table_exists(conn: Any) -> bool:
    """Check for the shared migration table without creating database objects."""
    cursor = conn.cursor()
    cursor.execute("SELECT to_regclass(%s)", (SCHEMA_MIGRATION_TABLE_NAME,))
    row = cursor.fetchone()
    if not row:
        return False
    if isinstance(row, dict):
        return next(iter(row.values()), None) is not None
    return row[0] is not None
