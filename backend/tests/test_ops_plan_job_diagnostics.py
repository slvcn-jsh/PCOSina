import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_ops_plan_job_diagnostics_endpoint(monkeypatch):
    monkeypatch.setattr(
        main.database,
        "get_plan_job_diagnostics",
        lambda: {"metrics": {"sync_requests_total": 12}, "updatedAtMsByKey": {"sync_requests_total": 1}},
    )

    def _count(status: str) -> int:
        return {"queued": 2, "running": 1, "done": 9, "error": 1, "dead-letter": 0}.get(status, 0)

    monkeypatch.setattr(main.database, "count_plan_jobs_by_status", _count)
    main.app.dependency_overrides[main.require_admin_config_token] = lambda: {"actor": "test-admin"}

    with TestClient(main.app) as client:
        response = client.get("/ops/plan-jobs/diagnostics")

    main.app.dependency_overrides = {}
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["queueStatus"]["queued"] == 2
    assert body["diagnostics"]["metrics"]["sync_requests_total"] == 12
