import sqlite3
import json
import os
import time

try:
    import psycopg
    from psycopg.rows import dict_row
except Exception:
    psycopg = None
    dict_row = None

DB_NAME = "pcosina.db"
DATABASE_URL = os.getenv("DATABASE_URL", "").strip()

ING_SYNONYMS = {
    "baboy": "pork",
    "liempo": "pork",
    "lechon": "pork",
    "litson": "pork",
    "baka": "beef",
    "bulalo": "beef",
    "tapa": "beef",
    "manok": "chicken",
    "isda": "fish",
    "hipon": "shrimp",
    "pusit": "squid",
    "gatas": "dairy",
    "keso": "cheese",
    "itlog": "egg",
    "tokwa": "tofu",
}

MEAT_TOKENS = {"pork", "beef", "chicken", "meat", "lamb", "goat", "duck"}
SEAFOOD_TOKENS = {"fish", "shrimp", "squid", "tuna", "salmon", "crab", "seafood"}
DAIRY_TOKENS = {"dairy", "milk", "cheese", "yogurt", "cream", "butter"}
EGG_TOKENS = {"egg"}

def _normalize_token(t: str) -> str:
    t = "".join(ch for ch in t.lower() if ch.isalnum() or ch in ("_", "-"))
    return ING_SYNONYMS.get(t, t)

def _normalize_ingredients(ings):
    tokens = []
    for ing in ings or []:
        name = ""
        if isinstance(ing, dict):
            name = str(ing.get("name", ""))
        else:
            name = str(ing)
        for raw in name.replace("/", " ").replace("-", " ").split():
            tok = _normalize_token(raw)
            if tok:
                tokens.append(tok)
    return tokens

def _infer_tags(recipe):
    tags = set([t.lower() for t in recipe.get("tags", []) if t])
    tokens = set(_normalize_ingredients(recipe.get("ingredients", [])))
    if tokens & MEAT_TOKENS: tags.add("contains_meat")
    if tokens & SEAFOOD_TOKENS: tags.add("contains_seafood")
    if tokens & DAIRY_TOKENS: tags.add("contains_dairy")
    if tokens & EGG_TOKENS: tags.add("contains_egg")

    nut = recipe.get("nutrition", {})
    p = nut.get("protein_g") or 0
    c = nut.get("carbs_g") or 0
    fiber = nut.get("fiber_g") or 0
    if p >= 25: tags.add("high_protein")
    if fiber >= 8: tags.add("high_fiber")
    if c <= 35: tags.add("low_carb")
    return list(tags)


def _median(values: list[int]) -> int:
    if not values:
        return 0
    sorted_vals = sorted(values)
    mid = len(sorted_vals) // 2
    if len(sorted_vals) % 2 == 1:
        return int(sorted_vals[mid])
    return int((sorted_vals[mid - 1] + sorted_vals[mid]) / 2)


def _compute_nutrition_medians(recipes: list[dict]) -> dict:
    calories = []
    protein = []
    carbs = []
    fats = []
    fiber = []
    for r in recipes:
        nut = r.get("nutrition", {}) or {}
        for key, bucket in [
            ("calories", calories),
            ("protein_g", protein),
            ("carbs_g", carbs),
            ("fat_g", fats),
            ("fiber_g", fiber),
        ]:
            raw = nut.get(key)
            if raw is not None and raw != 0:
                try:
                    bucket.append(int(raw))
                except Exception:
                    continue
    return {
        "calories": _median(calories) or 500,
        "protein_g": _median(protein) or 25,
        "carbs_g": _median(carbs) or 45,
        "fat_g": _median(fats) or 15,
        "fiber_g": _median(fiber) or 6,
    }


def _normalize_nutrition(nut: dict, medians: dict) -> tuple[int, int, int, int, int]:
    def pick(key: str, default: int) -> int:
        raw = nut.get(key)
        if raw is None or raw == 0:
            return default
        try:
            return max(0, int(raw))
        except Exception:
            return default
    cal = pick("calories", medians["calories"])
    prot = pick("protein_g", medians["protein_g"])
    carb = pick("carbs_g", medians["carbs_g"])
    fat = pick("fat_g", medians["fat_g"])
    fiber = pick("fiber_g", medians["fiber_g"])
    return cal, prot, carb, fat, fiber

def _use_postgres() -> bool:
    return DATABASE_URL.startswith("postgres")

def db_mode() -> str:
    return "postgres" if _use_postgres() else "sqlite"

def db_mode() -> str:
    return "postgres" if _use_postgres() else "sqlite"

