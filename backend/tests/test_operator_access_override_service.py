import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi import HTTPException

from domain.models import OperatorAccessOverrideUpsertRequest
from services.operator_access_override_service import OperatorAccessOverrideService


class _FakeDatabase:
    def __init__(self):
        self.logged_actions = []
        self.override = {
            "uid": "blocked-1",
            "email": "blocked@example.com",
            "blocked": True,
            "reason": "manual_disable",
            "updatedBy": "ops@example.com",
            "createdAt": 1,
            "updatedAt": 1,
        }

    def list_operator_access_overrides(self, blocked_only=False, limit=100):
        items = [self.override]
        if blocked_only:
            items = [item for item in items if item["blocked"]]
        return items[:limit]

    def get_operator_access_override(self, uid):
        return self.override if uid == self.override["uid"] else None

    def upsert_operator_access_override(self, uid, email=None, blocked=True, reason=None, updated_by=None):
        self.override = {
            "uid": uid,
            "email": email,
            "blocked": blocked,
            "reason": reason,
            "updatedBy": updated_by,
            "createdAt": 1,
            "updatedAt": 2,
        }
        return self.override

    def revoke_admin_sessions_for_uid(self, uid, revoked_by=None, reason=None):
        return [
            {"id": "session-1", "uid": uid, "revokedAt": 123, "revokedBy": revoked_by, "revokeReason": reason}
        ]

    def log_admin_action(self, action, **kwargs):
        self.logged_actions.append((action, kwargs))


def test_operator_access_override_service_list_get_and_upsert():
    fake_db = _FakeDatabase()
    service = OperatorAccessOverrideService(database_module=fake_db)

    listed = service.list_overrides(blocked_only=True)
    assert listed["count"] == 1

    fetched = service.get_override("blocked-1")
    assert fetched.uid == "blocked-1"

    updated = service.upsert_override(
        "blocked-2",
        OperatorAccessOverrideUpsertRequest(
            email="blocked2@example.com",
            blocked=True,
            reason="offboarded",
            revokeActiveSessions=True,
        ),
        principal={"actor": "ops@example.com"},
    )
    assert updated["item"]["uid"] == "blocked-2"
    assert updated["revokedSessionCount"] == 1
    assert fake_db.logged_actions[-1][0] == "operator_access.upsert"


def test_operator_access_override_service_get_missing_raises_not_found():
    service = OperatorAccessOverrideService(database_module=_FakeDatabase())
    try:
        service.get_override("missing")
        assert False, "Expected HTTPException"
    except HTTPException as exc:
        assert exc.status_code == 404
        assert exc.detail == "Operator access override not found"
