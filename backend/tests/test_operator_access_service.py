import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services.operator_access_service import OperatorAccessService


def test_resolve_principal_requires_mfa_before_building_principal():
    calls: list[str] = []

    def _assert_operator_mfa(decoded):
        calls.append(f"mfa:{decoded['uid']}")

    def _build_admin_principal(decoded, *, auth_type: str):
        calls.append(f"principal:{auth_type}")
        return {"uid": decoded["uid"], "roles": ["ops_admin"], "authType": auth_type}

    service = OperatorAccessService(
        assert_operator_mfa=_assert_operator_mfa,
        build_admin_principal=_build_admin_principal,
    )

    principal = service.resolve_principal({"uid": "ops-1"}, auth_type="bearer")

    assert principal["uid"] == "ops-1"
    assert principal["roles"] == ["ops_admin"]
    assert calls == ["mfa:ops-1", "principal:bearer"]


def test_build_mobile_access_status_wraps_principal_with_allowed_flag():
    service = OperatorAccessService(
        assert_operator_mfa=lambda decoded: None,
        build_admin_principal=lambda decoded, *, auth_type: {
            "uid": decoded["uid"],
            "roles": ["ops_admin"],
            "authType": auth_type,
        },
    )

    status = service.build_mobile_access_status({"uid": "ops-2"}, auth_type="bearer")

    assert status == {
        "allowed": True,
        "uid": "ops-2",
        "roles": ["ops_admin"],
        "authType": "bearer",
    }
