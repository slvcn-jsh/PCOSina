#!/usr/bin/env python
"""Build reproducible LightGBM V1 training datasets from stage1 telemetry tables."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sqlite3
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, List, Tuple

ROOT = Path(__file__).resolve().parents[2]
BACKEND_DIR = ROOT / "backend"
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from services.reason_normalizer import normalize_reason_payload  # type: ignore


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Build PCOSINA ML training dataset v1")
    parser.add_argument("--db-path", default="pcosina.db", help="SQLite path that contains ml_stage1_candidate_features table")
    parser.add_argument("--output-dir", default="ml/offline_training/artifacts/dataset_v1", help="Dataset output directory")
    parser.add_argument("--seed", type=int, default=2026, help="Deterministic seed tag")
    return parser


def _request_bucket(request_id: str) -> int:
    digest = hashlib.sha256(request_id.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % 10


def _split_name(request_id: str) -> str:
    bucket = _request_bucket(request_id)
    if bucket == 0:
        return "test"
    if bucket == 1:
        return "val"
    return "train"


def _split_name_row_fallback(request_id: str, recipe_id: str) -> str:
    digest = hashlib.sha256(f"{request_id}|{recipe_id}".encode("utf-8")).hexdigest()
    bucket = int(digest[:8], 16) % 10
    if bucket == 0:
        return "test"
    if bucket == 1:
        return "val"
    return "train"


def _time_window(values: List[int], value_key: str) -> Dict[str, Any]:
    cleaned = [int(value) for value in values if int(value) > 0]
    if not cleaned:
        return {
            "rowCount": 0,
            f"min{value_key}": None,
            f"max{value_key}": None,
        }
    return {
        "rowCount": len(cleaned),
        f"min{value_key}": min(cleaned),
        f"max{value_key}": max(cleaned),
    }


def _load_rows(db_path: Path) -> Tuple[List[Dict], Dict[str, Any]]:
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT request_id, uid_hash, recipe_id, meal_bucket, generated_at_ms, selected_by_solver,
                   model_score, heuristic_score, ranking_strategy, model_version, feature_json
            FROM ml_stage1_candidate_features
            WHERE selected_by_solver IS NOT NULL
            ORDER BY generated_at_ms ASC
            """
        )
        rows = cur.fetchall()
    finally:
        conn.close()
    out: List[Dict] = []
    for row in rows:
        feature_json = row["feature_json"] or "{}"
        try:
            features = json.loads(feature_json)
            if not isinstance(features, dict):
                features = {}
        except Exception:
            features = {}
        payload = {
            "request_id": row["request_id"],
            "uid_hash": row["uid_hash"],
            "recipe_id": row["recipe_id"],
            "meal_bucket": row["meal_bucket"] or "Universal",
            "generated_at_ms": int(row["generated_at_ms"] or 0),
            "selected_by_solver": int(row["selected_by_solver"] or 0),
            "model_score": float(row["model_score"] or 0.0),
            "heuristic_score": float(row["heuristic_score"] or 0.0),
            "ranking_strategy": row["ranking_strategy"] or "unknown",
            "model_version": row["model_version"] or "unknown",
            "features": features,
        }
        out.append(payload)
    telemetry_window = _time_window(
        [int(row.get("generated_at_ms") or 0) for row in out],
        "GeneratedAtMs",
    )
    return out, telemetry_window


def _table_exists(conn: sqlite3.Connection, table_name: str) -> bool:
    cur = conn.cursor()
    cur.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
        (str(table_name),),
    )
    return cur.fetchone() is not None


def _load_reason_features(db_path: Path) -> Tuple[Dict[str, Dict[str, float]], Dict[str, Any]]:
    conn = sqlite3.connect(str(db_path))
    conn.row_factory = sqlite3.Row
    try:
        if not _table_exists(conn, "ml_events"):
            return {}, {
                "rowCount": 0,
                "uniqueUsers": 0,
                "minEventTimeMs": None,
                "maxEventTimeMs": None,
            }
        cur = conn.cursor()
        cur.execute(
            """
            SELECT uid_hash, event_name, event_time_ms, payload_json
            FROM ml_events
            WHERE event_name IN ('why_replaced_submitted', 'why_skipped_submitted')
            """
        )
        rows = cur.fetchall()
    finally:
        conn.close()

    by_user = defaultdict(Counter)
    for row in rows:
        uid_hash = str(row["uid_hash"] or "")
        if not uid_hash:
            continue
        event_name = str(row["event_name"] or "")
        payload_json = row["payload_json"] or "{}"
        try:
            payload = json.loads(payload_json)
            if not isinstance(payload, dict):
                payload = {}
        except Exception:
            payload = {}
        normalized = normalize_reason_payload(event_name, payload)
        tags = [str(tag) for tag in (normalized.get("reason_tags") or []) if str(tag).strip()]
        if not tags:
            continue
        by_user[uid_hash]["reason_events_total"] += 1
        if event_name == "why_replaced_submitted":
            by_user[uid_hash]["replace_reason_events_total"] += 1
            prefix = "replace_reason_tag_hist_"
        else:
            by_user[uid_hash]["skip_reason_events_total"] += 1
            prefix = "skip_reason_tag_hist_"
        for tag in tags:
            by_user[uid_hash][f"{prefix}{tag}"] += 1

    out: Dict[str, Dict[str, float]] = {}
    for uid, counter in by_user.items():
        out[uid] = {str(k): float(v) for k, v in counter.items()}
    window = _time_window(
        [int(row["event_time_ms"] or 0) for row in rows],
        "EventTimeMs",
    )
    window["uniqueUsers"] = len(out)
    return out, window


