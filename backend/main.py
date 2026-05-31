from fastapi import FastAPI, HTTPException, Request, Depends, Header, Form, BackgroundTasks
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from starlette.responses import JSONResponse, HTMLResponse, RedirectResponse
from typing import Dict, Any, Optional
import base64
import datetime
import hashlib
import hmac
import html
import json
import os
import time
import traceback
import socket
import uuid
from contextlib import asynccontextmanager
from urllib.parse import urlencode
import database
import policy_store
import queue_broker
import firebase_admin
from db_url import is_postgres_database_url
from firebase_admin import credentials, auth, app_check
import sentry_sdk
from sentry_sdk.integrations.fastapi import FastApiIntegration
from services.meal_planner import build_swap_candidates, solve_meal_plan
from services.plan_response_builder import (
    build_no_safe_plan_response as shared_build_no_safe_plan_response,
    freshen_cached_plan_response as shared_freshen_cached_plan_response,
)
from services.operator_access_service import OperatorAccessService
from services.plan_job_ops_service import PlanJobOpsService
from services.reason_normalizer import normalize_reason_payload
from services.admin_session_ops_service import AdminSessionOpsService
from services.operator_access_override_service import OperatorAccessOverrideService
from services.support_case_service import SupportCaseService
from policy_config import (
    PolicyActivateRequest,
    PolicyCreateRequest,
    PolicyRollbackRequest,
    POLICY_SCHEMA_VERSION,
    default_policy,
    resolve_policy_for_environment,
)
from ml_events import build_event, validate_event, EVENT_DEFINITIONS, uid_hash
from schema_contract import SCHEMA_VERSION, load_schema_contract
from domain.models import (
    AdminNutritionCorrection,
    AdminNutritionCorrectionUpsertRequest,
    AdminPriceRule,
    AdminPriceRuleUpsertRequest,
    AdminRecipeUpsertRequest,
    AdminSessionBulkRevokeRequest,
    AdminSessionCleanupRequest,
    AdminSessionRecord,
    AdminSessionRevokeRequest,
    AdminSupportCase,
    AdminSupportCaseCreateRequest,
    AdminSupportCaseNoteRequest,
    AdminSupportCaseUpdateRequest,
    OperatorAccessStatus,
    OperatorAccessOverrideRecord,
    OperatorAccessOverrideUpsertRequest,
    RecipeDetail,
    RecipeSummary,
    GeneratePlanRequest,
    GeneratePlanResponse,
    SwapOptionsRequest,
    FeedbackRequest,
    MlClientEventRequest,
)
from price_catalog import invalidate_override_cache as invalidate_price_rule_cache

PLAN_CACHE_TTL_SECONDS = 600
PLAN_CACHE_MAX_SIZE = 200
_plan_cache = {}
_DEFAULT_POLICY_BOOT = default_policy().to_runtime_dict()
PLAN_CACHE_TTL_SECONDS = int(_DEFAULT_POLICY_BOOT.get("sync_offline", {}).get("local_cache_ttl", PLAN_CACHE_TTL_SECONDS))
PLAN_CACHE_MAX_SIZE = int(_DEFAULT_POLICY_BOOT.get("sync_offline", {}).get("local_cache_max_entries", PLAN_CACHE_MAX_SIZE))
IDEMPOTENCY_TTL_SECONDS = int(_DEFAULT_POLICY_BOOT.get("security", {}).get("token_ttl", 3600))
_idempotency_cache: Dict[str, tuple[float, Dict[str, Any]]] = {}
FEEDBACK_RATE_LIMIT_WINDOW_SECONDS = int(os.getenv("PCOSINA_FEEDBACK_RATE_LIMIT_WINDOW_SECONDS", "3600"))
FEEDBACK_RATE_LIMIT_MAX = int(os.getenv("PCOSINA_FEEDBACK_RATE_LIMIT_MAX", "20"))
FEEDBACK_RETENTION_DAYS = int(os.getenv("PCOSINA_FEEDBACK_RETENTION_DAYS", "365"))
POLICY_CACHE_TTL_SECONDS = int(os.getenv("PCOSINA_POLICY_CACHE_TTL_SECONDS", "30"))
_policy_cache: Dict[str, Any] = {"loaded_at": 0.0, "value": None}
_planner_circuit_state: Dict[str, Any] = {"opened_at": 0.0, "consecutive_failures": 0}
_CANARY_WEBHOOK_EVENT_LIMIT = int(os.getenv("PCOSINA_CANARY_WEBHOOK_EVENT_LIMIT", "200"))
_canary_webhook_events: list[Dict[str, Any]] = []
ADMIN_SESSION_COOKIE = "pcosina_admin_session"
ADMIN_SESSION_TTL_SECONDS = int(os.getenv("PCOSINA_ADMIN_SESSION_TTL_SECONDS", "28800"))
ADMIN_CSRF_TTL_SECONDS = int(os.getenv("PCOSINA_ADMIN_CSRF_TTL_SECONDS", "900"))
APP_CHECK_HEADER_NAME = os.getenv("PCOSINA_APP_CHECK_HEADER", "X-Firebase-AppCheck").strip() or "X-Firebase-AppCheck"
_APP_CHECK_MODE_LOGGED: set[str] = set()
SUPPORTED_SCHEMA_VERSIONS = {
    item.strip()
    for item in os.getenv("PCOSINA_SUPPORTED_SCHEMA_VERSIONS", f"1.2.0,{SCHEMA_VERSION}").split(",")
    if item.strip()
}

ENVIRONMENT = os.getenv("PCOSINA_ENV", "development").lower()
IS_PRODUCTION = ENVIRONMENT in ("prod", "production")
ASYNC_MODE = os.getenv("PCOSINA_ASYNC_MODE", "queued").strip().lower()
QUEUE_BROKER = queue_broker.build_broker_from_env()

sentry_dsn = os.getenv("SENTRY_DSN")
if sentry_dsn:
    sentry_sdk.init(
        dsn=sentry_dsn,
        integrations=[FastApiIntegration()],
        traces_sample_rate=float(os.getenv("SENTRY_TRACES_SAMPLE_RATE", "0.1")),
        environment=os.getenv("SENTRY_ENVIRONMENT", "production"),
        release=os.getenv("SENTRY_RELEASE"),
    )


def _firebase_credentials_configured() -> bool:
    credentials_json = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    credentials_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "backend/secrets/firebase-service-account.json")
    return bool(credentials_json) or os.path.exists(credentials_path)


def _app_check_enforced() -> bool:
    configured = os.getenv("PCOSINA_ENFORCE_APP_CHECK", "").strip().lower()
    if configured in ("1", "true", "yes", "on"):
        return True
    if configured in ("0", "false", "no", "off"):
        return False
    return IS_PRODUCTION


def _uid_hash_salt_configured_for_production() -> bool:
    salt = os.getenv("PCOSINA_UID_HASH_SALT", "").strip()
    return bool(salt) and salt != "pcosina-default-salt" and len(salt) >= 32


def _seed_nutrition_corrections_on_startup() -> bool:
    configured = os.getenv("PCOSINA_SEED_NUTRITION_CORRECTIONS", "").strip().lower()
    if configured:
        return configured in ("1", "true", "yes", "on")
    if os.getenv("PYTEST_CURRENT_TEST", "").strip():
        return False
    return True


def _seed_reviewed_price_rules_on_startup() -> bool:
    configured = os.getenv("PCOSINA_SEED_REVIEWED_PRICE_RULES", "").strip().lower()
    if configured:
        return configured in ("1", "true", "yes", "on")
    if os.getenv("PYTEST_CURRENT_TEST", "").strip():
        return False
    return True


def _log_app_check_mode(enforced: bool) -> None:
    mode = "enforced" if enforced else "skipped"
    if mode in _APP_CHECK_MODE_LOGGED:
        return
    _APP_CHECK_MODE_LOGGED.add(mode)
    if enforced:
        print("Firebase App Check verification is enforced for protected mobile routes.")
    else:
        print("Firebase App Check verification is skipped for protected mobile routes.")


def _schema_readiness_report() -> Dict[str, Any]:
    app_status = database.get_schema_migration_status()
    policy_status = policy_store.get_schema_migration_status()
    pending = list(app_status.get("pending") or []) + list(policy_status.get("pending") or [])
    return {
        "ok": len(pending) == 0,
        "application": app_status,
        "policy": policy_status,
        "pending": pending,
    }


def _release_metadata() -> Dict[str, Any]:
    git_commit = (
        os.getenv("RENDER_GIT_COMMIT")
        or os.getenv("GIT_COMMIT")
        or os.getenv("SOURCE_VERSION")
        or ""
    ).strip()
    metadata: Dict[str, Any] = {
        "gitCommit": git_commit or None,
        "gitCommitShort": git_commit[:7] if git_commit else None,
        "serviceId": os.getenv("RENDER_SERVICE_ID", "").strip() or None,
        "serviceName": os.getenv("RENDER_SERVICE_NAME", "").strip() or None,
        "sentryRelease": os.getenv("SENTRY_RELEASE", "").strip() or None,
    }
    return metadata


def _runtime_readiness_report(*, include_schema: bool = False) -> Dict[str, Any]:
    errors: list[str] = []
    warnings: list[str] = []
    queue_backend = str(getattr(QUEUE_BROKER, "backend", "db") or "db")
    allowed_hosts_raw = os.getenv("PCOSINA_ALLOWED_HOSTS", "").strip()
    db_pool_status = database.get_database_connection_pool_status(os.getenv("DATABASE_URL", ""))

    if IS_PRODUCTION:
        if not _app_check_enforced():
            errors.append("PCOSINA_ENFORCE_APP_CHECK must remain enabled in production")
        if not _operator_require_verified_email():
            errors.append("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL must remain enabled in production")
        if not _operator_require_mfa_for_admin_access():
            errors.append("PCOSINA_REQUIRE_OPERATOR_MFA must remain enabled in production")
        if not _operator_require_recent_auth_for_admin_session():
            errors.append("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH must remain enabled in production")
        if _admin_session_idle_timeout_seconds() <= 0:
            errors.append("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS must remain enabled in production")
        if _admin_max_active_sessions_per_uid() <= 0:
            errors.append("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID must remain enabled in production")
        if not os.getenv("PCOSINA_ADMIN_SESSION_SECRET", "").strip():
            errors.append("PCOSINA_ADMIN_SESSION_SECRET is required in production")
        if os.getenv("FIREBASE_AUTH_DISABLED", "").strip().lower() == "true":
            errors.append("FIREBASE_AUTH_DISABLED cannot be enabled in production")
        if not is_postgres_database_url(os.getenv("DATABASE_URL", "")):
            errors.append("Production requires a Postgres DATABASE_URL")
        elif not db_pool_status.get("enabled"):
            errors.append("PCOSINA_DB_POOL_ENABLED must remain enabled in production")
        elif not db_pool_status.get("driverAvailable"):
            errors.append("psycopg-pool is required for production Postgres connection pooling")
        if not _firebase_credentials_configured() and not firebase_admin._apps:
            errors.append("Firebase credentials are required in production")
        if not sentry_dsn:
            warnings.append("SENTRY_DSN is not configured")
        if allowed_hosts_raw.strip() == "*":
            errors.append("PCOSINA_ALLOWED_HOSTS cannot be wildcard in production")
        if ASYNC_MODE == "queued" and queue_backend == "memory":
            errors.append("PCOSINA_QUEUE_BACKEND=memory is not allowed in production queued mode")
        if queue_backend == "redis" and not os.getenv("PCOSINA_REDIS_URL", "").strip():
            errors.append("PCOSINA_REDIS_URL is required when PCOSINA_QUEUE_BACKEND=redis")
        if RATE_LIMIT_BACKEND == "memory":
            errors.append("PCOSINA_RATE_LIMIT_BACKEND=memory is not allowed in production")
        if RATE_LIMIT_BACKEND == "redis" and not os.getenv("PCOSINA_REDIS_URL", "").strip():
            errors.append("PCOSINA_REDIS_URL is required when PCOSINA_RATE_LIMIT_BACKEND=redis")
        if not _uid_hash_salt_configured_for_production():
            errors.append("PCOSINA_UID_HASH_SALT must be set to a non-default value of at least 32 characters in production")
    else:
        if not os.getenv("PCOSINA_ADMIN_SESSION_SECRET", "").strip():
            warnings.append("PCOSINA_ADMIN_SESSION_SECRET is using the development fallback secret")

    report = {
        "environment": ENVIRONMENT,
        "asyncMode": ASYNC_MODE,
        "queueBackend": queue_backend,
        "rateLimitBackend": RATE_LIMIT_BACKEND,
        "appCheckEnforced": _app_check_enforced(),
        "operatorMfaRequired": _operator_require_mfa_for_admin_access(),
        "release": _release_metadata(),
        "database": {
            "mode": db_pool_status.get("mode"),
            "pool": db_pool_status,
        },
        "errors": errors,
        "warnings": warnings,
    }
    if include_schema:
        try:
            schema_status = _schema_readiness_report()
        except Exception as exc:
            schema_status = {
                "ok": False,
                "application": None,
                "policy": None,
                "pending": [],
                "error": str(exc),
            }
            errors.append(f"Schema migration readiness check failed: {exc}")
        else:
            if not schema_status["ok"]:
                errors.append("Pending schema migrations detected")
        report["schemaMigrations"] = schema_status
        try:
            catalog_nutrition_status = database.get_recipe_catalog_nutrition_status()
        except Exception as exc:
            catalog_nutrition_status = {
                "ok": False,
                "errors": [str(exc)],
                "warnings": [],
            }
            errors.append(f"Recipe catalog nutrition readiness check failed: {exc}")
        else:
            if not catalog_nutrition_status.get("ok"):
                messages = list(catalog_nutrition_status.get("errors") or [])
                if IS_PRODUCTION:
                    errors.extend(messages or ["Recipe catalog nutrition readiness failed"])
                else:
                    warnings.extend(messages or ["Recipe catalog nutrition readiness has gaps"])
            warnings.extend(list(catalog_nutrition_status.get("warnings") or []))
        report["recipeCatalogNutrition"] = catalog_nutrition_status
    report["ok"] = len(errors) == 0
    return report


def _validate_runtime_readiness(*, include_schema: bool = False) -> None:
    report = _runtime_readiness_report(include_schema=include_schema)
    if not report["ok"]:
        raise RuntimeError("; ".join(report["errors"]))

def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        IP = s.getsockname()[0]
    except Exception:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

@asynccontextmanager
async def lifespan(app: FastAPI):
    print("\n" + "="*50)
    print(f"PCOSINA BRAIN IS STARTING...")
    print(f"LOCAL IP: {get_ip()}")
    print(f"URL FOR PHONE: http://{get_ip()}:8000")
    print("="*50 + "\n")
    _log_app_check_mode(_app_check_enforced())
    _validate_runtime_readiness()
    try:
        init_firebase()
    except Exception as e:
        if IS_PRODUCTION:
            raise
        print(f"WARNING: Firebase initialization failed: {e}")
        print("Backend will continue without Firebase Auth (Local Dev Mode)")
    
    database.init_db()
    database.seed_recipes()
    if _seed_reviewed_price_rules_on_startup():
        database.seed_reviewed_price_rules()
        invalidate_price_rule_cache()
    if _seed_nutrition_corrections_on_startup():
        database.seed_nutrition_corrections()
    policy_store.init_policy_store()
    policy_store.ensure_default_policy(actor="system-bootstrap")
    _validate_runtime_readiness(include_schema=True)
    yield

docs_flag = os.getenv("PCOSINA_ENABLE_DOCS")
if docs_flag is None:
    docs_enabled = not IS_PRODUCTION
else:
    docs_enabled = docs_flag.strip().lower() in ("1", "true", "yes", "on")

app = FastAPI(
    title="PCOSINA Optimization API",
    lifespan=lifespan,
    docs_url="/docs" if docs_enabled else None,
    redoc_url="/redoc" if docs_enabled else None,
    openapi_url="/openapi.json" if docs_enabled else None,
)

MAX_REQUEST_BYTES = int(os.getenv("MAX_REQUEST_BYTES", str(512 * 1024)))
RATE_LIMIT_WINDOW_SECONDS = int(os.getenv("PCOSINA_RATE_LIMIT_WINDOW_SECONDS", "60"))
RATE_LIMIT_MAX = int(os.getenv("PCOSINA_RATE_LIMIT_MAX", "60"))
RATE_LIMIT_BACKEND = os.getenv("PCOSINA_RATE_LIMIT_BACKEND", "memory").strip().lower()
RATE_LIMIT_REDIS_PREFIX = os.getenv("PCOSINA_RATE_LIMIT_REDIS_PREFIX", "pcosina:rate_limit").strip() or "pcosina:rate_limit"
_rate_limit: Dict[str, list] = {}


class _MemoryRateLimitStore:
    backend = "memory"

    def allow(self, key: str, *, window_seconds: int, max_requests: int, now: float | None = None) -> bool:
        current = float(now if now is not None else time.time())
        bucket = _rate_limit.get(key, [])
        bucket = [ts for ts in bucket if current - ts < window_seconds]
        if len(bucket) >= max_requests:
            _rate_limit[key] = bucket
            return False
        bucket.append(current)
        _rate_limit[key] = bucket
        return True

    def health(self) -> Dict[str, Any]:
        return {"backend": self.backend, "enabled": True}


class _RedisRateLimitStore:
    backend = "redis"

    def __init__(self, redis_url: str, key_prefix: str):
        self._redis_url = str(redis_url or "").strip()
        self._key_prefix = str(key_prefix or "pcosina:rate_limit").strip() or "pcosina:rate_limit"
        self._client: Any = None
        self._load_error: str = ""
        self._connect()

    def _connect(self) -> None:
        if not self._redis_url:
            self._load_error = "missing_redis_url"
            self._client = None
            return
        try:
            import redis  # type: ignore

            self._client = redis.Redis.from_url(self._redis_url, decode_responses=True)
            self._client.ping()
        except Exception as exc:
            self._client = None
            self._load_error = str(exc)

    def allow(self, key: str, *, window_seconds: int, max_requests: int, now: float | None = None) -> bool:
        if self._client is None:
            return _MemoryRateLimitStore().allow(key, window_seconds=window_seconds, max_requests=max_requests, now=now)
        ts = int(now if now is not None else time.time())
        bucket = ts // max(1, int(window_seconds))
        redis_key = f"{self._key_prefix}:{key}:{bucket}"
        try:
            count = int(self._client.incr(redis_key))
            if count == 1:
                self._client.expire(redis_key, max(1, int(window_seconds)) + 5)
            return count <= max_requests
        except Exception:
            return _MemoryRateLimitStore().allow(key, window_seconds=window_seconds, max_requests=max_requests, now=now)

    def health(self) -> Dict[str, Any]:
        if self._client is None:
            return {"backend": self.backend, "enabled": False, "error": self._load_error or "redis_unavailable"}
        try:
            ok = bool(self._client.ping())
        except Exception as exc:
            return {"backend": self.backend, "enabled": False, "error": str(exc)}
        return {"backend": self.backend, "enabled": ok, "prefix": self._key_prefix}


def _build_rate_limit_store():
    if RATE_LIMIT_BACKEND == "redis":
        return _RedisRateLimitStore(os.getenv("PCOSINA_REDIS_URL", ""), RATE_LIMIT_REDIS_PREFIX)
    return _MemoryRateLimitStore()


RATE_LIMIT_STORE = _build_rate_limit_store()


def _rate_limit_allowed(ip: str, now: float | None = None) -> bool:
    token = str(ip or "unknown")
    return RATE_LIMIT_STORE.allow(token, window_seconds=RATE_LIMIT_WINDOW_SECONDS, max_requests=RATE_LIMIT_MAX, now=now)


def _decode_unverified_bearer_uid(authorization: str | None) -> str | None:
    raw = str(authorization or "").strip()
    if not raw.startswith("Bearer "):
        return None
    token = raw.split(" ", 1)[1].strip()
    parts = token.split(".")
    if len(parts) < 2:
        return None
    payload_segment = parts[1].strip()
    if not payload_segment:
        return None
    padding = "=" * (-len(payload_segment) % 4)
    try:
        decoded = base64.urlsafe_b64decode(f"{payload_segment}{padding}".encode("utf-8"))
        payload = json.loads(decoded.decode("utf-8"))
    except Exception:
        return None
    for field in ("uid", "user_id", "sub"):
        value = str(payload.get(field) or "").strip()
        if value:
            return value
    return None


def _rate_limit_bucket_key(request: Request) -> str:
    route = request.url.path or "/"
    ip = request.client.host if request.client else "unknown"
    session_payload = _unsign_token_payload(request.cookies.get(ADMIN_SESSION_COOKIE))
    session_uid = str((session_payload or {}).get("uid") or "").strip()
    if session_uid:
        return f"{route}|uid:{session_uid}|ip:{ip}"
    bearer_uid = _decode_unverified_bearer_uid(request.headers.get("authorization"))
    if bearer_uid:
        return f"{route}|uid:{bearer_uid}|ip:{ip}"
    return f"{route}|ip:{ip}"


def _feedback_rate_limit_allowed(request: Request, now: float | None = None) -> bool:
    key = f"feedback|{_rate_limit_bucket_key(request)}"
    return RATE_LIMIT_STORE.allow(
        key,
        window_seconds=FEEDBACK_RATE_LIMIT_WINDOW_SECONDS,
        max_requests=FEEDBACK_RATE_LIMIT_MAX,
        now=now,
    )


@app.middleware("http")
async def limit_request_size(request: Request, call_next):
    content_length = request.headers.get("content-length")
    if content_length is not None:
        try:
            if int(content_length) > MAX_REQUEST_BYTES:
                return JSONResponse(
                    status_code=413,
                    content={"detail": "Request too large"},
                )
        except ValueError:
            return JSONResponse(
                status_code=400,
                content={"detail": "Invalid Content-Length"},
            )
    received = 0
    body_parts: list[bytes] = []
    async for chunk in request.stream():
        received += len(chunk or b"")
        if received > MAX_REQUEST_BYTES:
            return JSONResponse(
                status_code=413,
                content={"detail": "Request too large"},
            )
        if chunk:
            body_parts.append(chunk)

    body = b"".join(body_parts)
    request._body = body
    replayed = False

    async def receive_replay():
        nonlocal replayed
        if replayed:
            return {"type": "http.request", "body": b"", "more_body": False}
        replayed = True
        return {"type": "http.request", "body": body, "more_body": False}

    request._receive = receive_replay
    return await call_next(request)

@app.middleware("http")
async def rate_limit(request: Request, call_next):
    bucket_key = _rate_limit_bucket_key(request)
    if not _rate_limit_allowed(bucket_key):
        return JSONResponse(
            status_code=429,
            content={"detail": "Too many requests"},
        )
    return await call_next(request)

@app.middleware("http")
async def attach_schema_version(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-PCOSINA-Schema-Version"] = getattr(request.state, "response_schema_version", SCHEMA_VERSION)
    return response

allowed_hosts_raw = os.getenv("PCOSINA_ALLOWED_HOSTS", "").strip()
if allowed_hosts_raw:
    allowed_hosts = [host.strip() for host in allowed_hosts_raw.split(",") if host.strip()]
elif IS_PRODUCTION:
    allowed_hosts = ["pcosina-backend.onrender.com"]
else:
    allowed_hosts = ["*"]  # Allow all for local phone testing
app.add_middleware(TrustedHostMiddleware, allowed_hosts=allowed_hosts)

@app.get("/", response_class=HTMLResponse)
def root(request: Request):
    base = str(request.base_url).rstrip("/")
    return HTMLResponse(
        content=f"""
        <!doctype html>
        <html>
        <head><meta charset="utf-8" /><title>PCOSINA Backend</title></head>
        <body style="font-family: Arial, sans-serif; margin: 24px;">
          <h1>PCOSINA Backend</h1>
          <p>API is running. Useful endpoints:</p>
          <ul>
            <li><a href="{base}/health" target="_blank" rel="noopener noreferrer">{base}/health</a></li>
            <li><a href="{base}/docs" target="_blank" rel="noopener noreferrer">{base}/docs</a></li>
          </ul>
          <p>Admin console:</p>
          <p><a href="{base}/admin/login" target="_blank" rel="noopener noreferrer">{base}/admin/login</a></p>
          <p style="color:#666;">Authenticated operator session required.</p>
        </body>
        </html>
        """
    )

def init_firebase():
    if firebase_admin._apps:
        return
    if os.getenv("FIREBASE_AUTH_DISABLED", "").lower() == "true":
        if IS_PRODUCTION:
            raise RuntimeError("FIREBASE_AUTH_DISABLED is not allowed in production")
        return

    credentials_json = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
    credentials_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "backend/secrets/firebase-service-account.json")

    if credentials_json:
        try:
            cred = credentials.Certificate(json.loads(credentials_json))
        except Exception as e:
            raise RuntimeError("Invalid FIREBASE_SERVICE_ACCOUNT_JSON") from e
    elif os.path.exists(credentials_path):
        cred = credentials.Certificate(credentials_path)
    else:
        # Instead of crashing, we'll return a warning to the lifespan handler
        raise FileNotFoundError("Firebase serviceAccountKey.json missing")

    firebase_admin.initialize_app(cred)

def require_firebase_auth(authorization: str = Header(None)):
    # Local dev: If no firebase app is initialized, bypass auth
    if not firebase_admin._apps:
        if IS_PRODUCTION:
            raise HTTPException(status_code=503, detail="Auth service unavailable")
        return {"uid": "local-dev-user"}
        
    if os.getenv("FIREBASE_AUTH_DISABLED", "").lower() == "true":
        if IS_PRODUCTION:
            raise HTTPException(status_code=503, detail="Auth disabled in production")
        return {"uid": "auth-disabled-user"}
        
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization.split(" ", 1)[1].strip()
    try:
        decoded = auth.verify_id_token(token, check_revoked=True)
        return decoded
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")


def require_app_check(x_firebase_appcheck: str | None = Header(default=None, alias=APP_CHECK_HEADER_NAME)):
    enforced = _app_check_enforced()
    _log_app_check_mode(enforced)
    if not enforced:
        return None

    token = str(x_firebase_appcheck or "").strip()
    if not token:
        raise HTTPException(status_code=401, detail="Missing Firebase App Check token")
    if not firebase_admin._apps:
        raise HTTPException(status_code=503, detail="App Check unavailable")
    try:
        return app_check.verify_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid Firebase App Check token")


def _verify_firebase_id_token(token: str) -> Dict[str, Any]:
    raw = str(token or "").strip()
    if not raw:
        raise HTTPException(status_code=401, detail="Missing Firebase ID token")
    if not firebase_admin._apps:
        if IS_PRODUCTION:
            raise HTTPException(status_code=503, detail="Auth service unavailable")
        raise HTTPException(status_code=401, detail="Firebase auth unavailable in local dev mode")
    try:
        return auth.verify_id_token(raw, check_revoked=True)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired Firebase ID token")


def _verify_firebase_bearer_optional(authorization: str | None) -> Dict[str, Any] | None:
    if not authorization:
        return None
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    return _verify_firebase_id_token(authorization.split(" ", 1)[1].strip())


def _admin_email_set(env_name: str) -> set[str]:
    raw = os.getenv(env_name, "")
    return {item.strip().lower() for item in raw.split(",") if item.strip()}


def _admin_uid_set(env_name: str) -> set[str]:
    raw = os.getenv(env_name, "")
    return {item.strip() for item in raw.split(",") if item.strip()}


def _normalize_roles(raw: Any) -> set[str]:
    if raw is None:
        return set()
    if isinstance(raw, str):
        candidates = [part.strip().lower() for part in raw.split(",")]
    elif isinstance(raw, (list, tuple, set)):
        candidates = [str(part).strip().lower() for part in raw]
    else:
        candidates = [str(raw).strip().lower()]
    return {role for role in candidates if role}


def _env_flag(name: str, *, default: bool) -> bool:
    raw = os.getenv(name, "").strip().lower()
    if raw in ("1", "true", "yes", "on"):
        return True
    if raw in ("0", "false", "no", "off"):
        return False
    return bool(default)


def _operator_require_verified_email() -> bool:
    return _env_flag("PCOSINA_REQUIRE_VERIFIED_OPERATOR_EMAIL", default=IS_PRODUCTION)


def _operator_require_recent_auth_for_admin_session() -> bool:
    return _env_flag("PCOSINA_REQUIRE_RECENT_ADMIN_AUTH", default=IS_PRODUCTION)


def _operator_require_mfa_for_admin_access() -> bool:
    return _env_flag("PCOSINA_REQUIRE_OPERATOR_MFA", default=IS_PRODUCTION)


def _operator_max_auth_age_seconds() -> int:
    raw = str(os.getenv("PCOSINA_ADMIN_MAX_AUTH_AGE_SECONDS", "900") or "900").strip()
    try:
        return max(60, int(raw))
    except Exception:
        return 900


def _admin_session_idle_timeout_seconds() -> int:
    default_value = "1800" if IS_PRODUCTION else "0"
    raw = str(os.getenv("PCOSINA_ADMIN_SESSION_IDLE_TIMEOUT_SECONDS", default_value) or default_value).strip()
    try:
        return max(0, int(raw))
    except Exception:
        return 1800 if IS_PRODUCTION else 0


