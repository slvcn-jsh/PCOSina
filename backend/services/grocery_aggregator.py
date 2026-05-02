import re
from typing import List, Dict, Tuple, Optional
from backend.price_catalog import _parse_quantity, _unit_to_kg, _unit_to_l, _PIECE_WEIGHT_KG
from backend.database import _normalize_token

# Basic conversion map to bridge disparate units into a base unit (grams/ml)
# 1 clove = 5g, 1 tbsp = 15g, 1 cup = 240g
BASE_CONVERSIONS = {
    "piece": 20.0, # Default piece weight if unknown
    "cup": 240.0,
    "tbsp": 15.0,
    "tsp": 5.0,
    "g": 1.0,
    "kg": 1000.0,
    "ml": 1.0,
    "l": 1000.0
}

def aggregate_grocery_list(plan: List[Dict]) -> Dict[str, Dict]:
    """
    Consolidates ingredients from a plan into a summed grocery list.
    """
    aggregated: Dict[str, Dict] = {}

    for day in plan:
        for meal in day.get("meals", []):
            for ing in meal.get("ingredients", []):
                name = str(ing.get("name", ""))
                qty = str(ing.get("quantity", ""))
                
                # Canonicalize name
                tokens = name.lower().split()
                canonical_name = _normalize_token(tokens[-1] if tokens else name)
                
                # Extract Quantity
                val, unit = _parse_quantity(f"{qty} {name}")
                if val is None:
                    val = 1.0
                    unit = "piece"
                
                # Convert to base grams/ml
                # Simplified aggregation logic
                base_val = val * BASE_CONVERSIONS.get(unit, 1.0)
                
                if canonical_name not in aggregated:
                    aggregated[canonical_name] = {"total_val": 0.0, "unit": "g", "original_names": set()}
                
                aggregated[canonical_name]["total_val"] += base_val
                aggregated[canonical_name]["original_names"].add(name)

    return aggregated