def _connect():
    if _use_postgres():
        if psycopg is None:
            raise RuntimeError("psycopg is not installed. Add psycopg[binary] to requirements.")
        return psycopg.connect(DATABASE_URL)
    return sqlite3.connect(DB_NAME)

def _create_table_sql() -> str:
    return """
        CREATE TABLE IF NOT EXISTS recipes (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            meal_type TEXT NOT NULL,
            calories INTEGER,
            protein INTEGER,
            carbs INTEGER,
            fats INTEGER,
            fiber INTEGER,
            tags TEXT,
            minutes INTEGER,
            ingredients_json TEXT,
            steps_json TEXT
        )
    """

def _ensure_recipe_columns(conn):
    cur = conn.cursor()
    if _use_postgres():
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS minutes INTEGER")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS ingredients_json TEXT")
        cur.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS steps_json TEXT")
    else:
        cur.execute("PRAGMA table_info(recipes)")
        cols = {row[1] for row in cur.fetchall()}
        if "minutes" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN minutes INTEGER")
        if "ingredients_json" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN ingredients_json TEXT")
        if "steps_json" not in cols:
            cur.execute("ALTER TABLE recipes ADD COLUMN steps_json TEXT")
    cur.execute("UPDATE recipes SET minutes = COALESCE(minutes, 25)")
    cur.execute("UPDATE recipes SET ingredients_json = COALESCE(ingredients_json, '[]')")
    cur.execute("UPDATE recipes SET steps_json = COALESCE(steps_json, '[]')")
    
def _create_feedback_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS feedback (
                id SERIAL PRIMARY KEY,
                message TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT NOW()
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS feedback (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            message TEXT NOT NULL,
            created_at INTEGER NOT NULL
        )
    """

def _create_plan_jobs_table_sql() -> str:
    if _use_postgres():
        return """
            CREATE TABLE IF NOT EXISTS plan_jobs (
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                result_json TEXT,
                error TEXT
            )
        """
    return """
        CREATE TABLE IF NOT EXISTS plan_jobs (
            id TEXT PRIMARY KEY,
            status TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            result_json TEXT,
            error TEXT
        )
    """

def save_feedback(message: str):
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("INSERT INTO feedback (message) VALUES (%s)", (message,))
        else:
            cur.execute(
                "INSERT INTO feedback (message, created_at) VALUES (?, ?)",
                (message, int(time.time() * 1000))
            )
        conn.commit()
    finally:
        conn.close()

def get_recent_feedback(limit: int = 50, order: str = "desc"):
    conn = _connect()
    try:
        cur = conn.cursor()
        order_dir = "ASC" if str(order).lower() == "asc" else "DESC"
        if _use_postgres():
            cur.execute(f"SELECT id, message, created_at FROM feedback ORDER BY id {order_dir} LIMIT %s", (limit,))
            rows = cur.fetchall()
            return [{"id": r[0], "message": r[1], "created_at": str(r[2])} for r in rows]
        else:
            cur.execute(f"SELECT id, message, created_at FROM feedback ORDER BY id {order_dir} LIMIT ?", (limit,))
            rows = cur.fetchall()
            return [{"id": r[0], "message": r[1], "created_at": r[2]} for r in rows]
    finally:
        conn.close()

def delete_feedback_by_id(feedback_id: int) -> int:
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("DELETE FROM feedback WHERE id = %s", (feedback_id,))
        else:
            cur.execute("DELETE FROM feedback WHERE id = ?", (feedback_id,))
        conn.commit()
        return cur.rowcount or 0
    finally:
        conn.close()

def init_db():
    conn = _connect()
    cursor = conn.cursor()
    cursor.execute(_create_table_sql())
    _ensure_recipe_columns(conn)
    try:
        cursor.execute(_create_feedback_table_sql())
        cursor.execute(_create_plan_jobs_table_sql())
    except Exception as e:
        # Defensive: ignore rare Postgres type-creation race for "feedback"
        if "pg_type_typname_nsp_index" not in str(e):
            raise
    conn.commit()
    conn.close()

def create_plan_job(job_id: str):
    conn = _connect()
    try:
        now = int(time.time() * 1000)
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at
                """,
                (job_id, "queued", now, now)
            )
        else:
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at, result_json, error)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """,
                (job_id, "queued", now, now, None, None)
            )
        conn.commit()
    finally:
        conn.close()

def update_plan_job(job_id: str, status: str, result_json: str | None = None, error: str | None = None):
    conn = _connect()
    try:
        now = int(time.time() * 1000)
        cur = conn.cursor()
        if _use_postgres():
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at, result_json, error)
                VALUES (%s, %s, %s, %s, %s, %s)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at,
                    result_json = COALESCE(EXCLUDED.result_json, plan_jobs.result_json),
                    error = COALESCE(EXCLUDED.error, plan_jobs.error)
                """,
                (job_id, status, now, now, result_json, error)
            )
        else:
            cur.execute(
                """
                INSERT INTO plan_jobs (id, status, created_at, updated_at, result_json, error)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    updated_at = excluded.updated_at,
                    result_json = COALESCE(excluded.result_json, plan_jobs.result_json),
                    error = COALESCE(excluded.error, plan_jobs.error)
                """,
                (job_id, status, now, now, result_json, error)
            )
        conn.commit()
    finally:
        conn.close()

