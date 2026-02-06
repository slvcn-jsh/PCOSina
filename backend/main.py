from fastapi import FastAPI, HTTPException, Request, Depends, Header, Form
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from starlette.responses import JSONResponse, HTMLResponse
from typing import Dict, Any
import json
import os
import time
import traceback
import socket
from contextlib import asynccontextmanager
import database
import firebase_admin
from firebase_admin import credentials, auth
import sentry_sdk
from sentry_sdk.integrations.fastapi import FastApiIntegration
from services.meal_planner import solve_meal_plan
from schema_contract import SCHEMA_VERSION, load_schema_contract
from domain.models import (
    RecipeDetail,
    GeneratePlanRequest,
    GeneratePlanResponse,
    FeedbackRequest,
)

PLAN_CACHE_TTL_SECONDS = 600
PLAN_CACHE_MAX_SIZE = 200
_plan_cache = {}

sentry_dsn = os.getenv("SENTRY_DSN")
if sentry_dsn:
    sentry_sdk.init(
        dsn=sentry_dsn,
        integrations=[FastApiIntegration()],
        traces_sample_rate=float(os.getenv("SENTRY_TRACES_SAMPLE_RATE", "0.1")),
        environment=os.getenv("SENTRY_ENVIRONMENT", "production"),
        release=os.getenv("SENTRY_RELEASE"),
    )

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
    try:
        init_firebase()
    except Exception as e:
        print(f"WARNING: Firebase initialization failed: {e}")
        print("Backend will continue without Firebase Auth (Local Dev Mode)")
    
    database.init_db()
    database.seed_recipes()
    yield

docs_enabled = True # Always enable for easier testing
app = FastAPI(
    title="PCOSINA Optimization API",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)

MAX_REQUEST_BYTES = int(os.getenv("MAX_REQUEST_BYTES", str(512 * 1024)))

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
    return await call_next(request)

