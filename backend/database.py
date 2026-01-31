import sqlite3
import json
import os

DB_NAME = "pcosina.db"

def init_db():
    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()
    
    # Create the table with the structure needed for the View Recipe screen
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
            minutes INTEGER,
            ingredients_json TEXT,
            steps_json TEXT
        )
    ''')
    conn.commit()
    conn.close()

def seed_recipes():
    if not os.path.exists("recipes.json"):
        return

    with open("recipes.json", "r") as f:
        recipes = json.load(f)

    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()

    for r in recipes:
        # Convert Lists and Objects into JSON Strings so SQLite can store them
        ingredients_str = json.dumps(r.get("ingredients", []))
        steps_str = json.dumps(r.get("steps", []))
        tags_str = ",".join(r.get("tags", []))

        cursor.execute('''
            INSERT OR REPLACE INTO recipes (
                id, title, meal_type, calories, protein, carbs, fats, fiber, 
                tags, minutes, ingredients_json, steps_json
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ''', (
            r.get("id"),
            r.get("title"),
            r.get("mealType"),
            r.get("calories", 0),
            r.get("proteinGrams", 0),
            r.get("carbsGrams", 0),
            r.get("fatsGrams", 0),
            r.get("fiberGrams", 0),
            tags_str,
            r.get("minutes", 20),
            ingredients_str,
            steps_str
        ))

    conn.commit()
    conn.close()
    print("Database successfully synced with recipes.json format.")

def get_all_recipes():
    """Fetches all recipes from the SQLite database and converts JSON strings back to lists/objects."""
    if not os.path.exists(DB_NAME):
        return []
        
    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    try:
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
                "tags": row["tags"].split(",") if row["tags"] else [],
                "minutes": row["minutes"],
                "ingredients": json.loads(row["ingredients_json"]),
                "steps": json.loads(row["steps_json"])
            })
        return recipes
    except Exception as e:
        print(f"Database Error: {e}")
        return []
    finally:
        conn.close()

if __name__ == "__main__":
    init_db()
    seed_recipes()
