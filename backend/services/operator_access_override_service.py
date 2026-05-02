import time
from typing import Any, Dict

from fastapi import HTTPException

from domain.models import OperatorAccessOverrideRecord, OperatorAccessOverrideUpsertRequest


class OperatorAccessOverrideService:
    def __init__(self, *, database_module: Any):
        self._database = database_module

    def list_overrides(
        self,
        *,
        blocked_only: bool = False,
        limit: int = 100,
    ) -> Dict[str, Any]:
        items = self._database.list_operator_access_overrides(blocked_only=blocked_only, limit=limit)
        return {"status": "ok", "items": items, "count": len(items), "generatedAtMs": int(time.time() * 1000)}

    def get_override(self, uid: str) -> OperatorAccessOverrideRecord:
        item = self._database.get_operator_access_override(uid)
        if not item:
            raise HTTPException(status_code=404, detail="Operator access override not found")
        return OperatorAccessOverrideRecord(**item)

    def upsert_override(
        self,
        uid: str,
        payload: OperatorAccessOverrideUpsertRequest,
        *,
        principal: Dict[str, Any],
    ) -> Dict[str, Any]:
        actor = str(principal.get("actor") or "ops-admin")
        item = self._database.upsert_operator_access_override(
            uid,
            email=payload.email,
            blocked=payload.blocked,
            reason=payload.reason,
            updated_by=actor,
        )
        revoked_items: list[Dict[str, Any]] = []
        if item.get("blocked") and payload.revokeActiveSessions:
            revoked_items = self._database.revoke_admin_sessions_for_uid(
                uid,
                revoked_by=actor,
                reason="operator_access_override",
            )
        revoked_count = sum(1 for entry in revoked_items if entry.get("revokedAt") is not None)
        self._database.log_admin_action(
            "operator_access.upsert",
            actor=actor,
            resource_type="operator_access",
            resource_id=str(uid),
            details={
                "blocked": bool(item.get("blocked")),
                "reason": str(item.get("reason") or "").strip() or None,
                "email": item.get("email"),
                "revokeActiveSessions": bool(payload.revokeActiveSessions),
                "revokedSessionCount": revoked_count,
            },
        )
        return {
            "status": "ok",
            "item": item,
            "revokedSessionCount": revoked_count,
            "generatedAtMs": int(time.time() * 1000),
        }
