from __future__ import annotations

import hashlib
import json
import os
import time
import uuid
from typing import Any, Dict, Iterable, Tuple

ML_EVENT_SCHEMA_VERSION = "1.1.0"

COMMON_REQUIRED_FIELDS = (
    "event_id",
    "event_name",
    "event_time_ms",
    "uid_hash",
    "request_id",
    "policy_version",
)

EVENT_DEFINITIONS: Dict[str, Dict[str, Any]] = {
    "plan_generation_requested": {
        "description": "User requested a new plan generation run.",
        "required_fields": ("days", "meals_per_day"),
    },
    "stage1_candidates_scored": {
        "description": "Stage 1 candidate filtering/ranking summary.",
        "required_fields": (
            "candidate_count_pre",
            "candidate_count_post",
            "ranking_strategy",
            "ml_score_enabled",
            "ml_model_version",
        ),
    },
    "solver_completed": {
        "description": "Authoritative solver path completed.",
        "required_fields": ("status", "runtime_ms", "reason_codes"),
    },
    "async_solver_completed": {
        "description": "Async authoritative solver path completed.",
        "required_fields": ("status", "runtime_ms", "reason_codes"),
    },
    # User-facing product telemetry events required by ML roadmap.
    "plan_generated": {
        "description": "A plan was successfully generated and returned.",
        "required_fields": ("status", "plan_id", "slot_count"),
    },
    "plan_viewed": {
        "description": "User viewed a generated plan.",
        "required_fields": ("plan_id",),
    },
    "meal_accepted": {
        "description": "User accepted a meal suggestion.",
        "required_fields": ("plan_id", "slot_index", "recipe_id"),
    },
    "meal_replaced": {
        "description": "User replaced a planned meal.",
        "required_fields": ("plan_id", "slot_index", "old_recipe_id", "new_recipe_id"),
    },
    "meal_skipped": {
        "description": "User skipped a meal.",
        "required_fields": ("plan_id", "slot_index", "recipe_id"),
    },
    "recipe_opened": {
        "description": "User opened a recipe details page.",
        "required_fields": ("recipe_id",),
    },
    "grocery_completed": {
        "description": "User completed grocery checklist.",
        "required_fields": ("plan_id",),
    },
    "pantry_item_added": {
        "description": "User added pantry item.",
        "required_fields": ("item_token",),
    },
    "pantry_item_removed": {
        "description": "User removed pantry item.",
        "required_fields": ("item_token",),
    },
    "pantry_item_expired": {
        "description": "Pantry item marked expired.",
        "required_fields": ("item_token",),
    },
    "cook_completed": {
        "description": "User marked cooking as completed.",
        "required_fields": ("plan_id", "recipe_id"),
    },
    "no_safe_plan_encountered": {
        "description": "User got no-safe-plan result.",
        "required_fields": ("reason_codes",),
    },
    "manual_override_attempted": {
        "description": "User attempted override against hard-rule constraints.",
        "required_fields": ("override_type",),
    },
    "why_replaced_submitted": {
        "description": "User submitted replace reason text/tag.",
        "required_fields": ("plan_id", "slot_index"),
    },
    "why_skipped_submitted": {
        "description": "User submitted skip reason text/tag.",
        "required_fields": ("plan_id", "slot_index"),
    },
}


def uid_hash(uid: str | None) -> str:
    raw_uid = (uid or "").strip() or "anonymous"
    salt = os.getenv("PCOSINA_UID_HASH_SALT", "pcosina-default-salt")
    digest = hashlib.sha256(f"{salt}|{raw_uid}".encode("utf-8")).hexdigest()
    return digest


def _normalize_reason_codes(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, (list, tuple, set)):
        out = []
        for item in value:
            text = str(item).strip()
            if text:
                out.append(text)
        return out
    text = str(value).strip()
    return [text] if text else []


def build_event(
    event_name: str,
    *,
    uid: str | None,
    request_id: str | None,
    policy_version: str | None,
    payload: Dict[str, Any] | None = None,
) -> Dict[str, Any]:
    now_ms = int(time.time() * 1000)
    base = {
        "event_id": uuid.uuid4().hex,
        "event_name": str(event_name or "").strip(),
        "event_time_ms": now_ms,
        "uid_hash": uid_hash(uid),
        "request_id": str(request_id or "").strip() or "none",
        "policy_version": str(policy_version or "unknown"),
        "event_schema_version": ML_EVENT_SCHEMA_VERSION,
    }
    body = dict(payload or {})
    if "reason_codes" in body:
        body["reason_codes"] = _normalize_reason_codes(body.get("reason_codes"))
    base.update(body)
    return base


def validate_event(event: Dict[str, Any]) -> Tuple[bool, str]:
    if not isinstance(event, dict):
        return False, "event must be an object"
    for field in COMMON_REQUIRED_FIELDS:
        if field not in event:
            return False, f"missing common required field: {field}"
    event_name = str(event.get("event_name") or "").strip()
    if event_name not in EVENT_DEFINITIONS:
        return False, f"unknown event_name: {event_name}"
    required_fields: Iterable[str] = EVENT_DEFINITIONS[event_name]["required_fields"]
    for field in required_fields:
        if field not in event:
            return False, f"missing required field for {event_name}: {field}"
    try:
        ts = int(event.get("event_time_ms"))
        if ts <= 0:
            return False, "event_time_ms must be positive"
    except Exception:
        return False, "event_time_ms must be integer"
    if not str(event.get("uid_hash") or "").strip():
        return False, "uid_hash must be non-empty"
    if not str(event.get("request_id") or "").strip():
        return False, "request_id must be non-empty"
    return True, ""


def serialize_event_payload(event: Dict[str, Any]) -> str:
    return json.dumps(event, sort_keys=True, ensure_ascii=True)

