import type { PlanSnapshot } from "../types";
import { getActivePlanSnapshot, listPlanSnapshots } from "../storage/db";

export type OfflinePlanFallback =
  | {
      kind: "saved-plan";
      snapshot: PlanSnapshot;
      message: string;
    }
  | {
      kind: "unavailable";
      message: string;
    };

export async function resolveOfflinePlanFallback(uid: string): Promise<OfflinePlanFallback> {
  const active = await getActivePlanSnapshot(uid);
  if (active) {
    return {
      kind: "saved-plan",
      snapshot: {
        ...active,
        source: "restored-cache"
      },
      message:
        "PCOSina reopened your latest saved plan. New plans need the meal planner to be reachable."
    };
  }

  const [newest] = await listPlanSnapshots(uid);
  if (newest) {
    return {
      kind: "saved-plan",
      snapshot: {
        ...newest,
        source: "restored-cache"
      },
      message:
        "PCOSina reopened your newest saved plan. New plans need the meal planner to be reachable."
    };
  }

  return {
    kind: "unavailable",
    message:
      "PCOSina cannot make a new plan right now. You can still edit your profile and pantry, and saved plans will open here."
  };
}