def get_plan_job(job_id: str):
    conn = _connect()
    try:
        if _use_postgres() and dict_row is not None:
            cur = conn.cursor(row_factory=dict_row)
        else:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
        if _use_postgres():
            cur.execute("SELECT id, status, created_at, updated_at, result_json, error FROM plan_jobs WHERE id = %s", (job_id,))
        else:
            cur.execute("SELECT id, status, created_at, updated_at, result_json, error FROM plan_jobs WHERE id = ?", (job_id,))
        row = cur.fetchone()
        if not row:
            return None
        if isinstance(row, dict):
            result_json = row.get("result_json")
            payload = {
                "id": row.get("id"),
                "status": row.get("status"),
                "createdAt": row.get("created_at"),
                "updatedAt": row.get("updated_at"),
                "error": row.get("error"),
            }
        else:
            result_json = row["result_json"]
            payload = {
                "id": row["id"],
                "status": row["status"],
                "createdAt": row["created_at"],
                "updatedAt": row["updated_at"],
                "error": row["error"],
            }
        if result_json:
            try:
                payload["result"] = json.loads(result_json)
            except Exception:
                payload["result"] = result_json
        return payload
    finally:
        conn.close()

def _recipe_count(conn) -> int:
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM recipes")
    row = cur.fetchone()
    if isinstance(row, dict):
        return int(list(row.values())[0])
    return int(row[0])

def get_recipe_count() -> int:
    conn = _connect()
    try:
        return _recipe_count(conn)
    finally:
        conn.close()

def get_sample_recipes(limit: int = 3):
    conn = _connect()
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
    else:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
    try:
        cursor.execute("SELECT id, title FROM recipes ORDER BY id LIMIT ?", (limit,)) if not _use_postgres() else cursor.execute(
            "SELECT id, title FROM recipes ORDER BY id LIMIT %s", (limit,)
        )
        rows = cursor.fetchall()
        samples = []
        for row in rows:
            if isinstance(row, dict):
                samples.append({"id": row.get("id"), "title": row.get("title")})
            else:
                samples.append({"id": row["id"], "title": row["title"]})
        return samples
    finally:
        conn.close()

def seed_recipes():
    if not os.path.exists("recipes.json"):
        return

    force_reseed = os.getenv("PCOSINA_FORCE_RESEED", "").strip().lower() in ("1", "true", "yes")

    with open("recipes.json", "r") as f:
        recipes = json.load(f)
    medians = _compute_nutrition_medians(recipes)

    conn = _connect()
    cursor = conn.cursor()
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
    if _use_postgres():
        insert_sql = '''
            INSERT INTO recipes (
                id, title, meal_type, calories, protein, carbs, fats, fiber, 
                tags, minutes, ingredients_json, steps_json
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (id) DO UPDATE SET
                title = EXCLUDED.title,
                meal_type = EXCLUDED.meal_type,
                calories = EXCLUDED.calories,
                protein = EXCLUDED.protein,
                carbs = EXCLUDED.carbs,
                fats = EXCLUDED.fats,
                fiber = EXCLUDED.fiber,
                tags = EXCLUDED.tags,
                minutes = EXCLUDED.minutes,
                ingredients_json = EXCLUDED.ingredients_json,
                steps_json = EXCLUDED.steps_json
        '''
    else:
        insert_sql = '''
            INSERT OR REPLACE INTO recipes (
                id, title, meal_type, calories, protein, carbs, fats, fiber, 
                tags, minutes, ingredients_json, steps_json
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        '''

    # For Postgres, avoid full reseed only when counts are already up-to-date.
    # This keeps startup fast while still syncing new recipes added to recipes.json.
    try:
        if _use_postgres() and not force_reseed:
            existing_count = _recipe_count(conn)
            if existing_count >= len(recipes):
                return
            print(f"DATABASE SYNC NEEDED: db={existing_count}, file={len(recipes)}")
    except Exception:
        pass

    for r in recipes:
        nut = r.get("nutrition", {})
        cal, prot, carb, fat, fiber = _normalize_nutrition(nut, medians)

        tags = _infer_tags(r)
        cursor.execute(insert_sql, (
            r.get("id"),
            r.get("name") or r.get("title") or "Unnamed",
            r.get("mealType", "Universal"),
            cal, prot, carb, fat, fiber,
            ",".join(tags),
            r.get("minutes", 25),
            json.dumps(r.get("ingredients", [])),
            json.dumps(r.get("instructions", []) or r.get("steps", []))
        ))

    conn.commit()
    conn.close()
    print(f"DATABASE SYNCED: {len(recipes)} recipes ready for MILP Brain.")

