from typing import Any, Callable, Dict


class OperatorAccessService:
    def __init__(
        self,
        *,
        assert_operator_mfa: Callable[[Dict[str, Any]], None],
        build_admin_principal: Callable[..., Dict[str, Any]],
    ):
        self._assert_operator_mfa = assert_operator_mfa
        self._build_admin_principal = build_admin_principal

    def resolve_principal(self, decoded: Dict[str, Any], *, auth_type: str) -> Dict[str, Any]:
        self._assert_operator_mfa(decoded)
        return self._build_admin_principal(decoded, auth_type=auth_type)

    def build_mobile_access_status(
        self,
        decoded: Dict[str, Any],
        *,
        auth_type: str,
    ) -> Dict[str, Any]:
        # Mobile operator routing is a lightweight gate for Android-only internal
        # tools. Full admin sessions still use resolve_principal(), which keeps
        # the stricter MFA check for privileged web/admin consoles.
        principal = self._build_admin_principal(decoded, auth_type=auth_type)
        return {"allowed": True, **principal}
