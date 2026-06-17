"""Priority canonical nutrition and price reference mappings.

Nutrition values reuse the repository's existing local reference profiles and
remain pending review. Price mappings select reviewed NCR audit rows.
"""

from __future__ import annotations


PRIORITY_NUTRITION_PROFILE_KEYS: dict[str, str] = {
    "ing_rice_generic": "rice",
    "ing_egg": "egg",
    "ing_chicken_generic": "chicken",
    "ing_chicken_breast_raw": "chicken",
    "ing_chicken_thigh_raw": "chicken",
    "ing_pork_generic": "pork",
    "ing_pork_belly_raw": "pork belly",
    "ing_fish_generic": "fish",
    "ing_tilapia": "tilapia",
    "ing_bangus": "milkfish",
    "ing_tomato": "tomato",
    "ing_onion_generic": "onion",
    "ing_onion_red": "onion",
    "ing_onion_white": "onion",
    "ing_garlic": "garlic",
    "ing_bok_choy": "bok choy",
    "ing_kangkong": "spinach",
    "ing_malunggay": "moringa leaves",
    "ing_bitter_melon": "bitter melon",
    "ing_sayote": "sayote",
    "ing_squash": "squash",
    "ing_cooking_oil": "cooking oil",
    "ing_canola_oil": "cooking oil",
    "ing_olive_oil": "cooking oil",
    "ing_soy_sauce": "soy sauce",
    "ing_vinegar": "vinegar",
}


PRIORITY_PRICE_KEYWORDS: dict[str, tuple[str, ...]] = {
    "ing_rice_generic": ("rice",),
    "ing_egg": ("egg",),
    "ing_chicken_generic": ("chicken",),
    "ing_chicken_breast_raw": ("chicken breast", "chicken"),
    "ing_chicken_thigh_raw": ("chicken thigh", "chicken"),
    "ing_pork_generic": ("pork",),
    "ing_pork_belly_raw": ("pork belly", "liempo"),
    "ing_fish_generic": ("fish",),
    "ing_tilapia": ("tilapia",),
    "ing_bangus": ("bangus", "milkfish"),
    "ing_tomato": ("tomato",),
    "ing_onion_generic": ("onion",),
    "ing_onion_red": ("red onion",),
    "ing_onion_white": ("white onion",),
    "ing_garlic": ("garlic",),
    "ing_bok_choy": ("pechay", "bok choy"),
    "ing_kangkong": ("kangkong",),
    "ing_malunggay": ("malunggay",),
    "ing_bitter_melon": ("ampalaya",),
    "ing_sayote": ("sayote",),
    "ing_squash": ("kalabasa",),
    "ing_cooking_oil": ("cooking oil",),
    "ing_canola_oil": ("canola oil", "cooking oil"),
    "ing_olive_oil": ("olive oil", "cooking oil"),
    "ing_soy_sauce": ("soy sauce",),
    "ing_vinegar": ("vinegar",),
}
