import time
from typing import Any, Dict

from fastapi import HTTPException

from domain.models import (
    AdminSessionBulkRevokeRequest,
    AdminSessionCleanupRequest,
    AdminSessionRecord,
    AdminSessionRevokeRequest,
)


class AdminSessionOpsService:
    def __init__(self, *, database_module: Any):
        self._database = database_module

    def list_sessions(
        self,
        *,
        uid: str | None = None,
        active_only: bool = True,
        limit: int = 100,
    ) -> Dict[str, Any]:
        items = self._database.list_admin_sessions(uid=uid, active_only=active_only, limit=limit)
        return {"status": "ok", "items": items, "count": len(items), "generatedAtMs": int(time.time() * 1000)}

    def revoke_session(
        self,
        session_id: str,
        payload: AdminSessionRevokeRequest,
        *,
        principal: Dict[str, Any],
    ) -> AdminSessionRecord:
        actor = str(principal.get("actor") or "ops-admin")
        revoked = self._database.revoke_admin_session(
            session_id,
            revoked_by=actor,
            reason=payload.reason,
        )
        if not revoked:
            raise HTTPException(status_code=404, detail="Admin session not found")
        self._database.log_admin_action(
            "admin_session.revoke",
            actor=actor,
            resource_type="admin_session",
            resource_id=session_id,
            details={"reason": str(payload.reason or "").strip() or "manual_revoke"},
        )
        return AdminSessionRecord(**revoked)

    def revoke_sessions_for_uid(
        self,
        uid: str,
        payload: AdminSessionBulkRevokeRequest,
        *,
        principal: Dict[str, Any],
    ) -> Dict[str, Any]:
        actor = str(principal.get("actor") or "ops-admin")
        current_session_id = str(principal.get("sessionId") or "").strip()
        revoked = self._database.revoke_admin_sessions_for_uid(
            uid,
            revoked_by=actor,
            reason=payload.reason,
            exclude_session_id=current_session_id if payload.excludeCurrentSession else None,
        )
        revoked_count = sum(1 for item in revoked if item.get("revokedAt") is not None)
        self._database.log_admin_action(
            "admin_session.revoke_user",
            actor=actor,
            resource_type="admin_session",
            resource_id=str(uid),
            details={
                "reason": str(payload.reason or "").strip() or "bulk_revoke",
                "excludeCurrentSession": bool(payload.excludeCurrentSession),
                "currentSessionId": current_session_id or None,
                "revokedCount": revoked_count,
            },
        )
        return {
            "status": "ok",
            "uid": str(uid),
            "count": len(revoked),
            "revokedCount": revoked_count,
            "items": revoked,
            "generatedAtMs": int(time.time() * 1000),
        }

    def cleanup_sessions(
        self,
        payload: AdminSessionCleanupRequest,
        *,
        principal: Dict[str, Any],
    ) -> Dict[str, Any]:
        result = self._database.cleanup_admin_sessions(
            retention_days=payload.retentionDays,
            include_revoked=payload.includeRevoked,
            include_expired=payload.includeExpired,
        )
        self._database.log_admin_action(
            "admin_session.cleanup",
            actor=str(principal.get("actor") or "ops-admin"),
            resource_type="admin_session",
            resource_id="cleanup",
            details=result,
        )
        return {"status": "ok", **result, "generatedAtMs": int(time.time() * 1000)}
