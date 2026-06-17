import { beforeEach, describe, expect, it } from "vitest";
import {
  clearUserWeekData,
  enqueueOperation,
  getProfile,
  listPendingOperations,
  listPlanSnapshots,
  resetDatabaseForTests,
  savePlanSnapshot,
  saveProfile,
  stableStringify
} from "./db";
import { defaultProfile } from "../domain/profile";

describe("offline queue", () => {
  beforeEach(async () => {
    await resetDatabaseForTests();
  });

  it("deduplicates operations with stable payload ordering", async () => {
    await enqueueOperation("u1", "profile_upsert", { b: 2, a: 1 });
    await enqueueOperation("u1", "profile_upsert", { a: 1, b: 2 });

    const pending = await listPendingOperations("u1");
    expect(pending).toHaveLength(1);
  });

  it("stableStringify sorts object keys recursively", () => {
    expect(stableStringify({ z: 1, a: { y: 2, b: 3 } })).toBe('{"a":{"b":3,"y":2},"z":1}');
  });

  it("clears week data without deleting the profile", async () => {
    await saveProfile("u1", { ...defaultProfile(), displayName: "Ana" }, { queue: false });
    await savePlanSnapshot("u1", {
      weekLabel: "2026-06-08",
      status: "success",
      message: "Ready",
      days: [],
      groceryOutput: { finalGroceryEstimatePhp: 1000 }
    });

    await clearUserWeekData("u1");

    expect(await listPlanSnapshots("u1")).toEqual([]);
    expect((await listPendingOperations("u1")).filter((item) => item.type === "plan_saved" || item.type === "grocery_saved")).toEqual([]);
    expect((await getProfile("u1")).displayName).toBe("Ana");
  });
});
