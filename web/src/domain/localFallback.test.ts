import { beforeEach, describe, expect, it } from "vitest";
import { resolveOfflinePlanFallback } from "./localFallback";
import { resetDatabaseForTests, savePlanSnapshot } from "../storage/db";
import type { GeneratePlanResponse } from "../types";

const uid = "test-user";

describe("resolveOfflinePlanFallback", () => {
  beforeEach(async () => {
    await resetDatabaseForTests();
  });

  it("does not fabricate a plan when nothing has been saved", async () => {
    const result = await resolveOfflinePlanFallback(uid);
    expect(result.kind).toBe("unavailable");
    expect(result.message).toContain("cannot make a new plan right now");
  });

  it("reopens the saved authoritative plan as a restored cache", async () => {
    await savePlanSnapshot(uid, samplePlan("plan-a"), "backend", {
      queue: false,
      generatedAtMs: 1000
    });

    const result = await resolveOfflinePlanFallback(uid);

    expect(result.kind).toBe("saved-plan");
    if (result.kind === "saved-plan") {
      expect(result.snapshot.planId).toBe("plan-a");
      expect(result.snapshot.source).toBe("restored-cache");
      expect(result.snapshot.response.days).toHaveLength(1);
    }
  });
});

function samplePlan(planId: string): GeneratePlanResponse {
  return {
    weekLabel: "PCOSINA 7-Day Plan",
    days: [
      {
        dayLabel: "Day 1",
        totalCalories: 1500,
        meals: [{ mealLabel: "Breakfast", recipeId: "r1", title: "Egg Bowl" }]
      }
    ],
    status: "success",
    message: "ok",
    planId,
    requestId: "req-1",
    policyVersion: "test",
    timestamps: { requestedAtMs: 1, completedAtMs: 2 }
  };
}