def _admin_max_active_sessions_per_uid() -> int:
    default_value = "3" if IS_PRODUCTION else "0"
    raw = str(os.getenv("PCOSINA_ADMIN_MAX_ACTIVE_SESSIONS_PER_UID", default_value) or default_value).strip()
    try:
        return max(0, int(raw))
    except Exception:
        return 3 if IS_PRODUCTION else 0


def _get_operator_access_override(uid: str | None, email: str | None) -> Dict[str, Any] | None:
    return database.find_blocking_operator_access(uid=uid, email=email)


def _assert_operator_access_allowed(uid: str | None, email: str | None) -> None:
    override = _get_operator_access_override(uid, email)
    if override:
        reason = str(override.get("reason") or "").strip()
        detail = "Operator access disabled by server-side override"
        if reason:
            detail = f"{detail}: {reason}"
        raise HTTPException(status_code=403, detail=detail)


def _as_bool(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, (int, float)):
        return bool(value)
    text = str(value or "").strip().lower()
    return text in ("1", "true", "yes", "on")


def _firebase_token_has_mfa(decoded: Dict[str, Any]) -> bool:
    if _as_bool(decoded.get("pcosina_mfa")) or _as_bool(decoded.get("mfa_verified")):
        return True
    firebase_claim = decoded.get("firebase")
    if isinstance(firebase_claim, dict):
        if str(firebase_claim.get("sign_in_second_factor") or "").strip():
            return True
        second_factor_id = firebase_claim.get("second_factor_identifier")
        if str(second_factor_id or "").strip():
            return True
    amr = decoded.get("amr")
    if isinstance(amr, (list, tuple, set)):
        normalized = {str(item or "").strip().lower() for item in amr if str(item or "").strip()}
        if normalized.intersection({"mfa", "otp", "totp", "sms", "phone"}):
            return True
    return False


def _assert_recent_admin_auth(decoded: Dict[str, Any]) -> None:
    if not _operator_require_recent_auth_for_admin_session():
        return
    try:
        auth_time = int(decoded.get("auth_time") or 0)
    except Exception:
        auth_time = 0
    if auth_time <= 0:
        raise HTTPException(status_code=403, detail="Recent Firebase authentication required for admin session")
    age_seconds = max(0, int(time.time()) - auth_time)
    if age_seconds > _operator_max_auth_age_seconds():
        raise HTTPException(status_code=403, detail="Admin session requires a more recent Firebase sign-in")


def _assert_operator_mfa(decoded: Dict[str, Any]) -> None:
    if not _operator_require_mfa_for_admin_access():
        return
    if not _firebase_token_has_mfa(decoded):
        raise HTTPException(status_code=403, detail="Privileged operator access requires MFA-verified Firebase authentication")


def _build_admin_principal(decoded: Dict[str, Any], *, auth_type: str) -> Dict[str, Any]:
    uid = str(decoded.get("uid") or decoded.get("user_id") or "").strip()
    email = str(decoded.get("email") or "").strip().lower()
    email_verified = _as_bool(decoded.get("email_verified"))
    _assert_operator_access_allowed(uid, email)
    roles = set()
    role_sources: dict[str, str] = {}
    roles.update(_normalize_roles(decoded.get("roles")))
    for role in _normalize_roles(decoded.get("roles")):
        role_sources.setdefault(role, "claims.roles")
    roles.update(_normalize_roles(decoded.get("pcosina_roles")))
    for role in _normalize_roles(decoded.get("pcosina_roles")):
        role_sources.setdefault(role, "claims.pcosina_roles")
    if (
        bool(decoded.get("admin"))
        or bool(decoded.get("pcosina_admin"))
        or uid in _admin_uid_set("PCOSINA_ADMIN_UIDS")
    ):
        roles.add("admin")
        role_sources.setdefault("admin", "claims_or_uid_allowlist")
    if email in _admin_email_set("PCOSINA_ADMIN_EMAILS"):
        roles.add("admin")
        role_sources["admin"] = "email_allowlist"
    if email in _admin_email_set("PCOSINA_POLICY_ADMIN_EMAILS"):
        roles.add("policy_admin")
        role_sources["policy_admin"] = "email_allowlist"
    if uid in _admin_uid_set("PCOSINA_POLICY_ADMIN_UIDS"):
        roles.add("policy_admin")
        role_sources.setdefault("policy_admin", "uid_allowlist")
    if email in _admin_email_set("PCOSINA_OPS_ADMIN_EMAILS"):
        roles.add("ops_admin")
        role_sources["ops_admin"] = "email_allowlist"
    if uid in _admin_uid_set("PCOSINA_OPS_ADMIN_UIDS"):
        roles.add("ops_admin")
        role_sources.setdefault("ops_admin", "uid_allowlist")
    if email in _admin_email_set("PCOSINA_FEEDBACK_ADMIN_EMAILS"):
        roles.add("feedback_admin")
        role_sources["feedback_admin"] = "email_allowlist"
    if uid in _admin_uid_set("PCOSINA_FEEDBACK_ADMIN_UIDS"):
        roles.add("feedback_admin")
        role_sources.setdefault("feedback_admin", "uid_allowlist")
    if email in _admin_email_set("PCOSINA_CONTENT_ADMIN_EMAILS"):
        roles.add("content_admin")
        role_sources["content_admin"] = "email_allowlist"
    if uid in _admin_uid_set("PCOSINA_CONTENT_ADMIN_UIDS"):
        roles.add("content_admin")
        role_sources.setdefault("content_admin", "uid_allowlist")
    if _operator_require_verified_email():
        email_granted_roles = [role for role, source in role_sources.items() if source == "email_allowlist"]
        if email_granted_roles and not email_verified:
            raise HTTPException(status_code=403, detail="Verified operator email required for allowlisted admin access")
    if "admin" in roles:
        roles.update({"policy_admin", "ops_admin", "feedback_admin", "content_admin"})
        for inherited in ("policy_admin", "ops_admin", "feedback_admin", "content_admin"):
            role_sources.setdefault(inherited, role_sources.get("admin", "admin_inheritance"))
    if not roles:
        raise HTTPException(status_code=403, detail="Admin role required")
    actor = email or uid or "unknown-admin"
    return {
        "uid": uid or "unknown-admin",
        "email": email or None,
        "emailVerified": bool(email_verified),
        "mfaVerified": _firebase_token_has_mfa(decoded),
        "roles": sorted(roles),
        "roleSources": role_sources,
        "actor": actor,
        "authType": auth_type,
    }


def _operator_access_service() -> OperatorAccessService:
    return OperatorAccessService(
        assert_operator_mfa=_assert_operator_mfa,
        build_admin_principal=_build_admin_principal,
    )


def _support_case_service() -> SupportCaseService:
    return SupportCaseService(
        database_module=database,
        get_firestore_profile_snapshot=_get_firestore_profile_snapshot,
    )


def _plan_job_ops_service() -> PlanJobOpsService:
    return PlanJobOpsService(
        database_module=database,
        queue_broker=QUEUE_BROKER,
        rate_limit_store=RATE_LIMIT_STORE,
        init_job=_init_job,
        dispatch_async_job=_dispatch_async_job,
        increment_plan_job_diag=_inc_plan_job_diag,
    )


def _admin_session_ops_service() -> AdminSessionOpsService:
    return AdminSessionOpsService(database_module=database)


def _operator_access_override_service() -> OperatorAccessOverrideService:
    return OperatorAccessOverrideService(database_module=database)


def _admin_session_secret() -> str:
    configured = os.getenv("PCOSINA_ADMIN_SESSION_SECRET", "").strip()
    if configured:
        return configured
    if not IS_PRODUCTION:
        return "pcosina-dev-admin-session-secret"
    return ""


def _firestore_client():
    if not firebase_admin._apps:
        raise HTTPException(status_code=503, detail="Firebase admin is not initialized")
    try:
        from firebase_admin import firestore  # type: ignore
    except Exception as exc:
        raise HTTPException(status_code=503, detail=f"Firestore admin unavailable: {exc}")
    return firestore.client()


def _parse_json_text(value: Any) -> Any:
    raw = str(value or "").strip()
    if not raw:
        return None
    try:
        return json.loads(raw)
    except Exception:
        return None


def _cloud_profile_summary(uid: str, data: Dict[str, Any]) -> Dict[str, Any]:
    profile_fields = [
        "displayName",
        "age",
        "weightKg",
        "heightCm",
        "activityLevel",
        "goal",
        "dietaryRestrictions",
        "allergies",
        "isProfileCompleted",
    ]
    profile_present = {key: data.get(key) for key in profile_fields if key in data}
    profile_core_count = sum(1 for value in profile_present.values() if value not in (None, "", [], {}))

    json_artifacts = {
        "pantryEntriesJson": _parse_json_text(data.get("pantryEntriesJson")),
        "lastPlanJson": _parse_json_text(data.get("lastPlanJson")),
        "planHistoryJson": _parse_json_text(data.get("planHistoryJson")),
        "groceryJson": _parse_json_text(data.get("groceryJson")),
        "grocerySourcesJson": _parse_json_text(data.get("grocerySourcesJson")),
        "grocerySnapshotsJson": _parse_json_text(data.get("grocerySnapshotsJson")),
        "dailyLogsJson": _parse_json_text(data.get("dailyLogsJson")),
        "feedbackQueueJson": _parse_json_text(data.get("feedbackQueueJson")),
        "notificationLogsJson": _parse_json_text(data.get("notificationLogsJson")),
        "notificationLastFiredJson": _parse_json_text(data.get("notificationLastFiredJson")),
    }

    def _count_artifact(value: Any) -> int | None:
        if isinstance(value, (list, tuple, set, dict)):
            return len(value)
        return None

    weekly_map = data.get("weeklyJournalMap") if isinstance(data.get("weeklyJournalMap"), dict) else {}
    return {
        "uid": uid,
        "profileUpdatedAtEpochMs": data.get("updatedAtEpochMs"),
        "artifactsUpdatedAtEpochMs": data.get("artifactsUpdatedAtEpochMs"),
        "profile": {
            "presentFieldCount": profile_core_count,
            "isProfileCompleted": bool(data.get("isProfileCompleted")),
            "displayName": data.get("displayName"),
            "goal": data.get("goal"),
            "activityLevel": data.get("activityLevel"),
        },
        "artifacts": {
            key: {
                "present": key in data and str(data.get(key) or "").strip() != "",
                "count": _count_artifact(value),
            }
            for key, value in json_artifacts.items()
        },
        "weeklyJournalCount": len(weekly_map),
    }