@app.middleware("http")
async def attach_schema_version(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-PCOSINA-Schema-Version"] = SCHEMA_VERSION
    return response

allowed_hosts = ["*"] # Allow all for local phone testing
app.add_middleware(TrustedHostMiddleware, allowed_hosts=allowed_hosts)

@app.get("/", response_class=HTMLResponse)
def root(request: Request):
    base = str(request.base_url).rstrip("/")
    token = os.getenv("ADMIN_FEEDBACK_TOKEN", "").strip()
    admin_link = f"{base}/admin/feedback?token={token}" if token else f"{base}/admin/feedback?token=YOUR_TOKEN"
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
          <p>Admin feedback requires a token:</p>
          <p><a href="{admin_link}" target="_blank" rel="noopener noreferrer">{admin_link}</a></p>
        </body>
        </html>
        """
    )

def init_firebase():
    if firebase_admin._apps:
        return
    if os.getenv("FIREBASE_AUTH_DISABLED", "").lower() == "true":
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
        return {"uid": "local-dev-user"}
        
    if os.getenv("FIREBASE_AUTH_DISABLED", "").lower() == "true":
        return {"uid": "auth-disabled-user"}
        
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization.split(" ", 1)[1].strip()
    try:
        decoded = auth.verify_id_token(token, check_revoked=True)
        return decoded
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

def require_schema_version(x_pcosina_schema_version: str | None = Header(default=None)):
    if x_pcosina_schema_version and x_pcosina_schema_version != SCHEMA_VERSION:
        raise HTTPException(
            status_code=409,
            detail=f"Schema version mismatch. Server={SCHEMA_VERSION}, Client={x_pcosina_schema_version}"
        )

@app.get("/schema")
def schema_contract():
    return load_schema_contract()

def _cache_key(request: GeneratePlanRequest) -> str:
    try:
        return json.dumps(request.model_dump(), sort_keys=True)
    except Exception:
        return json.dumps({
            "profile": request.profile.model_dump() if hasattr(request.profile, "model_dump") else request.profile.dict(),
            "days": request.days
        }, sort_keys=True)

def _cache_get(key: str):
    item = _plan_cache.get(key)
    if not item:
        return None
    ts, value = item
    if (time.time() - ts) > PLAN_CACHE_TTL_SECONDS:
        _plan_cache.pop(key, None)
        return None
    return value

def _cache_set(key: str, value: GeneratePlanResponse):
    if len(_plan_cache) >= PLAN_CACHE_MAX_SIZE:
        oldest_key = min(_plan_cache.items(), key=lambda kv: kv[1][0])[0]
        _plan_cache.pop(oldest_key, None)
    _plan_cache[key] = (time.time(), value)

@app.post("/generate-plan", response_model=GeneratePlanResponse)
async def generate_plan(
    request: GeneratePlanRequest,
    user: Any = Depends(require_firebase_auth),
    _: Any = Depends(require_schema_version)
):
    try:
        key = _cache_key(request)
        cached = _cache_get(key)
        if cached is not None:
            return cached

        all_recipes = database.get_all_recipes()
        result, msg, explanation = solve_meal_plan(request, all_recipes)
        if result:
            response = GeneratePlanResponse(
                weekLabel=f"PCOSINA {request.days}-Day Plan",
                days=result,
                status="success",
                message=msg,
                explanation=explanation
            )
            _cache_set(key, response)
            return response
        raise HTTPException(status_code=422, detail=f"Infeasible: {msg}")
    except Exception as e:
        traceback.print_exc()
        raise HTTPException(status_code=500, detail="Internal server error")

@app.get("/recipe/{recipe_id}", response_model=RecipeDetail)
async def get_recipe(recipe_id: str, user: Any = Depends(require_firebase_auth)):
    recipe = next((r for r in database.get_all_recipes() if r["id"] == recipe_id), None)
    if recipe: return RecipeDetail(**recipe)
    raise HTTPException(status_code=404, detail="Recipe not found")

@app.get("/health")
def health(): return {"status": "alive"}

@app.get("/db-status")
def db_status():
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
def feedback(payload: FeedbackRequest, _: Any = Depends(require_schema_version)):
    try:
        database.save_feedback(payload.message)
        return {"status": "ok"}
    except Exception:
        raise HTTPException(status_code=500, detail="Failed to save feedback")

@app.get("/admin/feedback", response_class=HTMLResponse)
def admin_feedback(
    x_admin_token: str | None = Header(default=None),
    token: str | None = None,
    q: str | None = None,
    page: int = 1,
    page_size: int = 25,
    export: str | None = None,
):
    expected = os.getenv("ADMIN_FEEDBACK_TOKEN", "").strip()
    if not expected or (x_admin_token != expected and token != expected):
        raise HTTPException(status_code=401, detail="Unauthorized")
    try:
        items = database.get_recent_feedback(500)
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
    for item in page_items:
        msg = str(item.get("message", ""))
        created_at = str(item.get("created_at", ""))
        fid = item.get("id", "")
        rows.append(
            "<tr>"
            "<td>"
            f"<input type='checkbox' name='ids' value='{fid}' form='bulk-delete'/>"
            "</td>"
            f"<td>{created_at}</td>"
            f"<td>{msg}</td>"
            "<td>"
            f"<form method='post' action='/admin/feedback/delete'>"
            f"<input type='hidden' name='id' value='{fid}'/>"
            f"<input type='hidden' name='token' value='{expected}'/>"
            f"<input type='hidden' name='q' value='{query}'/>"
            f"<input type='hidden' name='page' value='{page}'/>"
            "<button type='submit'>Delete</button>"
            "</form>"
            "</td>"
            "</tr>"
        )
    rows_html = "\n".join(rows) if rows else "<tr><td colspan='4'>No feedback yet.</td></tr>"
    base_params = f"token={expected}&page_size={page_size}"
    if query:
        base_params += f"&q={query}"
    prev_page = max(1, page - 1)
    next_page = min(total_pages, page + 1)
    page_label = f"Page {page} of {total_pages} • {total} items"

    html = f"""
    <!doctype html>
    <html>
    <head>
      <meta charset="utf-8" />
      <title>PCOSINA Feedback</title>
      <style>
        body {{ font-family: Arial, sans-serif; margin: 24px; background: #f7f7f7; }}
        h1 {{ margin-bottom: 12px; }}
        form.inline {{ display: inline; }}
        .toolbar {{ display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }}
        .pill {{ background: #fff; padding: 8px 12px; border-radius: 8px; border: 1px solid #ddd; }}
        table {{ width: 100%; border-collapse: collapse; background: #fff; }}
        th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; vertical-align: top; }}
        th {{ background: #f0f0f0; }}
        tr:nth-child(even) {{ background: #fafafa; }}
        button {{ padding: 6px 10px; }}
        .nav a {{ margin-right: 10px; }}
      </style>
    </head>
    <body>
      <h1>PCOSINA Feedback</h1>
      <div class="toolbar">
        <form method="get" action="/admin/feedback" class="pill">
          <input type="hidden" name="token" value="{expected}"/>
          <input type="hidden" name="page_size" value="{page_size}"/>
          <input type="text" name="q" value="{query}" placeholder="Search message..." />
          <button type="submit">Search</button>
        </form>
        <form id="bulk-delete" method="post" action="/admin/feedback/delete-bulk" class="pill">
          <input type="hidden" name="token" value="{expected}"/>
          <input type="hidden" name="q" value="{query}"/>
          <input type="hidden" name="page" value="{page}"/>
          <input type="hidden" name="page_size" value="{page_size}"/>
          <button type="submit">Delete selected</button>
        </form>
        <div class="pill">{page_label}</div>
        <div class="nav">
          <a href="/admin/feedback?{base_params}&page={prev_page}">Prev</a>
          <a href="/admin/feedback?{base_params}&page={next_page}">Next</a>
        </div>
        <div class="nav">
          <a href="/admin/feedback?{base_params}&export=json">Export JSON</a>
          <a href="/admin/feedback?{base_params}&export=csv">Export CSV</a>
        </div>
      </div>
      <table>
        <thead><tr><th></th><th>Created At</th><th>Message</th><th>Action</th></tr></thead>
        <tbody>
          {rows_html}
        </tbody>
      </table>
    </body>
    </html>
    """
    return HTMLResponse(content=html)

@app.post("/admin/feedback/delete")
def admin_feedback_delete(
    id: int = Form(...),
    token: str = Form(...),
    q: str | None = Form(default=None),
    page: int = Form(default=1),
):
    expected = os.getenv("ADMIN_FEEDBACK_TOKEN", "").strip()
    if not expected or token != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")
    try:
        database.delete_feedback_by_id(id)
        q = (q or "").strip()
        qs = f"?token={expected}"
        if q:
            qs += f"&q={q}"
        if page and int(page) > 1:
            qs += f"&page={int(page)}"
        return HTMLResponse(content=f"<html><body>Deleted. <a href='/admin/feedback{qs}'>Back</a></body></html>")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete feedback: {e}")

@app.post("/admin/feedback/delete-bulk")
def admin_feedback_delete_bulk(
    token: str = Form(...),
    ids: list[str] = Form(default=[]),
    q: str | None = Form(default=None),
    page: int = Form(default=1),
    page_size: int = Form(default=25),
):
    expected = os.getenv("ADMIN_FEEDBACK_TOKEN", "").strip()
    if not expected or token != expected:
        raise HTTPException(status_code=401, detail="Unauthorized")
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
        qs = f"?token={expected}&page_size={page_size}"
        if q:
            qs += f"&q={q}"
        if page and int(page) > 1:
            qs += f"&page={int(page)}"
        return HTMLResponse(content=f"<html><body>Deleted {deleted}. <a href='/admin/feedback{qs}'>Back</a></body></html>")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to delete feedback: {e}")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