def get_all_recipes():
    if not _use_postgres() and not os.path.exists(DB_NAME):
        return []
    conn = _connect()
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
    else:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
    try:
        cursor.execute("SELECT * FROM recipes")
        rows = cursor.fetchall()
        recipes = []
        for row in rows:
            recipes.append({
                "id": row["id"], "title": row["title"], "mealType": row["meal_type"],
                "calories": row["calories"], "proteinGrams": row["protein"],
                "carbsGrams": row["carbs"], "fatsGrams": row["fats"],
                "fiberGrams": row["fiber"], "tags": row["tags"].split(",") if row["tags"] else [],
                "minutes": row["minutes"], "ingredients": json.loads(row["ingredients_json"]),
                "steps": json.loads(row["steps_json"])
            })
        return recipes
    finally:
        conn.close()

def get_recipe_summaries(meal_type: str | None = None, limit: int = 50):
    if not _use_postgres() and not os.path.exists(DB_NAME):
        return []
    limit = max(1, min(int(limit or 50), 200))
    conn = _connect()
    if _use_postgres() and dict_row is not None:
        cursor = conn.cursor(row_factory=dict_row)
    else:
        conn.row_factory = sqlite3.Row
        cursor = conn.cursor()
    try:
        if meal_type:
            if _use_postgres():
                cursor.execute(
                    """
                    SELECT id, title, meal_type, minutes
                    FROM recipes
                    WHERE meal_type ILIKE %s OR meal_type ILIKE '%%universal%%'
                    ORDER BY id
                    LIMIT %s
                    """,
                    (f"%{meal_type}%", limit)
                )
            else:
                cursor.execute(
                    """
                    SELECT id, title, meal_type, minutes
                    FROM recipes
                    WHERE lower(meal_type) LIKE lower(?) OR lower(meal_type) LIKE '%universal%'
                    ORDER BY id
                    LIMIT ?
                    """,
                    (f"%{meal_type}%", limit)
                )
        else:
            cursor.execute(
                "SELECT id, title, meal_type, minutes FROM recipes ORDER BY id LIMIT ?",
                (limit,)
            ) if not _use_postgres() else cursor.execute(
                "SELECT id, title, meal_type, minutes FROM recipes ORDER BY id LIMIT %s",
                (limit,)
            )
        rows = cursor.fetchall()
        summaries = []
        for row in rows:
            if isinstance(row, dict):
                summaries.append({
                    "id": row.get("id"),
                    "title": row.get("title"),
                    "mealType": row.get("meal_type"),
                    "minutes": row.get("minutes"),
                })
            else:
                summaries.append({
                    "id": row["id"],
                    "title": row["title"],
                    "mealType": row["meal_type"],
                    "minutes": row["minutes"],
                })
        return summaries
    finally:
        conn.close()

def save_feedback(message: str):
    conn = _connect()
    try:
        cur = conn.cursor()
        if _use_postgres():
            cur.execute("INSERT INTO feedback (message) VALUES (%s)", (message,))
        else:
            cur.execute(
                "INSERT INTO feedback (message, created_at) VALUES (?, ?)",
                (message, int(time.time() * 1000))
            )
        conn.commit()
    finally:
        conn.close()

def get_recent_feedback(limit: int = 50, order: str = "desc"):
    conn = _connect()
    try:
        cur = conn.cursor()
        order_dir = "ASC" if str(order).lower() == "asc" else "DESC"
        if _use_postgres():
            cur.execute(f"SELECT id, message, created_at FROM feedback ORDER BY id {order_dir} LIMIT %s", (limit,))
            rows = cur.fetchall()
            return [{"id": r[0], "message": r[1], "created_at": str(r[2])} for r in rows]
        else:
            cur.execute(f"SELECT id, message, created_at FROM feedback ORDER BY id {order_dir} LIMIT ?", (limit,))
            rows = cur.fetchall()
            return [{"id": r[0], "message": r[1], "created_at": r[2]} for r in rows]
    finally:
        conn.close()

if __name__ == "__main__":
    init_db()
    seed_recipes()