def _get_firestore_profile_snapshot(uid: str) -> tuple[Dict[str, Any], Dict[str, Any]]:
    token = str(uid or "").strip()
    if not token:
        raise HTTPException(status_code=400, detail="uid is required")
    client = _firestore_client()
    try:
        snapshot = client.collection("profiles").document(token).get()
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Firestore profile lookup failed: {exc}")
    if not getattr(snapshot, "exists", False):
        raise HTTPException(status_code=404, detail="Profile document not found")
    raw = snapshot.to_dict() or {}
    if not isinstance(raw, dict):
        raw = {"value": raw}
    summary = _cloud_profile_summary(token, raw)
    return raw, summary


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _b64url_decode(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode((data + padding).encode("ascii"))


def _sign_token_payload(payload: Dict[str, Any]) -> str:
    secret = _admin_session_secret()
    if not secret:
        raise HTTPException(status_code=503, detail="Admin session secret is not configured")
    body = _b64url_encode(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8"))
    signature = hmac.new(secret.encode("utf-8"), body.encode("utf-8"), hashlib.sha256).hexdigest()
    return f"{body}.{signature}"


def _unsign_token_payload(token: str) -> Dict[str, Any] | None:
    raw = str(token or "").strip()
    if not raw or "." not in raw:
        return None
    secret = _admin_session_secret()
    if not secret:
        return None
    body, signature = raw.rsplit(".", 1)
    expected = hmac.new(secret.encode("utf-8"), body.encode("utf-8"), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(signature, expected):
        return None
    try:
        decoded = json.loads(_b64url_decode(body).decode("utf-8"))
    except Exception:
        return None
    if not isinstance(decoded, dict):
        return None
    return decoded


def _issue_admin_session(principal: Dict[str, Any]) -> tuple[str, Dict[str, Any]]:
    now = int(time.time())
    session_id = uuid.uuid4().hex
    session_payload = {
        "uid": principal.get("uid"),
        "email": principal.get("email"),
        "roles": list(principal.get("roles") or []),
        "actor": principal.get("actor"),
        "sessionId": session_id,
        "nonce": uuid.uuid4().hex,
        "iat": now,
        "exp": now + max(300, ADMIN_SESSION_TTL_SECONDS),
    }
    token = _sign_token_payload(session_payload)
    database.register_admin_session(
        session_id=session_id,
        uid=str(session_payload.get("uid") or "unknown-admin"),
        email=str(session_payload.get("email") or "").strip() or None,
        actor=str(session_payload.get("actor") or session_payload.get("uid") or "unknown-admin"),
        roles=list(session_payload.get("roles") or []),
        auth_type="session",
        created_at=int(session_payload["iat"]) * 1000,
        expires_at=int(session_payload["exp"]) * 1000,
    )
    max_active_sessions = _admin_max_active_sessions_per_uid()
    if max_active_sessions > 0:
        pruned_sessions = database.enforce_admin_session_limit(
            str(session_payload.get("uid") or "unknown-admin"),
            max_active=max_active_sessions,
            keep_session_id=session_id,
            revoked_by=str(session_payload.get("actor") or session_payload.get("uid") or "unknown-admin"),
            reason="session_limit",
        )
        if pruned_sessions:
            database.log_admin_action(
                "admin_session.limit_revoke",
                actor=str(session_payload.get("actor") or session_payload.get("uid") or "unknown-admin"),
                resource_type="admin_session",
                resource_id=str(session_payload.get("uid") or "unknown-admin"),
                details={
                    "maxActiveSessionsPerUid": max_active_sessions,
                    "keptSessionId": session_id,
                    "revokedCount": len(pruned_sessions),
                    "revokedSessionIds": [str(item.get("id") or "") for item in pruned_sessions],
                    "reason": "session_limit",
                },
            )
    session_principal = {
        "uid": session_payload["uid"],
        "email": session_payload.get("email"),
        "roles": list(session_payload.get("roles") or []),
        "actor": session_payload.get("actor") or session_payload["uid"],
        "authType": "session",
        "sessionId": session_id,
        "nonce": session_payload["nonce"],
        "sessionExpiresAt": session_payload["exp"],
    }
    return token, session_principal


def _principal_from_session_token(token: str | None) -> Dict[str, Any] | None:
    payload = _unsign_token_payload(str(token or ""))
    if not payload:
        return None
    now = int(time.time())
    if int(payload.get("exp") or 0) < now:
        return None
    now_ms = int(now * 1000)
    session_id = str(payload.get("sessionId") or "").strip()
    if not session_id:
        return None
    session_record = database.get_admin_session(session_id, include_revoked=True)
    if not session_record:
        return None
    override = _get_operator_access_override(
        str(session_record.get("uid") or payload.get("uid") or "").strip(),
        str(session_record.get("email") or payload.get("email") or "").strip().lower(),
    )
    if override:
        database.revoke_admin_session(session_id, revoked_by="system", reason="operator_access_blocked")
        database.log_admin_action(
            "admin_session.blocked_override_revoke",
            actor="system",
            resource_type="admin_session",
            resource_id=session_id,
            details={
                "uid": str(session_record.get("uid") or payload.get("uid") or ""),
                "email": str(session_record.get("email") or payload.get("email") or "").strip().lower() or None,
                "overrideUid": str(override.get("uid") or ""),
                "reason": str(override.get("reason") or "").strip() or None,
            },
        )
        return None
    revoked_at = session_record.get("revokedAt")
    if revoked_at is not None:
        return None
    if int(session_record.get("expiresAt") or 0) < now_ms:
        return None
    idle_timeout_seconds = _admin_session_idle_timeout_seconds()
    last_seen_at = int(session_record.get("lastSeenAt") or 0)
    if idle_timeout_seconds > 0 and last_seen_at > 0 and last_seen_at < (now_ms - (idle_timeout_seconds * 1000)):
        database.revoke_admin_session(session_id, revoked_by="system", reason="idle_timeout")
        return None
    database.touch_admin_session(session_id, at_ms=now_ms)
    return {
        "uid": str(session_record.get("uid") or payload.get("uid") or "unknown-admin"),
        "email": session_record.get("email") or payload.get("email"),
        "roles": list(session_record.get("roles") or payload.get("roles") or []),
        "actor": str(session_record.get("actor") or payload.get("actor") or payload.get("email") or payload.get("uid") or "unknown-admin"),
        "authType": "session",
        "sessionId": session_id,
        "nonce": str(payload.get("nonce") or ""),
        "sessionExpiresAt": int(payload.get("exp") or int(session_record.get("expiresAt") or 0) // 1000 or now),
    }


def _set_admin_session_cookie(response, token: str) -> None:
    response.set_cookie(
        ADMIN_SESSION_COOKIE,
        token,
        httponly=True,
        secure=IS_PRODUCTION,
        samesite="lax",
        max_age=max(300, ADMIN_SESSION_TTL_SECONDS),
        path="/admin",
    )


def _clear_admin_session_cookie(response) -> None:
    response.delete_cookie(ADMIN_SESSION_COOKIE, path="/admin")


def _build_admin_csrf_token(principal: Dict[str, Any], purpose: str) -> str:
    nonce = str(principal.get("nonce") or "").strip()
    if not nonce:
        raise HTTPException(status_code=403, detail="Admin session required to save changes")
    now = int(time.time())
    payload = {
        "uid": principal.get("uid"),
        "nonce": nonce,
        "purpose": str(purpose or ""),
        "exp": now + max(60, ADMIN_CSRF_TTL_SECONDS),
    }
    return _sign_token_payload(payload)


def _verify_admin_csrf_token(principal: Dict[str, Any], token: str, purpose: str) -> None:
    payload = _unsign_token_payload(token)
    now = int(time.time())
    if not payload:
        raise HTTPException(status_code=403, detail="Invalid CSRF token")
    if int(payload.get("exp") or 0) < now:
        raise HTTPException(status_code=403, detail="Expired CSRF token")
    if str(payload.get("purpose") or "") != str(purpose or ""):
        raise HTTPException(status_code=403, detail="Invalid CSRF token")
    if str(payload.get("uid") or "") != str(principal.get("uid") or ""):
        raise HTTPException(status_code=403, detail="Invalid CSRF token")
    if str(payload.get("nonce") or "") != str(principal.get("nonce") or ""):
        raise HTTPException(status_code=403, detail="Invalid CSRF token")

def require_schema_version(request: Request, x_pcosina_schema_version: str | None = Header(default=None)):
    client_version = str(x_pcosina_schema_version or "").strip()
    if client_version and client_version not in SUPPORTED_SCHEMA_VERSIONS:
        raise HTTPException(
            status_code=409,
            detail=f"Schema version mismatch. Server={SCHEMA_VERSION}, Client={client_version}"
        )
    request.state.response_schema_version = client_version or SCHEMA_VERSION


def require_admin_config_token(
    request: Request,
    authorization: str | None = Header(default=None),
):
    session_principal = _principal_from_session_token(request.cookies.get(ADMIN_SESSION_COOKIE))
    if session_principal:
        return session_principal

    decoded = _verify_firebase_bearer_optional(authorization)
    if decoded is not None:
        return _operator_access_service().resolve_principal(decoded, auth_type="bearer")
    raise HTTPException(status_code=401, detail="Unauthorized")


def _require_admin_roles(*required_roles: str):
    allowed = {str(role).strip().lower() for role in required_roles if str(role).strip()}

    def dependency(principal: Dict[str, Any] = Depends(require_admin_config_token)):
        roles = {str(role).strip().lower() for role in (principal.get("roles") or []) if str(role).strip()}
        if not roles:
            roles = {"admin"}
        if allowed and roles.isdisjoint(allowed):
            raise HTTPException(status_code=403, detail="Insufficient admin role")
        enriched = dict(principal)
        enriched["roles"] = sorted(roles)
        return enriched

    return dependency


require_policy_admin = _require_admin_roles("admin", "policy_admin")
require_ops_admin = _require_admin_roles("admin", "ops_admin")
require_feedback_admin = _require_admin_roles("admin", "feedback_admin")
require_content_admin = _require_admin_roles("admin", "content_admin", "ops_admin")


def _expected_webhook_receiver_key() -> str:
    configured = os.getenv("PCOSINA_WEBHOOK_RECEIVER_KEY", "").strip()
    if configured:
        return configured
    # Safe local convenience only; production must set explicit key.
    if not IS_PRODUCTION:
        return "local-dev-webhook-key"
    return ""


def _validate_webhook_receiver_key(receiver_key: str) -> bool:
    expected = _expected_webhook_receiver_key()
    token = str(receiver_key or "").strip()
    return bool(expected) and token == expected


def _append_canary_webhook_event(entry: Dict[str, Any]) -> None:
    _canary_webhook_events.append(entry)
    overflow = len(_canary_webhook_events) - max(1, int(_CANARY_WEBHOOK_EVENT_LIMIT))
    if overflow > 0:
        del _canary_webhook_events[:overflow]


def _load_runtime_policy(force_refresh: bool = False) -> tuple[Dict[str, Any], str]:
    now = time.time()
    cached_value = _policy_cache.get("value")
    loaded_at = float(_policy_cache.get("loaded_at") or 0.0)
    if not force_refresh and cached_value and (now - loaded_at) <= POLICY_CACHE_TTL_SECONDS:
        return cached_value["policy"], cached_value["version"]

    active = policy_store.get_active_policy()
    if active and isinstance(active.get("policy"), dict):
        policy_payload = resolve_policy_for_environment(active["policy"], ENVIRONMENT)
        version = f"policy-v{active.get('version_number')}:{active.get('id')}"
    else:
        policy_payload = default_policy().to_runtime_dict(environment=ENVIRONMENT)
        version = f"default:{POLICY_SCHEMA_VERSION}"

    _policy_cache["loaded_at"] = now
    _policy_cache["value"] = {"policy": policy_payload, "version": version}
    return policy_payload, version


def _policy_value(policy: Dict[str, Any], path: str, default: Any) -> Any:
    current: Any = policy
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return default
        current = current.get(part)
    return default if current is None else current


def _reason_codes_from_message(msg: str) -> list[str]:
    text = (msg or "").lower()
    codes: list[str] = []
    if "no safe recipes found" in text:
        codes.append("NO_SAFE_CANDIDATES")
    if "conflicting restrictions" in text:
        codes.append("CONFLICTING_RESTRICTIONS")
    if "only mealsperday" in text:
        codes.append("UNSUPPORTED_MEAL_SLOTS")
    if "infeasible" in text:
        codes.append("MODEL_INFEASIBLE")
    if not codes:
        codes.append("UNKNOWN_INFEASIBILITY")
    return codes


def _guidance_from_profile(request: GeneratePlanRequest, reason_codes: list[str]) -> tuple[list[str], list[str]]:
    profile = request.profile
    guidance: list[str] = []
    relaxations: list[str] = []

    if "CONFLICTING_RESTRICTIONS" in reason_codes:
        guidance.append("Your current restriction combination conflicts. Remove one conflicting restriction and retry.")
    if len(profile.dietaryRestrictions or []) >= 3:
        guidance.append("Too many active restrictions can remove all candidates. Temporarily relax one non-safety preference.")
        relaxations.append("Reduce non-safety dietary preferences by one level.")
    if profile.weeklyBudgetPhp and profile.weeklyBudgetPhp < 900:
        guidance.append("Current weekly budget is very tight for 21 meals. Consider increasing budget slightly.")
        relaxations.append("Increase weekly budget by at least 10%.")
    if profile.maxCookingTimeMinutes and profile.maxCookingTimeMinutes < 20:
        guidance.append("Very strict cooking-time limits can prevent feasible planning.")
        relaxations.append("Increase max cooking time by 10-15 minutes.")
    if not guidance:
        guidance.append("No safe plan was found with the current hard constraints.")
        guidance.append("Update non-safety preferences and retry. Safety and allergy rules remain strict.")
        relaxations.append("Adjust variety preference or budget while preserving allergy and restriction safety.")
    return guidance, relaxations


def _build_no_safe_plan_response(
    request: GeneratePlanRequest,
    request_id: str,
    message: str,
    policy_version: str,
    started_ms: int,
    completed_ms: int,
    diagnostics_ref: str | None = None,
    telemetry: dict | None = None,
) -> GeneratePlanResponse:
    return shared_build_no_safe_plan_response(
        request=request,
        request_id=request_id,
        message=message,
        policy_version=policy_version,
        started_ms=started_ms,
        completed_ms=completed_ms,
        diagnostics_ref=diagnostics_ref,
        telemetry=telemetry,
    )


@app.get("/schema")
def schema_contract():
    return load_schema_contract()


@app.post("/ml/events")
def ingest_ml_event(
    event: MlClientEventRequest,
    user: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
    _: Any = Depends(require_schema_version),
):
    event_name = str(event.eventName or "").strip()
    if event_name not in EVENT_DEFINITIONS:
        raise HTTPException(status_code=400, detail=f"Unknown eventName: {event_name}")
    request_id = (
        str(event.requestId or "").strip()
        or str((event.payload or {}).get("request_id") or (event.payload or {}).get("requestId") or "none")
    )
    uid = str((user or {}).get("uid") or "anonymous")
    policy_payload, policy_version = _load_runtime_policy()
    payload = normalize_reason_payload(event_name, dict(event.payload or {}))
    _emit_ml_event(
        event_name=event_name,
        payload=payload,
        uid=uid,
        request_id=request_id,
        policy_version=policy_version,
    )
    return {"status": "accepted", "eventName": event_name, "requestId": request_id}


@app.get("/mobile/operator/access", response_model=OperatorAccessStatus)
def mobile_operator_access(
    user: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
):
    return _operator_access_service().build_mobile_access_status(user, auth_type="bearer")


def _env_first(*names: str) -> str:
    for name in names:
        value = os.getenv(name, "").strip()
        if value:
            return value
    return ""


def _firebase_web_sign_in_config() -> tuple[Dict[str, str], list[str]]:
    project_id = _env_first("PCOSINA_FIREBASE_WEB_PROJECT_ID", "FIREBASE_WEB_PROJECT_ID")
    auth_domain = _env_first("PCOSINA_FIREBASE_WEB_AUTH_DOMAIN", "FIREBASE_WEB_AUTH_DOMAIN")
    if not auth_domain and project_id:
        auth_domain = f"{project_id}.firebaseapp.com"
    config: Dict[str, str] = {
        "apiKey": _env_first("PCOSINA_FIREBASE_WEB_API_KEY", "FIREBASE_WEB_API_KEY"),
        "authDomain": auth_domain,
        "projectId": project_id,
        "appId": _env_first("PCOSINA_FIREBASE_WEB_APP_ID", "FIREBASE_WEB_APP_ID"),
    }
    optional_values = {
        "messagingSenderId": _env_first(
            "PCOSINA_FIREBASE_WEB_MESSAGING_SENDER_ID",
            "FIREBASE_WEB_MESSAGING_SENDER_ID",
        ),
        "measurementId": _env_first("PCOSINA_FIREBASE_WEB_MEASUREMENT_ID", "FIREBASE_WEB_MEASUREMENT_ID"),
    }
    for key, value in optional_values.items():
        if value:
            config[key] = value
    missing = [key for key in ("apiKey", "authDomain", "projectId", "appId") if not config.get(key)]
    return config, missing


def _admin_google_login_html(error_message: str | None = None) -> str:
    firebase_config, missing = _firebase_web_sign_in_config()
    firebase_config = {key: value for key, value in firebase_config.items() if value}
    firebase_config_json = json.dumps(firebase_config, sort_keys=True)
    configured_json = json.dumps(not missing)
    status_message = str(error_message or "Ready for Google sign-in.").strip()
    status_class = "admin-status error" if error_message else "admin-status"
    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>PCOSINA Admin Login</title>
          <style>__ADMIN_BASE_CSS__</style>
        </head>
        <body class="admin-login-body">
          <div class="admin-login-panel">
            <h1>PCOSINA Admin Login</h1>
            <p style="color:var(--admin-muted);line-height:1.55;">
              Continue with the Google account registered for PCOSINA admin access. After sign-in,
              you will only see the tools your account is allowed to use.
            </p>
            <button id="google-sign-in" type="button">Continue with Google</button>
            <div id="login-status" class="__STATUS_CLASS__" role="status">__STATUS_MESSAGE__</div>
            <p style="margin-top:14px;font-size:13px;color:var(--admin-muted);">
              Use the same admin email configured for the PCOSINA backend.
            </p>
            <form id="admin-session-form" method="post" action="/admin/session" style="display:none;">
              <input type="hidden" name="next_path" value="/admin/login">
              <input type="hidden" name="interactive" value="true">
              <input type="hidden" id="id_token" name="id_token" required>
            </form>
          </div>
          <script type="module">
            import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
            import {
              getAuth,
              GoogleAuthProvider,
              getRedirectResult,
              signInWithPopup,
              signInWithRedirect
            } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";

            const firebaseConfig = __FIREBASE_CONFIG_JSON__;
            const isConfigured = __CONFIGURED_JSON__;
            const button = document.getElementById("google-sign-in");
            const statusBox = document.getElementById("login-status");
            const form = document.getElementById("admin-session-form");
            const tokenInput = document.getElementById("id_token");

            function setStatus(message, isError = false) {
              statusBox.textContent = message;
              statusBox.classList.toggle("error", isError);
            }

            if (!isConfigured) {
              button.disabled = true;
              setStatus("Admin Google sign-in needs Firebase web setup before it can be used here.", true);
            } else {
              const app = initializeApp(firebaseConfig);
              const auth = getAuth(app);
              const provider = new GoogleAuthProvider();
              provider.setCustomParameters({ prompt: "select_account" });

              async function submitCredential(result) {
                if (!result || !result.user) {
                  return;
                }
                setStatus("Verifying Firebase session...");
                tokenInput.value = await result.user.getIdToken(true);
                form.submit();
              }

              getRedirectResult(auth)
                .then(submitCredential)
                .catch((error) => setStatus(error.message || "Google sign-in failed.", true));

              button.addEventListener("click", async () => {
                button.disabled = true;
                setStatus("Opening Google sign-in...");
                try {
                  await submitCredential(await signInWithPopup(auth, provider));
                } catch (error) {
                  const code = String(error && error.code || "");
                  if (code.includes("popup") || code.includes("cancelled")) {
                    setStatus("Popup was blocked. Redirecting to Google sign-in...");
                    await signInWithRedirect(auth, provider);
                    return;
                  }
                  button.disabled = false;
                  setStatus(error.message || "Google sign-in failed.", true);
                }
              });
            }
          </script>
        </body>
        </html>
    """.replace("__ADMIN_BASE_CSS__", _admin_base_css()).replace(
        "__STATUS_CLASS__",
        status_class,
    ).replace("__STATUS_MESSAGE__", html.escape(status_message, quote=True)).replace("__FIREBASE_CONFIG_JSON__", firebase_config_json).replace(
        "__CONFIGURED_JSON__",
        configured_json,
    )


@app.get("/admin")
def admin_entry():
    return RedirectResponse(url="/admin/login", status_code=307)


@app.get("/admin/login", response_class=HTMLResponse)
def admin_login_page(request: Request):
    principal = _principal_from_session_token(request.cookies.get(ADMIN_SESSION_COOKIE))
    if principal:
        role_list = sorted(principal.get("roles") or [])
        cards: list[tuple[str, str, str]] = []
        snapshot_cards: list[tuple[str, str, str, int]] = []
        if "feedback_admin" in role_list or "admin" in role_list:
            cards.append(("Read Feedback", "/admin/feedback", "See messages users sent from the mobile app."))
            snapshot_cards.append((
                "Feedback",
                "/admin/feedback",
                "Messages waiting in the feedback list.",
                _admin_safe_count(lambda: database.get_recent_feedback(500)),
            ))
        if "policy_admin" in role_list or "admin" in role_list:
            cards.append(("Planner Settings", "/admin/policy", "Review or change the planner rules used by the backend."))
            active_policy = policy_store.get_active_policy()
            snapshot_cards.append((
                "Planner Version",
                "/admin/policy",
                "Current saved planner setting version.",
                int((active_policy or {}).get("version_number") or 0),
            ))
        if "content_admin" in role_list or "admin" in role_list:
            cards.append(("Manage Meals", "/admin/content", "Edit recipes, prices, and nutrition corrections."))
            snapshot_cards.extend([
                (
                    "Recipes",
                    "/admin/content/recipes",
                    "Meals currently available to the planner.",
                    _admin_safe_count(lambda: database.list_admin_recipes(limit=500)),
                ),
                (
                    "Price Rules",
                    "/admin/content/price-rules",
                    "Saved ingredient cost rules.",
                    _admin_safe_count(lambda: database.list_admin_price_rules(limit=500)),
                ),
            ])
        if "ops_admin" in role_list or "admin" in role_list:
            cards.append(("Fix App Issues", "/admin/ops", "Handle support cases, sign-ins, access, and change history."))
            snapshot_cards.extend([
                (
                    "Open Issues",
                    "/admin/ops/support-cases",
                    "Recent support cases and app issues.",
                    _admin_safe_count(lambda: database.list_support_cases(limit=500)),
                ),
                (
                    "Change History",
                    "/admin/ops/audit-logs",
                    "Recent admin changes.",
                    _admin_safe_count(lambda: database.list_admin_action_logs(limit=500)),
                ),
            ])
        body_html = (
            "<section class='admin-card' style='margin-bottom:16px;'>"
            f"{_admin_section_header('What do you need to do?', 'Choose the task first. Each card opens the right tool for that job.')}"
            f"{_admin_workspace_cards_html(cards) if cards else '<p class=\"admin-copy\">No admin tools are available for this account.</p>'}"
            "</section>"
            + (
                "<section class='admin-card'>"
                f"{_admin_section_header('Quick Counts', 'A short summary of what is currently in the admin tools.')}"
                f"{_admin_metric_cards_html(snapshot_cards)}"
                "</section>"
                if snapshot_cards
                else ""
            )
        )
        return _admin_shell(
            "Admin Home",
            principal,
            body_html,
            current_console="",
            description="Choose a maintenance task for PCOSina.",
            max_width=1180,
        )
    return HTMLResponse(content=_admin_google_login_html())


@app.post("/admin/session")
def admin_create_session(
    id_token: str = Form(...),
    next_path: str = Form(default="/admin/feedback"),
    interactive: str | None = Form(default=None),
):
    try:
        decoded = _verify_firebase_id_token(id_token)
        _assert_recent_admin_auth(decoded)
        principal = _operator_access_service().resolve_principal(decoded, auth_type="bearer")
        token, _session_principal = _issue_admin_session(principal)
    except HTTPException as exc:
        if str(interactive or "").lower() == "true":
            message = str(exc.detail or "Admin sign-in failed.")
            return HTMLResponse(content=_admin_google_login_html(message), status_code=int(exc.status_code or 403))
        raise
    auth_time = int(decoded.get("auth_time") or 0) if str(decoded.get("auth_time") or "").strip() else 0
    auth_age_seconds = max(0, int(time.time()) - auth_time) if auth_time > 0 else None
    database.log_admin_action(
        "admin_session.create",
        actor=str(principal.get("actor") or "admin"),
        resource_type="admin_session",
        resource_id=str(principal.get("uid") or ""),
        details={
            "roles": list(principal.get("roles") or []),
            "emailVerified": bool(principal.get("emailVerified")),
            "mfaVerified": bool(principal.get("mfaVerified")),
            "authType": str(principal.get("authType") or "bearer"),
            "authAgeSeconds": auth_age_seconds,
        },
    )
    target = str(next_path or "/admin/feedback").strip()
    if not target.startswith("/admin"):
        target = "/admin/feedback"
    response = RedirectResponse(url=target, status_code=303)
    _set_admin_session_cookie(response, token)
    return response


@app.post("/admin/logout")
def admin_logout(request: Request):
    principal = _principal_from_session_token(request.cookies.get(ADMIN_SESSION_COOKIE))
    if principal:
        session_id = str(principal.get("sessionId") or "").strip()
        if session_id:
            database.revoke_admin_session(
                session_id,
                revoked_by=str(principal.get("actor") or "admin"),
                reason="logout",
            )
        database.log_admin_action(
            "admin_session.logout",
            actor=str(principal.get("actor") or "admin"),
            resource_type="admin_session",
            resource_id=session_id or str(principal.get("uid") or ""),
            details={"roles": list(principal.get("roles") or []), "authType": str(principal.get("authType") or "session")},
        )
    response = RedirectResponse(url="/admin/login", status_code=303)
    _clear_admin_session_cookie(response)
    return response


def _admin_html_attr(value: Any) -> str:
    return html.escape(str(value or ""), quote=True)


def _admin_text_lines(value: str | None) -> list[str]:
    return [line.strip() for line in str(value or "").splitlines() if line.strip()]


def _admin_csv_items(value: str | None) -> list[str]:
    return [item.strip() for item in str(value or "").split(",") if item.strip()]


def _admin_parse_optional_int(value: str | None) -> int | None:
    raw = str(value or "").strip()
    if not raw:
        return None
    return int(raw)


def _admin_parse_ingredients(value: str | None) -> list[dict[str, str]]:
    items: list[dict[str, str]] = []
    for line in _admin_text_lines(value):
        if "|" in line:
            name, quantity = line.split("|", 1)
        elif ":" in line:
            name, quantity = line.split(":", 1)
        else:
            name, quantity = line, ""
        name = str(name or "").strip()
        quantity = str(quantity or "").strip()
        if name:
            items.append({"name": name, "quantity": quantity})
    return items


def _admin_format_ingredients(items: list[dict[str, Any]] | None) -> str:
    lines: list[str] = []
    for item in items or []:
        name = str((item or {}).get("name") or "").strip()
        quantity = str((item or {}).get("quantity") or "").strip()
        if not name:
            continue
        lines.append(f"{name} | {quantity}" if quantity else name)
    return "\n".join(lines)


def _admin_format_lines(items: list[Any] | None) -> str:
    return "\n".join(str(item).strip() for item in (items or []) if str(item).strip())


def _admin_notice_html(status: str | None, error: str | None) -> str:
    if error:
        return (
            "<div style='margin:0 0 16px;padding:12px 14px;border-radius:10px;"
            "background:#fff1f0;border:1px solid #ffccc7;color:#a8071a;'>"
            f"{html.escape(str(error), quote=True)}</div>"
        )
    status_messages = {
        "recipe_saved": "Recipe saved.",
        "recipe_deleted": "Recipe deleted.",
        "price_rule_saved": "Price rule saved.",
        "price_rule_deleted": "Price rule deleted.",
        "nutrition_correction_saved": "Nutrition correction saved.",
        "nutrition_correction_deleted": "Nutrition correction deleted.",
        "policy_saved": "Policy version saved.",
        "policy_saved_and_activated": "Policy version saved and activated.",
        "policy_activated": "Policy version activated.",
        "policy_rolled_back": "Policy rollback completed.",
        "support_case_created": "Support case created.",
        "support_case_updated": "Support case updated.",
        "support_case_noted": "Support case note added.",
        "admin_session_revoked": "Admin session revoked.",
        "admin_sessions_cleaned": "Admin sessions cleanup completed.",
        "operator_access_saved": "Admin access saved.",
        "recipe_seeded": "Recipe seed import completed.",
    }
    message = status_messages.get(str(status or "").strip())
    if not message:
        return ""
    return (
        "<div style='margin:0 0 16px;padding:12px 14px;border-radius:10px;"
        "background:#f6ffed;border:1px solid #b7eb8f;color:#135200;'>"
        f"{html.escape(message, quote=True)}</div>"
    )


def _admin_redirect(path: str, **params: Any) -> RedirectResponse:
    filtered = {}
    for key, value in params.items():
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        filtered[key] = value
    query = urlencode(filtered)
    return RedirectResponse(url=f"{path}?{query}" if query else path, status_code=303)


def _admin_format_epoch_ms(value: Any) -> str:
    raw = int(value or 0)
    if raw <= 0:
        return "—"
    return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(raw / 1000.0))


def _admin_role_set(principal: Dict[str, Any]) -> set[str]:
    role_set = {str(role) for role in (principal.get("roles") or [])}
    if "admin" in role_set:
        role_set.update({"feedback_admin", "content_admin", "ops_admin", "policy_admin"})
    return role_set


def _admin_visible_consoles(principal: Dict[str, Any]) -> list[tuple[str, str, str, str]]:
    role_set = _admin_role_set(principal)
    console_items = [
        ("feedback", "Feedback", "/admin/feedback", "feedback_admin"),
        ("content", "Meals", "/admin/content", "content_admin"),
        ("ops", "Issues", "/admin/ops", "ops_admin"),
        ("policy", "Settings", "/admin/policy", "policy_admin"),
    ]
    return [item for item in console_items if item[3] in role_set]


def _admin_console_switcher_html(principal: Dict[str, Any], *, current: str) -> str:
    visible_items = _admin_visible_consoles(principal)
    if len(visible_items) <= 1:
        return ""
    pills = "".join(
        (
            "<a href='{href}' class='admin-console-link {active}'>{label}</a>"
        ).format(
            href=href,
            label=html.escape(label, quote=True),
            active="is-active" if key == current else "",
        )
        for key, label, href, _required_role in visible_items
    )
    return (
        "<nav class='admin-console-switcher' aria-label='Admin consoles'>"
        "<span class='admin-switcher-label'>Tools</span>"
        f"{pills}</nav>"
    )


def _admin_section_nav_html(items: list[tuple[str, str, str]], *, active: str) -> str:
    return "".join(
        (
            "<a href='{href}' class='admin-section-link {active}'>{label}</a>"
        ).format(
            href=href,
            label=html.escape(label, quote=True),
            active="is-active" if key == active else "",
        )
        for label, href, key in items
    )


def _admin_base_css() -> str:
    return """
      :root {
        --admin-bg: #f4f7f6;
        --admin-surface: #ffffff;
        --admin-surface-muted: #f8faf9;
        --admin-line: #d9e2df;
        --admin-text: #17211d;
        --admin-muted: #5d6b66;
        --admin-strong: #0f2f26;
        --admin-green: #237a5b;
        --admin-green-dark: #185b43;
        --admin-blue: #1f6feb;
        --admin-red: #b42318;
        --admin-red-bg: #fff1f0;
        --admin-ok-bg: #ecfdf3;
        --admin-shadow: 0 10px 28px rgba(15, 45, 35, 0.08);
      }
      * { box-sizing: border-box; }
      body {
        margin: 0;
        font-family: Arial, sans-serif;
        background: linear-gradient(180deg, #eef6f1 0, var(--admin-bg) 240px);
        color: var(--admin-text);
      }
      a { color: var(--admin-green); }
      .admin-shell {
        min-height: 100vh;
      }
      .admin-topbar {
        position: sticky;
        top: 0;
        z-index: 20;
        background: rgba(255, 255, 255, 0.96);
        border-bottom: 1px solid var(--admin-line);
        backdrop-filter: blur(10px);
      }
      .admin-topbar-inner {
        max-width: var(--admin-width, 1240px);
        margin: 0 auto;
        padding: 14px 20px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 16px;
      }
      .admin-brand {
        display: flex;
        flex-direction: column;
        gap: 2px;
        min-width: 180px;
      }
      .admin-brand a {
        color: var(--admin-strong);
        text-decoration: none;
        font-size: 17px;
        font-weight: 800;
      }
      .admin-brand a:before {
        content: "";
        display: inline-block;
        width: 9px;
        height: 9px;
        margin-right: 8px;
        border-radius: 999px;
        background: var(--admin-green);
        box-shadow: 0 0 0 4px #e9f6ef;
      }
      .admin-brand small {
        color: var(--admin-muted);
        font-size: 12px;
      }
      .admin-home-link {
        text-decoration: none;
        border: 1px solid var(--admin-line);
        background: var(--admin-surface);
        color: var(--admin-green-dark);
        border-radius: 8px;
        padding: 8px 12px;
        font-size: 13px;
        font-weight: 800;
        white-space: nowrap;
      }
      .admin-home-link:hover {
        border-color: var(--admin-green);
        background: var(--admin-surface-muted);
      }
      .admin-console-switcher,
      .admin-section-nav {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
        align-items: center;
      }
      .admin-switcher-label {
        color: var(--admin-muted);
        font-size: 12px;
        font-weight: 800;
        text-transform: uppercase;
        letter-spacing: 0;
      }
      .admin-console-link,
      .admin-section-link {
        text-decoration: none;
        border: 1px solid var(--admin-line);
        background: var(--admin-surface);
        color: var(--admin-muted);
        border-radius: 999px;
        padding: 8px 12px;
        font-size: 13px;
        font-weight: 700;
        white-space: nowrap;
      }
      .admin-console-link.is-active,
      .admin-section-link.is-active {
        border-color: var(--admin-green);
        background: #e9f6ef;
        color: var(--admin-green-dark);
      }
      .admin-user {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 10px;
        flex-wrap: wrap;
      }
      .admin-user-chip {
        border: 1px solid var(--admin-line);
        background: var(--admin-surface-muted);
        border-radius: 999px;
        padding: 8px 12px;
        color: var(--admin-muted);
        font-size: 13px;
      }
      .admin-main {
        max-width: var(--admin-width, 1240px);
        margin: 0 auto;
        padding: 22px 20px 42px;
      }
      .admin-hero {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 18px;
        margin-bottom: 18px;
      }
      .admin-hero h1 {
        margin: 0 0 6px;
        font-size: 30px;
        letter-spacing: 0;
      }
      .admin-hero p {
        margin: 0;
        color: var(--admin-muted);
        line-height: 1.5;
      }
      .admin-section-nav {
        margin: 0 0 18px;
      }
      .admin-card,
      section {
        background: var(--admin-surface) !important;
        border: 1px solid var(--admin-line) !important;
        border-radius: 8px !important;
        box-shadow: var(--admin-shadow);
      }
      .admin-card {
        padding: 18px;
      }
      .admin-card.is-quiet {
        background: var(--admin-surface-muted) !important;
        box-shadow: none;
      }
      .admin-page-grid {
        display: grid;
        grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
        gap: 18px;
        align-items: start;
      }
      .admin-page-grid.is-three {
        grid-template-columns: minmax(300px, 360px) minmax(300px, 390px) minmax(0, 1fr);
      }
      .admin-section-stack {
        display: grid;
        gap: 18px;
      }
      .admin-section-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 14px;
        flex-wrap: wrap;
        margin-bottom: 14px;
      }
      .admin-section-header h2 {
        margin: 0 0 5px;
        font-size: 18px;
      }
      .admin-section-header p,
      .admin-copy {
        margin: 0;
        color: var(--admin-muted);
        line-height: 1.5;
      }
      .admin-eyebrow {
        margin: 0 0 4px;
        color: var(--admin-green);
        font-size: 12px;
        font-weight: 800;
        text-transform: uppercase;
        letter-spacing: 0;
      }
      .admin-toolbar {
        display: flex;
        gap: 10px;
        align-items: center;
        flex-wrap: wrap;
        margin-bottom: 14px;
      }
      .admin-toolbar.is-split {
        justify-content: space-between;
      }
      .admin-toolbar input,
      .admin-toolbar select {
        width: auto;
        min-width: 160px;
        flex: 1 1 170px;
      }
      .admin-actions {
        display: flex;
        gap: 8px;
        align-items: center;
        flex-wrap: wrap;
      }
      .admin-field-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 12px;
      }
      .admin-field {
        display: grid;
        gap: 6px;
        margin-bottom: 12px;
        color: var(--admin-strong);
        font-weight: 700;
      }
      .admin-field span {
        font-size: 13px;
      }
      .admin-field-help {
        margin: -4px 0 12px;
        color: var(--admin-muted);
        font-size: 13px;
        line-height: 1.45;
      }
      .admin-link-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-height: 36px;
        text-decoration: none;
        border: 1px solid var(--admin-line);
        border-radius: 8px;
        padding: 8px 12px;
        background: #fff;
        color: var(--admin-green-dark);
        font-weight: 800;
      }
      .admin-link-button:hover {
        border-color: var(--admin-green);
        background: var(--admin-surface-muted);
      }
      .admin-danger-button {
        border-color: #f1b4ae;
        background: #fff;
        color: var(--admin-red);
      }
      .admin-danger-button:hover {
        border-color: var(--admin-red);
        background: var(--admin-red-bg);
        color: var(--admin-red);
      }
      .admin-badge {
        display: inline-flex;
        align-items: center;
        min-height: 26px;
        border-radius: 999px;
        padding: 4px 9px;
        background: var(--admin-surface-muted);
        border: 1px solid var(--admin-line);
        color: var(--admin-muted);
        font-size: 12px;
        font-weight: 800;
        white-space: nowrap;
      }
      .admin-badge.ok {
        background: var(--admin-ok-bg);
        border-color: #b7ebc9;
        color: #146c43;
      }
      .admin-badge.warn {
        background: #fff8e6;
        border-color: #f0d58c;
        color: #7a4f00;
      }
      .admin-badge.danger {
        background: var(--admin-red-bg);
        border-color: #f1b4ae;
        color: var(--admin-red);
      }
      .admin-empty-row td,
      .admin-empty {
        color: var(--admin-muted);
        background: #fbfdfc;
        text-align: center;
      }
      .admin-id {
        font-family: Consolas, monospace;
        font-size: 12px;
        word-break: break-all;
      }
      .admin-note-list {
        margin: 0;
        padding-left: 18px;
        color: var(--admin-muted);
        line-height: 1.5;
      }
      .admin-timeline {
        display: grid;
        gap: 10px;
        margin: 0;
        padding: 0;
        list-style: none;
      }
      .admin-timeline li {
        border-left: 3px solid #d9e9e1;
        padding: 2px 0 2px 12px;
        color: var(--admin-muted);
        line-height: 1.45;
      }
      .admin-timeline strong {
        color: var(--admin-strong);
      }
      .admin-json-editor {
        min-height: 420px;
        font-family: Consolas, monospace;
        font-size: 13px;
        line-height: 1.45;
      }
      .admin-json-preview {
        max-width: 520px;
        max-height: 260px;
        overflow: auto;
        margin: 8px 0 0;
        padding: 10px;
        border-radius: 8px;
        background: #f7fbf9;
        border: 1px solid var(--admin-line);
        font-family: Consolas, monospace;
        font-size: 12px;
        line-height: 1.45;
        white-space: pre-wrap;
      }
      .admin-card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 14px;
      }
      .admin-metric {
        display: block;
        text-decoration: none;
        color: inherit;
        min-height: 132px;
        padding: 18px;
        background: var(--admin-surface);
        border: 1px solid var(--admin-line);
        border-radius: 8px;
        box-shadow: var(--admin-shadow);
        transition: border-color 120ms ease, transform 120ms ease, box-shadow 120ms ease;
      }
      .admin-metric:hover {
        border-color: var(--admin-green);
        transform: translateY(-1px);
        box-shadow: 0 14px 32px rgba(15, 45, 35, 0.12);
      }
      .admin-metric-title {
        font-size: 15px;
        font-weight: 800;
        margin-bottom: 8px;
      }
      .admin-metric-copy {
        color: var(--admin-muted);
        line-height: 1.45;
        min-height: 42px;
      }
      .admin-metric-value {
        margin-top: 14px;
        font-size: 13px;
        color: var(--admin-green);
        font-weight: 800;
      }
      h2, h3 { color: var(--admin-strong); }
      input, textarea, select {
        width: 100%;
        border: 1px solid #cfd9d5;
        border-radius: 8px;
        padding: 10px 11px;
        font: inherit;
        color: var(--admin-text);
        background: #fff;
      }
      input[type="checkbox"],
      input[type="radio"] {
        width: auto;
        margin-right: 6px;
      }
      input:focus, textarea:focus, select:focus {
        outline: 3px solid rgba(35, 122, 91, 0.18);
        border-color: var(--admin-green);
      }
      button, .admin-button {
        border: 1px solid var(--admin-green);
        border-radius: 8px;
        padding: 9px 13px;
        background: var(--admin-green);
        color: #fff;
        font: inherit;
        font-weight: 800;
        cursor: pointer;
      }
      button:hover, .admin-button:hover {
        background: var(--admin-green-dark);
        border-color: var(--admin-green-dark);
      }
      button[disabled] {
        cursor: not-allowed;
        opacity: 0.6;
      }
      .admin-button-secondary,
      .admin-logout button,
      form.pill button {
        border: 1px solid var(--admin-line);
        border-radius: 8px;
        padding: 9px 13px;
        font: inherit;
        font-weight: 800;
        background: #fff;
        color: var(--admin-green-dark);
      }
      .admin-button-secondary:hover,
      .admin-logout button:hover,
      form.pill button:hover {
        background: var(--admin-surface-muted);
        border-color: var(--admin-green);
      }
      table {
        width: 100%;
        border-collapse: separate !important;
        border-spacing: 0;
        background: var(--admin-surface);
        overflow: hidden;
      }
      tr:hover td {
        background: #f7fbf9;
      }
      section {
        overflow-x: auto;
      }
      th, td {
        border-bottom: 1px solid #e8efec !important;
        padding: 11px 10px !important;
        text-align: left;
        vertical-align: top;
      }
      th {
        position: sticky;
        top: 0;
        z-index: 1;
        background: #f1f6f4 !important;
        color: var(--admin-strong);
        font-size: 12px;
        text-transform: uppercase;
        letter-spacing: 0;
      }
      tr:nth-child(even) {
        background: #fbfdfc;
      }
      code {
        background: #eef5f2;
        color: var(--admin-strong);
        padding: 2px 5px;
        border-radius: 6px;
      }
      .admin-scroll {
        overflow-x: auto;
      }
      .admin-login-body {
        min-height: 100vh;
        display: grid;
        place-items: center;
        padding: 24px;
      }
      .admin-login-panel {
        max-width: 560px;
        width: 100%;
        background: var(--admin-surface);
        border: 1px solid var(--admin-line);
        border-radius: 8px;
        padding: 26px;
        box-shadow: var(--admin-shadow);
      }
      .admin-shortcut-hint {
        color: var(--admin-muted);
        font-size: 12px;
      }
      .admin-status {
        margin-top: 16px;
        padding: 12px;
        border-radius: 8px;
        background: #eef5ff;
        color: #173b69;
        min-height: 20px;
      }
      .admin-status.error {
        background: var(--admin-red-bg);
        color: var(--admin-red);
      }
      .admin-alert {
        margin: 0 0 16px;
        padding: 12px 14px;
        border-radius: 8px;
        border: 1px solid var(--admin-line);
        background: var(--admin-surface-muted);
        color: var(--admin-muted);
        line-height: 1.45;
      }
      .admin-alert strong {
        display: block;
        margin-bottom: 6px;
        color: var(--admin-strong);
      }
      .admin-alert ul {
        margin: 6px 0 0;
        padding-left: 18px;
      }
      .admin-alert.danger {
        background: var(--admin-red-bg);
        border-color: #f1b4ae;
        color: var(--admin-red);
      }
      .admin-alert.warn {
        background: #fff8e6;
        border-color: #f0d58c;
        color: #7a4f00;
      }
      .admin-alert.ok {
        background: var(--admin-ok-bg);
        border-color: #b7ebc9;
        color: #146c43;
      }
      .admin-task-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
        gap: 14px;
      }
      .admin-task-card {
        display: grid;
        gap: 8px;
        min-height: 150px;
        padding: 16px;
        text-decoration: none;
        color: inherit;
        border: 1px solid var(--admin-line);
        border-radius: 8px;
        background: #fff;
        transition: border-color 120ms ease, transform 120ms ease, box-shadow 120ms ease;
      }
      .admin-task-card:hover {
        border-color: var(--admin-green);
        transform: translateY(-1px);
        box-shadow: 0 14px 32px rgba(15, 45, 35, 0.12);
      }
      .admin-task-card strong {
        color: var(--admin-strong);
        font-size: 16px;
      }
      .admin-task-card span {
        color: var(--admin-muted);
        line-height: 1.45;
      }
      .admin-task-step {
        width: fit-content;
        border-radius: 999px;
        padding: 4px 8px;
        background: #e9f6ef;
        color: var(--admin-green-dark) !important;
        font-size: 12px;
        font-weight: 800;
      }
      @media (max-width: 760px) {
        .admin-topbar-inner,
        .admin-hero,
        .admin-user {
          align-items: stretch;
          flex-direction: column;
        }
        .admin-main {
          padding: 18px 14px 32px;
        }
        .admin-hero h1 {
          font-size: 23px;
        }
        .admin-main div[style*="grid-template-columns"] {
          grid-template-columns: 1fr !important;
        }
        .admin-page-grid,
        .admin-page-grid.is-three,
        .admin-field-grid {
          grid-template-columns: 1fr;
        }
        .admin-toolbar input,
        .admin-toolbar select {
          width: 100%;
          min-width: 0;
        }
        .admin-console-link,
        .admin-section-link,
        button,
        .admin-button,
        .admin-button-secondary {
          min-height: 40px;
        }
      }
    """


def _admin_shell(
    title: str,
    principal: Dict[str, Any],
    body_html: str,
    *,
    current_console: str,
    section_nav_html: str = "",
    description: str = "",
    max_width: int = 1240,
) -> HTMLResponse:
    actor = html.escape(str(principal.get("actor") or principal.get("uid") or "admin"), quote=True)
    roles = ", ".join(sorted(str(role) for role in (principal.get("roles") or []))) or "admin"
    return HTMLResponse(
        content=f"""
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>{html.escape(title, quote=True)}</title>
          <style>{_admin_base_css()}</style>
        </head>
        <body>
          <div class="admin-shell" style="--admin-width:{int(max_width)}px;">
            <header class="admin-topbar">
              <div class="admin-topbar-inner">
                <div class="admin-brand">
                  <a href="/admin/login">PCOSina Admin</a>
                  <small>Simple maintenance tools</small>
                </div>
                <a href="/admin/login" class="admin-home-link">Home</a>
                {_admin_console_switcher_html(principal, current=current_console)}
                <div class="admin-user">
                  <span class="admin-user-chip">{actor}</span>
                  <span class="admin-user-chip">Roles: {html.escape(roles, quote=True)}</span>
                  <form method="post" action="/admin/logout" class="admin-logout" style="margin:0;">
                    <button type="submit">Sign out</button>
                  </form>
                </div>
              </div>
            </header>
            <main class="admin-main">
              <div class="admin-hero">
                <div>
                  <h1>{html.escape(title, quote=True)}</h1>
                  <p>{html.escape(description, quote=True)}</p>
                </div>
              </div>
              {f'<nav class="admin-section-nav" aria-label="Section navigation">{section_nav_html}</nav>' if section_nav_html else ''}
              {body_html}
            </main>
          </div>
          <script>
            document.addEventListener("submit", (event) => {{
              const form = event.target;
              if (!form || !form.dataset || !form.dataset.confirm) {{
                return;
              }}
              if (!window.confirm(form.dataset.confirm)) {{
                event.preventDefault();
              }}
            }});

            document.addEventListener("keydown", (event) => {{
              const key = String(event.key || "").toLowerCase();
              if ((event.ctrlKey || event.metaKey) && key === "k") {{
                const search = document.querySelector('input[name="q"], input[type="search"]');
                if (search) {{
                  event.preventDefault();
                  search.focus();
                  if (typeof search.select === "function") {{
                    search.select();
                  }}
                }}
              }}
            }});
          </script>
        </body>
        </html>
        """
    )


def _admin_metric_cards_html(cards: list[tuple[str, str, str, int]]) -> str:
    card_html = "".join(
        f"""
        <a href="{href}" class="admin-metric">
          <div class="admin-metric-title">{html.escape(label, quote=True)}</div>
          <div class="admin-metric-copy">{html.escape(description, quote=True)}</div>
          <div class="admin-metric-value">Total: {int(count)}</div>
        </a>
        """
        for label, href, description, count in cards
    )
    return f"<div class='admin-card-grid'>{card_html}</div>"


def _admin_workspace_cards_html(cards: list[tuple[str, str, str]]) -> str:
    card_html = "".join(
        f"""
        <a href="{href}" class="admin-metric">
          <div class="admin-metric-title">{html.escape(label, quote=True)}</div>
          <div class="admin-metric-copy">{html.escape(description, quote=True)}</div>
          <div class="admin-metric-value">Open console</div>
        </a>
        """
        for label, href, description in cards
    )
    return f"<div class='admin-card-grid'>{card_html}</div>"


def _admin_safe_count(loader) -> int:
    try:
        return len(loader())
    except Exception:
        return 0


def _admin_safe_load(label: str, loader, default: Any):
    try:
        return loader(), ""
    except Exception as exc:
        traceback.print_exc()
        return default, f"{label} could not be loaded right now."


def _admin_alert_list_html(messages: list[str]) -> str:
    cleaned = [str(item or "").strip() for item in messages if str(item or "").strip()]
    if not cleaned:
        return ""
    items = "".join(f"<li>{html.escape(item, quote=True)}</li>" for item in cleaned)
    return (
        "<div class='admin-alert danger' role='status'>"
        "<strong>Some information could not be loaded.</strong>"
        f"<ul>{items}</ul>"
        "</div>"
    )


def _admin_task_cards_html(cards: list[tuple[str, str, str, str]]) -> str:
    card_html = "".join(
        f"""
        <a href="{href}" class="admin-task-card">
          <span class="admin-task-step">{html.escape(step, quote=True)}</span>
          <strong>{html.escape(title, quote=True)}</strong>
          <span>{html.escape(description, quote=True)}</span>
        </a>
        """
        for step, title, href, description in cards
    )
    return f"<div class='admin-task-grid'>{card_html}</div>"


def _admin_select_options(options: list[tuple[str, str]], current: Any) -> str:
    current_value = str(current or "").strip().lower()
    return "".join(
        "<option value='{value}' {selected}>{label}</option>".format(
            value=html.escape(str(value), quote=True),
            label=html.escape(str(label), quote=True),
            selected="selected" if str(value).strip().lower() == current_value else "",
        )
        for value, label in options
    )


def _admin_timeline_html(items: list[tuple[str, str]]) -> str:
    rows = "".join(
        f"<li><strong>{html.escape(title, quote=True)}</strong><br/>{html.escape(copy, quote=True)}</li>"
        for title, copy in items
    )
    return f"<ul class='admin-timeline'>{rows}</ul>"


def _admin_badge(label: Any, tone: str = "") -> str:
    tone_class = f" {tone}" if tone else ""
    return f"<span class='admin-badge{tone_class}'>{html.escape(str(label), quote=True)}</span>"


def _admin_empty_row(colspan: int, message: str) -> str:
    return f"<tr class='admin-empty-row'><td colspan='{int(colspan)}'>{html.escape(message, quote=True)}</td></tr>"


def _admin_section_header(title: str, description: str, action_html: str = "") -> str:
    action_block = f"<div class='admin-actions'>{action_html}</div>" if action_html else ""
    return (
        "<div class='admin-section-header'>"
        "<div>"
        f"<h2>{html.escape(title, quote=True)}</h2>"
        f"<p>{html.escape(description, quote=True)}</p>"
        "</div>"
        f"{action_block}"
        "</div>"
    )


def _admin_new_link(href: str, label: str) -> str:
    return f"<a href='{html.escape(href, quote=True)}' class='admin-link-button'>{html.escape(label, quote=True)}</a>"


def _admin_danger_confirm(message: str) -> str:
    return f"data-confirm='{html.escape(message, quote=True)}'"


def _admin_content_layout(
    title: str,
    principal: Dict[str, Any],
    body_html: str,
    *,
    active: str,
) -> HTMLResponse:
    nav_items = [
        ("Overview", "/admin/content", "overview"),
        ("Recipes", "/admin/content/recipes", "recipes"),
        ("Price Rules", "/admin/content/price-rules", "price-rules"),
        ("Nutrition Corrections", "/admin/content/nutrition-corrections", "nutrition-corrections"),
    ]
    return _admin_shell(
        title,
        principal,
        body_html,
        current_console="content",
        section_nav_html=_admin_section_nav_html(nav_items, active=active),
        description="Edit meals, prices, and nutrition corrections used by PCOSina.",
        max_width=1240,
    )


def _admin_ops_layout(
    title: str,
    principal: Dict[str, Any],
    body_html: str,
    *,
    active: str,
) -> HTMLResponse:
    nav_items = [
        ("Start", "/admin/ops", "overview"),
        ("Support", "/admin/ops/support-cases", "support-cases"),
        ("Sign-ins", "/admin/ops/admin-sessions", "admin-sessions"),
        ("Access", "/admin/ops/operator-access", "operator-access"),
        ("History", "/admin/ops/audit-logs", "audit-logs"),
    ]
    return _admin_shell(
        title,
        principal,
        body_html,
        current_console="ops",
        section_nav_html=_admin_section_nav_html(nav_items, active=active),
        description="Handle app issues, admin sign-ins, access, and change history.",
        max_width=1280,
    )


def _admin_policy_layout(
    title: str,
    principal: Dict[str, Any],
    body_html: str,
) -> HTMLResponse:
    return _admin_shell(
        title,
        principal,
        body_html,
        current_console="policy",
        description="Review and update the planner settings used by the backend.",
        max_width=1320,
    )


@app.get("/admin/content", response_class=HTMLResponse)
def admin_content_home(principal: Any = Depends(require_content_admin)):
    recipes_count = len(database.list_admin_recipes(limit=25))
    price_rule_count = len(database.list_admin_price_rules(limit=25))
    nutrition_count = len(database.list_admin_nutrition_corrections(limit=25))
    cards = [
        (
            "Recipes",
            "/admin/content/recipes",
            "Create, edit, and retire meal content used by the planner.",
            recipes_count,
        ),
        (
            "Ingredient Price Rules",
            "/admin/content/price-rules",
            "Manage ingredient cost overrides that affect grocery and budget logic.",
            price_rule_count,
        ),
        (
            "Nutrition Corrections",
            "/admin/content/nutrition-corrections",
            "Apply reviewed nutrition overrides without replacing the underlying recipe.",
            nutrition_count,
        ),
    ]
    body_html = (
        "<section class='admin-card' style='margin-bottom:16px;'>"
        "<p style='margin:0;line-height:1.6;'>Use this area to keep the meal planner data updated. "
        "Start with recipes, then review price rules and nutrition corrections when needed.</p>"
        "</section>"
        f"{_admin_metric_cards_html(cards)}"
    )
    return _admin_content_layout("Content Console", principal, body_html, active="overview")


@app.get("/admin/content/recipes", response_class=HTMLResponse)
def admin_content_recipes_page(
    q: str | None = None,
    meal_type: str | None = None,
    edit_id: str | None = None,
    status: str | None = None,
    error: str | None = None,
    limit: int = 50,
    principal: Any = Depends(require_content_admin),
):
    query = str(q or "").strip()
    meal = str(meal_type or "").strip()
    edit_token = str(edit_id or "").strip()
    items = database.list_admin_recipes(q=query, meal_type=meal, limit=limit)
    edit_item = database.get_recipe_by_id(edit_token) if edit_token else None
    if edit_token and not edit_item and not error:
        error = f"Recipe not found: {edit_token}"
    catalog_status = database.get_recipe_catalog_status()

    save_csrf = _build_admin_csrf_token(principal, "content-recipe-save")
    delete_csrf = _build_admin_csrf_token(principal, "content-recipe-delete")
    seed_csrf = _build_admin_csrf_token(principal, "content-recipe-seed")

    current = edit_item or {
        "id": "",
        "title": "",
        "mealType": "",
        "calories": 0,
        "proteinGrams": 0,
        "carbsGrams": 0,
        "fatsGrams": 0,
        "fiberGrams": 0,
        "minutes": 25,
        "tags": [],
        "ingredients": [],
        "steps": [],
    }

    rows = []
    for item in items:
        edit_params = {"edit_id": item.get("id"), "q": query, "meal_type": meal}
        edit_link = f"/admin/content/recipes?{urlencode({k: v for k, v in edit_params.items() if v})}"
        rows.append(
            "<tr>"
            f"<td>{html.escape(str(item.get('title') or ''), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('mealType') or ''), quote=True)}</td>"
            f"<td>{int(item.get('calories') or 0)}</td>"
            f"<td>{int(item.get('minutes') or 0)}</td>"
            f"<td>{html.escape(', '.join(item.get('tags') or []), quote=True)}</td>"
            "<td><div class='admin-actions'>"
            f"<a href='{edit_link}' class='admin-link-button'>Edit</a>"
            f"<form method='post' action='/admin/content/recipes/{html.escape(str(item.get('id') or ''), quote=True)}/delete' {_admin_danger_confirm('Delete this recipe from the planner catalog?')}>"
            f"<input type='hidden' name='csrf_token' value='{html.escape(delete_csrf, quote=True)}'/>"
            f"<input type='hidden' name='q' value='{html.escape(query, quote=True)}'/>"
            f"<input type='hidden' name='meal_type' value='{html.escape(meal, quote=True)}'/>"
            "<button type='submit' class='admin-danger-button'>Delete</button>"
            "</form>"
            "</div></td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(6, "No recipes match the current filters.")
    seed_source_count = int(catalog_status.get("seedSourceCount") or 0)
    missing_seed_count = int(catalog_status.get("missingSeedCount") or 0)
    database_count = int(catalog_status.get("databaseCount") or 0)
    database_total_count = int(catalog_status.get("databaseTotalCount") or database_count)
    database_inactive_count = int(catalog_status.get("databaseInactiveCount") or 0)
    seed_source_label = html.escape(str(catalog_status.get("seedSourcePath") or "not found"), quote=True)

    body_html = f"""
    {_admin_notice_html(status, error)}
    <section class="admin-card" style="margin-bottom:18px;">
      {_admin_section_header("Catalog Status", "Keep the working recipe database aligned with the bundled seed catalog before reviewing or editing meals.")}
      <div class="admin-card-grid">
        <div>{_admin_badge(database_count, "ok")}<p class="admin-copy">Active database recipes</p></div>
        <div>{_admin_badge(database_total_count)}<p class="admin-copy">Total recipe rows</p></div>
        <div>{_admin_badge(database_inactive_count, "warn" if database_inactive_count else "ok")}<p class="admin-copy">Inactive rows</p></div>
        <div>{_admin_badge(missing_seed_count, "warn" if missing_seed_count else "ok")}<p class="admin-copy">Missing bundled recipes</p></div>
      </div>
      <div class="admin-toolbar" style="margin-top:14px;margin-bottom:0;">
        <code>{seed_source_label}</code>
        <form method="post" action="/admin/content/recipes/seed" style="margin:0;">
          <input type="hidden" name="csrf_token" value="{html.escape(seed_csrf, quote=True)}"/>
          <input type="hidden" name="q" value="{html.escape(query, quote=True)}"/>
          <input type="hidden" name="meal_type" value="{html.escape(meal, quote=True)}"/>
          <input type="hidden" name="limit" value="{limit}"/>
          <button type="submit">Import missing seed recipes</button>
        </form>
      </div>
    </section>
    <div class="admin-page-grid">
      <section class="admin-card">
        {_admin_section_header("Recipe Operations", "Create a new planner recipe or load an existing one from the library to update it.", _admin_new_link("/admin/content/recipes", "New recipe"))}
        <p class="admin-field-help">Tags use comma-separated values. Ingredients use one item per line in <code>name | quantity</code> format.</p>
        <form method="post" action="/admin/content/recipes/save">
          <input type="hidden" name="csrf_token" value="{html.escape(save_csrf, quote=True)}"/>
          <input type="hidden" name="recipe_id" value="{_admin_html_attr(current.get('id'))}"/>
          <label class="admin-field"><span>Title</span><input type="text" name="title" value="{_admin_html_attr(current.get('title'))}" required/></label>
          <label class="admin-field"><span>Meal type</span><input type="text" name="meal_type" value="{_admin_html_attr(current.get('mealType'))}" required/></label>
          <div class="admin-field-grid">
            <label class="admin-field"><span>Calories</span><input type="number" name="calories" value="{_admin_html_attr(current.get('calories'))}" min="0" required/></label>
            <label class="admin-field"><span>Protein (g)</span><input type="number" name="protein_grams" value="{_admin_html_attr(current.get('proteinGrams'))}" min="0" required/></label>
            <label class="admin-field"><span>Carbs (g)</span><input type="number" name="carbs_grams" value="{_admin_html_attr(current.get('carbsGrams'))}" min="0" required/></label>
            <label class="admin-field"><span>Fats (g)</span><input type="number" name="fats_grams" value="{_admin_html_attr(current.get('fatsGrams'))}" min="0" required/></label>
            <label class="admin-field"><span>Fiber (g)</span><input type="number" name="fiber_grams" value="{_admin_html_attr(current.get('fiberGrams'))}" min="0" required/></label>
            <label class="admin-field"><span>Minutes</span><input type="number" name="minutes" value="{_admin_html_attr(current.get('minutes'))}" min="0" required/></label>
          </div>
          <label class="admin-field"><span>Tags</span><input type="text" name="tags" value="{_admin_html_attr(', '.join(current.get('tags') or []))}"/></label>
          <label class="admin-field"><span>Ingredients</span><textarea name="ingredients_text" rows="7">{html.escape(_admin_format_ingredients(current.get('ingredients')), quote=True)}</textarea></label>
          <label class="admin-field"><span>Steps</span><textarea name="steps_text" rows="7">{html.escape(_admin_format_lines(current.get('steps')), quote=True)}</textarea></label>
          <button type="submit">{'Update recipe' if current.get('id') else 'Create recipe'}</button>
        </form>
      </section>
      <section class="admin-card">
        {_admin_section_header("Recipe Library", "Search existing meals, load a record for editing, or remove retired content.")}
        <form method="get" action="/admin/content/recipes" class="admin-toolbar">
          <input type="text" name="q" value="{html.escape(query, quote=True)}" placeholder="Search title"/>
          <input type="text" name="meal_type" value="{html.escape(meal, quote=True)}" placeholder="Meal type"/>
          <input type="hidden" name="limit" value="{limit}"/>
          <button type="submit">Filter</button>
          <a href="/admin/content/recipes" class="admin-link-button">Clear</a>
        </form>
        <table>
          <thead>
            <tr><th>Title</th><th>Meal</th><th>Calories</th><th>Minutes</th><th>Tags</th><th>Actions</th></tr>
          </thead>
          <tbody>{rows_html}</tbody>
        </table>
      </section>
    </div>
    """
    return _admin_content_layout("Recipe Console", principal, body_html, active="recipes")


@app.post("/admin/content/recipes/save")
def admin_content_save_recipe(
    csrf_token: str = Form(...),
    recipe_id: str = Form(default=""),
    title: str = Form(...),
    meal_type: str = Form(...),
    calories: int = Form(...),
    protein_grams: int = Form(...),
    carbs_grams: int = Form(...),
    fats_grams: int = Form(...),
    fiber_grams: int = Form(...),
    minutes: int = Form(...),
    tags: str = Form(default=""),
    ingredients_text: str = Form(default=""),
    steps_text: str = Form(default=""),
    principal: Any = Depends(require_content_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "content-recipe-save")
    payload = AdminRecipeUpsertRequest(
        title=str(title or "").strip(),
        mealType=str(meal_type or "").strip(),
        calories=max(0, int(calories or 0)),
        proteinGrams=max(0, int(protein_grams or 0)),
        carbsGrams=max(0, int(carbs_grams or 0)),
        fatsGrams=max(0, int(fats_grams or 0)),
        fiberGrams=max(0, int(fiber_grams or 0)),
        minutes=max(0, int(minutes or 0)),
        tags=_admin_csv_items(tags),
        ingredients=_admin_parse_ingredients(ingredients_text),
        steps=_admin_text_lines(steps_text),
    )
    try:
        token = str(recipe_id or "").strip()
        saved = admin_update_recipe(token, payload, principal=principal) if token else admin_create_recipe(payload, principal=principal)
        saved_id = getattr(saved, "id", None) or (saved.model_dump() if hasattr(saved, "model_dump") else {}).get("id")
        return _admin_redirect("/admin/content/recipes", edit_id=saved_id, status="recipe_saved")
    except HTTPException as exc:
        return _admin_redirect("/admin/content/recipes", edit_id=str(recipe_id or "").strip(), error=str(exc.detail or "Recipe save failed"))


@app.post("/admin/content/recipes/{recipe_id}/delete")
def admin_content_delete_recipe(
    recipe_id: str,
    csrf_token: str = Form(...),
    q: str | None = Form(default=None),
    meal_type: str | None = Form(default=None),
    principal: Any = Depends(require_content_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "content-recipe-delete")
    try:
        admin_delete_recipe(recipe_id, principal=principal)
        return _admin_redirect("/admin/content/recipes", q=q, meal_type=meal_type, status="recipe_deleted")
    except HTTPException as exc:
        return _admin_redirect("/admin/content/recipes", q=q, meal_type=meal_type, error=str(exc.detail or "Recipe delete failed"))


@app.post("/admin/content/recipes/seed")
def admin_content_seed_recipes(
    csrf_token: str = Form(...),
    q: str | None = Form(default=None),
    meal_type: str | None = Form(default=None),
    limit: int = Form(default=50),
    principal: Any = Depends(require_content_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "content-recipe-seed")
    try:
        admin_seed_recipes(force=False, principal=principal)
        return _admin_redirect("/admin/content/recipes", q=q, meal_type=meal_type, limit=limit, status="recipe_seeded")
    except Exception as exc:
        return _admin_redirect("/admin/content/recipes", q=q, meal_type=meal_type, limit=limit, error=f"Recipe seed import failed: {exc}")


@app.get("/admin/content/price-rules", response_class=HTMLResponse)
def admin_content_price_rules_page(
    q: str | None = None,
    category: str | None = None,
    edit_id: str | None = None,
    status: str | None = None,
    error: str | None = None,
    limit: int = 50,
    principal: Any = Depends(require_content_admin),
):
    query = str(q or "").strip()
    category_value = str(category or "").strip()
    edit_token = str(edit_id or "").strip()
    items = database.list_admin_price_rules(q=query, category=category_value, limit=limit)
    edit_item = database.get_price_rule_by_id(edit_token) if edit_token else None
    if edit_token and not edit_item and not error:
        error = f"Price rule not found: {edit_token}"

    save_csrf = _build_admin_csrf_token(principal, "content-price-rule-save")
    delete_csrf = _build_admin_csrf_token(principal, "content-price-rule-delete")
    current = edit_item or {
        "id": "",
        "keywords": [],
        "pricePhp": "",
        "priceMinPhp": "",
        "priceMaxPhp": "",
        "category": "",
        "unit": "",
        "active": True,
        "notes": "",
    }

    rows = []
    for item in items:
        edit_params = {"edit_id": item.get("id"), "q": query, "category": category_value}
        edit_link = f"/admin/content/price-rules?{urlencode({k: v for k, v in edit_params.items() if v})}"
        range_text = (
            f"{int(item.get('priceMinPhp'))}-{int(item.get('priceMaxPhp'))}"
            if item.get("priceMinPhp") is not None and item.get("priceMaxPhp") is not None
            else "Exact estimate"
        )
        rows.append(
            "<tr>"
            f"<td>{html.escape(', '.join(item.get('keywords') or []), quote=True)}</td>"
            f"<td>{int(item.get('pricePhp') or 0)}</td>"
            f"<td>{html.escape(range_text, quote=True)}</td>"
            f"<td>{html.escape(str(item.get('category') or ''), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('unit') or ''), quote=True)}</td>"
            f"<td>{_admin_badge('Active' if item.get('active') else 'Inactive', 'ok' if item.get('active') else 'warn')}</td>"
            f"<td>{html.escape(str(item.get('notes') or ''), quote=True)}</td>"
            "<td><div class='admin-actions'>"
            f"<a href='{edit_link}' class='admin-link-button'>Edit</a>"
            f"<form method='post' action='/admin/content/price-rules/{html.escape(str(item.get('id') or ''), quote=True)}/delete' {_admin_danger_confirm('Delete this price rule? Grocery estimates may change.')}>"
            f"<input type='hidden' name='csrf_token' value='{html.escape(delete_csrf, quote=True)}'/>"
            f"<input type='hidden' name='q' value='{html.escape(query, quote=True)}'/>"
            f"<input type='hidden' name='category' value='{html.escape(category_value, quote=True)}'/>"
            "<button type='submit' class='admin-danger-button'>Delete</button>"
            "</form>"
            "</div></td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(8, "No price rules match the current filters.")

    active_checked = "checked" if current.get("active", True) else ""
    body_html = f"""
    {_admin_notice_html(status, error)}
    <div class="admin-page-grid">
      <section class="admin-card">
        {_admin_section_header("Ingredient Price Rules", "Control the cost estimates used by grocery guidance and budget-aware planning.", _admin_new_link("/admin/content/price-rules", "New rule"))}
        <p class="admin-field-help">Keywords should match pantry or grocery ingredient names. Use ranges when store prices vary often.</p>
        <p class="admin-field-help">Prices are estimates and may vary by store, location, and date.</p>
        <form method="post" action="/admin/content/price-rules/save">
          <input type="hidden" name="csrf_token" value="{html.escape(save_csrf, quote=True)}"/>
          <input type="hidden" name="rule_id" value="{_admin_html_attr(current.get('id'))}"/>
          <label class="admin-field"><span>Keywords</span><input type="text" name="keywords" value="{_admin_html_attr(', '.join(current.get('keywords') or []))}" required/></label>
          <label class="admin-field"><span>Estimate / midpoint (PHP)</span><input type="number" name="price_php" value="{_admin_html_attr(current.get('pricePhp'))}" min="1" required/></label>
          <div class="admin-field-grid">
            <label class="admin-field"><span>Min price</span><input type="number" name="price_min_php" value="{_admin_html_attr(current.get('priceMinPhp'))}" min="1"/></label>
            <label class="admin-field"><span>Max price</span><input type="number" name="price_max_php" value="{_admin_html_attr(current.get('priceMaxPhp'))}" min="1"/></label>
          </div>
          <label class="admin-field"><span>Category</span><input type="text" name="category" value="{_admin_html_attr(current.get('category'))}" required/></label>
          <label class="admin-field"><span>Unit</span><input type="text" name="unit" value="{_admin_html_attr(current.get('unit'))}"/></label>
          <label class="admin-field"><span><input type="checkbox" name="active" value="true" {active_checked}/> Active rule</span></label>
          <label class="admin-field"><span>Notes</span><textarea name="notes" rows="5">{html.escape(str(current.get('notes') or ''), quote=True)}</textarea></label>
          <button type="submit">{'Update rule' if current.get('id') else 'Create rule'}</button>
        </form>
      </section>
      <section class="admin-card">
        {_admin_section_header("Rule Library", "Find, edit, or retire ingredient pricing rules.")}
        <form method="get" action="/admin/content/price-rules" class="admin-toolbar">
          <input type="text" name="q" value="{html.escape(query, quote=True)}" placeholder="Search keywords or category"/>
          <input type="text" name="category" value="{html.escape(category_value, quote=True)}" placeholder="Category"/>
          <input type="hidden" name="limit" value="{limit}"/>
          <button type="submit">Filter</button>
          <a href="/admin/content/price-rules" class="admin-link-button">Clear</a>
        </form>
        <table>
          <thead>
            <tr><th>Keywords</th><th>Estimate</th><th>Range</th><th>Category</th><th>Unit</th><th>Active</th><th>Notes</th><th>Actions</th></tr>
          </thead>
          <tbody>{rows_html}</tbody>
        </table>
      </section>
    </div>
    """
    return _admin_content_layout("Price Rule Console", principal, body_html, active="price-rules")


@app.post("/admin/content/price-rules/save")
def admin_content_save_price_rule(
    csrf_token: str = Form(...),
    rule_id: str = Form(default=""),
    keywords: str = Form(...),
    price_php: int = Form(...),
    price_min_php: str = Form(default=""),
    price_max_php: str = Form(default=""),
    category: str = Form(...),
    unit: str = Form(default=""),
    active: str | None = Form(default=None),
    notes: str = Form(default=""),
    principal: Any = Depends(require_content_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "content-price-rule-save")
    def optional_price(value: str) -> int | None:
        value = str(value or "").strip()
        if not value:
            return None
        try:
            return max(1, int(value))
        except ValueError:
            return None

    payload = AdminPriceRuleUpsertRequest(
        keywords=_admin_csv_items(keywords),
        pricePhp=max(1, int(price_php or 0)),
        priceMinPhp=optional_price(price_min_php),
        priceMaxPhp=optional_price(price_max_php),
        category=str(category or "").strip(),
        unit=str(unit or "").strip() or None,
        active=active is not None,
        notes=str(notes or "").strip() or None,
    )
    try:
        token = str(rule_id or "").strip()
        saved = admin_update_price_rule(token, payload, principal=principal) if token else admin_create_price_rule(payload, principal=principal)
        saved_id = getattr(saved, "id", None) or (saved.model_dump() if hasattr(saved, "model_dump") else {}).get("id")
        return _admin_redirect("/admin/content/price-rules", edit_id=saved_id, status="price_rule_saved")
    except HTTPException as exc:
        return _admin_redirect("/admin/content/price-rules", edit_id=str(rule_id or "").strip(), error=str(exc.detail or "Price rule save failed"))


@app.post("/admin/content/price-rules/{rule_id}/delete")
def admin_content_delete_price_rule(
    rule_id: str,
    csrf_token: str = Form(...),
    q: str | None = Form(default=None),
    category: str | None = Form(default=None),
    principal: Any = Depends(require_content_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "content-price-rule-delete")
    try:
        admin_delete_price_rule(rule_id, principal=principal)
        return _admin_redirect("/admin/content/price-rules", q=q, category=category, status="price_rule_deleted")
    except HTTPException as exc:
        return _admin_redirect("/admin/content/price-rules", q=q, category=category, error=str(exc.detail or "Price rule delete failed"))


@app.get("/admin/content/nutrition-corrections", response_class=HTMLResponse)
def admin_content_nutrition_page(
    q: str | None = None,
    edit_recipe_id: str | None = None,
    status: str | None = None,
    error: str | None = None,
    limit: int = 50,
    principal: Any = Depends(require_content_admin),
):
    query = str(q or "").strip()
    recipe_token = str(edit_recipe_id or "").strip()
    items = database.list_admin_nutrition_corrections(q=query, limit=limit)
    edit_recipe = database.get_recipe_by_id(recipe_token) if recipe_token else None
    edit_correction = database.get_nutrition_correction_by_recipe_id(recipe_token) if recipe_token else None
    if recipe_token and not edit_recipe and not edit_correction and not error:
        error = f"Recipe not found: {recipe_token}"

    current = edit_correction or {
        "recipeId": recipe_token,
        "calories": "",
        "proteinGrams": "",
        "carbsGrams": "",
        "fatsGrams": "",
        "fiberGrams": "",
        "sodiumMg": "",
        "sugarGrams": "",
        "active": True,
        "notes": "",
    }
    save_csrf = _build_admin_csrf_token(principal, "content-nutrition-save")
    delete_csrf = _build_admin_csrf_token(principal, "content-nutrition-delete")
    recipe_hint = (
        f"Editing correction for {edit_recipe.get('title')} ({edit_recipe.get('id')})"
        if edit_recipe
        else ("Editing existing correction" if edit_correction else "Enter a recipe ID to create a correction.")
    )

    rows = []
    for item in items:
        edit_link = f"/admin/content/nutrition-corrections?{urlencode({k: v for k, v in {'edit_recipe_id': item.get('recipeId'), 'q': query}.items() if v})}"
        rows.append(
            "<tr>"
            f"<td>{html.escape(str(item.get('recipeTitle') or ''), quote=True)}</td>"
            f"<td><span class='admin-id'>{html.escape(str(item.get('recipeId') or ''), quote=True)}</span></td>"
            f"<td>{_admin_badge('Active' if item.get('active') else 'Inactive', 'ok' if item.get('active') else 'warn')}</td>"
            f"<td>{html.escape(str(item.get('notes') or ''), quote=True)}</td>"
            "<td><div class='admin-actions'>"
            f"<a href='{edit_link}' class='admin-link-button'>Edit</a>"
            f"<form method='post' action='/admin/content/nutrition-corrections/{html.escape(str(item.get('recipeId') or ''), quote=True)}/delete' {_admin_danger_confirm('Delete this nutrition correction? The recipe will use its base nutrition values.')}>"
            f"<input type='hidden' name='csrf_token' value='{html.escape(delete_csrf, quote=True)}'/>"
            f"<input type='hidden' name='q' value='{html.escape(query, quote=True)}'/>"
            "<button type='submit' class='admin-danger-button'>Delete</button>"
            "</form>"
            "</div></td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(5, "No nutrition corrections match the current filters.")
    active_checked = "checked" if current.get("active", True) else ""

    body_html = f"""
    {_admin_notice_html(status, error)}
    <div class="admin-page-grid">
      <section class="admin-card">
        {_admin_section_header("Nutrition Corrections", "Apply reviewed nutrition overrides while preserving the original recipe record.", _admin_new_link("/admin/content/nutrition-corrections", "New correction"))}
        <p class="admin-field-help">{html.escape(recipe_hint, quote=True)}</p>
        <form method="post" action="/admin/content/nutrition-corrections/save">
          <input type="hidden" name="csrf_token" value="{html.escape(save_csrf, quote=True)}"/>
          <label class="admin-field"><span>Recipe ID</span><input type="text" name="recipe_id" value="{_admin_html_attr(current.get('recipeId'))}" required/></label>
          <div class="admin-field-grid">
            <label class="admin-field"><span>Calories</span><input type="number" name="calories" value="{_admin_html_attr(current.get('calories'))}" min="0"/></label>
            <label class="admin-field"><span>Protein (g)</span><input type="number" name="protein_grams" value="{_admin_html_attr(current.get('proteinGrams'))}" min="0"/></label>
            <label class="admin-field"><span>Carbs (g)</span><input type="number" name="carbs_grams" value="{_admin_html_attr(current.get('carbsGrams'))}" min="0"/></label>
            <label class="admin-field"><span>Fats (g)</span><input type="number" name="fats_grams" value="{_admin_html_attr(current.get('fatsGrams'))}" min="0"/></label>
            <label class="admin-field"><span>Fiber (g)</span><input type="number" name="fiber_grams" value="{_admin_html_attr(current.get('fiberGrams'))}" min="0"/></label>
            <label class="admin-field"><span>Sodium (mg)</span><input type="number" name="sodium_mg" value="{_admin_html_attr(current.get('sodiumMg'))}" min="0"/></label>
          </div>
          <label class="admin-field"><span>Sugar (g)</span><input type="number" name="sugar_grams" value="{_admin_html_attr(current.get('sugarGrams'))}" min="0"/></label>
          <label class="admin-field"><span><input type="checkbox" name="active" value="true" {active_checked}/> Active correction</span></label>
          <label class="admin-field"><span>Notes</span><textarea name="notes" rows="5">{html.escape(str(current.get('notes') or ''), quote=True)}</textarea></label>
          <button type="submit">Save correction</button>
        </form>
      </section>
      <section class="admin-card">
        {_admin_section_header("Correction Library", "Search reviewed corrections and load one into the editor.")}
        <form method="get" action="/admin/content/nutrition-corrections" class="admin-toolbar">
          <input type="text" name="q" value="{html.escape(query, quote=True)}" placeholder="Search recipe title or ID"/>
          <input type="hidden" name="limit" value="{limit}"/>
          <button type="submit">Filter</button>
          <a href="/admin/content/nutrition-corrections" class="admin-link-button">Clear</a>
        </form>
        <table>
          <thead>
            <tr><th>Recipe</th><th>Recipe ID</th><th>Active</th><th>Notes</th><th>Actions</th></tr>
          </thead>
          <tbody>{rows_html}</tbody>
        </table>
      </section>
    </div>
    """
    return _admin_content_layout("Nutrition Correction Console", principal, body_html, active="nutrition-corrections")


@app.post("/admin/content/nutrition-corrections/save")
def admin_content_save_nutrition_correction(
    csrf_token: str = Form(...),
    recipe_id: str = Form(...),
    calories: str | None = Form(default=None),
    protein_grams: str | None = Form(default=None),
    carbs_grams: str | None = Form(default=None),
    fats_grams: str | None = Form(default=None),
    fiber_grams: str | None = Form(default=None),
    sodium_mg: str | None = Form(default=None),
    sugar_grams: str | None = Form(default=None),
    active: str | None = Form(default=None),
    notes: str = Form(default=""),
    principal: Any = Depends(require_content_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "content-nutrition-save")
    token = str(recipe_id or "").strip()
    payload = AdminNutritionCorrectionUpsertRequest(
        calories=_admin_parse_optional_int(calories),
        proteinGrams=_admin_parse_optional_int(protein_grams),
        carbsGrams=_admin_parse_optional_int(carbs_grams),
        fatsGrams=_admin_parse_optional_int(fats_grams),
        fiberGrams=_admin_parse_optional_int(fiber_grams),
        sodiumMg=_admin_parse_optional_int(sodium_mg),
        sugarGrams=_admin_parse_optional_int(sugar_grams),
        active=active is not None,
        notes=str(notes or "").strip() or None,
    )
    try:
        admin_upsert_nutrition_correction(token, payload, principal=principal)
        return _admin_redirect("/admin/content/nutrition-corrections", edit_recipe_id=token, status="nutrition_correction_saved")
    except (HTTPException, ValueError) as exc:
        detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
        return _admin_redirect("/admin/content/nutrition-corrections", edit_recipe_id=token, error=str(detail or "Nutrition correction save failed"))


@app.post("/admin/content/nutrition-corrections/{recipe_id}/delete")
def admin_content_delete_nutrition_correction(
    recipe_id: str,
    csrf_token: str = Form(...),
    q: str | None = Form(default=None),
    principal: Any = Depends(require_content_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "content-nutrition-delete")
    try:
        admin_delete_nutrition_correction(recipe_id, principal=principal)
        return _admin_redirect("/admin/content/nutrition-corrections", q=q, status="nutrition_correction_deleted")
    except HTTPException as exc:
        return _admin_redirect("/admin/content/nutrition-corrections", q=q, error=str(exc.detail or "Nutrition correction delete failed"))


@app.get("/admin/ops", response_class=HTMLResponse)
def admin_ops_home(principal: Any = Depends(require_ops_admin)):
    support_cases, support_error = _admin_safe_load("Support cases", lambda: database.list_support_cases(limit=50), [])
    open_cases = sum(1 for item in support_cases if str(item.get("status") or "").lower() not in {"resolved", "closed"})
    sessions, sessions_error = _admin_safe_load("Admin sessions", lambda: database.list_admin_sessions(active_only=True, limit=100), [])
    blocked_overrides, access_error = _admin_safe_load(
        "Admin access rules",
        lambda: database.list_operator_access_overrides(blocked_only=True, limit=100),
        [],
    )
    audit_events, audit_error = _admin_safe_load("Change history", lambda: database.list_admin_action_logs(limit=50), [])
    load_errors = [support_error, sessions_error, access_error, audit_error]
    urgent_cases = sum(1 for item in support_cases if str(item.get("priority") or "").lower() in {"urgent", "high"} or bool(item.get("escalated")))
    cards = [
        ("Open Issues", "/admin/ops/support-cases", "Support cases that are not closed yet.", open_cases),
        ("Urgent Issues", "/admin/ops/support-cases", "Cases marked high, urgent, or escalated.", urgent_cases),
        ("Active Sign-ins", "/admin/ops/admin-sessions", "Admin accounts currently signed in.", len(sessions)),
        ("Blocked Accounts", "/admin/ops/operator-access", "Admin accounts blocked from access.", len(blocked_overrides)),
        ("Recent Changes", "/admin/ops/audit-logs", "Latest admin actions saved by the system.", len(audit_events)),
    ]
    tasks = [
        ("Help", "User Issues", "/admin/ops/support-cases", "Create or update a case when a user reports a problem."),
        ("Check", "Admin Sign-ins", "/admin/ops/admin-sessions", "Review who is signed in and revoke suspicious sessions."),
        ("Control", "Admin Access", "/admin/ops/operator-access", "Block or restore an admin account."),
        ("Review", "Change History", "/admin/ops/audit-logs", "See recent admin changes for traceability."),
    ]
    body_html = (
        f"{_admin_alert_list_html(load_errors)}"
        "<section class='admin-card' style='margin-bottom:16px;'>"
        f"{_admin_section_header('What do you need to do?', 'Choose the task. The technical checks stay in the background unless something needs attention.')}"
        f"{_admin_task_cards_html(tasks)}"
        "</section>"
        "<section class='admin-card'>"
        f"{_admin_section_header('At a Glance', 'Simple counts for the admin work that may need follow-up.')}"
        f"{_admin_metric_cards_html(cards)}"
        "</section>"
    )
    return _admin_ops_layout("Ops Home", principal, body_html, active="overview")


@app.get("/admin/ops/support-cases", response_class=HTMLResponse)
def admin_ops_support_cases_page(
    q: str | None = None,
    status: str | None = None,
    assignee: str | None = None,
    escalated: str | None = None,
    edit_case_id: str | None = None,
    notice: str | None = None,
    error: str | None = None,
    limit: int = 50,
    principal: Any = Depends(require_ops_admin),
):
    query = str(q or "").strip()
    status_filter = str(status or "").strip()
    assignee_filter = str(assignee or "").strip()
    edit_token = str(edit_case_id or "").strip()
    escalated_filter = None if escalated in (None, "", "all") else str(escalated).lower() == "true"
    items, list_error = _admin_safe_load(
        "Support case queue",
        lambda: database.list_support_cases(
            user_uid=None,
            status=status_filter or None,
            assignee=assignee_filter or None,
            escalated=escalated_filter,
            q=query or None,
            limit=limit,
        ),
        [],
    )
    edit_item, edit_error = _admin_safe_load(
        "Selected support case",
        lambda: database.get_support_case(edit_token),
        None,
    ) if edit_token else (None, "")
    if edit_token and not edit_item and not error:
        error = f"Support case not found: {edit_token}"
    if not error:
        error = list_error or edit_error or None

    create_csrf = _build_admin_csrf_token(principal, "ops-support-case-create")
    update_csrf = _build_admin_csrf_token(principal, "ops-support-case-update")
    note_csrf = _build_admin_csrf_token(principal, "ops-support-case-note")

    rows = []
    for item in items:
        edit_params = {"edit_case_id": item.get("id"), "q": query, "status": status_filter, "assignee": assignee_filter, "escalated": escalated or "all"}
        edit_link = f"/admin/ops/support-cases?{urlencode({k: v for k, v in edit_params.items() if v not in (None, '')})}"
        export_link = f"/ops/support-cases/{html.escape(str(item.get('id') or ''), quote=True)}/export"
        row_status = str(item.get("status") or "")
        row_priority = str(item.get("priority") or "")
        status_tone = "ok" if row_status.lower() in {"resolved", "closed"} else "warn"
        priority_tone = "danger" if row_priority.lower() in {"urgent", "high"} else ""
        rows.append(
            "<tr>"
            f"<td>{html.escape(str(item.get('summary') or ''), quote=True)}</td>"
            f"<td><span class='admin-id'>{html.escape(str(item.get('userUid') or ''), quote=True)}</span></td>"
            f"<td>{_admin_badge(row_status or 'open', status_tone)}</td>"
            f"<td>{_admin_badge(row_priority or 'normal', priority_tone)}</td>"
            f"<td>{html.escape(str(item.get('assignee') or '—'), quote=True)}</td>"
            f"<td>{_admin_badge('Escalated' if item.get('escalated') else 'Normal', 'danger' if item.get('escalated') else 'ok')}</td>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('updatedAt')), quote=True)}</td>"
            f"<td><div class='admin-actions'><a href='{edit_link}' class='admin-link-button'>Open</a><a href='{export_link}' class='admin-link-button'>Download</a></div></td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(8, "No support cases match the current filters.")

    current = edit_item or {
        "id": "",
        "userUid": "",
        "relatedJobId": "",
        "summary": "",
        "status": "open",
        "priority": "normal",
        "assignee": "",
        "escalated": False,
        "notes": [],
    }
    note_items = current.get("notes") or []
    current_status_options = _admin_select_options(
        [("open", "Open"), ("investigating", "Investigating"), ("resolved", "Resolved"), ("closed", "Closed")],
        current.get("status") or "open",
    )
    current_priority_options = _admin_select_options(
        [("normal", "Normal"), ("low", "Low"), ("high", "High"), ("urgent", "Urgent")],
        current.get("priority") or "normal",
    )
    status_filter_options = _admin_select_options(
        [("", "All statuses"), ("open", "Open"), ("investigating", "Investigating"), ("resolved", "Resolved"), ("closed", "Closed")],
        status_filter,
    )
    notes_html = "".join(
        f"<li><strong>{html.escape(str(note.get('author') or ''), quote=True)}</strong> • "
        f"{html.escape(_admin_format_epoch_ms(note.get('createdAtMs')), quote=True)}<br/>"
        f"{html.escape(str(note.get('message') or ''), quote=True)}</li>"
        for note in note_items[-6:]
    ) or "<li>No notes yet.</li>"
    escalated_selected = "true" if current.get("escalated") else "false"

    body_html = f"""
    {_admin_notice_html(notice, error)}
    <section class="admin-card" style="margin-bottom:18px;">
      {_admin_section_header("User Issues", "Search, open, and update reported app problems.")}
      <form method="get" action="/admin/ops/support-cases" class="admin-toolbar">
        <input type="text" name="q" value="{html.escape(query, quote=True)}" placeholder="Search summary or job text"/>
        <select name="status">{status_filter_options}</select>
        <input type="text" name="assignee" value="{html.escape(assignee_filter, quote=True)}" placeholder="Assignee"/>
        <select name="escalated">
          <option value="all" {'selected' if (escalated or 'all') == 'all' else ''}>All escalation states</option>
          <option value="true" {'selected' if escalated == 'true' else ''}>Escalated only</option>
          <option value="false" {'selected' if escalated == 'false' else ''}>Not escalated</option>
        </select>
        <button type="submit">Filter</button>
        <a href="/admin/ops/support-cases" class="admin-link-button">Clear</a>
      </form>
      <table>
        <thead><tr><th>Summary</th><th>User</th><th>Status</th><th>Priority</th><th>Assignee</th><th>Escalation</th><th>Updated</th><th>Actions</th></tr></thead>
        <tbody>{rows_html}</tbody>
      </table>
    </section>
    <div class="admin-page-grid">
      <section class="admin-card">
        {_admin_section_header("New Issue", "Create a case when a user reports a problem or a meal plan job needs review.")}
        <form method="post" action="/admin/ops/support-cases/create">
          <input type="hidden" name="csrf_token" value="{html.escape(create_csrf, quote=True)}"/>
          <label class="admin-field"><span>User ID</span><input type="text" name="user_uid" required/></label>
          <label class="admin-field"><span>Meal plan job ID</span><input type="text" name="related_job_id"/></label>
          <label class="admin-field"><span>Summary</span><textarea name="summary" rows="4" required></textarea></label>
          <div class="admin-field-grid">
            <label class="admin-field"><span>Priority</span>
              <select name="priority">
                <option value="normal" selected>Normal</option>
                <option value="low">Low</option>
                <option value="high">High</option>
                <option value="urgent">Urgent</option>
              </select>
            </label>
            <label class="admin-field"><span>Assignee</span><input type="text" name="assignee"/></label>
          </div>
          <label class="admin-field"><span><input type="checkbox" name="escalated" value="true"/> Escalated</span></label>
          <label class="admin-field"><span>Initial note</span><textarea name="initial_note" rows="3"></textarea></label>
          <button type="submit">Create case</button>
        </form>
      </section>
      <section class="admin-card">
        {_admin_section_header("Issue Details", str(current.get('id') or 'Select an issue from the list to edit it or add notes.'), _admin_new_link("/admin/ops/support-cases", "Clear selection"))}
        <form method="post" action="/admin/ops/support-cases/{html.escape(str(current.get('id') or ''), quote=True)}/update">
          <input type="hidden" name="csrf_token" value="{html.escape(update_csrf, quote=True)}"/>
          <label class="admin-field"><span>Summary</span><textarea name="summary" rows="4" {'required' if current.get('id') else 'disabled'}>{html.escape(str(current.get('summary') or ''), quote=True)}</textarea></label>
          <div class="admin-field-grid">
            <label class="admin-field"><span>Status</span>
              <select name="status" {'required' if current.get('id') else 'disabled'}>
                {current_status_options}
              </select>
            </label>
            <label class="admin-field"><span>Priority</span>
              <select name="priority" {'required' if current.get('id') else 'disabled'}>
                {current_priority_options}
              </select>
            </label>
          </div>
          <label class="admin-field"><span>Assignee</span><input type="text" name="assignee" value="{_admin_html_attr(current.get('assignee'))}" {'disabled' if not current.get('id') else ''}/></label>
          <label class="admin-field"><span>Escalated</span>
            <select name="escalated" {'disabled' if not current.get('id') else ''}>
              <option value="false" {'selected' if escalated_selected == 'false' else ''}>No</option>
              <option value="true" {'selected' if escalated_selected == 'true' else ''}>Yes</option>
            </select>
          </label>
          <label class="admin-field"><span><input type="checkbox" name="clear_assignee" value="true" {'disabled' if not current.get('id') else ''}/> Clear assignee</span></label>
          <button type="submit" {'disabled' if not current.get('id') else ''}>Update case</button>
        </form>
        <hr style="margin:18px 0;border:none;border-top:1px solid #e8efec;"/>
        <form method="post" action="/admin/ops/support-cases/{html.escape(str(current.get('id') or ''), quote=True)}/notes">
          <input type="hidden" name="csrf_token" value="{html.escape(note_csrf, quote=True)}"/>
          <label class="admin-field"><span>Add note</span><textarea name="message" rows="3" {'required' if current.get('id') else 'disabled'}></textarea></label>
          <button type="submit" {'disabled' if not current.get('id') else ''}>Add note</button>
        </form>
        <div style="margin-top:16px;">
          <h3 style="margin:0 0 8px;">Recent Notes</h3>
          <ul class="admin-note-list">{notes_html}</ul>
        </div>
      </section>
    </div>
    """
    return _admin_ops_layout("User Issues", principal, body_html, active="support-cases")


@app.post("/admin/ops/support-cases/create")
def admin_ops_support_case_create(
    csrf_token: str = Form(...),
    user_uid: str = Form(...),
    related_job_id: str = Form(default=""),
    summary: str = Form(...),
    priority: str = Form(default="normal"),
    assignee: str = Form(default=""),
    escalated: str | None = Form(default=None),
    initial_note: str = Form(default=""),
    principal: Any = Depends(require_ops_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "ops-support-case-create")
    payload = AdminSupportCaseCreateRequest(
        userUid=str(user_uid or "").strip(),
        relatedJobId=str(related_job_id or "").strip() or None,
        summary=str(summary or "").strip(),
        priority=str(priority or "").strip() or "normal",
        assignee=str(assignee or "").strip() or None,
        escalated=escalated is not None,
        initialNote=str(initial_note or "").strip() or None,
    )
    try:
        created = ops_create_support_case(payload, principal=principal)
        case_id = getattr(created, "id", None) or (created.model_dump() if hasattr(created, "model_dump") else {}).get("id")
        return _admin_redirect("/admin/ops/support-cases", edit_case_id=case_id, notice="support_case_created")
    except HTTPException as exc:
        return _admin_redirect("/admin/ops/support-cases", error=str(exc.detail or "Support case create failed"))
    except Exception as exc:
        traceback.print_exc()
        return _admin_redirect("/admin/ops/support-cases", error=f"Support case create failed ({type(exc).__name__})")


@app.post("/admin/ops/support-cases/{case_id}/update")
def admin_ops_support_case_update(
    case_id: str,
    csrf_token: str = Form(...),
    summary: str = Form(...),
    status: str = Form(...),
    priority: str = Form(...),
    assignee: str = Form(default=""),
    escalated: str = Form(default="false"),
    clear_assignee: str | None = Form(default=None),
    principal: Any = Depends(require_ops_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "ops-support-case-update")
    payload = AdminSupportCaseUpdateRequest(
        summary=str(summary or "").strip() or None,
        status=str(status or "").strip() or None,
        priority=str(priority or "").strip() or None,
        assignee=str(assignee or "").strip() or None,
        clearAssignee=clear_assignee is not None,
        escalated=str(escalated).lower() == "true",
    )
    try:
        ops_update_support_case(case_id, payload, principal=principal)
        return _admin_redirect("/admin/ops/support-cases", edit_case_id=case_id, notice="support_case_updated")
    except HTTPException as exc:
        return _admin_redirect("/admin/ops/support-cases", edit_case_id=case_id, error=str(exc.detail or "Support case update failed"))
    except Exception as exc:
        traceback.print_exc()
        return _admin_redirect("/admin/ops/support-cases", edit_case_id=case_id, error=f"Support case update failed ({type(exc).__name__})")


@app.post("/admin/ops/support-cases/{case_id}/notes")
def admin_ops_support_case_note(
    case_id: str,
    csrf_token: str = Form(...),
    message: str = Form(...),
    principal: Any = Depends(require_ops_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "ops-support-case-note")
    try:
        ops_add_support_case_note(case_id, AdminSupportCaseNoteRequest(message=str(message or "").strip()), principal=principal)
        return _admin_redirect("/admin/ops/support-cases", edit_case_id=case_id, notice="support_case_noted")
    except HTTPException as exc:
        return _admin_redirect("/admin/ops/support-cases", edit_case_id=case_id, error=str(exc.detail or "Support case note failed"))
    except Exception as exc:
        traceback.print_exc()
        return _admin_redirect("/admin/ops/support-cases", edit_case_id=case_id, error=f"Support case note failed ({type(exc).__name__})")


@app.get("/admin/ops/admin-sessions", response_class=HTMLResponse)
def admin_ops_admin_sessions_page(
    uid: str | None = None,
    active_only: str | None = "true",
    notice: str | None = None,
    error: str | None = None,
    limit: int = 50,
    principal: Any = Depends(require_ops_admin),
):
    uid_filter = str(uid or "").strip()
    active_only_flag = str(active_only or "true").lower() != "false"
    items, list_error = _admin_safe_load(
        "Admin sessions",
        lambda: database.list_admin_sessions(uid=uid_filter or None, active_only=active_only_flag, limit=limit),
        [],
    )
    if not error:
        error = list_error or None
    revoke_csrf = _build_admin_csrf_token(principal, "ops-admin-session-revoke")
    cleanup_csrf = _build_admin_csrf_token(principal, "ops-admin-session-cleanup")

    rows = []
    for item in items:
        revoke_form = ""
        if item.get("revokedAt") is None:
            revoke_form = (
                f"<form method='post' action='/admin/ops/admin-sessions/{html.escape(str(item.get('id') or ''), quote=True)}/revoke' style='display:inline;' {_admin_danger_confirm('Revoke this admin session now?')}>"
                f"<input type='hidden' name='csrf_token' value='{html.escape(revoke_csrf, quote=True)}'/>"
                f"<input type='hidden' name='uid' value='{html.escape(uid_filter, quote=True)}'/>"
                f"<input type='hidden' name='active_only' value='{str(active_only_flag).lower()}'/>"
                "<input type='hidden' name='reason' value='manual_review'/>"
                "<button type='submit' class='admin-danger-button'>Revoke</button>"
                "</form>"
            )
        rows.append(
            "<tr>"
            f"<td>{html.escape(str(item.get('actor') or ''), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('uid') or ''), quote=True)}</td>"
            f"<td>{html.escape(', '.join(item.get('roles') or []), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('authType') or ''), quote=True)}</td>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('createdAt')), quote=True)}</td>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('expiresAt')), quote=True)}</td>"
            f"<td>{_admin_badge('Active', 'ok') if item.get('revokedAt') is None else _admin_badge('Revoked', 'danger')}</td>"
            f"<td>{revoke_form or '—'}</td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(8, "No admin sessions match the current filter.")

    body_html = f"""
    {_admin_notice_html(notice, error)}
    <div class="admin-page-grid">
      <section class="admin-card">
        {_admin_section_header("Clean Up Old Sign-ins", "Remove old expired or revoked admin sign-in records.")}
        <form method="get" action="/admin/ops/admin-sessions" class="admin-toolbar">
          <input type="text" name="uid" value="{html.escape(uid_filter, quote=True)}" placeholder="Filter by user ID"/>
          <select name="active_only">
            <option value="true" {'selected' if active_only_flag else ''}>Active only</option>
            <option value="false" {'selected' if not active_only_flag else ''}>Include revoked</option>
          </select>
          <button type="submit">Filter</button>
          <a href="/admin/ops/admin-sessions" class="admin-link-button">Clear</a>
        </form>
        <form method="post" action="/admin/ops/admin-sessions/cleanup">
          <input type="hidden" name="csrf_token" value="{html.escape(cleanup_csrf, quote=True)}"/>
          <label class="admin-field"><span>Retention days</span><input type="number" name="retention_days" value="30" min="1"/></label>
          <label class="admin-field"><span><input type="checkbox" name="include_revoked" value="true" checked/> Include revoked</span></label>
          <label class="admin-field"><span><input type="checkbox" name="include_expired" value="true" checked/> Include expired</span></label>
          <button type="submit">Run cleanup</button>
        </form>
      </section>
      <section class="admin-card">
        {_admin_section_header("Signed-in Admins", "Review active admin sign-ins and revoke anything suspicious.")}
        <table>
          <thead><tr><th>Admin</th><th>User ID</th><th>Role</th><th>Sign-in</th><th>Created</th><th>Expires</th><th>State</th><th>Actions</th></tr></thead>
          <tbody>{rows_html}</tbody>
        </table>
      </section>
    </div>
    """
    return _admin_ops_layout("Admin Sign-ins", principal, body_html, active="admin-sessions")


@app.post("/admin/ops/admin-sessions/{session_id}/revoke")
def admin_ops_revoke_admin_session(
    session_id: str,
    csrf_token: str = Form(...),
    uid: str | None = Form(default=None),
    active_only: str | None = Form(default="true"),
    reason: str | None = Form(default=None),
    principal: Any = Depends(require_ops_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "ops-admin-session-revoke")
    try:
        ops_revoke_admin_session(session_id, AdminSessionRevokeRequest(reason=str(reason or "").strip() or None), principal=principal)
        return _admin_redirect("/admin/ops/admin-sessions", uid=uid, active_only=active_only, notice="admin_session_revoked")
    except HTTPException as exc:
        return _admin_redirect("/admin/ops/admin-sessions", uid=uid, active_only=active_only, error=str(exc.detail or "Admin session revoke failed"))
    except Exception as exc:
        traceback.print_exc()
        return _admin_redirect("/admin/ops/admin-sessions", uid=uid, active_only=active_only, error=f"Admin session revoke failed ({type(exc).__name__})")


@app.post("/admin/ops/admin-sessions/cleanup")
def admin_ops_cleanup_admin_sessions(
    csrf_token: str = Form(...),
    retention_days: int = Form(default=30),
    include_revoked: str | None = Form(default=None),
    include_expired: str | None = Form(default=None),
    principal: Any = Depends(require_ops_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "ops-admin-session-cleanup")
    try:
        ops_cleanup_admin_sessions(
            AdminSessionCleanupRequest(
                retentionDays=max(1, int(retention_days or 1)),
                includeRevoked=include_revoked is not None,
                includeExpired=include_expired is not None,
            ),
            principal=principal,
        )
        return _admin_redirect("/admin/ops/admin-sessions", active_only="false", notice="admin_sessions_cleaned")
    except (HTTPException, ValueError) as exc:
        detail = exc.detail if isinstance(exc, HTTPException) else str(exc)
        return _admin_redirect("/admin/ops/admin-sessions", active_only="false", error=str(detail or "Admin sessions cleanup failed"))
    except Exception as exc:
        traceback.print_exc()
        return _admin_redirect("/admin/ops/admin-sessions", active_only="false", error=f"Admin sessions cleanup failed ({type(exc).__name__})")


@app.get("/admin/ops/operator-access", response_class=HTMLResponse)
def admin_ops_operator_access_page(
    blocked_only: str | None = "false",
    edit_uid: str | None = None,
    notice: str | None = None,
    error: str | None = None,
    limit: int = 50,
    principal: Any = Depends(require_ops_admin),
):
    blocked_only_flag = str(blocked_only or "false").lower() == "true"
    edit_token = str(edit_uid or "").strip()
    items, list_error = _admin_safe_load(
        "Operator access overrides",
        lambda: database.list_operator_access_overrides(blocked_only=blocked_only_flag, limit=limit),
        [],
    )
    edit_item, edit_error = _admin_safe_load(
        "Selected admin access rule",
        lambda: database.get_operator_access_override(edit_token),
        None,
    ) if edit_token else (None, "")
    if edit_token and not edit_item and not error:
        error = f"Operator access override not found: {edit_token}"
    if not error:
        error = list_error or edit_error or None

    save_csrf = _build_admin_csrf_token(principal, "ops-operator-access-save")
    current = edit_item or {"uid": "", "email": "", "blocked": True, "reason": ""}
    blocked_selected = "true" if current.get("blocked", True) else "false"

    rows = []
    for item in items:
        edit_link = f"/admin/ops/operator-access?{urlencode({'edit_uid': item.get('uid'), 'blocked_only': str(blocked_only_flag).lower()})}"
        rows.append(
            "<tr>"
            f"<td><span class='admin-id'>{html.escape(str(item.get('uid') or ''), quote=True)}</span></td>"
            f"<td>{html.escape(str(item.get('email') or ''), quote=True)}</td>"
            f"<td>{_admin_badge('Blocked' if item.get('blocked') else 'Allowed', 'danger' if item.get('blocked') else 'ok')}</td>"
            f"<td>{html.escape(str(item.get('reason') or ''), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('updatedBy') or ''), quote=True)}</td>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('updatedAt')), quote=True)}</td>"
            f"<td><a href='{edit_link}' class='admin-link-button'>Edit</a></td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(7, "No admin access rules match the current filter.")

    body_html = f"""
    {_admin_notice_html(notice, error)}
    <div class="admin-page-grid">
      <section class="admin-card">
        {_admin_section_header("Block or Restore Admin", "Control admin access without changing code.", _admin_new_link("/admin/ops/operator-access", "New rule"))}
        <form method="post" action="/admin/ops/operator-access/save">
          <input type="hidden" name="csrf_token" value="{html.escape(save_csrf, quote=True)}"/>
          <label class="admin-field"><span>Admin user ID</span><input type="text" name="uid" value="{_admin_html_attr(current.get('uid'))}" required/></label>
          <label class="admin-field"><span>Email</span><input type="text" name="email" value="{_admin_html_attr(current.get('email'))}"/></label>
          <label class="admin-field"><span>Access state</span>
            <select name="blocked">
              <option value="true" {'selected' if blocked_selected == 'true' else ''}>Blocked</option>
              <option value="false" {'selected' if blocked_selected == 'false' else ''}>Allowed</option>
            </select>
          </label>
          <label class="admin-field"><span>Reason</span><textarea name="reason" rows="4">{html.escape(str(current.get('reason') or ''), quote=True)}</textarea></label>
          <label class="admin-field"><span><input type="checkbox" name="revoke_active_sessions" value="true" checked/> Revoke active sessions when blocking</span></label>
          <button type="submit">Save access</button>
        </form>
      </section>
      <section class="admin-card">
        {_admin_section_header("Saved Access Rules", "Review current allow/block decisions for admin accounts.")}
        <form method="get" action="/admin/ops/operator-access" class="admin-toolbar">
          <select name="blocked_only">
            <option value="false" {'selected' if not blocked_only_flag else ''}>All rules</option>
            <option value="true" {'selected' if blocked_only_flag else ''}>Blocked only</option>
          </select>
          <button type="submit">Filter</button>
          <a href="/admin/ops/operator-access" class="admin-link-button">Clear</a>
        </form>
        <table>
          <thead><tr><th>User ID</th><th>Email</th><th>State</th><th>Reason</th><th>Updated by</th><th>Updated</th><th>Actions</th></tr></thead>
          <tbody>{rows_html}</tbody>
        </table>
      </section>
    </div>
    """
    return _admin_ops_layout("Admin Access", principal, body_html, active="operator-access")


@app.post("/admin/ops/operator-access/save")
def admin_ops_operator_access_save(
    csrf_token: str = Form(...),
    uid: str = Form(...),
    email: str = Form(default=""),
    blocked: str = Form(default="true"),
    reason: str = Form(default=""),
    revoke_active_sessions: str | None = Form(default=None),
    principal: Any = Depends(require_ops_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "ops-operator-access-save")
    try:
        ops_upsert_operator_access_override(
            str(uid or "").strip(),
            OperatorAccessOverrideUpsertRequest(
                email=str(email or "").strip() or None,
                blocked=str(blocked).lower() == "true",
                reason=str(reason or "").strip() or None,
                revokeActiveSessions=revoke_active_sessions is not None,
            ),
            principal=principal,
        )
        return _admin_redirect("/admin/ops/operator-access", edit_uid=uid, notice="operator_access_saved")
    except HTTPException as exc:
        return _admin_redirect("/admin/ops/operator-access", edit_uid=uid, error=str(exc.detail or "Operator access save failed"))
    except Exception as exc:
        traceback.print_exc()
        return _admin_redirect("/admin/ops/operator-access", edit_uid=uid, error=f"Operator access save failed ({type(exc).__name__})")


@app.get("/admin/ops/audit-logs", response_class=HTMLResponse)
def admin_ops_audit_logs_page(
    resource_type: str | None = None,
    action: str | None = None,
    resource_id: str | None = None,
    actor: str | None = None,
    limit: int = 100,
    error: str | None = None,
    principal: Any = Depends(require_ops_admin),
):
    resource_type_filter = str(resource_type or "").strip()
    action_filter = str(action or "").strip()
    resource_id_filter = str(resource_id or "").strip()
    actor_filter = str(actor or "").strip()
    limit_value = max(10, min(int(limit or 100), 500))
    items, list_error = _admin_safe_load(
        "Change history",
        lambda: database.list_admin_action_logs(
            limit=limit_value,
            resource_type=resource_type_filter or None,
            action=action_filter or None,
            resource_id=resource_id_filter or None,
            actor=actor_filter or None,
        ),
        [],
    )
    if not error:
        error = list_error or None

    rows = []
    for item in items:
        details = item.get("details")
        details_text = json.dumps(details, indent=2, sort_keys=True) if isinstance(details, dict) else str(details or "")
        resource_label = str(item.get("resource_type") or "")
        rows.append(
            "<tr>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('created_at')), quote=True)}</td>"
            f"<td>{_admin_badge(str(item.get('action') or 'event'))}</td>"
            f"<td>{html.escape(str(item.get('actor') or '—'), quote=True)}</td>"
            f"<td>{html.escape(resource_label or '—', quote=True)}</td>"
            f"<td><span class='admin-id'>{html.escape(str(item.get('resource_id') or '—'), quote=True)}</span></td>"
            "<td>"
            "<details>"
            "<summary>View details</summary>"
            f"<pre class='admin-json-preview'>{html.escape(details_text, quote=True)}</pre>"
            "</details>"
            "</td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(6, "No changes match the current filters.")

    body_html = f"""
    {_admin_notice_html(None, error)}
    <section class="admin-card" style="margin-bottom:18px;">
      {_admin_section_header("Find Changes", "Search recent admin activity by area, action, item, or admin account.")}
      <form method="get" action="/admin/ops/audit-logs" class="admin-toolbar">
        <input type="text" name="resource_type" value="{html.escape(resource_type_filter, quote=True)}" placeholder="Area"/>
        <input type="text" name="action" value="{html.escape(action_filter, quote=True)}" placeholder="Action"/>
        <input type="text" name="resource_id" value="{html.escape(resource_id_filter, quote=True)}" placeholder="Item ID"/>
        <input type="text" name="actor" value="{html.escape(actor_filter, quote=True)}" placeholder="Admin"/>
        <select name="limit">
          <option value="50" {'selected' if limit_value == 50 else ''}>50 events</option>
          <option value="100" {'selected' if limit_value == 100 else ''}>100 events</option>
          <option value="250" {'selected' if limit_value == 250 else ''}>250 events</option>
          <option value="500" {'selected' if limit_value == 500 else ''}>500 events</option>
        </select>
        <button type="submit">Filter</button>
        <a href="/admin/ops/audit-logs" class="admin-link-button">Clear</a>
      </form>
    </section>
    <section class="admin-card">
      {_admin_section_header("Saved Changes", "A readable history of recent admin actions.")}
      <table>
        <thead><tr><th>Time</th><th>Action</th><th>Admin</th><th>Area</th><th>Item ID</th><th>Details</th></tr></thead>
        <tbody>{rows_html}</tbody>
      </table>
    </section>
    """
    return _admin_ops_layout("Change History", principal, body_html, active="audit-logs")


@app.get("/admin/recipes")
def admin_list_recipes(
    q: str | None = None,
    meal_type: str | None = None,
    limit: int = 100,
    _: Any = Depends(require_content_admin),
):
    items = database.list_admin_recipes(q=q, meal_type=meal_type, limit=limit)
    return {"items": items, "count": len(items)}


@app.get("/admin/recipes/status")
def admin_recipe_catalog_status(_: Any = Depends(require_content_admin)):
    return database.get_recipe_catalog_status()


@app.post("/admin/recipes/seed")
def admin_seed_recipes(force: bool = False, principal: Any = Depends(require_content_admin)):
    summary = database.seed_recipes(force_reseed=force)
    _invalidate_plan_cache()
    database.log_admin_action(
        "recipe.seed",
        actor=str(principal.get("actor") or "admin"),
        resource_type="recipe_catalog",
        resource_id=str(summary.get("sourcePath") or "recipes.json"),
        details=summary,
    )
    return {"status": "ok", **summary}


@app.get("/admin/recipes/{recipe_id}", response_model=RecipeDetail)
def admin_get_recipe(recipe_id: str, _: Any = Depends(require_content_admin)):
    recipe = database.get_recipe_by_id(recipe_id)
    if not recipe:
        raise HTTPException(status_code=404, detail="Recipe not found")
    return RecipeDetail(**recipe)


@app.post("/admin/recipes", response_model=RecipeDetail)
def admin_create_recipe(payload: AdminRecipeUpsertRequest, principal: Any = Depends(require_content_admin)):
    if payload.id and database.get_recipe_by_id(payload.id):
        raise HTTPException(status_code=409, detail="Recipe already exists")
    saved = database.upsert_recipe(payload.model_dump())
    _invalidate_plan_cache()
    database.log_admin_action(
        "recipe.create",
        actor=str(principal.get("actor") or "admin"),
        resource_type="recipe",
        resource_id=saved.get("id"),
        details={"title": saved.get("title"), "mealType": saved.get("mealType")},
    )
    return RecipeDetail(**saved)


@app.put("/admin/recipes/{recipe_id}", response_model=RecipeDetail)
def admin_update_recipe(recipe_id: str, payload: AdminRecipeUpsertRequest, principal: Any = Depends(require_content_admin)):
    existing = database.get_recipe_by_id(recipe_id)
    if not existing:
        raise HTTPException(status_code=404, detail="Recipe not found")
    merged = payload.model_dump()
    merged["id"] = recipe_id
    saved = database.upsert_recipe(merged)
    _invalidate_plan_cache()
    database.log_admin_action(
        "recipe.update",
        actor=str(principal.get("actor") or "admin"),
        resource_type="recipe",
        resource_id=recipe_id,
        details={"title": saved.get("title"), "mealType": saved.get("mealType")},
    )
    return RecipeDetail(**saved)


@app.delete("/admin/recipes/{recipe_id}")
def admin_delete_recipe(recipe_id: str, principal: Any = Depends(require_content_admin)):
    deleted = database.delete_recipe(recipe_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Recipe not found")
    _invalidate_plan_cache()
    database.log_admin_action(
        "recipe.delete",
        actor=str(principal.get("actor") or "admin"),
        resource_type="recipe",
        resource_id=recipe_id,
        details={},
    )
    return {"status": "ok", "deleted": deleted, "id": recipe_id}


@app.get("/admin/price-rules")
def admin_list_price_rules(
    q: str | None = None,
    category: str | None = None,
    limit: int = 100,
    _: Any = Depends(require_content_admin),
):
    items = database.list_admin_price_rules(q=q, category=category, limit=limit)
    return {"items": items, "count": len(items)}


@app.get("/admin/price-rules/{rule_id}", response_model=AdminPriceRule)
def admin_get_price_rule(rule_id: str, _: Any = Depends(require_content_admin)):
    rule = database.get_price_rule_by_id(rule_id)
    if not rule:
        raise HTTPException(status_code=404, detail="Price rule not found")
    return AdminPriceRule(**rule)


@app.post("/admin/price-rules", response_model=AdminPriceRule)
def admin_create_price_rule(payload: AdminPriceRuleUpsertRequest, principal: Any = Depends(require_content_admin)):
    if payload.id and database.get_price_rule_by_id(payload.id):
        raise HTTPException(status_code=409, detail="Price rule already exists")
    saved = database.upsert_price_rule(payload.model_dump())
    invalidate_price_rule_cache()
    _invalidate_plan_cache()
    database.log_admin_action(
        "price_rule.create",
        actor=str(principal.get("actor") or "admin"),
        resource_type="ingredient_price_rule",
        resource_id=saved.get("id"),
        details={
            "keywords": saved.get("keywords"),
            "category": saved.get("category"),
            "pricePhp": saved.get("pricePhp"),
            "priceMinPhp": saved.get("priceMinPhp"),
            "priceMaxPhp": saved.get("priceMaxPhp"),
        },
    )
    return AdminPriceRule(**saved)


@app.put("/admin/price-rules/{rule_id}", response_model=AdminPriceRule)
def admin_update_price_rule(
    rule_id: str,
    payload: AdminPriceRuleUpsertRequest,
    principal: Any = Depends(require_content_admin),
):
    existing = database.get_price_rule_by_id(rule_id)
    if not existing:
        raise HTTPException(status_code=404, detail="Price rule not found")
    merged = payload.model_dump()
    merged["id"] = rule_id
    saved = database.upsert_price_rule(merged)
    invalidate_price_rule_cache()
    _invalidate_plan_cache()
    database.log_admin_action(
        "price_rule.update",
        actor=str(principal.get("actor") or "admin"),
        resource_type="ingredient_price_rule",
        resource_id=rule_id,
        details={
            "keywords": saved.get("keywords"),
            "category": saved.get("category"),
            "pricePhp": saved.get("pricePhp"),
            "priceMinPhp": saved.get("priceMinPhp"),
            "priceMaxPhp": saved.get("priceMaxPhp"),
        },
    )
    return AdminPriceRule(**saved)


@app.delete("/admin/price-rules/{rule_id}")
def admin_delete_price_rule(rule_id: str, principal: Any = Depends(require_content_admin)):
    deleted = database.delete_price_rule(rule_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Price rule not found")
    invalidate_price_rule_cache()
    _invalidate_plan_cache()
    database.log_admin_action(
        "price_rule.delete",
        actor=str(principal.get("actor") or "admin"),
        resource_type="ingredient_price_rule",
        resource_id=rule_id,
        details={},
    )
    return {"status": "ok", "deleted": deleted, "id": rule_id}


@app.get("/admin/nutrition-corrections")
def admin_list_nutrition_corrections(
    q: str | None = None,
    limit: int = 100,
    _: Any = Depends(require_content_admin),
):
    items = database.list_admin_nutrition_corrections(q=q, limit=limit)
    return {"items": items, "count": len(items)}


@app.get("/admin/nutrition-corrections/{recipe_id}", response_model=AdminNutritionCorrection)
def admin_get_nutrition_correction(recipe_id: str, _: Any = Depends(require_content_admin)):
    correction = database.get_nutrition_correction_by_recipe_id(recipe_id)
    if not correction:
        raise HTTPException(status_code=404, detail="Nutrition correction not found")
    return AdminNutritionCorrection(**correction)


@app.put("/admin/nutrition-corrections/{recipe_id}", response_model=AdminNutritionCorrection)
def admin_upsert_nutrition_correction(
    recipe_id: str,
    payload: AdminNutritionCorrectionUpsertRequest,
    principal: Any = Depends(require_content_admin),
):
    recipe = database.get_recipe_by_id(recipe_id)
    if not recipe:
        raise HTTPException(status_code=404, detail="Recipe not found")
    saved = database.upsert_nutrition_correction(recipe_id, payload.model_dump())
    _invalidate_plan_cache()
    database.log_admin_action(
        "nutrition_correction.upsert",
        actor=str(principal.get("actor") or "admin"),
        resource_type="recipe_nutrition_correction",
        resource_id=recipe_id,
        details={
            "recipeTitle": recipe.get("title"),
            "fields": [key for key, value in payload.model_dump().items() if value is not None],
            "active": saved.get("active"),
        },
    )
    return AdminNutritionCorrection(**saved)


@app.delete("/admin/nutrition-corrections/{recipe_id}")
def admin_delete_nutrition_correction(recipe_id: str, principal: Any = Depends(require_content_admin)):
    deleted = database.delete_nutrition_correction(recipe_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Nutrition correction not found")
    _invalidate_plan_cache()
    database.log_admin_action(
        "nutrition_correction.delete",
        actor=str(principal.get("actor") or "admin"),
        resource_type="recipe_nutrition_correction",
        resource_id=recipe_id,
        details={},
    )
    return {"status": "ok", "deleted": deleted, "recipeId": recipe_id}


@app.get("/admin/audit/logs")
def admin_audit_logs(
    limit: int = 100,
    resource_type: str | None = None,
    action: str | None = None,
    resource_id: str | None = None,
    actor: str | None = None,
    _: Any = Depends(require_ops_admin),
):
    items = database.list_admin_action_logs(
        limit=limit,
        resource_type=resource_type,
        action=action,
        resource_id=resource_id,
        actor=actor,
    )
    return {"items": items, "count": len(items)}


@app.get("/admin/policy/active")
def admin_get_active_policy(_: Any = Depends(require_policy_admin)):
    active = policy_store.get_active_policy()
    if not active:
        active = policy_store.ensure_default_policy(actor="admin-bootstrap")
    return {
        "active": active,
        "schemaVersion": POLICY_SCHEMA_VERSION,
    }


@app.get("/admin/policy", response_class=HTMLResponse)
def admin_policy_console(
    source_policy_id: str | None = None,
    notice: str | None = None,
    error: str | None = None,
    principal: Any = Depends(require_policy_admin),
):
    active = policy_store.get_active_policy()
    if not active:
        active = policy_store.ensure_default_policy(actor="admin-bootstrap")
        _load_runtime_policy(force_refresh=True)
    versions = policy_store.list_policy_versions(limit=25)
    audit_rows = policy_store.list_policy_audit(limit=25)
    source_token = str(source_policy_id or "").strip()
    source_policy = policy_store.get_policy(source_token) if source_token else active
    if source_token and not source_policy and not error:
        error = f"Policy version not found: {source_token}"
    source_policy = source_policy or active

    create_csrf = _build_admin_csrf_token(principal, "policy-create")
    activate_csrf = _build_admin_csrf_token(principal, "policy-activate")
    rollback_csrf = _build_admin_csrf_token(principal, "policy-rollback")

    active_policy_payload = active.get("policy") or {}
    source_policy_payload = source_policy.get("policy") or {}
    editor_json = json.dumps(source_policy_payload, indent=2, sort_keys=True)
    source_label = f"Loaded from version #{source_policy.get('version_number')}" if source_policy.get("version_number") else "Loaded from active policy"
    rollback_options = []
    for item in versions:
        if item.get("id") == active.get("id"):
            continue
        policy_name = str((item.get("policy") or {}).get("policy_name") or "unnamed-policy")
        rollback_options.append(
            f"<option value='{html.escape(str(item.get('id') or ''), quote=True)}'>"
            f"#{int(item.get('version_number') or 0)} • {html.escape(policy_name, quote=True)}</option>"
        )
    rollback_options_html = "".join(rollback_options)

    version_rows = []
    for item in versions:
        item_id = str(item.get("id") or "")
        item_policy = item.get("policy") or {}
        policy_name = str(item_policy.get("policy_name") or "unnamed-policy")
        load_link = f"/admin/policy?{urlencode({'source_policy_id': item_id})}"
        activate_action = "Active"
        if not item.get("is_active"):
            activate_action = (
                f"<form method='post' action='/admin/policy/activate-form'>"
                f"<input type='hidden' name='csrf_token' value='{html.escape(activate_csrf, quote=True)}'/>"
                f"<input type='hidden' name='policy_id' value='{html.escape(item_id, quote=True)}'/>"
                "<input type='hidden' name='notes' value='Activated from policy console'/>"
                "<button type='submit'>Activate</button>"
                "</form>"
            )
        else:
            activate_action = _admin_badge("Active", "ok")
        version_rows.append(
            "<tr>"
            f"<td>#{int(item.get('version_number') or 0)}</td>"
            f"<td>{html.escape(policy_name, quote=True)}</td>"
            f"<td>{html.escape(str(item.get('created_by') or ''), quote=True)}</td>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('created_at')), quote=True)}</td>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('activated_at')), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('notes') or ''), quote=True)}</td>"
            f"<td>{_admin_badge('Active' if item.get('is_active') else 'Stored', 'ok' if item.get('is_active') else '')}</td>"
            f"<td><div class='admin-actions'><a href='{load_link}' class='admin-link-button'>Load into editor</a>{activate_action}</div></td>"
            "</tr>"
        )
    version_rows_html = "\n".join(version_rows) if version_rows else _admin_empty_row(8, "No policy versions available.")

    audit_items = []
    for item in audit_rows:
        details = item.get("details")
        details_text = json.dumps(details, sort_keys=True) if isinstance(details, dict) else str(details or "")
        audit_items.append(
            "<tr>"
            f"<td>{html.escape(str(item.get('action') or ''), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('actor') or ''), quote=True)}</td>"
            f"<td>{html.escape(str(item.get('policy_id') or ''), quote=True)}</td>"
            f"<td>{html.escape(_admin_format_epoch_ms(item.get('created_at')), quote=True)}</td>"
            f"<td style='word-break:break-word;'>{html.escape(details_text, quote=True)}</td>"
            "</tr>"
        )
    audit_rows_html = "\n".join(audit_items) if audit_items else _admin_empty_row(5, "No policy audit events yet.")

    body_html = f"""
    {_admin_notice_html(notice, error)}
    <div class="admin-page-grid">
      <section class="admin-card">
        {_admin_section_header("Active Policy", "This is the runtime planner policy currently used by the backend.")}
        <div class="admin-section-stack" style="gap:12px;">
          <div>{_admin_badge(f"Version #{int(active.get('version_number') or 0)}", "ok")}<p class="admin-copy">Current active version</p></div>
          <div><strong>Policy name</strong><br/>{html.escape(str(active_policy_payload.get('policy_name') or 'unnamed-policy'), quote=True)}</div>
          <div><strong>Schema</strong><br/>{html.escape(str(active.get('schema_version') or POLICY_SCHEMA_VERSION), quote=True)}</div>
          <div><strong>Created by</strong><br/>{html.escape(str(active.get('created_by') or ''), quote=True)}</div>
          <div><strong>Activated</strong><br/>{html.escape(_admin_format_epoch_ms(active.get('activated_at')), quote=True)}</div>
          <div><strong>Notes</strong><br/>{html.escape(str(active.get('notes') or '—'), quote=True)}</div>
          <div><strong>Policy hash</strong><br/><code class="admin-id">{html.escape(str(active.get('policy_hash') or ''), quote=True)}</code></div>
        </div>
        <hr style="margin:18px 0;border:none;border-top:1px solid #e8efec;"/>
        {_admin_section_header("Rollback", "Use rollback only when the active policy has caused planner behavior that must be reversed.")}
        <form method="post" action="/admin/policy/rollback-form" {_admin_danger_confirm('Rollback the active planner policy? This changes runtime planner behavior.')}>
          <input type="hidden" name="csrf_token" value="{html.escape(rollback_csrf, quote=True)}"/>
          <label class="admin-field"><span>Rollback target</span>
            <select name="target_policy_id">
              <option value="">Latest previous version</option>
              {rollback_options_html}
            </select>
          </label>
          <label class="admin-field"><span>Notes</span><input type="text" name="notes" value="Rollback from policy console"/></label>
          <button type="submit" class="admin-danger-button">Rollback</button>
        </form>
      </section>
      <section class="admin-section-stack">
        <section class="admin-card">
          {_admin_section_header("Create Policy Version", f"{source_label}. Saving creates a new immutable version; activation remains explicit unless selected below.", _admin_new_link("/admin/policy", "Reset editor"))}
          <form method="post" action="/admin/policy/create">
            <input type="hidden" name="csrf_token" value="{html.escape(create_csrf, quote=True)}"/>
            <label class="admin-field"><span>Notes</span><input type="text" name="notes" value="{html.escape(str(source_policy.get('notes') or ''), quote=True)}"/></label>
            <label class="admin-field"><span>Policy JSON</span><textarea name="policy_json" rows="20" class="admin-json-editor" required>{html.escape(editor_json, quote=True)}</textarea></label>
            <label class="admin-field"><span><input type="checkbox" name="activate" value="true"/> Activate immediately after save</span></label>
            <button type="submit">Save policy version</button>
          </form>
        </section>
        <section class="admin-card">
          {_admin_section_header("Policy Version History", "Load past versions into the editor or activate a reviewed version.")}
          <table>
            <thead><tr><th>Version</th><th>Name</th><th>Created by</th><th>Created</th><th>Activated</th><th>Notes</th><th>State</th><th>Actions</th></tr></thead>
            <tbody>{version_rows_html}</tbody>
          </table>
        </section>
        <section class="admin-card">
          {_admin_section_header("Recent Policy Audit", "Trace policy changes and activation events.")}
          <table>
            <thead><tr><th>Action</th><th>Actor</th><th>Policy</th><th>Created</th><th>Details</th></tr></thead>
            <tbody>{audit_rows_html}</tbody>
          </table>
        </section>
      </section>
    </div>
    """
    return _admin_policy_layout("Policy Console", principal, body_html)


@app.post("/admin/policy/create")
def admin_policy_console_create(
    csrf_token: str = Form(...),
    policy_json: str = Form(...),
    notes: str = Form(default=""),
    activate: str | None = Form(default=None),
    principal: Any = Depends(require_policy_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "policy-create")
    try:
        parsed = json.loads(str(policy_json or "").strip())
    except Exception as exc:
        return _admin_redirect("/admin/policy", error=f"Invalid policy JSON: {exc}")
    try:
        result = admin_create_policy_version(
            PolicyCreateRequest(
                policy=parsed,
                notes=str(notes or "").strip() or None,
                activate=activate is not None,
            ),
            principal=principal,
        )
        created = result.get("created") if isinstance(result, dict) else None
        created_id = str((created or {}).get("id") or "").strip()
        status = "policy_saved_and_activated" if activate is not None else "policy_saved"
        return _admin_redirect("/admin/policy", source_policy_id=created_id, notice=status)
    except Exception as exc:
        detail = getattr(exc, "detail", None) or str(exc) or "Policy save failed"
        return _admin_redirect("/admin/policy", error=str(detail))


@app.post("/admin/policy/activate-form")
def admin_policy_console_activate(
    csrf_token: str = Form(...),
    policy_id: str = Form(...),
    notes: str = Form(default=""),
    principal: Any = Depends(require_policy_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "policy-activate")
    try:
        admin_activate_policy(
            PolicyActivateRequest(
                policy_id=str(policy_id or "").strip(),
                notes=str(notes or "").strip() or None,
            ),
            principal=principal,
        )
        return _admin_redirect("/admin/policy", source_policy_id=policy_id, notice="policy_activated")
    except Exception as exc:
        detail = getattr(exc, "detail", None) or str(exc) or "Policy activation failed"
        return _admin_redirect("/admin/policy", source_policy_id=policy_id, error=str(detail))


@app.post("/admin/policy/rollback-form")
def admin_policy_console_rollback(
    csrf_token: str = Form(...),
    target_policy_id: str = Form(default=""),
    notes: str = Form(default=""),
    principal: Any = Depends(require_policy_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "policy-rollback")
    try:
        result = admin_rollback_policy(
            PolicyRollbackRequest(
                target_policy_id=str(target_policy_id or "").strip() or None,
                notes=str(notes or "").strip() or None,
            ),
            principal=principal,
        )
        active_policy = result.get("active") if isinstance(result, dict) else None
        active_id = str((active_policy or {}).get("id") or "").strip()
        return _admin_redirect("/admin/policy", source_policy_id=active_id, notice="policy_rolled_back")
    except Exception as exc:
        detail = getattr(exc, "detail", None) or str(exc) or "Policy rollback failed"
        return _admin_redirect("/admin/policy", error=str(detail))


@app.get("/admin/policy/versions")
def admin_list_policy_versions(limit: int = 50, _: Any = Depends(require_policy_admin)):
    versions = policy_store.list_policy_versions(limit=limit)
    return {"items": versions, "count": len(versions)}


@app.get("/admin/policy/audit")
def admin_list_policy_audit(limit: int = 100, _: Any = Depends(require_policy_admin)):
    rows = policy_store.list_policy_audit(limit=limit)
    return {"items": rows, "count": len(rows)}


@app.post("/admin/policy/versions")
def admin_create_policy_version(payload: PolicyCreateRequest, principal: Any = Depends(require_policy_admin)):
    created = policy_store.create_policy_version(
        policy_input=payload.policy,
        actor=str(principal.get("actor") or "admin"),
        notes=payload.notes,
        activate=payload.activate,
    )
    if payload.activate:
        _load_runtime_policy(force_refresh=True)
    return {"status": "ok", "created": created}


@app.post("/admin/policy/activate")
def admin_activate_policy(payload: PolicyActivateRequest, principal: Any = Depends(require_policy_admin)):
    activated = policy_store.activate_policy(payload.policy_id, actor=str(principal.get("actor") or "admin"), notes=payload.notes)
    _load_runtime_policy(force_refresh=True)
    return {"status": "ok", "active": activated}


@app.post("/admin/policy/rollback")
def admin_rollback_policy(payload: PolicyRollbackRequest, principal: Any = Depends(require_policy_admin)):
    rolled = policy_store.rollback_policy(
        actor=str(principal.get("actor") or "admin"),
        target_policy_id=payload.target_policy_id,
        notes=payload.notes,
    )
    _load_runtime_policy(force_refresh=True)
    return {"status": "ok", "active": rolled}


@app.get("/ops/plan-jobs/diagnostics")
def ops_plan_job_diagnostics(_: Any = Depends(require_ops_admin)):
    return _plan_job_ops_service().diagnostics()


@app.get("/ops/plan-jobs")
def ops_list_plan_jobs(
    status: str | None = None,
    owner_uid: str | None = None,
    q: str | None = None,
    limit: int = 100,
    _: Any = Depends(require_ops_admin),
):
    return _plan_job_ops_service().list_jobs(status=status, owner_uid=owner_uid, q=q, limit=limit)


@app.get("/ops/plan-jobs/{job_id}")
def ops_get_plan_job(job_id: str, _: Any = Depends(require_ops_admin)):
    return _plan_job_ops_service().get_job_detail(job_id)


@app.get("/ops/users/{uid}/cloud-profile")
def ops_get_user_cloud_profile(
    uid: str,
    include_raw: bool = False,
    principal: Any = Depends(require_ops_admin),
):
    raw, summary = _get_firestore_profile_snapshot(uid)
    database.log_admin_action(
        "user_profile.inspect",
        actor=str(principal.get("actor") or "ops-admin"),
        resource_type="user_profile",
        resource_id=str(uid),
        details={"includeRaw": bool(include_raw)},
    )
    response = {
        "status": "ok",
        "summary": summary,
        "generatedAtMs": int(time.time() * 1000),
    }
    if include_raw:
        response["document"] = raw
    return response


@app.get("/ops/support-cases")
def ops_list_support_cases(
    user_uid: str | None = None,
    status: str | None = None,
    assignee: str | None = None,
    escalated: bool | None = None,
    q: str | None = None,
    limit: int = 100,
    _: Any = Depends(require_ops_admin),
):
    items = database.list_support_cases(
        user_uid=user_uid,
        status=status,
        assignee=assignee,
        escalated=escalated,
        q=q,
        limit=limit,
    )
    return {"status": "ok", "items": items, "count": len(items), "generatedAtMs": int(time.time() * 1000)}


@app.get("/ops/support-cases/{case_id}", response_model=AdminSupportCase)
def ops_get_support_case(case_id: str, _: Any = Depends(require_ops_admin)):
    item = database.get_support_case(case_id)
    if not item:
        raise HTTPException(status_code=404, detail="Support case not found")
    return AdminSupportCase(**item)


@app.post("/ops/support-cases", response_model=AdminSupportCase)
def ops_create_support_case(payload: AdminSupportCaseCreateRequest, principal: Any = Depends(require_ops_admin)):
    return _support_case_service().create_case(payload, principal=principal)


@app.patch("/ops/support-cases/{case_id}", response_model=AdminSupportCase)
def ops_update_support_case(
    case_id: str,
    payload: AdminSupportCaseUpdateRequest,
    principal: Any = Depends(require_ops_admin),
):
    return _support_case_service().update_case(case_id, payload, principal=principal)


@app.post("/ops/support-cases/{case_id}/notes", response_model=AdminSupportCase)
def ops_add_support_case_note(
    case_id: str,
    payload: AdminSupportCaseNoteRequest,
    principal: Any = Depends(require_ops_admin),
):
    return _support_case_service().add_note(case_id, payload, principal=principal)


@app.get("/ops/support-cases/{case_id}/export")
def ops_export_support_case(
    case_id: str,
    include_raw_profile: bool = False,
    job_limit: int = 10,
    audit_limit: int = 30,
    principal: Any = Depends(require_ops_admin),
):
    return _support_case_service().export_case(
        case_id,
        principal=principal,
        include_raw_profile=include_raw_profile,
        job_limit=job_limit,
        audit_limit=audit_limit,
    )


@app.get("/ops/admin-sessions")
def ops_list_admin_sessions(
    uid: str | None = None,
    active_only: bool = True,
    limit: int = 100,
    _: Any = Depends(require_ops_admin),
):
    return _admin_session_ops_service().list_sessions(uid=uid, active_only=active_only, limit=limit)


@app.get("/ops/operator-access")
def ops_list_operator_access_overrides(
    blocked_only: bool = False,
    limit: int = 100,
    _: Any = Depends(require_ops_admin),
):
    return _operator_access_override_service().list_overrides(blocked_only=blocked_only, limit=limit)


@app.get("/ops/operator-access/{uid}", response_model=OperatorAccessOverrideRecord)
def ops_get_operator_access_override(uid: str, _: Any = Depends(require_ops_admin)):
    return _operator_access_override_service().get_override(uid)


@app.put("/ops/operator-access/{uid}")
def ops_upsert_operator_access_override(
    uid: str,
    payload: OperatorAccessOverrideUpsertRequest,
    principal: Any = Depends(require_ops_admin),
):
    return _operator_access_override_service().upsert_override(uid, payload, principal=principal)


@app.post("/ops/admin-sessions/{session_id}/revoke", response_model=AdminSessionRecord)
def ops_revoke_admin_session(
    session_id: str,
    payload: AdminSessionRevokeRequest,
    principal: Any = Depends(require_ops_admin),
):
    return _admin_session_ops_service().revoke_session(session_id, payload, principal=principal)


@app.post("/ops/admin-sessions/revoke-user/{uid}")
def ops_revoke_admin_sessions_for_uid(
    uid: str,
    payload: AdminSessionBulkRevokeRequest,
    principal: Any = Depends(require_ops_admin),
):
    return _admin_session_ops_service().revoke_sessions_for_uid(uid, payload, principal=principal)


@app.post("/ops/admin-sessions/cleanup")
def ops_cleanup_admin_sessions(
    payload: AdminSessionCleanupRequest,
    principal: Any = Depends(require_ops_admin),
):
    return _admin_session_ops_service().cleanup_sessions(payload, principal=principal)


@app.get("/ops/users/{uid}/support-bundle")
def ops_get_user_support_bundle(
    uid: str,
    include_raw_profile: bool = False,
    job_limit: int = 20,
    audit_limit: int = 20,
    principal: Any = Depends(require_ops_admin),
):
    raw_profile, profile_summary = _get_firestore_profile_snapshot(uid)
    jobs = database.list_plan_jobs(owner_uid=uid, limit=job_limit)
    job_counts: Dict[str, int] = {}
    for item in jobs:
        status_key = str(item.get("status") or "unknown").lower()
        job_counts[status_key] = int(job_counts.get(status_key) or 0) + 1
    support_cases = database.list_support_cases(user_uid=uid, limit=max(1, min(20, audit_limit)))

    audit_logs = database.list_admin_action_logs(
        limit=audit_limit,
        resource_id=str(uid),
    )
    database.log_admin_action(
        "user_support_bundle.inspect",
        actor=str(principal.get("actor") or "ops-admin"),
        resource_type="user_support_bundle",
        resource_id=str(uid),
        details={"includeRawProfile": bool(include_raw_profile), "jobLimit": int(job_limit), "auditLimit": int(audit_limit)},
    )
    response = {
        "status": "ok",
        "uid": str(uid),
        "profileSummary": profile_summary,
        "recentPlanJobs": jobs,
        "planJobCounts": job_counts,
        "supportCases": support_cases,
        "supportAuditTrail": audit_logs,
        "generatedAtMs": int(time.time() * 1000),
    }
    if include_raw_profile:
        response["profileDocument"] = raw_profile
    return response


@app.post("/ops/plan-jobs/{job_id}/requeue")
def ops_requeue_plan_job(job_id: str, principal: Any = Depends(require_ops_admin)):
    return _plan_job_ops_service().requeue_job(job_id, principal=principal)


@app.post("/ops/plan-jobs/{job_id}/replay")
def ops_replay_plan_job(job_id: str, principal: Any = Depends(require_ops_admin)):
    return _plan_job_ops_service().replay_job(job_id, principal=principal)


@app.get("/ops/schema/migrations")
def ops_schema_migrations(_: Any = Depends(require_ops_admin)):
    app_status = database.get_schema_migration_status()
    policy_status = policy_store.get_schema_migration_status()
    return {
        "status": "ok",
        "app": app_status,
        "policy": policy_status,
        "generatedAtMs": int(time.time() * 1000),
    }


async def _ingest_canary_webhook_event(channel: str, receiver_key: str, request: Request):
    channel_norm = str(channel or "").strip().lower()
    if channel_norm not in ("alert", "dashboard"):
        raise HTTPException(status_code=404, detail="Unknown webhook channel")
    if not _validate_webhook_receiver_key(receiver_key):
        raise HTTPException(status_code=401, detail="Unauthorized webhook receiver key")

    payload: Dict[str, Any]
    try:
        decoded = await request.json()
        payload = decoded if isinstance(decoded, dict) else {"value": decoded}
    except Exception:
        raw = (await request.body() or b"").decode("utf-8", errors="replace").strip()
        payload = {"raw": raw} if raw else {}

    event_id = uuid.uuid4().hex
    now_ms = int(time.time() * 1000)
    event = {
        "eventId": event_id,
        "channel": channel_norm,
        "receivedAtMs": now_ms,
        "clientIp": request.client.host if request.client else "unknown",
        "userAgent": request.headers.get("user-agent"),
        "payload": payload,
    }
    _append_canary_webhook_event(event)
    _inc_plan_job_diag(f"canary_webhook_{channel_norm}_received_total")
    print(
        "CANARY_WEBHOOK_RECEIVED",
        json.dumps(
            {
                "eventId": event_id,
                "channel": channel_norm,
                "receivedAtMs": now_ms,
                "clientIp": event["clientIp"],
            },
            sort_keys=True,
        ),
    )
    return {
        "status": "accepted",
        "eventId": event_id,
        "channel": channel_norm,
        "receivedAtMs": now_ms,
    }


@app.post("/ops/webhooks/canary/{channel}")
async def ingest_canary_webhook_with_header(
    channel: str,
    request: Request,
    x_pcosina_webhook_key: str | None = Header(default=None, alias="X-PCOSINA-Webhook-Key"),
):
    return await _ingest_canary_webhook_event(channel, x_pcosina_webhook_key or "", request)


@app.post("/ops/webhooks/canary/{channel}/{receiver_key}")
async def ingest_canary_webhook(channel: str, receiver_key: str, request: Request):
    return await _ingest_canary_webhook_event(channel, receiver_key, request)


@app.get("/ops/webhooks/canary/recent")
def list_canary_webhook_receipts(limit: int = 20, _: Any = Depends(require_ops_admin)):
    safe_limit = max(1, min(int(limit or 20), 200))
    items = list(_canary_webhook_events[-safe_limit:])
    items.reverse()
    return {
        "status": "ok",
        "count": len(items),
        "receiverKeyConfigured": bool(_expected_webhook_receiver_key()),
        "items": items,
    }

def _cache_key(request: GeneratePlanRequest) -> str:
    def normalize_profile(profile_obj: Any) -> Dict[str, Any]:
        data = profile_obj.model_dump() if hasattr(profile_obj, "model_dump") else profile_obj.dict()
        for key in ["dietaryRestrictions", "pantryItems", "allergies", "symptoms", "comorbidities"]:
            raw = data.get(key)
            if isinstance(raw, list):
                data[key] = sorted([str(x).strip() for x in raw if str(x).strip()])
        return data
    try:
        payload = request.model_dump()
    except Exception:
        payload = {
            "profile": request.profile.model_dump() if hasattr(request.profile, "model_dump") else request.profile.dict(),
            "days": request.days
        }
    if "profile" in payload:
        payload["profile"] = normalize_profile(request.profile)
    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
    return f"sha256:{hashlib.sha256(canonical.encode('utf-8')).hexdigest()}"

def _cache_get(key: str, ttl_seconds: int = PLAN_CACHE_TTL_SECONDS):
    item = _plan_cache.get(key)
    if not item:
        return None
    ts, value = item
    if (time.time() - ts) > ttl_seconds:
        _plan_cache.pop(key, None)
        return None
    return value

def _cache_set(key: str, value: GeneratePlanResponse, max_size: int = PLAN_CACHE_MAX_SIZE):
    if len(_plan_cache) >= max_size:
        oldest_key = min(_plan_cache.items(), key=lambda kv: kv[1][0])[0]
        _plan_cache.pop(oldest_key, None)
    _plan_cache[key] = (time.time(), value)


def _freshen_cached_plan_response(
    cached: GeneratePlanResponse,
    *,
    request_id: str,
    started_ms: int,
    completed_ms: int,
) -> GeneratePlanResponse:
    return shared_freshen_cached_plan_response(
        cached,
        request_id=request_id,
        started_ms=started_ms,
        completed_ms=completed_ms,
    )


def _emit_cache_hit_completion_events(
    response: GeneratePlanResponse,
    *,
    request: GeneratePlanRequest,
    uid: str,
    policy_version: str,
) -> None:
    runtime_ms = 0
    if str(response.status or "").strip().lower() == "success":
        _emit_planner_event(
            "solver_completed",
            {
                "requestId": response.requestId,
                "status": "success",
                "runtimeMs": runtime_ms,
                "policyVersion": policy_version,
                "reasonCodes": [],
                "candidateCountPost": ((response.explanation or {}).get("candidatePoolSize") if isinstance(response.explanation, dict) else None),
                "cacheHit": True,
            },
            uid=uid,
            policy_version=policy_version,
        )
        _emit_ml_event(
            event_name="plan_generated",
            payload={
                "status": "success",
                "plan_id": response.planId,
                "slot_count": int(request.days or 7) * int(request.mealsPerDay or 3),
                "cache_hit": True,
            },
            uid=uid,
            request_id=response.requestId,
            policy_version=policy_version,
        )
        return
    _emit_planner_event(
        "solver_completed",
        {
            "requestId": response.requestId,
            "status": "no-safe-plan",
            "runtimeMs": runtime_ms,
            "policyVersion": policy_version,
            "reasonCodes": response.machineReasonCodes,
            "candidateCountPre": _optional_int((response.diagnosticsSummary or {}).get("candidateCountPre")),
            "candidateCountPost": _optional_int((response.diagnosticsSummary or {}).get("candidateCountPost")),
            "pricingDiagnostics": (response.diagnosticsSummary or {}).get("pricingDiagnostics") or {},
            "cacheHit": True,
        },
        uid=uid,
        policy_version=policy_version,
    )
    _emit_ml_event(
        event_name="no_safe_plan_encountered",
        payload={
            "reason_codes": response.machineReasonCodes,
            "cache_hit": True,
        },
        uid=uid,
        request_id=response.requestId,
        policy_version=policy_version,
    )


def _invalidate_plan_cache() -> None:
    _plan_cache.clear()


def _idempotency_get(key: str, ttl_seconds: int = IDEMPOTENCY_TTL_SECONDS) -> Optional[Dict[str, Any]]:
    item = _idempotency_cache.get(key)
    if not item:
        return None
    ts, payload = item
    if (time.time() - ts) > ttl_seconds:
        _idempotency_cache.pop(key, None)
        return None
    return payload


def _idempotency_set(key: str, payload: Dict[str, Any]):
    _idempotency_cache[key] = (time.time(), payload)


def _idempotency_scope_key(
    idempotency_key: str,
    *,
    uid: str | None,
    policy_version: str | None,
    request: GeneratePlanRequest,
) -> str:
    request_fingerprint = hashlib.sha256(_cache_key(request).encode("utf-8")).hexdigest()
    scoped_uid = str(uid or "anonymous").strip() or "anonymous"
    scoped_policy = str(policy_version or "unknown").strip() or "unknown"
    raw = "|".join(
        [
            str(idempotency_key or "").strip(),
            scoped_uid,
            scoped_policy,
            request_fingerprint,
        ]
    )
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _emit_ml_event(
    event_name: str,
    payload: Dict[str, Any],
    *,
    uid: str | None,
    request_id: str | None,
    policy_version: str | None,
):
    event = build_event(
        event_name=event_name,
        uid=uid,
        request_id=request_id,
        policy_version=policy_version,
        payload=payload,
    )
    valid, error = validate_event(event)
    if not valid:
        print("ML_EVENT_DROPPED", json.dumps({"event_name": event_name, "error": error}, sort_keys=True))
        return
    database.record_ml_event(event)
    print("ML_EVENT", json.dumps(event, sort_keys=True))


def _inc_plan_job_diag(metric_key: str, delta: int = 1) -> None:
    try:
        database.increment_plan_job_diagnostic(metric_key, delta=delta)
    except Exception:
        # Diagnostics must not block planner behavior.
        pass


def _emit_planner_event(event: str, payload: Dict[str, Any], *, uid: str | None = None, policy_version: str | None = None):
    request_id = str(payload.get("requestId") or payload.get("request_id") or "none")
    normalized_payload = dict(payload)
    normalized_payload.setdefault("reason_codes", normalized_payload.get("reasonCodes", []))
    normalized_payload.setdefault("runtime_ms", normalized_payload.get("runtimeMs"))
    if "days" in normalized_payload:
        normalized_payload.setdefault("meals_per_day", normalized_payload.get("mealsPerDay"))
    _emit_ml_event(
        event_name=event,
        payload=normalized_payload,
        uid=uid,
        request_id=request_id,
        policy_version=policy_version or "unknown",
    )


def _optional_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except Exception:
        return None


def _emit_planner_timing_log(
    *,
    request_id: str,
    policy_version: str | None,
    runtime_ms: int,
    telemetry: Dict[str, Any] | None,
) -> None:
    telemetry_payload = dict(telemetry or {})
    payload = {
        "requestId": str(request_id or "none"),
        "policyVersion": str(policy_version or "unknown"),
        "runtimeMs": max(0, int(runtime_ms or 0)),
        "candidateCountPre": _optional_int(telemetry_payload.get("candidate_count_pre")),
        "candidateCountPost": _optional_int(telemetry_payload.get("candidate_count_post")),
        "rankingStrategy": str(telemetry_payload.get("ranking_strategy") or "unknown"),
        "phaseTimingsMs": telemetry_payload.get("phase_timings_ms") or {},
        "stage1Diag": telemetry_payload.get("stage1_diag") or {},
        "pricingDiagnostics": telemetry_payload.get("pricing_diagnostics") or {},
        "solverBudget": telemetry_payload.get("solver_budget") or {},
        "budgetDiagnostics": telemetry_payload.get("budget_diagnostics") or {},
        "budgetExceededStage": telemetry_payload.get("budget_exceeded_stage"),
        "solvePairDiagnostics": telemetry_payload.get("solve_pair_diagnostics") or [],
    }
    print("PLANNER_TIMING", json.dumps(payload, sort_keys=True))


def _record_solver_outcome(success: bool) -> None:
    if success:
        _planner_circuit_state["consecutive_failures"] = 0
        _planner_circuit_state["opened_at"] = 0.0
        return
    _planner_circuit_state["consecutive_failures"] = int(_planner_circuit_state.get("consecutive_failures") or 0) + 1
    _planner_circuit_state["opened_at"] = time.time()


def _is_circuit_open(policy: Dict[str, Any]) -> bool:
    threshold = int(_policy_value(policy, "solver.circuit_breaker_threshold", 5))
    if threshold <= 0:
        return False
    failures = int(_planner_circuit_state.get("consecutive_failures") or 0)
    if failures < threshold:
        return False
    timeout_ms = int(_policy_value(policy, "solver.timeout_ms", 120000))
    opened_at = float(_planner_circuit_state.get("opened_at") or 0.0)
    if opened_at <= 0:
        return False
    cooldown_seconds = max(1.0, timeout_ms / 1000.0)
    if (time.time() - opened_at) < cooldown_seconds:
        return True
    _planner_circuit_state["consecutive_failures"] = 0
    _planner_circuit_state["opened_at"] = 0.0
    return False


def _set_job(job_id: str, status: str, result: GeneratePlanResponse | None = None, error: str | None = None):
    payload = None
    if result is not None:
        if hasattr(result, "model_dump"):
            payload = result.model_dump()
        elif hasattr(result, "dict"):
            payload = result.dict()
        else:
            payload = result
    result_json = json.dumps(payload) if payload is not None else None
    database.update_plan_job(job_id, status=status, result_json=result_json, error=error)


def _init_job(
    job_id: str,
    request_json: str | None = None,
    idempotency_key: str | None = None,
    owner_uid: str | None = None,
):
    database.create_plan_job(job_id, request_json=request_json, idempotency_key=idempotency_key, owner_uid=owner_uid)


def _dispatch_async_job(
    job_id: str,
    request: GeneratePlanRequest,
    background_tasks: BackgroundTasks | None = None,
    owner_uid: str | None = None,
) -> Dict[str, Any]:
    broker_enabled = QUEUE_BROKER.is_enabled()
    broker_publish_ok = False
    normalized_owner_uid = str(owner_uid or "").strip() or None
    if broker_enabled and ASYNC_MODE not in ("background", "inprocess"):
        try:
            broker_publish_ok = bool(QUEUE_BROKER.publish(job_id))
        except Exception:
            broker_publish_ok = False
        if broker_publish_ok:
            _inc_plan_job_diag("async_broker_publish_total")
        else:
            _inc_plan_job_diag("async_broker_publish_fail_total")

    if ASYNC_MODE in ("background", "inprocess"):
        if background_tasks is not None:
            background_tasks.add_task(_run_job, job_id, request, normalized_owner_uid)
        else:
            # Used by operator replay endpoints where FastAPI background tasks are not threaded through.
            _run_job(job_id, request, normalized_owner_uid)
        execution_mode = "inprocess"
        _inc_plan_job_diag("async_inprocess_dispatch_total")
    else:
        execution_mode = "queued-broker" if broker_enabled else "queued"
        _inc_plan_job_diag("async_queue_dispatch_total")

    return {
        "executionMode": execution_mode,
        "queueBackend": str(QUEUE_BROKER.health().get("backend") or "db"),
        "brokerSignalPublished": broker_publish_ok if broker_enabled else None,
    }


def _reason_feedback_features_for_uid(uid: str | None) -> Dict[str, float]:
    uid_token = str(uid or "").strip()
    if not uid_token:
        return {}
    getter = getattr(database, "get_reason_feedback_features", None)
    if not callable(getter):
        return {}
    try:
        return getter(uid_hash(uid_token))
    except Exception:
        return {}


def _solve_with_telemetry(
    request: GeneratePlanRequest,
    recipes: list[dict],
    policy_payload: Dict[str, Any],
    *,
    reason_feedback_features: Dict[str, float] | None = None,
) -> tuple[Any, str, Any, Dict[str, Any]]:
    telemetry: Dict[str, Any] = {}
    ml_feature_context = {
        "reason_feedback_features": reason_feedback_features or {},
    }
    try:
        result, msg, explanation = solve_meal_plan(
            request,
            recipes,
            policy=policy_payload,
            telemetry_out=telemetry,
            ml_feature_context=ml_feature_context,
        )
    except TypeError as exc:
        # Backward-compatible path for monkeypatched/legacy call signatures in tests.
        if "ml_feature_context" in str(exc):
            result, msg, explanation = solve_meal_plan(
                request,
                recipes,
                policy=policy_payload,
                telemetry_out=telemetry,
            )
        elif "telemetry_out" in str(exc):
            result, msg, explanation = solve_meal_plan(request, recipes, policy=policy_payload)
        else:
            raise
    return result, msg, explanation, telemetry


def _solve_with_user_ml_context(
    request: GeneratePlanRequest,
    recipes: list[dict],
    policy_payload: Dict[str, Any],
    *,
    uid: str | None,
) -> tuple[Any, str, Any, Dict[str, Any]]:
    reason_feedback_features = _reason_feedback_features_for_uid(uid)
    try:
        return _solve_with_telemetry(
            request,
            recipes,
            policy_payload,
            reason_feedback_features=reason_feedback_features,
        )
    except TypeError as exc:
        if "reason_feedback_features" not in str(exc):
            raise
        return _solve_with_telemetry(request, recipes, policy_payload)


def _emit_async_failure_event(
    job_id: str,
    *,
    uid: str | None,
    policy_version: str | None,
    runtime_ms: int,
    reason_codes: list[str],
    error_type: str | None = None,
) -> None:
    payload: Dict[str, Any] = {
        "requestId": job_id,
        "status": "error",
        "runtimeMs": max(0, int(runtime_ms or 0)),
        "policyVersion": policy_version or "unknown",
        "reasonCodes": reason_codes,
    }
    if error_type:
        payload["errorType"] = error_type
    _emit_planner_event(
        "async_solver_completed",
        payload,
        uid=uid,
        policy_version=policy_version,
    )


def _run_job(job_id: str, request: GeneratePlanRequest, owner_uid: str | None = None):
    try:
        _set_job(job_id, "running")
        _inc_plan_job_diag("async_inprocess_jobs_started_total")
        started_ms = int(time.time() * 1000)
        uid = str(owner_uid or "").strip() or None
        policy_payload, policy_version = _load_runtime_policy()
        _emit_planner_event(
            "plan_generation_requested",
            {
                "requestId": job_id,
                "days": int(request.days or 7),
                "mealsPerDay": int(request.mealsPerDay or 3),
                "restrictionCount": len(request.profile.dietaryRestrictions or []),
                "allergyCount": len(request.profile.allergies or []),
                "budgetWeeklyPhp": request.profile.weeklyBudgetPhp,
                "maxCookingTimeMinutes": request.profile.maxCookingTimeMinutes,
                "async": True,
            },
            uid=uid,
            policy_version=policy_version,
        )
        all_recipes = database.get_all_recipes()
        result, msg, explanation, telemetry = _solve_with_user_ml_context(
            request,
            all_recipes,
            policy_payload,
            uid=uid,
        )
        if telemetry.get("stage1_candidates"):
            database.record_stage1_candidate_features(
                request_id=job_id,
                uid_hash=uid_hash(uid),
                candidates=telemetry.get("stage1_candidates") or [],
                selected_recipe_ids=telemetry.get("selected_recipe_ids") or [],
                ranking_strategy=str(telemetry.get("ranking_strategy") or "stage1_heuristic_with_ml_shadow"),
                model_version=str(telemetry.get("ml_model_version") or "shadow_v0"),
                generated_at_ms=int(time.time() * 1000),
            )
        _emit_ml_event(
            event_name="stage1_candidates_scored",
            payload={
                "candidate_count_pre": _optional_int(telemetry.get("candidate_count_pre")),
                "candidate_count_post": _optional_int(telemetry.get("candidate_count_post")),
                "ranking_strategy": str(telemetry.get("ranking_strategy") or "stage1_heuristic_with_ml_shadow"),
                "ml_score_enabled": bool(telemetry.get("ml_score_enabled", True)),
                "ml_model_version": str(telemetry.get("ml_model_version") or "shadow_v0"),
                "phase_timings_ms": telemetry.get("phase_timings_ms") or {},
                "solver_budget": telemetry.get("solver_budget") or {},
                "budget_exceeded_stage": telemetry.get("budget_exceeded_stage"),
                "pricing_diagnostics": telemetry.get("pricing_diagnostics") or {},
            },
            uid=uid,
            request_id=job_id,
            policy_version=policy_version,
        )
        completed_ms = int(time.time() * 1000)
        runtime_ms = max(0, completed_ms - started_ms)
        _emit_planner_timing_log(
            request_id=job_id,
            policy_version=policy_version,
            runtime_ms=runtime_ms,
            telemetry=telemetry,
        )
        if result:
            response = GeneratePlanResponse(
                weekLabel=f"PCOSINA {request.days}-Day Plan",
                days=result,
                status="success",
                message=msg,
                explanation=explanation,
                requestId=job_id,
                planId=uuid.uuid4().hex,
                groceryOutput=telemetry.get("grocery_output"),
                policyVersion=policy_version,
                diagnosticsSummary={
                    "reasonCodes": [],
                    "summary": "success",
                    "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
                    "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                    "budgetDiagnostics": telemetry.get("budget_diagnostics") or {},
                    **(telemetry.get("budget_diagnostics") or {}),
                    "groceryBudgetAuthority": (explanation or {}).get("groceryBudgetAuthority") if isinstance(explanation, dict) else None,
                },
                solverMetadata={
                    "solverName": "OR-Tools CP-SAT",
                    "authorityStage": "stage2",
                    "authoritative": True,
                    "runtimeMs": runtime_ms,
                },
                timestamps={"requestedAtMs": started_ms, "completedAtMs": completed_ms},
            )
            _set_job(job_id, "done", result=response)
            _record_solver_outcome(True)
            _emit_planner_event(
                "async_solver_completed",
                {
                    "requestId": job_id,
                    "status": "success",
                    "runtimeMs": runtime_ms,
                    "policyVersion": policy_version,
                    "reasonCodes": [],
                    "candidateCountPre": _optional_int(telemetry.get("candidate_count_pre")),
                    "candidateCountPost": (explanation or {}).get("candidatePoolSize"),
                    "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                    "solverBudget": telemetry.get("solver_budget") or {},
                    "budgetExceededStage": telemetry.get("budget_exceeded_stage"),
                    "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
                },
                uid=uid,
                policy_version=policy_version,
            )
            _emit_ml_event(
                event_name="plan_generated",
                payload={
                    "status": "success",
                    "plan_id": response.planId,
                    "slot_count": int(request.days or 7) * int(request.mealsPerDay or 3),
                },
                uid=uid,
                request_id=job_id,
                policy_version=policy_version,
            )
            _inc_plan_job_diag("async_inprocess_jobs_success_total")
        else:
            response = _build_no_safe_plan_response(
                request=request,
                request_id=job_id,
                message=msg,
                policy_version=policy_version,
                started_ms=started_ms,
                completed_ms=completed_ms,
                telemetry=telemetry,
            )
            _set_job(job_id, "done", result=response)
            _record_solver_outcome(False)
            _emit_planner_event(
                "async_solver_completed",
                {
                    "requestId": job_id,
                    "status": "no-safe-plan",
                    "runtimeMs": runtime_ms,
                    "policyVersion": policy_version,
                    "reasonCodes": response.machineReasonCodes,
                    "candidateCountPre": _optional_int(telemetry.get("candidate_count_pre")),
                    "candidateCountPost": _optional_int(telemetry.get("candidate_count_post")),
                    "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                    "solverBudget": telemetry.get("solver_budget") or {},
                    "budgetExceededStage": telemetry.get("budget_exceeded_stage"),
                    "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
                },
                uid=uid,
                policy_version=policy_version,
            )
            _emit_ml_event(
                event_name="no_safe_plan_encountered",
                payload={"reason_codes": response.machineReasonCodes},
                uid=uid,
                request_id=job_id,
                policy_version=policy_version,
            )
            _inc_plan_job_diag("async_inprocess_jobs_no_safe_total")
    except Exception as e:
        _record_solver_outcome(False)
        _set_job(job_id, "error", error=str(e))
        _emit_async_failure_event(
            job_id,
            uid=str(owner_uid or "").strip() or None,
            policy_version=locals().get("policy_version"),
            runtime_ms=max(0, int(time.time() * 1000) - int(locals().get("started_ms") or int(time.time() * 1000))),
            reason_codes=["SOLVER_EXCEPTION"],
            error_type=type(e).__name__,
        )
        _inc_plan_job_diag("async_inprocess_jobs_error_total")

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(
    request: GeneratePlanRequest,
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    user: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
    _: Any = Depends(require_schema_version)
):
    try:
        _inc_plan_job_diag("sync_requests_total")
        started_ms = int(time.time() * 1000)
        request_id = uuid.uuid4().hex
        uid = str((user or {}).get("uid") or "anonymous")
        policy_payload, policy_version = _load_runtime_policy()
        configured_days = int(_policy_value(policy_payload, "planning.planning_horizon_days", 7))
        configured_meals = int(_policy_value(policy_payload, "planning.meals_per_day", 3))
        local_cache_ttl = int(_policy_value(policy_payload, "sync_offline.local_cache_ttl", PLAN_CACHE_TTL_SECONDS))
        local_cache_max = int(_policy_value(policy_payload, "sync_offline.local_cache_max_entries", PLAN_CACHE_MAX_SIZE))
        idempotency_ttl = int(_policy_value(policy_payload, "security.token_ttl", IDEMPOTENCY_TTL_SECONDS))
        if _is_circuit_open(policy_payload):
            raise HTTPException(status_code=503, detail="Planner temporarily unavailable (circuit open). Retry later.")

        # Generation Guard Logic: Only allow Sunday or if plan is near completion
        now = datetime.datetime.now()
        # weekday() 6 is Sunday.
        if IS_PRODUCTION and now.weekday() != 6:
             raise HTTPException(status_code=403, detail="New plan generation is only available on Sundays. Stay the course and finish your week!")

        _emit_planner_event(

            "plan_generation_requested",
            {
                "requestId": request_id,
                "days": int(request.days or 7),
                "mealsPerDay": int(request.mealsPerDay or 3),
                "restrictionCount": len(request.profile.dietaryRestrictions or []),
                "allergyCount": len(request.profile.allergies or []),
                "budgetWeeklyPhp": request.profile.weeklyBudgetPhp,
                "maxCookingTimeMinutes": request.profile.maxCookingTimeMinutes,
            },
            uid=uid,
            policy_version=policy_version,
        )
        scoped_idempotency_key = None
        if idempotency_key:
            scoped_idempotency_key = _idempotency_scope_key(
                idempotency_key,
                uid=uid,
                policy_version=policy_version,
                request=request,
            )
            cached_payload = _idempotency_get(scoped_idempotency_key, ttl_seconds=idempotency_ttl)
            if cached_payload:
                return GeneratePlanResponse(**cached_payload)
        if int(request.mealsPerDay or configured_meals) != configured_meals:
            raise HTTPException(status_code=400, detail=f"Only mealsPerDay={configured_meals} is supported by active policy.")
        if int(request.days or configured_days) != configured_days:
            raise HTTPException(status_code=400, detail=f"Only days={configured_days} is supported by active policy.")
        key = f"{policy_version}|{_cache_key(request)}"
        cached = _cache_get(key, ttl_seconds=local_cache_ttl)
        if cached is not None:
            _inc_plan_job_diag("sync_cache_hits_total")
            response = _freshen_cached_plan_response(
                cached,
                request_id=request_id,
                started_ms=started_ms,
                completed_ms=int(time.time() * 1000),
            )
            _emit_cache_hit_completion_events(
                response,
                request=request,
                uid=uid,
                policy_version=policy_version,
            )
            return response

        all_recipes = database.get_all_recipes()
        result, msg, explanation, telemetry = _solve_with_user_ml_context(
            request,
            all_recipes,
            policy_payload,
            uid=uid,
        )
        if telemetry.get("stage1_candidates"):
            database.record_stage1_candidate_features(
                request_id=request_id,
                uid_hash=uid_hash(uid),
                candidates=telemetry.get("stage1_candidates") or [],
                selected_recipe_ids=telemetry.get("selected_recipe_ids") or [],
                ranking_strategy=str(telemetry.get("ranking_strategy") or "stage1_heuristic_with_ml_shadow"),
                model_version=str(telemetry.get("ml_model_version") or "shadow_v0"),
                generated_at_ms=completed_ms if "completed_ms" in locals() else int(time.time() * 1000),
            )
        _emit_ml_event(
            event_name="stage1_candidates_scored",
            payload={
                "candidate_count_pre": _optional_int(telemetry.get("candidate_count_pre")),
                "candidate_count_post": _optional_int(telemetry.get("candidate_count_post")),
                "ranking_strategy": str(telemetry.get("ranking_strategy") or "stage1_heuristic_with_ml_shadow"),
                "ml_score_enabled": bool(telemetry.get("ml_score_enabled", True)),
                "ml_model_version": str(telemetry.get("ml_model_version") or "shadow_v0"),
                "phase_timings_ms": telemetry.get("phase_timings_ms") or {},
                "solver_budget": telemetry.get("solver_budget") or {},
                "budget_exceeded_stage": telemetry.get("budget_exceeded_stage"),
                "pricing_diagnostics": telemetry.get("pricing_diagnostics") or {},
            },
            uid=uid,
            request_id=request_id,
            policy_version=policy_version,
        )
        completed_ms = int(time.time() * 1000)
        runtime_ms = max(0, completed_ms - started_ms)
        _emit_planner_timing_log(
            request_id=request_id,
            policy_version=policy_version,
            runtime_ms=runtime_ms,
            telemetry=telemetry,
        )
        if result:
            response = GeneratePlanResponse(
                weekLabel=f"PCOSINA {request.days}-Day Plan",
                days=result,
                status="success",
                message=msg,
                explanation=explanation,
                requestId=request_id,
                planId=uuid.uuid4().hex,
                groceryOutput=telemetry.get("grocery_output"),
                policyVersion=policy_version,
                diagnosticsSummary={
                    "reasonCodes": [],
                    "summary": "success",
                    "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
                    "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                    "budgetDiagnostics": telemetry.get("budget_diagnostics") or {},
                    **(telemetry.get("budget_diagnostics") or {}),
                    "groceryBudgetAuthority": (explanation or {}).get("groceryBudgetAuthority") if isinstance(explanation, dict) else None,
                },
                solverMetadata={
                    "solverName": "OR-Tools CP-SAT",
                    "authorityStage": "stage2",
                    "authoritative": True,
                    "runtimeMs": runtime_ms,
                },
                timestamps={"requestedAtMs": started_ms, "completedAtMs": completed_ms},
            )
            _cache_set(key, response, max_size=local_cache_max)
            if scoped_idempotency_key:
                _idempotency_set(scoped_idempotency_key, response.model_dump())
            _record_solver_outcome(True)
            _emit_planner_event(
                "solver_completed",
                {
                    "requestId": request_id,
                    "status": "success",
                    "runtimeMs": runtime_ms,
                    "policyVersion": policy_version,
                    "reasonCodes": [],
                    "candidateCountPre": _optional_int(telemetry.get("candidate_count_pre")),
                    "candidateCountPost": (explanation or {}).get("candidatePoolSize"),
                    "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                    "solverBudget": telemetry.get("solver_budget") or {},
                    "budgetExceededStage": telemetry.get("budget_exceeded_stage"),
                    "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
                },
                uid=uid,
                policy_version=policy_version,
            )
            _emit_ml_event(
                event_name="plan_generated",
                payload={
                    "status": "success",
                    "plan_id": response.planId,
                    "slot_count": int(request.days or 7) * int(request.mealsPerDay or 3),
                },
                uid=uid,
                request_id=request_id,
                policy_version=policy_version,
            )
            _inc_plan_job_diag("sync_success_total")
            return response
        profile = request.profile
        print(
            "PLAN_INFEASIBLE",
            json.dumps(
                {
                    "requestId": request_id,
                    "message": msg,
                    "days": int(request.days or 7),
                    "mealsPerDay": int(request.mealsPerDay or 3),
                    "restrictionCount": len(profile.dietaryRestrictions or []),
                    "allergyCount": len(profile.allergies or []),
                    "maxCookingTimeMinutes": profile.maxCookingTimeMinutes,
                    "weeklyBudgetPhp": profile.weeklyBudgetPhp,
                    "planningPriority": profile.planningPriority,
                    "varietyPreference": profile.varietyPreference,
                }
            ),
        )
        response = _build_no_safe_plan_response(
            request=request,
            request_id=request_id,
            message=msg,
            policy_version=policy_version,
            started_ms=started_ms,
            completed_ms=completed_ms,
            telemetry=telemetry,
        )
        if scoped_idempotency_key:
            _idempotency_set(scoped_idempotency_key, response.model_dump())
        _record_solver_outcome(False)
        _emit_planner_event(
            "solver_completed",
            {
                "requestId": request_id,
                "status": "no-safe-plan",
                "runtimeMs": runtime_ms,
                "policyVersion": policy_version,
                "reasonCodes": response.machineReasonCodes,
                "candidateCountPre": _optional_int(telemetry.get("candidate_count_pre")),
                "candidateCountPost": _optional_int(telemetry.get("candidate_count_post")),
                "phaseTimingsMs": telemetry.get("phase_timings_ms") or {},
                "solverBudget": telemetry.get("solver_budget") or {},
                "budgetExceededStage": telemetry.get("budget_exceeded_stage"),
                "pricingDiagnostics": telemetry.get("pricing_diagnostics") or {},
            },
            uid=uid,
            policy_version=policy_version,
        )
        _emit_ml_event(
            event_name="no_safe_plan_encountered",
            payload={"reason_codes": response.machineReasonCodes},
            uid=uid,
            request_id=request_id,
            policy_version=policy_version,
        )
        _inc_plan_job_diag("sync_no_safe_total")
        return response
    except HTTPException:
        _inc_plan_job_diag("sync_http_error_total")
        raise
    except Exception as exc:
        _record_solver_outcome(False)
        _emit_planner_event(
            "solver_completed",
            {
                "requestId": str(locals().get("request_id") or "none"),
                "status": "error",
                "runtimeMs": max(0, int(time.time() * 1000) - int(locals().get("started_ms") or int(time.time() * 1000))),
                "policyVersion": str(locals().get("policy_version") or "unknown"),
                "reasonCodes": ["SOLVER_EXCEPTION"],
                "errorType": type(exc).__name__,
            },
            uid=str(locals().get("uid") or "").strip() or None,
            policy_version=str(locals().get("policy_version") or "unknown"),
        )
        _inc_plan_job_diag("sync_exception_total")
        traceback.print_exc()
        raise HTTPException(status_code=500, detail="Internal server error")


@app.post("/generate-plan-async")
async def generate_plan_async(
    request: GeneratePlanRequest,
    background_tasks: BackgroundTasks,
    idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    user: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
    _: Any = Depends(require_schema_version)
):
    _inc_plan_job_diag("async_requests_total")
    policy_payload, _policy_version = _load_runtime_policy()
    configured_days = int(_policy_value(policy_payload, "planning.planning_horizon_days", 7))
    configured_meals = int(_policy_value(policy_payload, "planning.meals_per_day", 3))
    if _is_circuit_open(policy_payload):
        _inc_plan_job_diag("async_rejected_circuit_open_total")
        raise HTTPException(status_code=503, detail="Planner queue is temporarily paused due to circuit breaker.")
    if int(request.mealsPerDay or configured_meals) != configured_meals:
        raise HTTPException(status_code=400, detail=f"Only mealsPerDay={configured_meals} is supported by active policy.")
    if int(request.days or configured_days) != configured_days:
        raise HTTPException(status_code=400, detail=f"Only days={configured_days} is supported by active policy.")
    request_json = request.model_dump_json() if hasattr(request, "model_dump_json") else json.dumps(request.dict())
    owner_uid = str(user.get("uid") or user.get("user_id") or "").strip() or None
    if idempotency_key:
        existing_job = database.find_plan_job_by_idempotency(
            idempotency_key,
            request_json=request_json,
            owner_uid=owner_uid,
        )
        if existing_job:
            return {
                "jobId": existing_job["id"],
                "status": existing_job["status"],
                "executionMode": "queued-existing",
                "reused": True,
            }
    queue_batch_size = int(_policy_value(policy_payload, "sync_offline.sync_batch_size", 100))
    queue_priority_rules = _policy_value(policy_payload, "solver.queue_priority_rules", {"default": "fifo"})
    priority_mode = str((queue_priority_rules or {}).get("default", "fifo")).lower()
    queued_count = database.count_plan_jobs_by_status("queued")
    running_count = database.count_plan_jobs_by_status("running")
    if queued_count >= max(1, queue_batch_size):
        _inc_plan_job_diag("async_rejected_queue_saturated_total")
        raise HTTPException(
            status_code=503,
            detail=f"Planner queue saturated ({queued_count} queued, {running_count} running, mode={priority_mode}).",
        )
    job_id = uuid.uuid4().hex
    try:
        _init_job(job_id, request_json=request_json, idempotency_key=idempotency_key, owner_uid=owner_uid)
    except TypeError as exc:
        if "owner_uid" not in str(exc):
            raise
        _init_job(job_id, request_json=request_json, idempotency_key=idempotency_key)
    _inc_plan_job_diag("async_jobs_enqueued_total")
    dispatch = _dispatch_async_job(job_id, request, background_tasks=background_tasks, owner_uid=owner_uid)
    return {
        "jobId": job_id,
        "status": "queued",
        **dispatch,
    }


@app.get("/plan-jobs/{job_id}")
def get_plan_job(
    job_id: str,
    user: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
):
    owner_uid = str(user.get("uid") or user.get("user_id") or "").strip() or None
    job = database.get_plan_job(job_id, owner_uid=owner_uid)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return job

@app.get("/recipe/{recipe_id}", response_model=RecipeDetail)
async def get_recipe(
    recipe_id: str,
    user: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
):
    recipe = next((r for r in database.get_all_recipes() if r["id"] == recipe_id), None)
    if recipe: return RecipeDetail(**recipe)
    raise HTTPException(status_code=404, detail="Recipe not found")

@app.get("/recipes/summary", response_model=list[RecipeSummary])
def recipe_summaries(
    meal_type: str | None = None,
    limit: int = 50,
    _: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
):
    try:
        rows = database.get_recipe_summaries(meal_type, limit)
        return [RecipeSummary(**r) for r in rows]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Recipe summaries failed: {e}")


@app.get("/recipes/catalog", response_model=list[RecipeDetail])
def recipe_catalog(
    limit: int = 2000,
    _: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
):
    try:
        capped_limit = max(1, min(int(limit or 2000), 5000))
        return [RecipeDetail(**recipe) for recipe in database.get_all_recipes()[:capped_limit]]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Recipe catalog failed: {e}")


@app.post("/recipes/swap-options", response_model=list[RecipeSummary])
def recipe_swap_options(
    payload: SwapOptionsRequest,
    _: Any = Depends(require_firebase_auth),
    __: Any = Depends(require_app_check),
):
    try:
        policy_payload, _ = _load_runtime_policy()
        candidates = build_swap_candidates(
            payload.profile,
            database.get_all_recipes(),
            meal_label=payload.mealLabel,
            current_recipe_id=payload.currentRecipeId,
            active_recipe_ids=payload.activeRecipeIds,
            limit=payload.limit,
            policy=policy_payload,
        )
        return [
            RecipeSummary(
                id=str(recipe.get("id") or ""),
                title=str(recipe.get("title") or "").strip(),
                mealType=str(recipe.get("mealType") or "Universal"),
                minutes=int(recipe.get("minutes") or 0),
            )
            for recipe in candidates
            if str(recipe.get("id") or "").strip() and str(recipe.get("title") or "").strip()
        ]
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Swap options failed: {e}")

@app.get("/health")
def health(): return {"status": "alive"}


@app.get("/health/ready")
def health_ready():
    report = _runtime_readiness_report(include_schema=True)
    report["brokerHealth"] = QUEUE_BROKER.health()
    report["rateLimitHealth"] = RATE_LIMIT_STORE.health()
    status_code = 200 if report.get("ok") else 503
    return JSONResponse(status_code=status_code, content=report)

@app.get("/db-status")
def db_status():
    if IS_PRODUCTION:
        return {"status": "restricted"}
    try:
        db_mode_fn = getattr(database, "db_mode", None)
        recipe_count_fn = getattr(database, "get_recipe_count", None)
        sample_fn = getattr(database, "get_sample_recipes", None)
        return {
            "db": db_mode_fn() if callable(db_mode_fn) else "unknown",
            "recipes": recipe_count_fn() if callable(recipe_count_fn) else None,
            "sample": sample_fn(3) if callable(sample_fn) else [],
            "db_module": getattr(database, "__file__", str(database)),
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"DB status failed: {e}")

@app.post("/feedback")
def feedback(
    request: Request,
    payload: FeedbackRequest,
    __: Any = Depends(require_app_check),
    _: Any = Depends(require_schema_version),
):
    if not _feedback_rate_limit_allowed(request):
        raise HTTPException(status_code=429, detail="Too many feedback submissions")
    message = str(payload.message or "").strip()
    if not message:
        raise HTTPException(status_code=400, detail="Feedback message is required")
    try:
        database.save_feedback(message)
        cleanup_fn = getattr(database, "cleanup_feedback", None)
        if callable(cleanup_fn) and FEEDBACK_RETENTION_DAYS > 0:
            try:
                cleanup_fn(retention_days=FEEDBACK_RETENTION_DAYS)
            except Exception:
                pass
        return {"status": "ok"}
    except Exception:
        raise HTTPException(status_code=500, detail="Failed to save feedback")

@app.get("/admin/feedback", response_class=HTMLResponse)
def admin_feedback(
    request: Request,
    q: str | None = None,
    page: int = 1,
    page_size: int = 25,
    export: str | None = None,
    sort: str | None = None,
    principal: Any = Depends(require_feedback_admin),
):
    try:
        order = "asc" if str(sort).lower() == "asc" else "desc"
        items = database.get_recent_feedback(500, order=order)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to load feedback: {e}")

    query = (q or "").strip()
    if query:
        q_lower = query.lower()
        items = [
            item for item in items
            if q_lower in str(item.get("message", "")).lower()
        ]

    if export:
        fmt = export.lower().strip()
        if fmt == "json":
            payload = {
                "count": len(items),
                "items": items,
            }
            return JSONResponse(content=payload)
        if fmt == "csv":
            lines = ["id,created_at,message"]
            for item in items:
                fid = str(item.get("id", ""))
                created_at = str(item.get("created_at", "")).replace("\n", " ").replace("\r", " ")
                msg = str(item.get("message", "")).replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")
                lines.append(f"\"{fid}\",\"{created_at}\",\"{msg}\"")
            return HTMLResponse(
                content="\n".join(lines),
                media_type="text/csv"
            )
        raise HTTPException(status_code=400, detail="Invalid export format. Use export=json or export=csv.")

    session_cookie = request.cookies.get(ADMIN_SESSION_COOKIE)
    session_principal = principal
    issued_session_token: str | None = None
    if not _principal_from_session_token(session_cookie):
        issued_session_token, session_principal = _issue_admin_session(principal)

    page_size = max(5, min(200, int(page_size or 25)))
    page = max(1, int(page or 1))
    total = len(items)
    total_pages = max(1, (total + page_size - 1) // page_size)
    if page > total_pages:
        page = total_pages
    start = (page - 1) * page_size
    end = start + page_size
    page_items = items[start:end]

    rows = []
    delete_csrf = _build_admin_csrf_token(session_principal, "feedback-delete")
    bulk_delete_csrf = _build_admin_csrf_token(session_principal, "feedback-delete-bulk")
    for item in page_items:
        msg = html.escape(str(item.get("message", "")), quote=True)
        created_at = html.escape(str(item.get("created_at", "")), quote=True)
        fid = html.escape(str(item.get("id", "")), quote=True)
        rows.append(
            "<tr>"
            "<td>"
            f"<input type='checkbox' class='feedback-row-check' name='ids' value='{fid}' form='bulk-delete'/>"
            "</td>"
            f"<td>{created_at}</td>"
            f"<td>{msg}</td>"
            "<td>"
            f"<form method='post' action='/admin/feedback/delete' {_admin_danger_confirm('Delete this feedback record?')}>"
            f"<input type='hidden' name='id' value='{fid}'/>"
            f"<input type='hidden' name='csrf_token' value='{html.escape(delete_csrf, quote=True)}'/>"
            f"<input type='hidden' name='q' value='{html.escape(query, quote=True)}'/>"
            f"<input type='hidden' name='page' value='{page}'/>"
            f"<input type='hidden' name='sort' value='{order}'/>"
            f"<input type='hidden' name='page_size' value='{page_size}'/>"
            "<button type='submit' class='admin-danger-button'>Delete</button>"
            "</form>"
            "</td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else _admin_empty_row(4, "No feedback matches the current view.")
    base_params_dict = {"page_size": page_size, "sort": order}
    if query:
        base_params_dict["q"] = query
    base_params = urlencode(base_params_dict)
    prev_page = max(1, page - 1)
    next_page = min(total_pages, page + 1)
    page_label = html.escape(f"Page {page} of {total_pages} • {total} items", quote=True)
    body_html = f"""
      <section class="admin-card" style="margin-bottom:16px;">
        {_admin_section_header("Feedback Inbox", "Search, export, and remove resolved feedback records.")}
        <div class="admin-toolbar" style="margin-bottom:0;">
          {_admin_badge(page_label)}
          <a class="admin-link-button" href="/admin/feedback?{base_params}&page={prev_page}">Prev</a>
          <a class="admin-link-button" href="/admin/feedback?{base_params}&page={next_page}">Next</a>
          <a class="admin-link-button" href="/admin/feedback?{base_params}&export=json">Export JSON</a>
          <a class="admin-link-button" href="/admin/feedback?{base_params}&export=csv">Export CSV</a>
        </div>
      </section>
      <section class="admin-card" style="margin-bottom:16px;">
        <form method="get" action="/admin/feedback" class="admin-toolbar">
          <input type="hidden" name="sort" value="{html.escape(order, quote=True)}"/>
          <input type="text" name="q" value="{html.escape(query, quote=True)}" placeholder="Search message..."/>
          <select name="page_size">
            <option value="25" {'selected' if page_size == 25 else ''}>25 per page</option>
            <option value="50" {'selected' if page_size == 50 else ''}>50 per page</option>
            <option value="100" {'selected' if page_size == 100 else ''}>100 per page</option>
            <option value="200" {'selected' if page_size == 200 else ''}>200 per page</option>
          </select>
          <button type="submit">Search</button>
          <a href="/admin/feedback" class="admin-link-button">Clear</a>
          <a href="/admin/feedback?{base_params}&sort=desc" class="admin-link-button">Newest first</a>
          <a href="/admin/feedback?{base_params}&sort=asc" class="admin-link-button">Oldest first</a>
        </form>
        <form id="bulk-delete" method="post" action="/admin/feedback/delete-bulk" class="admin-toolbar" style="margin-bottom:0;" {_admin_danger_confirm('Delete all selected feedback records?')}>
          <input type="hidden" name="csrf_token" value="{html.escape(bulk_delete_csrf, quote=True)}"/>
          <input type="hidden" name="q" value="{html.escape(query, quote=True)}"/>
          <input type="hidden" name="page" value="{page}"/>
          <input type="hidden" name="page_size" value="{page_size}"/>
          <input type="hidden" name="sort" value="{html.escape(order, quote=True)}"/>
          <button type="submit" class="admin-danger-button">Delete selected</button>
        </form>
      </section>
      <section class="admin-card admin-scroll">
        <table>
          <thead><tr><th><input type="checkbox" id="feedback-select-all" aria-label="Select visible feedback"/></th><th>Created At</th><th>Message</th><th>Action</th></tr></thead>
          <tbody>{rows_html}</tbody>
        </table>
      </section>
      <script>
        const selectAll = document.getElementById("feedback-select-all");
        if (selectAll) {{
          selectAll.addEventListener("change", () => {{
            document.querySelectorAll(".feedback-row-check").forEach((input) => {{
              input.checked = selectAll.checked;
            }});
          }});
        }}
      </script>
    """
    response = _admin_shell(
        "Feedback Console",
        session_principal,
        body_html,
        current_console="feedback",
        description="Review submitted feedback, search responses, export evidence, and remove resolved records.",
        max_width=1220,
    )
    if issued_session_token:
        _set_admin_session_cookie(response, issued_session_token)
    return response

@app.post("/admin/feedback/delete")
def admin_feedback_delete(
    id: int = Form(...),
    csrf_token: str = Form(...),
    q: str | None = Form(default=None),
    page: int = Form(default=1),
    page_size: int = Form(default=25),
    sort: str | None = Form(default=None),
    principal: Any = Depends(require_feedback_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "feedback-delete")
    try:
        database.delete_feedback_by_id(id)
        q = (q or "").strip()
        order = "asc" if str(sort).lower() == "asc" else "desc"
        params: Dict[str, Any] = {"sort": order, "page_size": max(5, min(200, int(page_size or 25)))}
        if q:
            params["q"] = q
        if page and int(page) > 1:
            params["page"] = int(page)
        return RedirectResponse(url=f"/admin/feedback?{urlencode(params)}", status_code=303)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete feedback: {e}")

@app.post("/admin/feedback/delete-bulk")
def admin_feedback_delete_bulk(
    csrf_token: str = Form(...),
    ids: list[str] = Form(default=[]),
    q: str | None = Form(default=None),
    page: int = Form(default=1),
    page_size: int = Form(default=25),
    sort: str | None = Form(default=None),
    principal: Any = Depends(require_feedback_admin),
):
    _verify_admin_csrf_token(principal, csrf_token, "feedback-delete-bulk")
    deleted = 0
    try:
        for raw in ids:
            try:
                fid = int(raw)
            except Exception:
                continue
            database.delete_feedback_by_id(fid)
            deleted += 1
        q = (q or "").strip()
        order = "asc" if str(sort).lower() == "asc" else "desc"
        params: Dict[str, Any] = {"page_size": max(5, min(200, int(page_size or 25))), "sort": order}
        if q:
            params["q"] = q
        if page and int(page) > 1:
            params["page"] = int(page)
        return RedirectResponse(url=f"/admin/feedback?{urlencode(params)}", status_code=303)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete feedback: {e}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