def _flatten_rows(rows: List[Dict], reason_features: Dict[str, Dict[str, float]]) -> Tuple[List[Dict], List[str]]:
    feature_columns = sorted(
        {
            key
            for row in rows
            for key in (row.get("features") or {}).keys()
        }
    )
    for uid_features in reason_features.values():
        for key in uid_features.keys():
            if key not in feature_columns:
                feature_columns.append(key)
    feature_columns = sorted(set(feature_columns))

    flattened: List[Dict] = []
    for row in rows:
        out = {
            "request_id": row["request_id"],
            "uid_hash": row["uid_hash"],
            "recipe_id": row["recipe_id"],
            "meal_bucket": row["meal_bucket"],
            "generated_at_ms": row["generated_at_ms"],
            "selected_by_solver": row["selected_by_solver"],
            "model_score": row["model_score"],
            "heuristic_score": row["heuristic_score"],
            "ranking_strategy": row["ranking_strategy"],
            "model_version": row["model_version"],
        }
        features = row.get("features") or {}
        uid_extra = reason_features.get(str(row.get("uid_hash") or ""), {})
        for col in feature_columns:
            raw = features.get(col, uid_extra.get(col, 0.0))
            try:
                out[col] = float(raw)
            except Exception:
                out[col] = 0.0
        flattened.append(out)
    return flattened, feature_columns


def _write_csv(path: Path, rows: List[Dict], columns: List[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)


def _quality_report(rows: List[Dict]) -> Dict:
    positives = sum(1 for r in rows if int(r.get("selected_by_solver") or 0) == 1)
    negatives = len(rows) - positives
    unique_requests = len({str(r.get("request_id") or "") for r in rows})
    unique_users = len({str(r.get("uid_hash") or "") for r in rows})
    return {
        "rows": len(rows),
        "positives": positives,
        "negatives": negatives,
        "positive_rate": (float(positives) / float(len(rows))) if rows else 0.0,
        "unique_requests": unique_requests,
        "unique_users": unique_users,
    }


def main() -> int:
    args = build_parser().parse_args()
    db_path = Path(args.db_path)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    if not db_path.exists():
        raise FileNotFoundError(f"Missing DB file: {db_path}")

    source_rows, telemetry_window = _load_rows(db_path)
    if not source_rows:
        raise RuntimeError("No rows found in ml_stage1_candidate_features with selected_by_solver labels")

    reason_features, reason_feedback_window = _load_reason_features(db_path)
    rows, feature_columns = _flatten_rows(source_rows, reason_features)
    quality = _quality_report(rows)
    if quality["positives"] <= 0 or quality["negatives"] <= 0:
        raise RuntimeError("Dataset must contain both positive and negative labels")

    train_rows: List[Dict] = []
    val_rows: List[Dict] = []
    test_rows: List[Dict] = []
    for row in rows:
        split = _split_name(str(row["request_id"]))
        if split == "train":
            train_rows.append(row)
        elif split == "val":
            val_rows.append(row)
        else:
            test_rows.append(row)

    leakage_strategy = {
        "strategy": "hash(request_id) modulo 10 split",
        "unit": "request_id",
        "fallbackUsed": False,
    }
    if not train_rows or not val_rows or not test_rows:
        train_rows = []
        val_rows = []
        test_rows = []
        for row in rows:
            split = _split_name_row_fallback(str(row["request_id"]), str(row["recipe_id"]))
            if split == "train":
                train_rows.append(row)
            elif split == "val":
                val_rows.append(row)
            else:
                test_rows.append(row)
        leakage_strategy = {
            "strategy": "hash(request_id|recipe_id) modulo 10 split",
            "unit": "request_id|recipe_id",
            "fallbackUsed": True,
            "reason": "insufficient unique request_id groups for strict request-level split",
        }
    if not train_rows or not val_rows or not test_rows:
        raise RuntimeError("Split failed: train/val/test must all be non-empty even after fallback")

    base_columns = [
        "request_id",
        "uid_hash",
        "recipe_id",
        "meal_bucket",
        "generated_at_ms",
        "selected_by_solver",
        "model_score",
        "heuristic_score",
        "ranking_strategy",
        "model_version",
    ]
    all_columns = base_columns + feature_columns
    _write_csv(output_dir / "dataset_full.csv", rows, all_columns)
    _write_csv(output_dir / "train.csv", train_rows, all_columns)
    _write_csv(output_dir / "val.csv", val_rows, all_columns)
    _write_csv(output_dir / "test.csv", test_rows, all_columns)

    manifest = {
        "datasetVersion": f"ml-dataset-v1-{int(time.time())}",
        "seed": int(args.seed),
        "sourceDbPath": str(db_path),
        "generatedAtMs": int(time.time() * 1000),
        "quality": quality,
        "splitRows": {
            "train": len(train_rows),
            "val": len(val_rows),
            "test": len(test_rows),
        },
        "featureColumns": feature_columns,
        "leakagePrevention": leakage_strategy,
        "sourceTelemetryWindow": telemetry_window,
        "reasonFeedbackWindow": reason_feedback_window,
        "files": {
            "dataset_full": "dataset_full.csv",
            "train": "train.csv",
            "val": "val.csv",
            "test": "test.csv",
        },
    }
    (output_dir / "dataset_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (output_dir / "dataset_quality_report.json").write_text(json.dumps(quality, indent=2), encoding="utf-8")
    print(f"Wrote dataset artifacts to: {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
