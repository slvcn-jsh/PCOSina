import json
import sqlite3
import subprocess
import sys
from pathlib import Path
import shutil


def _bucket(request_id: str) -> int:
    import hashlib

    digest = hashlib.sha256(request_id.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % 10


def _find_request_for_bucket(target: int) -> str:
    for idx in range(1, 10000):
        rid = f"req-{idx}"
        if _bucket(rid) == target:
            return rid
    raise RuntimeError(f"unable to find request id for bucket {target}")


def test_build_training_dataset_script():
    base = Path("backend/tests/.tmp_ml_dataset_builder")
    shutil.rmtree(base, ignore_errors=True)
    base.mkdir(parents=True, exist_ok=True)
    db_path = base / "ml.db"
    out_dir = base / "dataset_out"
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
        req_test = _find_request_for_bucket(0)
        req_val = _find_request_for_bucket(1)
        req_train = _find_request_for_bucket(2)
        rows = []
        now = 1730000000000
        for req in [req_train, req_val, req_test]:
            rows.append(
                (
                    f"{req}-pos",
                    req,
                    "uidhash",
                    f"{req}-r1",
                    "Lunch",
                    now,
                    1,
                    0.9,
                    1.5,
                    "stage1_heuristic_shadow_only",
                    "shadow_v0",
                    json.dumps({"recipe_calories": 500, "recipe_protein": 30}),
                    now,
                )
            )
            rows.append(
                (
                    f"{req}-neg",
                    req,
                    "uidhash",
                    f"{req}-r2",
                    "Lunch",
                    now + 1,
                    0,
                    0.1,
                    0.9,
                    "stage1_heuristic_shadow_only",
                    "shadow_v0",
                    json.dumps({"recipe_calories": 300, "recipe_protein": 10}),
                    now + 1,
                )
            )
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
        ml_rows = [
            (
                "ev-1",
                "why_replaced_submitted",
                "uidhash",
                req_train,
                "plan-1",
                f"{req_train}-r1",
                0,
                now + 2,
                "policy-v1",
                "1.1.0",
                json.dumps(
                    {
                        "plan_id": "plan-1",
                        "slot_index": 0,
                        "reason_text": "Masyadong mahal and kulang ingredients",
                        "reason_tag": "manual_swap",
                    }
                ),
                now + 2,
                "dedupe-1",
            ),
            (
                "ev-2",
                "why_skipped_submitted",
                "uidhash",
                req_train,
                "plan-1",
                f"{req_train}-r1",
                1,
                now + 3,
                "policy-v1",
                "1.1.0",
                json.dumps(
                    {
                        "plan_id": "plan-1",
                        "slot_index": 1,
                        "reason_tag": "unchecked_by_user",
                    }
                ),
                now + 3,
                "dedupe-2",
            ),
        ]
        cur.executemany(
            """
            INSERT INTO ml_events (
                id, event_name, uid_hash, request_id, plan_id, recipe_id, slot_index,
                event_time_ms, policy_version, schema_version, payload_json, created_at, dedupe_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            ml_rows,
        )
        conn.commit()
    finally:
        conn.close()

    script = Path("ml/offline_training/build_training_dataset_v1.py")
    result = subprocess.run(
        [
            sys.executable,
            str(script),
            "--db-path",
            str(db_path),
            "--output-dir",
            str(out_dir),
            "--seed",
            "2026",
        ],
        check=False,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr or result.stdout
    manifest_path = out_dir / "dataset_manifest.json"
    assert manifest_path.exists()
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert manifest["splitRows"]["train"] > 0
    assert manifest["splitRows"]["val"] > 0
    assert manifest["splitRows"]["test"] > 0
    assert manifest["sourceTelemetryWindow"]["rowCount"] == 6
    assert manifest["sourceTelemetryWindow"]["minGeneratedAtMs"] == now
    assert manifest["sourceTelemetryWindow"]["maxGeneratedAtMs"] == now + 1
    assert manifest["reasonFeedbackWindow"]["rowCount"] == 2
    assert manifest["reasonFeedbackWindow"]["uniqueUsers"] == 1
    assert manifest["reasonFeedbackWindow"]["minEventTimeMs"] == now + 2
    assert manifest["reasonFeedbackWindow"]["maxEventTimeMs"] == now + 3
    feature_columns = set(manifest.get("featureColumns") or [])
    assert any(col.startswith("replace_reason_tag_hist_") for col in feature_columns)
    assert any(col.startswith("skip_reason_tag_hist_") for col in feature_columns)
    assert (out_dir / "train.csv").exists()
    assert (out_dir / "val.csv").exists()
    assert (out_dir / "test.csv").exists()
