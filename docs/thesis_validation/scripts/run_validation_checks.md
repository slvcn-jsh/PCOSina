# Run Validation Checks

Run from repository root on the `dev` branch.

## 1. Regenerate This Evidence Pack

```powershell
python docs/thesis_validation/scripts/extract_validation_data.py
```

## 2. Backend Tests Most Relevant To Planner And Validation

```powershell
python -m pytest backend/tests/test_policy_config.py -q
python -m pytest backend/tests/test_schema_contract.py -q
python -m pytest backend/tests/test_planner_response_contract.py -q
python -m pytest backend/tests/test_no_safe_plan_contract.py -q
python -m pytest backend/tests/test_worker_plan_jobs.py -q
python -m pytest backend/tests/test_meal_planner.py -q
```

## 3. Android Unit And Instrumented Tests

```powershell
.\gradlew testDebugUnitTest
.\gradlew connectedDebugAndroidTest
```

## 4. Current Environment Warning

In the environment used to generate this pack, importing `backend/services/meal_planner.py` was blocked:

```text
OSError: [WinError 4551] An Application Control policy has blocked this file
```

If that same application-control block exists on the validation machine, planner tests that import OR-Tools-backed code may fail before test execution. In that case, run the full planner tests on a machine where OR-Tools can load correctly.
