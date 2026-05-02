import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi import HTTPException

from domain.models import (
    AdminSupportCaseCreateRequest,
    AdminSupportCaseNoteRequest,
    AdminSupportCaseUpdateRequest,
)
from services.support_case_service import SupportCaseService


class _FakeDatabase:
    def __init__(self):
        self.logged_actions = []
        self.case = {
            "id": "case-1",
            "userUid": "user-1",
            "relatedJobId": "job-1",
            "status": "open",
            "priority": "high",
            "assignee": "tier2@example.com",
            "escalated": False,
            "summary": "Investigate",
            "notes": [],
            "createdBy": "ops@example.com",
            "updatedBy": "ops@example.com",
            "createdAt": 1,
            "updatedAt": 1,
        }

    def get_plan_job(self, job_id, any_owner=False):
        return {"id": job_id} if job_id == "job-1" and any_owner else None

    def create_support_case(self, **kwargs):
        case = dict(self.case)
        case["userUid"] = kwargs["user_uid"]
        case["summary"] = kwargs["summary"]
        case["relatedJobId"] = kwargs["related_job_id"]
        case["priority"] = kwargs["priority"]
        case["assignee"] = kwargs["assignee"]
        case["escalated"] = kwargs["escalated"]
        initial_note = kwargs.get("initial_note")
        if initial_note:
            case["notes"] = [{"author": kwargs["actor"], "message": initial_note, "createdAtMs": 1}]
        self.case = case
        return case

    def update_support_case(self, case_id, **kwargs):
        if case_id != self.case["id"]:
            return None
        updated = dict(self.case)
        for key, value in {
            "status": kwargs.get("status"),
            "priority": kwargs.get("priority"),
            "assignee": None if kwargs.get("clear_assignee") else kwargs.get("assignee"),
            "escalated": kwargs.get("escalated"),
            "summary": kwargs.get("summary"),
        }.items():
            if value is not None:
                updated[key] = value
        self.case = updated
        return updated

    def append_support_case_note(self, case_id, *, actor, message):
        if case_id != self.case["id"]:
            return None
        updated = dict(self.case)
        notes = list(updated.get("notes") or [])
        notes.append({"author": actor, "message": message, "createdAtMs": 2})
        updated["notes"] = notes
        self.case = updated
        return updated

    def get_support_case(self, case_id):
        return self.case if case_id == self.case["id"] else None

    def list_plan_jobs(self, owner_uid=None, limit=10):
        return [{"id": "job-1", "owner_uid": owner_uid, "status": "dead-letter"}][:limit]

    def list_admin_action_logs(self, limit=30, resource_id=None):
        return [
            {"id": 2, "created_at": 200, "action": "support_case.create", "resource_id": resource_id},
            {"id": 1, "created_at": 100, "action": "user_profile.inspect", "resource_id": resource_id},
        ][:limit]

    def log_admin_action(self, action, **kwargs):
        self.logged_actions.append((action, kwargs))


def test_support_case_service_create_update_note_and_export():
    fake_db = _FakeDatabase()
    service = SupportCaseService(
        database_module=fake_db,
        get_firestore_profile_snapshot=lambda uid: (
            {"displayName": "Case User"},
            {"profile": {"displayName": "Case User"}, "uid": uid},
        ),
    )
    principal = {"actor": "ops@example.com"}

    created = service.create_case(
        AdminSupportCaseCreateRequest(
            userUid="user-1",
            relatedJobId="job-1",
            summary="Investigate repeated planner failures",
            priority="high",
            assignee="tier2@example.com",
            initialNote="First note",
        ),
        principal=principal,
    )
    assert created.relatedJobId == "job-1"
    assert fake_db.logged_actions[0][0] == "support_case.create"

    updated = service.update_case(
        "case-1",
        AdminSupportCaseUpdateRequest(status="investigating", escalated=True),
        principal=principal,
    )
    assert updated.status == "investigating"
    assert updated.escalated is True

    noted = service.add_note(
        "case-1",
        AdminSupportCaseNoteRequest(message="Need recent logs"),
        principal=principal,
    )
    assert noted.notes[-1].message == "Need recent logs"

    exported = service.export_case(
        "case-1",
        principal=principal,
        include_raw_profile=True,
        job_limit=5,
        audit_limit=5,
    )
    assert exported["case"]["id"] == "case-1"
    assert exported["profileDocument"]["displayName"] == "Case User"
    assert exported["recentPlanJobs"][0]["id"] == "job-1"
    assert fake_db.logged_actions[-1][0] == "support_case.export"


def test_support_case_service_rejects_missing_related_job():
    fake_db = _FakeDatabase()
    service = SupportCaseService(
        database_module=fake_db,
        get_firestore_profile_snapshot=lambda uid: ({}, {"uid": uid}),
    )

    try:
        service.create_case(
            AdminSupportCaseCreateRequest(
                userUid="user-1",
                relatedJobId="missing-job",
                summary="Should fail",
            ),
            principal={"actor": "ops@example.com"},
        )
        assert False, "Expected HTTPException"
    except HTTPException as exc:
        assert exc.status_code == 404
        assert exc.detail == "Related plan job not found"
