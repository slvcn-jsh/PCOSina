import json
import sqlite3
import subprocess
import sys
from pathlib import Path
import shutil

REPO_ROOT = Path(__file__).resolve().parents[2]


def test_generate_reason_telemetry_script():
    base = REPO_ROOT / "backend" / "tests" / ".tmp_reason_telemetry_generator"
    shutil.rmtree(base, ignore_errors=True)
    base.mkdir(parents=True, exist_ok=True)
    db_path = base / "ml.db"
    conn = sqlite3.connect(str(db_path))
    try:
        cur = conn.cursor()
        cur.execute(
            """
            CREATE TABLE ml_stage1_candidate_features (
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
        )
        now = 1730000000000
        rows = [
            (
                "s1",
                "req-a",
                "uidhash-a",
                "recipe-a",
                "Lunch",
                now,
                1,
                0.7,
                0.6,
                "stage1_heuristic_shadow_only",
                "shadow_v0",
                json.dumps({"recipe_calories": 500}),
                now,
            ),
            (
                "s2",
                "req-b",
                "uidhash-b",
                "recipe-b",
                "Dinner",
                now + 1,
                0,
                0.2,
                0.4,
                "stage1_heuristic_shadow_only",
                "shadow_v0",
                json.dumps({"recipe_calories": 420}),
                now + 1,
            ),
        ]
        cur.executemany(
            """
            INSERT INTO ml_stage1_candidate_features (
                id, request_id, uid_hash, recipe_id, meal_bucket, generated_at_ms,
                selected_by_solver, model_score, heuristic_score, ranking_strategy,
                model_version, feature_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            rows,
        )
        conn.commit()
    finally:
        conn.close()

    script = REPO_ROOT / "ml" / "offline_training" / "generate_reason_telemetry_v1.py"
    result = subprocess.run(
        [
            sys.executable,
            str(script),
            "--db-path",
            str(db_path),
            "--events",
            "20",
            "--seed",
            "2026",
        ],
        check=False,
        capture_output=True,
        text=True,
        cwd=REPO_ROOT,
    )
    assert result.returncode == 0, result.stderr or result.stdout

    conn = sqlite3.connect(str(db_path))
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT COUNT(*) FROM ml_events WHERE event_name IN ('why_replaced_submitted', 'why_skipped_submitted')"
        )
        count = int(cur.fetchone()[0] or 0)
    finally:
        conn.close()
    assert count >= 20
