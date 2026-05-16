import sys
from pathlib import Path

import pytest
from pydantic import ValidationError

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import main
from domain.models import GeneratePlanRequest, UserProfile


def test_plan_cache_key_is_fixed_size_hash_not_raw_request_json():
    request = GeneratePlanRequest(
        profile=UserProfile(
            displayName="A" * 80,
            pantryItems=[f"pantry-{i}" for i in range(100)],
            allergies=["peanut", "dairy"],
        ),
        days=7,
        mealsPerDay=3,
    )

    key = main._cache_key(request)

    assert key.startswith("sha256:")
    assert len(key) == len("sha256:") + 64
    assert "pantry-" not in key
    assert "displayName" not in key


def test_user_profile_rejects_oversized_strings_and_lists():
    with pytest.raises(ValidationError):
        UserProfile(displayName="x" * 81)

    with pytest.raises(ValidationError):
        UserProfile(allergies=[f"allergy-{i}" for i in range(31)])

    with pytest.raises(ValidationError):
        UserProfile(pantryItems=["x" * 121])


def test_generate_plan_request_rejects_excessive_slot_counts_and_start_date_length():
    with pytest.raises(ValidationError):
        GeneratePlanRequest(profile=UserProfile(), days=365)

    with pytest.raises(ValidationError):
        GeneratePlanRequest(profile=UserProfile(), mealsPerDay=20)

    with pytest.raises(ValidationError):
        GeneratePlanRequest(profile=UserProfile(), startDate="2026-05-16T00:00:00Z")
