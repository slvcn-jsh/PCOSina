import sys
from pathlib import Path
import uuid

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from fastapi.testclient import TestClient

import main


def test_admin_policy_endpoints_create_activate_and_rollback(monkeypatch):
    tmp_root = ROOT / "tests" / ".tmp_policy_api"
    tmp_root.mkdir(parents=True, exist_ok=True)
    test_db = tmp_root / f"policy_api_{uuid.uuid4().hex}.db"

    monkeypatch.setattr(main.policy_store, "DATABASE_URL", "")
    monkeypatch.setattr(main.policy_store, "DB_NAME", str(test_db))

    main.policy_store.init_policy_store()
    main.policy_store.ensure_default_policy(actor="test")
    main._load_runtime_policy(force_refresh=True)

    main.app.dependency_overrides[main.require_policy_admin] = lambda: {"actor": "policy-admin@example.com", "roles": ["policy_admin"]}
    monkeypatch.setattr(main, "_rate_limit_allowed", lambda ip, now=None: True)
    try:
        with TestClient(main.app) as client:
            active_resp = client.get("/admin/policy/active")
            assert active_resp.status_code == 200
            active_id = active_resp.json()["active"]["id"]

            create_payload = {
                "policy": {
                    "schema_version": "1.0.0",
                    "policy_name": "api-created",
                    "shortlist_limit": 85,
                    "shortlist_limit_restricted": 130,
                    "shortlist_keep_min": 20,
                    "shortlist_keep_ratio": 0.8,
                    "max_pool_size": 310,
                    "tolerance_levels": [0.2, 0.3, 0.4],
                    "max_per_week": [2, 3, 4, 10],
                    "solver_time_seconds": 6,
                    "solver_max_seconds": 12,
                    "total_solver_seconds": 25,
                    "solver_workers": 4,
                    "milp_weights": {
                        "repeat_weight": 5,
                        "group_weight": 2,
                        "diversity_weight": 1,
                        "pantry_weight": 1
                    },
                    "hard_rule_mode": "strict",
                    "allow_unsafe_overrides": False
                },
                "notes": "api-test",
                "activate": False
            }

            create_resp = client.post("/admin/policy/versions", json=create_payload)
            assert create_resp.status_code == 200
            created_id = create_resp.json()["created"]["id"]
            assert created_id != active_id

            activate_resp = client.post(
                "/admin/policy/activate",
                json={"policy_id": created_id, "notes": "activate-from-test"},
            )
            assert activate_resp.status_code == 200
            assert activate_resp.json()["active"]["id"] == created_id

            rollback_resp = client.post("/admin/policy/rollback", json={})
            assert rollback_resp.status_code == 200
            assert rollback_resp.json()["active"]["id"] != created_id
    finally:
        main.app.dependency_overrides = {}
