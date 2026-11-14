"""Canonical ingredient-family vocabulary and source-evidence safety tagging.

Planner hard filters and catalog reconciliation share this module so a newly
supported Filipino alias cannot silently protect one path but not the other.
Static wheat tags intentionally require explicit wheat evidence: generic
``pancit`` and rice-based ``bihon`` remain hard-filter search terms without
being asserted as wheat metadata.
"""

from __future__ import annotations

import re


FISH_FAMILY_TOKENS = {
    "fish", "isda", "bangus", "milkfish", "tilapia", "galunggong",
    "salmon", "tuna", "tambakol", "tulingan", "tanigue", "bisugo", "bream",
    "seafood", "sardine", "sardines", "sardinas", "dilis", "dulong",
    "tinapa", "tuyo", "daing", "lapu", "maya",
}
SHELLFISH_FAMILY_TOKENS = {
    "shellfish", "shrimp", "hipon", "crab", "alimango", "alimasag",
    "squid", "pusit", "mussel", "mussels", "tahong", "clam", "clams",
    "oyster", "oysters", "lobster", "seafood",
}
DAIRY_FAMILY_TOKENS = {
    "dairy", "milk", "gatas", "cheese", "keso", "yogurt", "butter", "cream",
}
EGG_FAMILY_TOKENS = {"egg", "itlog"}
NUTS_FAMILY_TOKENS = {
    "nuts", "nut", "almond", "almonds", "cashew", "cashews", "walnut",
    "walnuts", "hazelnut", "hazelnuts", "pistachio", "pistachios", "pecan",
    "pecans",
}
PEANUT_FAMILY_TOKENS = {"peanut", "peanuts", "mani"}
SOY_FAMILY_TOKENS = {"soy", "soya", "soybean", "soybeans", "toyo", "tofu"}
GLUTEN_FAMILY_TOKENS = {
    "gluten", "wheat", "flour", "bread", "pasta", "noodle", "bihon", "miki",
    "pancit",
}

SEAFOOD_TOKENS = FISH_FAMILY_TOKENS | SHELLFISH_FAMILY_TOKENS
DAIRY_TOKENS = {"dairy", "milk", "cheese", "yogurt", "cream", "butter"}
EGG_TOKENS = {"egg"}
ALLERGEN_FAMILY_TOKENS = {
    "fish": FISH_FAMILY_TOKENS,
    "shellfish": SHELLFISH_FAMILY_TOKENS,
    "dairy": DAIRY_FAMILY_TOKENS,
    "egg": EGG_FAMILY_TOKENS,
    "nuts": NUTS_FAMILY_TOKENS,
    "peanut": PEANUT_FAMILY_TOKENS,
    "soy": SOY_FAMILY_TOKENS,
    "gluten": GLUTEN_FAMILY_TOKENS,
}

_SOURCE_TAG_TOKENS = {
    "contains_fish": FISH_FAMILY_TOKENS
    | {"kingfish", "mackerel", "anchovy", "anchovies", "patis", "pompano"},
    "contains_shellfish": SHELLFISH_FAMILY_TOKENS
    | {"alamang", "crabmeat", "prawn", "prawns"},
    "contains_pork": {"pork", "baboy", "pig", "bacon", "ham"},
    "contains_beef": {"beef"},
    "contains_chicken": {"chicken", "manok"},
    "contains_egg": EGG_FAMILY_TOKENS | {"eggs"},
    "contains_dairy": DAIRY_FAMILY_TOKENS,
    "contains_soy": SOY_FAMILY_TOKENS | {"tokwa", "miso"},
    "contains_peanut": PEANUT_FAMILY_TOKENS,
    # These are affirmative wheat/gluten signals. ``noodle``, ``bihon``, and
    # ``pancit`` are excluded because the source may explicitly describe rice.
    "contains_wheat": {
        "wheat", "gluten", "flour", "bread", "pasta", "miki", "canton", "mami",
        "misua",
    },
}

MANAGED_SAFETY_TAGS = set(_SOURCE_TAG_TOKENS) | {"contains_seafood"}

# Product-formula allergens are not always visible in the ingredient name. These
# bounded rules come from the manufacturers' official Philippines product pages
# and intentionally require an explicit brand/product phrase in source evidence.
_VERIFIED_PRODUCT_SAFETY_TAGS = {
    "knorr ginataang gulay": {
        "contains_wheat", "contains_dairy", "contains_shellfish", "contains_seafood",
    },
    "knorr shrimp cube": {
        "contains_dairy", "contains_shellfish", "contains_seafood",
    },
    "knorr shrimp cubes": {
        "contains_dairy", "contains_shellfish", "contains_seafood",
    },
    "knorr pork cube": {
        "contains_wheat", "contains_dairy", "contains_shellfish",
        "contains_seafood", "contains_soy", "contains_egg", "contains_pork",
    },
}


def _tokens(text: str) -> set[str]:
    return set(re.findall(r"[a-z]+", text.lower()))


def safety_tags_from_source_text(text: str) -> set[str]:
    """Return conservative safety tags supported by ingredient source text."""

    normalized = text.lower()
    dairy_text = re.sub(
        r"\b(?:coconut[\s,/-]+(?:milk|cream)|cream[\s,/-]+dory|kakang[\s,/-]+gata)\b",
        " ",
        normalized,
    )
    source_tokens = _tokens(normalized)
    dairy_tokens = _tokens(dairy_text)
    tags = {
        tag
        for tag, candidates in _SOURCE_TAG_TOKENS.items()
        if candidates & (dairy_tokens if tag == "contains_dairy" else source_tokens)
    }
    if tags & {"contains_fish", "contains_shellfish"}:
        tags.add("contains_seafood")
    for product_phrase, product_tags in _VERIFIED_PRODUCT_SAFETY_TAGS.items():
        if product_phrase in normalized:
            tags.update(product_tags)
    return tags
