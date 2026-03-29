from __future__ import annotations

import time
from typing import Any, Dict, List, Tuple


def _as_ms(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except Exception:
        return int(default)


def _normalized_name_key(value: Any) -> str:
    raw = str(value or "").strip().lower()
    if not raw:
        return ""
    normalized_chars = [
        ch if ch.isalnum() else " "
        for ch in raw
    ]
    return " ".join("".join(normalized_chars).split())


def _item_key(item: Dict[str, Any], fallback_prefix: str) -> str:
    for key in ("id", "itemId", "ingredientId"):
        token = str(item.get(key) or "").strip()
        if token:
            return token.lower()
    for key in ("normalizedName", "name"):
        token = _normalized_name_key(item.get(key))
        if token:
            return token
    return f"{fallback_prefix}:{hash(str(sorted(item.items())))}"


def _lww_choice(local: Dict[str, Any], remote: Dict[str, Any]) -> Dict[str, Any]:
    l_ts = _as_ms(local.get("updatedAtMs", local.get("updated_at_ms", 0)))
    r_ts = _as_ms(remote.get("updatedAtMs", remote.get("updated_at_ms", 0)))
    return remote if r_ts >= l_ts else local


def _merge_lww_list(local_list: List[Dict[str, Any]], remote_list: List[Dict[str, Any]], key_prefix: str) -> List[Dict[str, Any]]:
    merged: Dict[str, Dict[str, Any]] = {}
    for item in local_list or []:
        if isinstance(item, dict):
            merged[_item_key(item, key_prefix)] = dict(item)
    for item in remote_list or []:
        if not isinstance(item, dict):
            continue
        k = _item_key(item, key_prefix)
        if k in merged:
            merged[k] = _lww_choice(merged[k], item)
        else:
            merged[k] = dict(item)
    return list(merged.values())


def merge_profile_fields(local_profile: Dict[str, Any], remote_profile: Dict[str, Any]) -> Dict[str, Any]:
    local_profile = dict(local_profile or {})
    remote_profile = dict(remote_profile or {})
    local_updated = _as_ms(local_profile.get("updatedAtMs", local_profile.get("updated_at_ms", 0)))
    remote_updated = _as_ms(remote_profile.get("updatedAtMs", remote_profile.get("updated_at_ms", 0)))
    local_field_ts = local_profile.get("fieldUpdatedAtMs") if isinstance(local_profile.get("fieldUpdatedAtMs"), dict) else {}
    remote_field_ts = remote_profile.get("fieldUpdatedAtMs") if isinstance(remote_profile.get("fieldUpdatedAtMs"), dict) else {}

    keys = set(local_profile.keys()) | set(remote_profile.keys())
    keys.discard("fieldUpdatedAtMs")
    keys.discard("updatedAtMs")
    keys.discard("updated_at_ms")
    merged: Dict[str, Any] = {}
    merged_field_ts: Dict[str, int] = {}
    for key in sorted(keys):
        l_exists = key in local_profile
        r_exists = key in remote_profile
        if not l_exists and r_exists:
            merged[key] = remote_profile.get(key)
            merged_field_ts[key] = _as_ms(remote_field_ts.get(key), remote_updated)
            continue
        if l_exists and not r_exists:
            merged[key] = local_profile.get(key)
            merged_field_ts[key] = _as_ms(local_field_ts.get(key), local_updated)
            continue
        l_ts = _as_ms(local_field_ts.get(key), local_updated)
        r_ts = _as_ms(remote_field_ts.get(key), remote_updated)
        if r_ts >= l_ts:
            merged[key] = remote_profile.get(key)
            merged_field_ts[key] = r_ts
        else:
            merged[key] = local_profile.get(key)
            merged_field_ts[key] = l_ts

    merged["fieldUpdatedAtMs"] = merged_field_ts
    ts_values = [local_updated, remote_updated, *merged_field_ts.values()]
    merged["updatedAtMs"] = max(ts_values) if ts_values else 0
    return merged


def resolve_uid_scoped_snapshot(
    *,
    uid: str,
    local_snapshot: Dict[str, Any],
    remote_snapshot: Dict[str, Any],
    conflict_resolution_policy: str = "last_write_wins",
) -> Tuple[Dict[str, Any], Dict[str, Any]]:
    uid_token = str(uid or "").strip()
    if not uid_token:
        raise ValueError("uid is required")
    local_uid = str(local_snapshot.get("uid") or uid_token)
    remote_uid = str(remote_snapshot.get("uid") or uid_token)
    if local_uid != uid_token or remote_uid != uid_token:
        raise ValueError("uid_scope_mismatch")
    if conflict_resolution_policy != "last_write_wins":
        raise ValueError("unsupported_conflict_policy")

    local_profile = local_snapshot.get("profile") if isinstance(local_snapshot.get("profile"), dict) else {}
    remote_profile = remote_snapshot.get("profile") if isinstance(remote_snapshot.get("profile"), dict) else {}
    merged_profile = merge_profile_fields(local_profile, remote_profile)

    merged_pantry = _merge_lww_list(
        local_snapshot.get("pantry") if isinstance(local_snapshot.get("pantry"), list) else [],
        remote_snapshot.get("pantry") if isinstance(remote_snapshot.get("pantry"), list) else [],
        "pantry",
    )
    merged_grocery = _merge_lww_list(
        local_snapshot.get("grocery") if isinstance(local_snapshot.get("grocery"), list) else [],
        remote_snapshot.get("grocery") if isinstance(remote_snapshot.get("grocery"), list) else [],
        "grocery",
    )
    merged_logs = _merge_lww_list(
        local_snapshot.get("logs") if isinstance(local_snapshot.get("logs"), list) else [],
        remote_snapshot.get("logs") if isinstance(remote_snapshot.get("logs"), list) else [],
        "log",
    )
    merged_plans = _merge_lww_list(
        local_snapshot.get("savedPlans") if isinstance(local_snapshot.get("savedPlans"), list) else [],
        remote_snapshot.get("savedPlans") if isinstance(remote_snapshot.get("savedPlans"), list) else [],
        "plan",
    )

    merged = {
        "uid": uid_token,
        "profile": merged_profile,
        "pantry": merged_pantry,
        "grocery": merged_grocery,
        "logs": merged_logs,
        "savedPlans": merged_plans,
        "recoveredAtMs": int(time.time() * 1000),
    }
    recovery_event = {
        "eventName": "recovery_complete",
        "uid": uid_token,
        "conflictResolutionPolicy": conflict_resolution_policy,
        "artifactCounts": {
            "pantry": len(merged_pantry),
            "grocery": len(merged_grocery),
            "logs": len(merged_logs),
            "savedPlans": len(merged_plans),
            "profileFields": len(merged_profile.get("fieldUpdatedAtMs") or {}),
        },
        "emittedAtMs": int(time.time() * 1000),
    }
    return merged, recovery_event
