import os
import json
import sqlite3

try:
    import psycopg
except Exception as e:
    psycopg = None

SQLITE_DB = os.getenv("SQLITE_DB", "pcosina.db")
DATABASE_URL = os.getenv("DATABASE_URL", "").strip()

CREATE_TABLE_SQL = """
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

INSERT_SQL = """
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
"""


def main():
    if not os.path.exists(SQLITE_DB):
        raise SystemExit(f"SQLite DB not found: {SQLITE_DB}")
    if not DATABASE_URL.startswith("postgres"):
        raise SystemExit("DATABASE_URL must be set to a Postgres connection string")
    if psycopg is None:
        raise SystemExit("psycopg is not installed. Add psycopg[binary] to requirements.")

    src = sqlite3.connect(SQLITE_DB)
    src.row_factory = sqlite3.Row
    s_cur = src.cursor()
    s_cur.execute("SELECT * FROM recipes")
    rows = s_cur.fetchall()

    dst = psycopg.connect(DATABASE_URL)
    d_cur = dst.cursor()
    d_cur.execute(CREATE_TABLE_SQL)

    for row in rows:
        d_cur.execute(
            INSERT_SQL,
            (
                row["id"],
                row["title"],
                row["meal_type"],
                row["calories"],
                row["protein"],
                row["carbs"],
                row["fats"],
                row["fiber"],
                row["tags"],
                row["minutes"],
                row["ingredients_json"],
                row["steps_json"],
            ),
        )

    dst.commit()
    src.close()
    dst.close()
    print(f"Migrated {len(rows)} rows from {SQLITE_DB} to Postgres.")


if __name__ == "__main__":
    main()
