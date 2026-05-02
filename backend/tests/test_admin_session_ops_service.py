import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi import HTTPException

from domain.models import (
    AdminSessionBulkRevokeRequest,
    AdminSessionCleanupRequest,
    AdminSessionRevokeRequest,
)
from services.admin_session_ops_service import AdminSessionOpsService


class _FakeDatabase:
    def __init__(self):
        self.logged_actions = []
        self.sessions = {
            "session-1": {
                "id": "session-1",
                "uid": "ops-1",
                "email": "ops@example.com",
                "actor": "ops@example.com",
                "roles": ["ops_admin"],
                "authType": "session",
                "createdAt": 1,
                "expiresAt": 2,
                "lastSeenAt": 1,
                "revokedAt": None,
                "revokedBy": None,
                "revokeReason": None,
            }
        }

    def list_admin_sessions(self, uid=None, active_only=True, limit=100):
        items = list(self.sessions.values())
        if uid:
            items = [item for item in items if item["uid"] == uid]
        if active_only:
            items = [item for item in items if item["revokedAt"] is None]
        return items[:limit]

    def revoke_admin_session(self, session_id, revoked_by=None, reason=None):
        item = self.sessions.get(session_id)
        if not item:
            return None
        updated = dict(item)
        updated["revokedAt"] = 123
        updated["revokedBy"] = revoked_by
        updated["revokeReason"] = reason
        self.sessions[session_id] = updated
        return updated

    def revoke_admin_sessions_for_uid(self, uid, revoked_by=None, reason=None, exclude_session_id=None):
        items = []
        for session_id, item in list(self.sessions.items()):
            if item["uid"] != uid:
                continue
            updated = dict(item)
            if session_id != exclude_session_id:
                updated["revokedAt"] = 456
                updated["revokedBy"] = revoked_by
                updated["revokeReason"] = reason
                self.sessions[session_id] = updated
            items.append(updated)
        return items

    def cleanup_admin_sessions(self, retention_days, include_revoked, include_expired):
        return {
            "deletedCount": 2,
            "retentionDays": retention_days,
            "includeRevoked": include_revoked,
            "includeExpired": include_expired,
        }

    def log_admin_action(self, action, **kwargs):
        self.logged_actions.append((action, kwargs))


def test_admin_session_ops_service_list_revoke_bulk_and_cleanup():
    fake_db = _FakeDatabase()
    service = AdminSessionOpsService(database_module=fake_db)
    principal = {"actor": "ops@example.com", "sessionId": "session-1"}

    listed = service.list_sessions(uid="ops-1")
    assert listed["count"] == 1

    revoked = service.revoke_session(
        "session-1",
        AdminSessionRevokeRequest(reason="manual_review"),
        principal=principal,
    )
    assert revoked.revokeReason == "manual_review"
    assert fake_db.logged_actions[-1][0] == "admin_session.revoke"

    bulk = service.revoke_sessions_for_uid(
        "ops-1",
        AdminSessionBulkRevokeRequest(reason="compromise", excludeCurrentSession=True),
        principal=principal,
    )
    assert bulk["uid"] == "ops-1"
    assert bulk["revokedCount"] == 1
    assert fake_db.logged_actions[-1][0] == "admin_session.revoke_user"

    cleanup = service.cleanup_sessions(
        AdminSessionCleanupRequest(retentionDays=30, includeRevoked=True, includeExpired=True),
        principal=principal,
    )
    assert cleanup["deletedCount"] == 2
    assert fake_db.logged_actions[-1][0] == "admin_session.cleanup"


def test_admin_session_ops_service_revoke_missing_session_raises_not_found():
    service = AdminSessionOpsService(database_module=_FakeDatabase())
    try:
        service.revoke_session(
            "missing-session",
            AdminSessionRevokeRequest(reason="manual_review"),
            principal={"actor": "ops@example.com"},
        )
        assert False, "Expected HTTPException"
    except HTTPException as exc:
        assert exc.status_code == 404
        assert exc.detail == "Admin session not found"
