"""Export runtime meal-catalog nutrition and pricing evidence to Excel."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter


REPO_ROOT = Path(__file__).resolve().parents[1]
BACKEND_ROOT = REPO_ROOT / "backend"
RUNTIME = BACKEND_ROOT / "recipes.json"
PRICED_DRAFT = BACKEND_ROOT / "seed_data" / "pcosina_philfct_portioned_priced_catalog_v2_draft.json"
OUT = (
    REPO_ROOT
    / "docs"
    / "thesis_validation"
    / "03_ACTUAL_SYSTEM_DATA_EXPORTS"
    / "pcosina_runtime_catalog_philfct_pricing_evidence.xlsx"
)

NUTRIENT_KEYS = ("calories", "protein_g", "carbs_g", "fat_g", "fiber_g")

BUDGET_FCT = {
    "cooked white rice": (129, 2.1, 29.7, 0.2, 0.4),
    "cooked munggo": (54, 4.8, 7.0, 0.5, 1.6),
    "egg": (139, 12.3, 1.4, 9.4, 0.0),
    "malunggay": (108, 9.7, 12.7, 2.0, 6.7),
    "tomato": (25, 0.8, 5.2, 0.1, 0.3),
    "onion": (52, 1.7, 10.5, 0.3, 2.0),
    "garlic": (129, 7.0, 24.6, 0.3, 1.7),
    "cooking oil": (896, 0.0, 0.0, 99.6, 0.0),
    "eggplant": (25, 1.0, 4.9, 0.1, 1.5),
    "pechay": (20, 1.7, 3.2, 0.2, 1.5),
    "okra": (30, 1.0, 6.1, 0.2, 2.6),
    "sitaw": (52, 4.0, 7.9, 0.5, 2.7),
    "squash": (47, 0.4, 10.8, 0.2, 1.1),
    "banana": (104, 0.9, 23.1, 0.9, 2.7),
    "papaya": (24, 0.7, 4.9, 0.2, 0.7),
    "peanuts": (617, 25.8, 17.1, 49.5, 8.6),
    "chicken breast": (131, 21.6, 0.0, 5.0, 0.0),
    "milkfish": (137, 23.4, 0.0, 4.8, 0.0),
    "pineapple": (55, 0.4, 13.0, 0.2, 1.4),
    "sardines": (180, 22.0, 0.0, 10.0, 0.0),
    "tuna": (132, 28.0, 0.0, 1.0, 0.0),
    "tofu": (80, 8.0, 2.0, 5.0, 1.0),
}

ADDON_FCT = {
    "cooked white rice": ("Rice, well-milled, boiled", "A020", 100.0, (129.0, 2.1, 29.7, 0.2, 0.4)),
    "pechay and tomato vegetable side": (
        "Pechay and tomato side, PhilFCT component estimate",
        "PCOSINA-GLOW-001",
        100.0,
        (23.0, 1.4, 4.2, 0.2, 1.2),
    ),
    "banana": ("Saging, cavendish, hinog", "E085", 80.0, (104.0, 0.9, 23.1, 0.9, 2.7)),
    "cooked munggo": ("Mung bean, boiled", "D132", 150.0, (54.0, 4.8, 7.0, 0.5, 1.6)),
}


def grams_from_quantity(quantity: Any) -> float | None:
    match = re.search(r"([-+]?\d+(?:\.\d+)?)\s*g\b", str(quantity or "").lower())
    return float(match.group(1)) if match else None


def contribution(portion_g: float, per_100g: tuple[float, float, float, float, float]) -> dict[str, float]:
    multiplier = float(portion_g) / 100.0
    return {
        "calories": round(multiplier * per_100g[0], 2),
        "protein_g": round(multiplier * per_100g[1], 2),
        "carbs_g": round(multiplier * per_100g[2], 2),
        "fat_g": round(multiplier * per_100g[3], 2),
        "fiber_g": round(multiplier * per_100g[4], 2),
    }


def append_sheet(wb: Workbook, title: str, rows: list[dict[str, Any]]) -> None:
    ws = wb.create_sheet(title)
    if not rows:
        ws.append(["No rows"])
        return
    headers = list(rows[0].keys())
    ws.append(headers)
    for row in rows:
        ws.append([row.get(header) for header in headers])
    header_fill = PatternFill("solid", fgColor="1F4E78")
    for cell in ws[1]:
        cell.fill = header_fill
        cell.font = Font(color="FFFFFF", bold=True)
        cell.alignment = Alignment(horizontal="center", vertical="center", wrap_text=True)
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = ws.dimensions
    for column_cells in ws.columns:
        header = str(column_cells[0].value or "")
        max_len = max(len(str(cell.value or "")) for cell in column_cells[:200])
        width = min(max(max_len + 2, len(header) + 2), 55)
        ws.column_dimensions[get_column_letter(column_cells[0].column)].width = width
    for row in ws.iter_rows(min_row=2):
        for cell in row:
            cell.alignment = Alignment(vertical="top", wrap_text=True)


def main() -> int:
    recipes = json.loads(RUNTIME.read_text(encoding="utf-8"))
    priced = {
        item["sourceRecipeId"]: item
        for item in json.loads(PRICED_DRAFT.read_text(encoding="utf-8")).get("meals", [])
        if item.get("sourceRecipeId")
    }

    summary_rows: list[dict[str, Any]] = []
    nutrition_rows: list[dict[str, Any]] = []
    pricing_rows: list[dict[str, Any]] = []

    for recipe in recipes:
        rid = str(recipe.get("id") or "")
        nutrition = recipe.get("nutrition") or {}
        summary_rows.append(
            {
                "Recipe ID": rid,
                "Meal Name": recipe.get("name"),
                "Meal Type": recipe.get("mealType"),
                "Calories": nutrition.get("calories"),
                "Protein (g)": nutrition.get("protein_g"),
                "Carbs (g)": nutrition.get("carbs_g"),
                "Fat (g)": nutrition.get("fat_g"),
                "Fiber (g)": nutrition.get("fiber_g"),
                "Estimated Cost (PHP)": recipe.get("estimatedCostPhp"),
                "Nutrition Source": recipe.get("nutritionDataSource"),
                "PhilFCT Coverage": recipe.get("philfctCoverage"),
                "Review Status": recipe.get("nutritionReviewStatus"),
                "Complete Plate Additions": ", ".join(recipe.get("completePlateAdditions") or []),
                "Tags": ", ".join(recipe.get("tags") or []),
            }
        )

        priced_meal = priced.get(rid)
        source_rows_by_name = {}
        if priced_meal:
            for item in priced_meal.get("portionIngredients") or []:
                source_rows_by_name.setdefault(str(item.get("clean_name") or "").lower(), []).append(item)

        for ingredient in recipe.get("ingredients") or []:
            name = str(ingredient.get("name") or "").strip()
            if not name or ingredient.get("excludedFromNutritionTotals"):
                continue

            portion_g = grams_from_quantity(ingredient.get("quantity"))
            per_100g = None
            philfct_name = ingredient.get("philfctName")
            philfct_code = ingredient.get("philfctCode")
            computed = None
            match_status = ""
            evidence_source = ""
            remarks = ""

            source_candidates = source_rows_by_name.get(name.lower()) or []
            if source_candidates:
                source = source_candidates.pop(0)
                source_rows_by_name[name.lower()] = source_candidates
                portion_g = float(source.get("portion_g") or portion_g or 0)
                philfct = source.get("philfct") or {}
                per_100g_map = philfct.get("per_100g") or {}
                per_100g = (
                    float(per_100g_map.get("calories") or 0),
                    float(per_100g_map.get("protein_g") or 0),
                    float(per_100g_map.get("carbs_g") or 0),
                    float(per_100g_map.get("fat_g") or 0),
                    float(per_100g_map.get("fiber_g") or 0),
                )
                philfct_name = philfct.get("philfct_name") or philfct_name
                philfct_code = philfct.get("philfct_code") or philfct_code
                computed = source.get("computed") or {}
                match_status = philfct.get("match_status") or ""
                evidence_source = "PhilFCT portioned priced draft"
                pricing = source.get("pricing") or {}
            elif name in ADDON_FCT:
                philfct_name, philfct_code, default_g, per_100g = ADDON_FCT[name]
                portion_g = portion_g or default_g
                computed = contribution(portion_g, per_100g)
                match_status = "complete_plate_addon"
                evidence_source = "PCOSina complete-plate companion"
                pricing = {}
            elif name in BUDGET_FCT:
                per_100g = BUDGET_FCT[name]
                portion_g = portion_g or 0.0
                computed = contribution(portion_g, per_100g)
                philfct_name = ingredient.get("philfctName") or name
                philfct_code = ingredient.get("philfctCode") or "PCOSINA-BUDGET-SUPPORT"
                match_status = "budget_support_component"
                evidence_source = "PCOSina budget-support PhilFCT-style component"
                pricing = {}
            else:
                pricing = {}
                remarks = "No ingredient-level PhilFCT row exported; review source mapping."

            if per_100g is None:
                per_100g = (None, None, None, None, None)
            computed = computed or {}
            portion_g = float(portion_g or 0)
            multiplier = round(portion_g / 100.0, 4) if portion_g else None

            nutrition_rows.append(
                {
                    "Recipe ID": rid,
                    "Meal Name": recipe.get("name"),
                    "Meal Type": recipe.get("mealType"),
                    "Ingredient": name,
                    "Portion (g)": round(portion_g, 2) if portion_g else None,
                    "Multiplier (portion/100)": multiplier,
                    "PhilFCT Food Name/Code": f"{philfct_name or ''} - {philfct_code or ''}".strip(" -"),
                    "kcal/100g": per_100g[0],
                    "Protein/100g": per_100g[1],
                    "Carbs/100g": per_100g[2],
                    "Fat/100g": per_100g[3],
                    "Fiber/100g": per_100g[4],
                    "Computed kcal": round(float(computed.get("calories") or 0), 2),
                    "Computed Protein": round(float(computed.get("protein_g") or 0), 2),
                    "Computed Carbs": round(float(computed.get("carbs_g") or 0), 2),
                    "Computed Fat": round(float(computed.get("fat_g") or 0), 2),
                    "Computed Fiber": round(float(computed.get("fiber_g") or 0), 2),
                    "Match Status": match_status,
                    "Evidence Source": evidence_source,
                    "Remarks": remarks,
                }
            )

            pricing_rows.append(
                {
                    "Recipe ID": rid,
                    "Meal Name": recipe.get("name"),
                    "Ingredient": name,
                    "Portion Used": ingredient.get("quantity"),
                    "Ingredient Cost PHP": ingredient.get("priceCostPhp"),
                    "Pricing Status": pricing.get("status") or ingredient.get("pricingSource") or "",
                    "Price Rule ID": pricing.get("price_rule_id") or "",
                    "Market Unit": pricing.get("market_unit") or "",
                    "Market Price PHP": pricing.get("price_php") or "",
                    "Market Source": pricing.get("market_source") or "",
                    "Source URL": pricing.get("source_url") or "",
                    "Confidence": pricing.get("confidence") or "",
                }
            )

    wb = Workbook()
    default = wb.active
    wb.remove(default)
    append_sheet(
        wb,
        "README",
        [
            {
                "Topic": "Purpose",
                "Details": "Evidence workbook for the active PCOSina runtime meal catalog. It documents recipe totals, ingredient-level PhilFCT nutrition inputs, and consumed-portion pricing references.",
            },
            {
                "Topic": "Nutrition Formula",
                "Details": "Ingredient contribution = (portion in grams / 100) x PhilFCT value per 100g. Meal total = sum of ingredient contributions.",
            },
            {
                "Topic": "Runtime Catalog",
                "Details": f"{len(recipes)} active runtime meals from backend/recipes.json.",
            },
            {
                "Topic": "Water",
                "Details": "Water is listed in meal ingredients for completeness but excluded from nutrition totals.",
            },
        ],
    )
    append_sheet(wb, "Meal Summary", summary_rows)
    append_sheet(wb, "PhilFCT Nutrition Inputs", nutrition_rows)
    append_sheet(wb, "Pricing Inputs", pricing_rows)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    wb.save(OUT)
    print(json.dumps({"path": str(OUT), "recipes": len(recipes), "nutritionRows": len(nutrition_rows), "pricingRows": len(pricing_rows)}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
