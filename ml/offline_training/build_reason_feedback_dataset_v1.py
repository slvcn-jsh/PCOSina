#!/usr/bin/env python
"""Build reason-feedback artifacts from ml_events for retraining features."""

from __future__ import annotations

import argparse
import csv
import json
import sqlite3
import sys
import time
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Dict, Iterable, List

ROOT = Path(__file__).resolve().parents[2]
BACKEND_DIR = ROOT / "backend"
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

from services.reason_normalizer import normalize_reason_payload  # type: ignore


REASON_EVENTS = ("why_replaced_submitted", "why_skipped_submitted")


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Build reason-feedback artifacts from ml_events")
    p.add_argument("--db-path", default="pcosina.db", help="SQLite path")
    p.add_argument(
        "--output-dir",
        default="ml/offline_training/artifacts/reason_feedback_v1",
        help="Output directory for artifacts",
    )
    p.add_argument("--limit", type=int, default=100000, help="Max events to process")
    p.add_argument("--start-time-ms", type=int, default=0, help="Optional lower bound event_time_ms")
    return p


def _connect_sqlite(path: Path) -> sqlite3.Connection:
    conn = sqlite3.connect(str(path))
    conn.row_factory = sqlite3.Row
    return conn


def _load_reason_events(conn: sqlite3.Connection, limit: int, start_time_ms: int) -> List[Dict[str, Any]]:
    cur = conn.cursor()
    cur.execute(
        """
        SELECT event_name, uid_hash, request_id, plan_id, recipe_id, slot_index, event_time_ms, payload_json
        FROM ml_events
        WHERE event_name IN (?, ?)
          AND event_time_ms >= ?
        ORDER BY event_time_ms ASC
        LIMIT ?
        """,
        (REASON_EVENTS[0], REASON_EVENTS[1], int(start_time_ms or 0), int(limit or 100000)),
    )
    rows = cur.fetchall()
    out: List[Dict[str, Any]] = []
    for row in rows:
        payload_text = row["payload_json"] or "{}"
        try:
            payload = json.loads(payload_text)
            if not isinstance(payload, dict):
                payload = {}
        except Exception:
            payload = {}
        normalized = normalize_reason_payload(str(row["event_name"]), payload)
        out.append(
            {
                "event_name": str(row["event_name"]),
                "uid_hash": str(row["uid_hash"] or ""),
                "request_id": str(row["request_id"] or ""),
                "plan_id": str(row["plan_id"] or ""),
                "recipe_id": str(row["recipe_id"] or ""),
                "slot_index": int(row["slot_index"] or 0),
                "event_time_ms": int(row["event_time_ms"] or 0),
                "reason_primary_tag": str(normalized.get("reason_primary_tag") or "other"),
                "reason_tags": list(normalized.get("reason_tags") or []),
                "reason_has_free_text": bool(normalized.get("reason_has_free_text", False)),
                "reason_text_length": int(normalized.get("reason_text_length") or 0),
                "reason_source": str(normalized.get("reason_source") or "none"),
                "reason_schema_version": str(normalized.get("reason_schema_version") or ""),
            }
        )
    return out


