import json
import os
import uuid
import time
import database

# Seed data based on realistic 2026 DTI SRP estimates for Philippines
SRP_SEED_DATA = [
    # Meat & Seafood (Price per KG)
    {"keywords": ["chicken", "manok"], "pricePhp": 210, "category": "Meat/Seafood", "unit": "kg", "notes": "DTI SRP 2026 Baseline"},
    {"keywords": ["pork", "liempo", "kasim", "baboy"], "pricePhp": 340, "category": "Meat/Seafood", "unit": "kg", "notes": "DTI SRP 2026 Baseline"},
    {"keywords": ["beef", "baka"], "pricePhp": 480, "category": "Meat/Seafood", "unit": "kg", "notes": "DTI SRP 2026 Baseline"},
    {"keywords": ["tilapia"], "pricePhp": 170, "category": "Meat/Seafood", "unit": "kg", "notes": "DTI SRP 2026 Baseline"},
    {"keywords": ["bangus", "milkfish"], "pricePhp": 220, "category": "Meat/Seafood", "unit": "kg", "notes": "DTI SRP 2026 Baseline"},
    {"keywords": ["shrimp", "hipon"], "pricePhp": 450, "category": "Meat/Seafood", "unit": "kg", "notes": "Market Average"},
    {"keywords": ["galunggong"], "pricePhp": 240, "category": "Meat/Seafood", "unit": "kg", "notes": "DTI SRP 2026 Baseline"},
    
    # Eggs & Dairy
    {"keywords": ["egg", "itlog"], "pricePhp": 9, "category": "Eggs & Dairy", "unit": "piece", "notes": "Large Egg SRP"},
    {"keywords": ["milk", "gatas"], "pricePhp": 110, "category": "Eggs & Dairy", "unit": "l", "notes": "UHT Full Cream Milk"},
    
    # Dry Goods
    {"keywords": ["rice", "bigas"], "pricePhp": 65, "category": "Dry Goods", "unit": "kg", "notes": "Well-milled Rice SRP"},
    {"keywords": ["brown rice"], "pricePhp": 85, "category": "Dry Goods", "unit": "kg", "notes": "Health-tier Premium"},
    
    # Produce (Price per KG)
    {"keywords": ["onion", "sibuyas"], "pricePhp": 160, "category": "Produce", "unit": "kg", "notes": "DTI SRP Baseline"},
    {"keywords": ["garlic", "bawang"], "pricePhp": 140, "category": "Produce", "unit": "kg", "notes": "DTI SRP Baseline"},
    {"keywords": ["tomato", "kamatis"], "pricePhp": 90, "category": "Produce", "unit": "kg", "notes": "DTI SRP Baseline"},
    {"keywords": ["calamansi"], "pricePhp": 100, "category": "Produce", "unit": "kg", "notes": "DTI SRP Baseline"},
    {"keywords": ["ampalaya"], "pricePhp": 120, "category": "Produce", "unit": "kg", "notes": "Market Average"},
    {"keywords": ["sitaw", "string beans"], "pricePhp": 100, "category": "Produce", "unit": "kg", "notes": "Market Average"},
    {"keywords": ["pechay", "cabbage", "repolyo"], "pricePhp": 80, "category": "Produce", "unit": "kg", "notes": "DTI SRP Baseline"},
    {"keywords": ["kalabasa", "squash"], "pricePhp": 60, "category": "Produce", "unit": "kg", "notes": "DTI SRP Baseline"},
]

def seed_srp():
    print(f"Seeding {len(SRP_SEED_DATA)} SRP rules into the database...")
    count = 0
    for entry in SRP_SEED_DATA:
        try:
            # We use keywords as a deterministic ID source for seeding to avoid duplicates
            # but allow the database logic to handle the actual insert/update (upsert)
            database.upsert_price_rule(entry)
            count += 1
        except Exception as e:
            print(f"Failed to seed {entry['keywords'][0]}: {e}")
    
    print(f"Successfully seeded {count} rules.")

if __name__ == "__main__":
    seed_srp()
