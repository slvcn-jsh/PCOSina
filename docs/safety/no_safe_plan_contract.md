# No-Safe-Plan Contract

When planner constraints cannot be satisfied safely, PCOSINA must return:

```json
{
  "status": "no-safe-plan",
  "machineReasonCodes": ["MODEL_INFEASIBLE"],
  "humanGuidance": ["..."],
  "suggestedRelaxations": ["..."],
  "policyVersion": "policy-vN:<id>",
  "diagnosticsReference": "<request-id>",
  "timestamps": {"requestedAtMs": 0, "completedAtMs": 0}
}
```

## Required Rules
- Never emit unsafe heuristic output as successful authoritative plan.
- Keep allergy/restriction safety hard and non-overridable by policy.
- Guidance may suggest only safe preference relaxations (budget/time/variety), not safety rule bypass.

## API Behavior
- Endpoint remains successful HTTP response with explicit status contract.
- `status=success` and `status=no-safe-plan` share one response schema.
