import sys
from pathlib import Path
from uuid import uuid4

import pytest

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import database


def _temp_db_path() -> Path:
    base = Path(__file__).resolve().parent / ".tmp_feedback_storage"
    base.mkdir(parents=True, exist_ok=True)
    return base / f"feedback_{uuid4().hex}.db"


def test_feedback_storage_rejects_oversized_message(monkeypatch):
    db_path = _temp_db_path()
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))

    database.init_db()

    with pytest.raises(ValueError):
        database.save_feedback("x" * (database.FEEDBACK_MESSAGE_MAX_CHARS + 1))

    assert database.get_recent_feedback() == []


def test_feedback_storage_cleanup_applies_retention(monkeypatch):
    db_path = _temp_db_path()
    monkeypatch.setattr(database, "DATABASE_URL", "")
    monkeypatch.setattr(database, "DB_NAME", str(db_path))

    database.init_db()
    database.save_feedback("new")

    conn = database._connect()
    try:
        old_ms = int(0)
        conn.execute(
            "INSERT INTO feedback (message, created_at) VALUES (?, ?)",
            ("old", old_ms),
        )
        conn.commit()
    finally:
        conn.close()

    deleted = database.cleanup_feedback(retention_days=365)
    remaining = database.get_recent_feedback(limit=10, order="asc")

    assert deleted == 1
    assert [item["message"] for item in remaining] == ["new"]
