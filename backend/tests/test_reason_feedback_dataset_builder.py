import json
import sqlite3
import subprocess
import sys
from pathlib import Path
import shutil


def test_build_reason_feedback_dataset_script():
    base = Path("backend/tests/.tmp_reason_feedback_builder")
    shutil.rmtree(base, ignore_errors=True)
    base.mkdir(parents=True, exist_ok=True)
    db_path = base / "ml.db"
    out_dir = base / "reason_out"
    conn = sqlite3.connect(str(db_path))
    try:
        cur = conn.cursor()
        cur.execute(
            """
            CREATE TABLE ml_events (
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
        )
        now = 1730000000000
        rows = [
            (
                "e1",
                "why_replaced_submitted",
                "uid-a",
                "req-1",
                "plan-1",
                "r1",
                0,
                now,
                "policy-v1",
                "1.1.0",
                json.dumps(
                    {
                        "plan_id": "plan-1",
                        "slot_index": 0,
                        "reason_text": "Too expensive and missing ingredients.",
                    }
                ),
                now,
                "d1",
            ),
            (
                "e2",
                "why_skipped_submitted",
                "uid-a",
                "req-2",
                "plan-1",
                "r2",
                1,
                now + 1000,
                "policy-v1",
                "1.1.0",
                json.dumps(
                    {
                        "plan_id": "plan-1",
                        "slot_index": 1,
                        "reason_tag": "unchecked_by_user",
                    }
                ),
                now + 1000,
                "d2",
            ),
        ]
        cur.executemany(
            """
            INSERT INTO ml_events (
                id, event_name, uid_hash, request_id, plan_id, recipe_id, slot_index,
                event_time_ms, policy_version, schema_version, payload_json, created_at, dedupe_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            rows,
        )
        conn.commit()
    finally:
        conn.close()

    script = Path("ml/offline_training/build_reason_feedback_dataset_v1.py")
    result = subprocess.run(
        [
            sys.executable,
            str(script),
            "--db-path",
            str(db_path),
            "--output-dir",
            str(out_dir),
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr or result.stdout
    summary_path = out_dir / "reason_feedback_summary.json"
    assert summary_path.exists()
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
    assert summary["rows"] == 2
    assert summary["uniqueUsers"] == 1
    assert summary["sourceEventWindow"]["minEventTimeMs"] == now
    assert summary["sourceEventWindow"]["maxEventTimeMs"] == now + 1000
    assert "cost_too_high" in (summary.get("tagCounts") or {})
    assert (out_dir / "reason_feedback_events.csv").exists()
    assert (out_dir / "reason_feedback_user_features.csv").exists()
