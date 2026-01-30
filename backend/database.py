import sqlite3
import json
import os

DB_NAME = "pcosina.db"

def init_db():
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()

    # 1. Create Recipes Table
    cursor.execute('''
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
            ingredients_json TEXT
        )
    ''')

    # 2. Create Plans Table (To store historical plans)
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS meal_plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_email TEXT,
            week_label TEXT,
            plan_json TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ''')

    conn.commit()
    conn.close()

def seed_recipes():
    if not os.path.exists("recipes.json"):
        print("recipes.json not found. Skipping seed.")
        return

    with open("recipes.json", "r") as f:
        recipes = json.load(f)

    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()

    for r in recipes:
        # Check if exists
        cursor.execute("SELECT id FROM recipes WHERE id = ?", (r["id"],))
        if not cursor.fetchone():
            cursor.execute('''
                INSERT INTO recipes (id, title, meal_type, calories, protein, carbs, fats, fiber, tags, ingredients_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                r["id"],
                r["title"],
                r["mealType"],
                r["calories"],
                r["proteinGrams"],
                r["carbsGrams"],
                r["fatsGrams"],
                r["fiberGrams"],
                ",".join(r["tags"]),
                json.dumps(r["ingredients"])
            ))

    conn.commit()
    conn.close()
    print("Database seeded successfully.")

def get_all_recipes():
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM recipes")
    rows = cursor.fetchall()

    recipes = []
    for row in rows:
        recipes.append({
            "id": row["id"],
            "title": row["title"],
            "mealType": row["meal_type"],
            "calories": row["calories"],
            "proteinGrams": row["protein"],
            "carbsGrams": row["carbs"],
            "fatsGrams": row["fats"],
            "fiberGrams": row["fiber"],
            "tags": row["tags"].split(","),
            "ingredients": json.loads(row["ingredients_json"])
        })
    conn.close()
    return recipes

if __name__ == "__main__":
    init_db()
    seed_recipes()