def _write_csv(path: Path, rows: Iterable[Dict[str, Any]], columns: List[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=columns)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def _build_user_features(rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    by_user_tag = defaultdict(Counter)
    by_user_event = defaultdict(Counter)
    by_user_meta = defaultdict(lambda: {"events": 0, "free_text_events": 0, "last_event_time_ms": 0})

    all_tags: set[str] = set()
    for row in rows:
        uid = row["uid_hash"]
        event_name = row["event_name"]
        tags = row.get("reason_tags") or []
        by_user_meta[uid]["events"] += 1
        if bool(row.get("reason_has_free_text")):
            by_user_meta[uid]["free_text_events"] += 1
        by_user_meta[uid]["last_event_time_ms"] = max(
            int(by_user_meta[uid]["last_event_time_ms"] or 0),
            int(row.get("event_time_ms") or 0),
        )
        by_user_event[uid][event_name] += 1
        for tag in tags:
            all_tags.add(str(tag))
            by_user_tag[uid][str(tag)] += 1

    ordered_tags = sorted(t for t in all_tags if t)
    feature_rows: List[Dict[str, Any]] = []
    for uid in sorted(by_user_meta.keys()):
        meta = by_user_meta[uid]
        total_events = int(meta["events"] or 0)
        free_text_events = int(meta["free_text_events"] or 0)
        row: Dict[str, Any] = {
            "uid_hash": uid,
            "reason_events_total": total_events,
            "reason_events_replace": int(by_user_event[uid].get("why_replaced_submitted", 0)),
            "reason_events_skip": int(by_user_event[uid].get("why_skipped_submitted", 0)),
            "reason_free_text_events": free_text_events,
            "reason_free_text_rate": (float(free_text_events) / float(total_events)) if total_events > 0 else 0.0,
            "last_reason_event_time_ms": int(meta["last_event_time_ms"] or 0),
        }
        for tag in ordered_tags:
            row[f"reason_tag_hist_{tag}"] = int(by_user_tag[uid].get(tag, 0))
        feature_rows.append(row)
    return feature_rows


def main() -> int:
    args = build_parser().parse_args()
    db_path = Path(args.db_path)
    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    if not db_path.exists():
        raise FileNotFoundError(f"Missing DB file: {db_path}")

    conn = _connect_sqlite(db_path)
    try:
        rows = _load_reason_events(conn, limit=int(args.limit), start_time_ms=int(args.start_time_ms))
    finally:
        conn.close()

    rows_for_csv: List[Dict[str, Any]] = []
    tag_counter = Counter()
    primary_counter = Counter()
    event_counter = Counter()
    source_counter = Counter()
    for row in rows:
        tags = list(row.get("reason_tags") or [])
        for tag in tags:
            tag_counter[str(tag)] += 1
        primary_counter[str(row.get("reason_primary_tag") or "other")] += 1
        event_counter[str(row.get("event_name") or "")] += 1
        source_counter[str(row.get("reason_source") or "none")] += 1
        csv_row = dict(row)
        csv_row["reason_tags"] = "|".join(tags)
        rows_for_csv.append(csv_row)

    event_columns = [
        "event_name",
        "uid_hash",
        "request_id",
        "plan_id",
        "recipe_id",
        "slot_index",
        "event_time_ms",
        "reason_primary_tag",
        "reason_tags",
        "reason_has_free_text",
        "reason_text_length",
        "reason_source",
        "reason_schema_version",
    ]
    _write_csv(out_dir / "reason_feedback_events.csv", rows_for_csv, event_columns)

    user_features = _build_user_features(rows)
    if user_features:
        user_feature_columns = sorted(
            {
                key
                for row in user_features
                for key in row.keys()
            }
        )
        _write_csv(out_dir / "reason_feedback_user_features.csv", user_features, user_feature_columns)
    else:
        _write_csv(
            out_dir / "reason_feedback_user_features.csv",
            [],
            ["uid_hash", "reason_events_total", "reason_events_replace", "reason_events_skip"],
        )

    summary = {
        "generatedAtMs": int(time.time() * 1000),
        "sourceDbPath": str(db_path),
        "rows": len(rows),
        "uniqueUsers": len({str(r.get("uid_hash") or "") for r in rows if str(r.get("uid_hash") or "")}),
        "uniqueRequests": len({str(r.get("request_id") or "") for r in rows if str(r.get("request_id") or "")}),
        "freeTextEvents": sum(1 for r in rows if bool(r.get("reason_has_free_text"))),
        "sourceEventWindow": {
            "minEventTimeMs": min((int(r.get("event_time_ms") or 0) for r in rows), default=None),
            "maxEventTimeMs": max((int(r.get("event_time_ms") or 0) for r in rows), default=None),
        },
        "eventCounts": dict(sorted(event_counter.items())),
        "sourceCounts": dict(sorted(source_counter.items())),
        "primaryTagCounts": dict(primary_counter.most_common()),
        "tagCounts": dict(tag_counter.most_common()),
        "files": {
            "events": "reason_feedback_events.csv",
            "user_features": "reason_feedback_user_features.csv",
        },
    }
    (out_dir / "reason_feedback_summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
