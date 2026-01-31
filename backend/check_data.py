import sqlite3
import json
import os

DB_NAME = "pcosina.db"

def export_to_text():
    if not os.path.exists(DB_NAME):
        print(f"Error: {DB_NAME} not found. Run database.py first.")
        return

    conn = sqlite3.connect(DB_NAME)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    try:
        cursor.execute("SELECT * FROM recipes")
        rows = cursor.fetchall()
    except Exception as e:
        print(f"Error reading database: {e}")
        return

    with open("database_content.txt", "w", encoding="utf-8") as f:
        f.write("=== PCOSINA DATABASE CONTENT EXPORT ===\n")
        f.write(f"Total Recipes Found: {len(rows)}\n")
        f.write("="*40 + "\n\n")

        for row in rows:
            f.write(f"ID: {row['id']}\n")
            f.write(f"TITLE: {row['title']}\n")
            f.write(f"TYPE: {row['meal_type']}\n")
            f.write(f"CALORIES: {row['calories']} kcal\n")
            f.write(f"MACROS: P:{row['protein']}g, C:{row['carbs']}g, F:{row['fats']}g, Fb:{row['fiber']}g\n")
            f.write(f"COOK TIME: {row['minutes']} mins\n")
            
            # Formatting Ingredients
            f.write("INGREDIENTS:\n")
            try:
                ings = json.loads(row['ingredients_json'])
                for ing in ings:
                    f.write(f"  - {ing['name']}: {ing['quantity']}\n")
            except:
                f.write("  (Error loading ingredients)\n")

            # Formatting Steps
            f.write("STEPS:\n")
            try:
                steps = json.loads(row['steps_json'])
                for i, step in enumerate(steps, 1):
                    f.write(f"  {i}. {step}\n")
            except:
                f.write("  (Error loading steps)\n")
            
            f.write("-" * 30 + "\n\n")

    conn.close()
    print("Success! A readable file 'database_content.txt' has been created in your backend folder.")

if __name__ == "__main__":
    export_to_text()
