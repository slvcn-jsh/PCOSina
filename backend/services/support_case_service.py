import time
from typing import Any, Callable, Dict

from fastapi import HTTPException

from domain.models import (
    AdminSupportCase,
    AdminSupportCaseCreateRequest,
    AdminSupportCaseNoteRequest,
    AdminSupportCaseUpdateRequest,
)


class SupportCaseService:
    def __init__(
        self,
        *,
        database_module: Any,
        get_firestore_profile_snapshot: Callable[[str], tuple[Dict[str, Any], Dict[str, Any]]],
    ):
        self._database = database_module
        self._get_firestore_profile_snapshot = get_firestore_profile_snapshot

    def create_case(
        self,
        payload: AdminSupportCaseCreateRequest,
        *,
        principal: Dict[str, Any],
    ) -> AdminSupportCase:
        related_job_id = str(payload.relatedJobId or "").strip() or None
        if related_job_id and not self._database.get_plan_job(related_job_id, any_owner=True):
            raise HTTPException(status_code=404, detail="Related plan job not found")
        actor = str(principal.get("actor") or "ops-admin")
        case = self._database.create_support_case(
            user_uid=payload.userUid,
            summary=payload.summary,
            actor=actor,
            related_job_id=related_job_id,
            priority=payload.priority,
            assignee=payload.assignee,
            escalated=payload.escalated,
            initial_note=payload.initialNote,
        )
        self._database.log_admin_action(
            "support_case.create",
            actor=actor,
            resource_type="support_case",
            resource_id=case.get("id"),
            details={
                "userUid": payload.userUid,
                "relatedJobId": related_job_id,
                "priority": payload.priority,
                "assignee": payload.assignee,
                "escalated": bool(payload.escalated),
            },
        )
        return AdminSupportCase(**case)

    def update_case(
        self,
        case_id: str,
        payload: AdminSupportCaseUpdateRequest,
        *,
        principal: Dict[str, Any],
    ) -> AdminSupportCase:
        actor = str(principal.get("actor") or "ops-admin")
        updated = self._database.update_support_case(
            case_id,
            actor=actor,
            status=payload.status,
            priority=payload.priority,
            assignee=payload.assignee,
            clear_assignee=bool(payload.clearAssignee),
            escalated=payload.escalated,
            summary=payload.summary,
        )
        if not updated:
            raise HTTPException(status_code=404, detail="Support case not found")
        self._database.log_admin_action(
            "support_case.update",
            actor=actor,
            resource_type="support_case",
            resource_id=case_id,
            details={k: v for k, v in payload.model_dump().items() if v is not None},
        )
        return AdminSupportCase(**updated)

    def add_note(
        self,
        case_id: str,
        payload: AdminSupportCaseNoteRequest,
        *,
        principal: Dict[str, Any],
    ) -> AdminSupportCase:
        actor = str(principal.get("actor") or "ops-admin")
        updated = self._database.append_support_case_note(
            case_id,
            actor=actor,
            message=payload.message,
        )
        if not updated:
            raise HTTPException(status_code=404, detail="Support case not found")
        self._database.log_admin_action(
            "support_case.note",
            actor=actor,
            resource_type="support_case",
            resource_id=case_id,
            details={"messageLength": len(str(payload.message or ""))},
        )
        return AdminSupportCase(**updated)

    def export_case(
        self,
        case_id: str,
        *,
        principal: Dict[str, Any],
        include_raw_profile: bool = False,
        job_limit: int = 10,
        audit_limit: int = 30,
    ) -> Dict[str, Any]:
        item = self._database.get_support_case(case_id)
        if not item:
            raise HTTPException(status_code=404, detail="Support case not found")

        user_uid = str(item.get("userUid") or "").strip()
        related_job_id = str(item.get("relatedJobId") or "").strip()
        raw_profile, profile_summary = self._get_firestore_profile_snapshot(user_uid)
        related_job = self._database.get_plan_job(related_job_id, any_owner=True) if related_job_id else None
        recent_jobs = self._database.list_plan_jobs(owner_uid=user_uid, limit=max(1, min(50, job_limit)))

        audit_candidates = []
        seen_audit_ids = set()
        for resource_id in [case_id, user_uid, related_job_id]:
            token = str(resource_id or "").strip()
            if not token:
                continue
            for entry in self._database.list_admin_action_logs(limit=audit_limit, resource_id=token):
                entry_id = entry.get("id")
                if entry_id in seen_audit_ids:
                    continue
                seen_audit_ids.add(entry_id)
                audit_candidates.append(entry)
        audit_candidates.sort(key=lambda candidate: (int(candidate.get("created_at") or 0), int(candidate.get("id") or 0)), reverse=True)
        audit_trail = audit_candidates[: max(1, min(200, audit_limit))]

        self._database.log_admin_action(
            "support_case.export",
            actor=str(principal.get("actor") or "ops-admin"),
            resource_type="support_case",
            resource_id=case_id,
            details={
                "userUid": user_uid,
                "relatedJobId": related_job_id or None,
                "includeRawProfile": bool(include_raw_profile),
                "jobLimit": int(job_limit),
                "auditLimit": int(audit_limit),
            },
        )

        response = {
            "status": "ok",
            "case": item,
            "profileSummary": profile_summary,
            "relatedJob": related_job,
            "recentPlanJobs": recent_jobs,
            "auditTrail": audit_trail,
            "generatedAtMs": int(time.time() * 1000),
        }
        if include_raw_profile:
            response["profileDocument"] = raw_profile
        return response
