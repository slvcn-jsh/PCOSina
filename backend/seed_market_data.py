import json
import os
import uuid
import time
import database

# Seasonality data for Philippine Market (Categories: Produce, Meat/Seafood)
# Multiplier examples: 1.2 = 20% price hike during peak/volatile months
SEASONALITY_SEED_DATA = [
    # Produce (Higher prices during rainy season/typhoons Q3-Q4)
    {"category": "Produce", "month": 7, "multiplier": 1.25, "notes": "Rainy season onset"},
    {"category": "Produce", "month": 8, "multiplier": 1.30, "notes": "Peak typhoon risk"},
    {"category": "Produce", "month": 9, "multiplier": 1.35, "notes": "Peak typhoon risk"},
    {"category": "Produce", "month": 10, "multiplier": 1.30, "notes": "Post-typhoon supply lag"},
    {"category": "Produce", "month": 11, "multiplier": 1.20, "notes": "Holiday demand start"},
    {"category": "Produce", "month": 12, "multiplier": 1.40, "notes": "Peak holiday demand"},
    
    # Meat/Seafood (Holiday peaks)
    {"category": "Meat/Seafood", "month": 12, "multiplier": 1.25, "notes": "Christmas/Noche Buena demand"},
    {"category": "Meat/Seafood", "month": 1, "multiplier": 1.10, "notes": "New Year demand"},
    {"category": "Meat/Seafood", "month": 4, "multiplier": 1.15, "notes": "Lenten season fish price hike"},
]

def seed_seasonality():
    print(f"Seeding {len(SEASONALITY_SEED_DATA)} market seasonality rules...")
    conn = database._connect()
    try:
        cur = conn.cursor()
        now = int(time.time() * 1000)
        for entry in SEASONALITY_SEED_DATA:
            rule_id = f"{entry['category']}_{entry['month']}"
            if database._use_postgres():
                cur.execute(
                    """
                    INSERT INTO market_seasonality_rules (id, category, month_index, multiplier, notes, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    ON CONFLICT (id) DO UPDATE SET
                        multiplier = EXCLUDED.multiplier,
                        notes = EXCLUDED.notes,
                        updated_at = EXCLUDED.updated_at
                    """,
                    (rule_id, entry['category'], entry['month'], entry['multiplier'], entry['notes'], now)
                )
            else:
                cur.execute(
                    """
                    INSERT OR REPLACE INTO market_seasonality_rules (id, category, month_index, multiplier, notes, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (rule_id, entry['category'], entry['month'], entry['multiplier'], entry['notes'], now)
                )
        conn.commit()
        print("Successfully seeded seasonality rules.")
    finally:
        conn.close()

if __name__ == "__main__":
    seed_seasonality()
