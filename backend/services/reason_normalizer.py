from __future__ import annotations

import hashlib
import re
from typing import Any, Dict, Iterable, List, Set

REASON_SCHEMA_VERSION = "1.0.0"

_REASON_EVENTS = {"why_replaced_submitted", "why_skipped_submitted"}
_FREE_TEXT_KEYS = ("reason_text", "free_text", "reason", "notes", "why")

_TAG_ALIASES = {
    "manual_swap": "user_preference",
    "manualreplace": "user_preference",
    "unchecked_by_user": "skipped_by_user",
    "uncheck_by_user": "skipped_by_user",
    "too_expensive": "cost_too_high",
    "budget": "cost_too_high",
    "out_of_budget": "cost_too_high",
    "no_ingredients": "ingredient_unavailable",
    "missing_ingredients": "ingredient_unavailable",
    "no_pantry": "ingredient_unavailable",
    "long_prep": "prep_time_too_long",
    "too_long_to_cook": "prep_time_too_long",
    "bored": "repeat_fatigue",
    "repeat": "repeat_fatigue",
}

_TAG_KEYWORDS = {
    "cost_too_high": (
        "expensive",
        "mahal",
        "budget",
        "cost",
        "price",
        "presyo",
        "tipid",
        "gastos",
    ),
    "ingredient_unavailable": (
        "walang",
        "missing",
        "ingredient",
        "ingredients",
        "pantry",
        "not available",
        "out of stock",
        "sold out",
    ),
    "prep_time_too_long": (
        "too long",
        "matagal",
        "tagal",
        "prep",
        "prepare",
        "cook time",
        "late",
    ),
    "taste_preference": (
        "ayaw",
        "dont like",
        "don't like",
        "hindi gusto",
        "hindi ko trip",
        "taste",
        "flavor",
        "lasang",
        "not tasty",
    ),
    "schedule_conflict": (
        "busy",
        "meeting",
        "class",
        "work",
        "schedule",
        "walang oras",
        "no time",
    ),
    "allergy_safety": (
        "allergy",
        "allergic",
        "allergen",
        "hika",
    ),
    "restriction_conflict": (
        "restriction",
        "bawal",
        "diet",
        "vegetarian",
        "pescatarian",
        "no pork",
        "no beef",
        "lactose",
    ),
    "repeat_fatigue": (
        "ulit",
        "repeat",
        "repetitive",
        "same meal",
        "sawa",
        "again",
    ),
    "not_hungry": (
        "not hungry",
        "busog",
        "full",
        "walang gana",
        "skip muna",
    ),
    "forgot_or_missed": (
        "forgot",
        "nakalimutan",
        "missed",
        "nakaligtaan",
    ),
    "cooking_skill_or_equipment": (
        "hirap lutuin",
        "hard to cook",
        "walang gamit",
        "no equipment",
        "oven",
        "blender",
    ),
    "portion_concern": (
        "portion",
        "konti",
        "sobra",
        "too much",
        "too little",
    ),
}

_PRIMARY_TAG_PRIORITY = [
    "allergy_safety",
    "restriction_conflict",
    "ingredient_unavailable",
    "cost_too_high",
    "prep_time_too_long",
    "schedule_conflict",
    "not_hungry",
    "repeat_fatigue",
    "taste_preference",
    "cooking_skill_or_equipment",
    "portion_concern",
    "forgot_or_missed",
    "user_preference",
    "skipped_by_user",
    "other",
]


def _normalize_tag(raw: str) -> str:
    text = str(raw or "").strip().lower()
    if not text:
        return ""
    text = re.sub(r"[^a-z0-9]+", "_", text).strip("_")
    if not text:
        return ""
    return _TAG_ALIASES.get(text, text)


def _collect_raw_tags(payload: Dict[str, Any]) -> List[str]:
    tags: List[str] = []
    for key in ("reason_tag", "reasonTag", "reason_tags", "reasonTags", "selected_tags"):
        value = payload.get(key)
        if value is None:
            continue
        if isinstance(value, (list, tuple, set)):
            tags.extend([str(v) for v in value if str(v).strip()])
        else:
            tags.append(str(value))
    return tags


def _collect_free_text(payload: Dict[str, Any]) -> str:
    for key in _FREE_TEXT_KEYS:
        value = payload.get(key)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _remove_free_text_keys(payload: Dict[str, Any]) -> None:
    for key in _FREE_TEXT_KEYS:
        payload.pop(key, None)


def _tags_from_text(text: str) -> Set[str]:
    lowered = text.lower()
    tags: Set[str] = set()
    for tag, keywords in _TAG_KEYWORDS.items():
        if any(keyword in lowered for keyword in keywords):
            tags.add(tag)
    return tags


def _select_primary_tag(tags: Iterable[str]) -> str:
    tag_set = {t for t in tags if t}
    if not tag_set:
        return "other"
    for candidate in _PRIMARY_TAG_PRIORITY:
        if candidate in tag_set:
            return candidate
    return sorted(tag_set)[0]


def normalize_reason_payload(event_name: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    normalized_payload = dict(payload or {})
    if event_name not in _REASON_EVENTS:
        return normalized_payload

    free_text = _collect_free_text(normalized_payload)
    raw_tags = _collect_raw_tags(normalized_payload)

    explicit_tags = {_normalize_tag(tag) for tag in raw_tags}
    explicit_tags = {tag for tag in explicit_tags if tag}
    text_tags = _tags_from_text(free_text) if free_text else set()

    merged_tags = set(explicit_tags) | set(text_tags)
    if not merged_tags:
        fallback = "user_preference" if event_name == "why_replaced_submitted" else "skipped_by_user"
        merged_tags.add(fallback)

    primary_tag = _select_primary_tag(merged_tags)

    reason_source = "none"
    if explicit_tags and text_tags:
        reason_source = "tag_and_text"
    elif explicit_tags:
        reason_source = "tag_only"
    elif text_tags:
        reason_source = "text_only"

    _remove_free_text_keys(normalized_payload)
    normalized_payload["reason_schema_version"] = REASON_SCHEMA_VERSION
    normalized_payload["reason_tags"] = sorted(merged_tags)
    normalized_payload["reason_primary_tag"] = primary_tag
    normalized_payload["reason_tag"] = primary_tag  # backward-compatible convenience field
    normalized_payload["reason_source"] = reason_source
    normalized_payload["reason_has_free_text"] = bool(free_text)
    normalized_payload["reason_text_length"] = len(free_text)
    if free_text:
        normalized_payload["reason_text_hash"] = hashlib.sha256(
            free_text.strip().lower().encode("utf-8")
        ).hexdigest()

    return normalized_payload

