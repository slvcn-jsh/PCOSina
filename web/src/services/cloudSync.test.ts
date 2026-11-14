import { describe, expect, it } from "vitest";
import { chooseMergeDirection } from "./cloudSync";

describe("chooseMergeDirection", () => {
  it("pulls remote data when the remote timestamp wins beyond skew", () => {
    expect(
      chooseMergeDirection({
        localUpdatedAtMs: 1000,
        remoteUpdatedAtMs: 2501,
        localHasData: true,
        remoteHasData: true
      })
    ).toBe("pull-remote");
  });

  it("pushes local data when remote is absent", () => {
    expect(
      chooseMergeDirection({
        localUpdatedAtMs: 1000,
        remoteUpdatedAtMs: 0,
        localHasData: true,
        remoteHasData: false
      })
    ).toBe("push-local");
  });

  it("does nothing inside timestamp skew", () => {
    expect(
      chooseMergeDirection({
        localUpdatedAtMs: 2000,
        remoteUpdatedAtMs: 2500,
        localHasData: true,
        remoteHasData: true
      })
    ).toBe("no-change");
  });
});
