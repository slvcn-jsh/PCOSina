#!/usr/bin/env python
"""Generate deterministic reason-feedback telemetry rows for pipeline validation."""

from __future__ import annotations

import argparse
import sys
import time
import uuid
from pathlib import Path
from typing import Dict, List

ROOT = Path(__file__).resolve().parents[2]
BACKEND_DIR = ROOT / "backend"
if str(BACKEND_DIR) not in sys.path:
    sys.path.insert(0, str(BACKEND_DIR))

import database  # type: ignore
from ml_events import build_event, validate_event  # type: ignore
from services.reason_normalizer import normalize_reason_payload  # type: ignore


REASON_POOL = [
    ("manual_swap", "Masyadong mahal for this week."),
    ("manual_swap", "Kulangan ingredients sa pantry."),
    ("manual_swap", "Matagal lutuin and busy schedule."),
    ("manual_swap", "Paulit-ulit na lasa this week."),
    ("unchecked_by_user", "Not hungry at that time."),
    ("unchecked_by_user", "Nakalimutan i-log after class."),
    ("unchecked_by_user", "Hindi pasok sa current diet restriction."),
]


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Generate reason-feedback telemetry for ML pipeline checks")
    p.add_argument("--db-path", default="pcosina.db", help="SQLite DB path")
    p.add_argument("--events", type=int, default=240, help="Number of reason events to emit")
    p.add_argument("--seed", type=int, default=2026, help="Deterministic seed")
    p.add_argument(
        "--request-prefix",
        default="mlreason_v1_",
        help="Prefix used for generated request ids",
    )
    return p


def _load_stage1_rows(limit: int = 20000, per_uid_cap: int = 50) -> List[Dict]:
    rows = database.get_stage1_candidate_features(limit=limit)
    by_uid_count: Dict[str, int] = {}
    out: List[Dict] = []
    for row in rows:
        uid_hash = str(row.get("uid_hash") or "")
        recipe_id = str(row.get("recipe_id") or "")
        request_id = str(row.get("request_id") or "")
        if not uid_hash or not recipe_id:
            continue
        current = int(by_uid_count.get(uid_hash, 0))
        if current >= max(1, int(per_uid_cap)):
            continue
        by_uid_count[uid_hash] = current + 1
        out.append(
            {
                "uid_hash": uid_hash,
                "recipe_id": recipe_id,
                "request_id": request_id,
            }
        )
    return out


def main() -> int:
    args = build_parser().parse_args()
    db_path = Path(args.db_path)
    if hasattr(database, "DB_NAME"):
        database.DB_NAME = str(db_path)
    database.init_db()
    source_rows = _load_stage1_rows()
    if not source_rows:
        raise RuntimeError("No stage1 candidate rows found. Generate stage1 telemetry first.")

    now_ms = int(time.time() * 1000)
    emitted = 0
    for idx in range(max(1, int(args.events))):
        base = source_rows[idx % len(source_rows)]
        event_name = "why_replaced_submitted" if idx % 2 == 0 else "why_skipped_submitted"
        reason_tag, reason_text = REASON_POOL[idx % len(REASON_POOL)]
        plan_id = f"plan_{idx % 40}"
        slot_index = idx % 21
        payload = {
            "plan_id": plan_id,
            "slot_index": slot_index,
            "recipe_id": base["recipe_id"],
            "reason_tag": reason_tag,
            "reason_text": reason_text,
            "day_index": slot_index // 3,
            "meal_index": slot_index % 3,
        }
        normalized_payload = normalize_reason_payload(event_name, payload)
        request_id = (
            f"{args.request_prefix}{base.get('request_id')}_{idx}_{uuid.uuid4().hex[:8]}"
        )
        event = build_event(
            event_name=event_name,
            uid=f"reason_seed_user_{idx % 30}",
            request_id=request_id,
            policy_version="policy-v1:reason-seed",
            payload=normalized_payload,
        )
        # Align generated reason events with existing stage1 training users by reusing hashed UID.
        event["uid_hash"] = base["uid_hash"]
        event["event_time_ms"] = now_ms + idx
        event["dedupe_key"] = f"{event_name}|{request_id}|{now_ms + idx}"

        valid, error = validate_event(event)
        if not valid:
            raise RuntimeError(f"Generated event failed validation: {error}")
        database.record_ml_event(event)
        emitted += 1

    print(
        {
            "status": "ok",
            "emitted": emitted,
            "seed": int(args.seed),
            "eventsRequested": int(args.events),
            "sourceRows": len(source_rows),
        }
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
