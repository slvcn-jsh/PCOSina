import { act, type ComponentProps } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ProgressView } from "./App";
import { defaultProfile } from "./domain/profile";
import type { PlanSnapshot } from "./types";

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const snapshot: PlanSnapshot = {
  planId: "plan-1",
  uid: "user-1",
  generatedAtMs: new Date("2026-06-08T08:00:00").getTime(),
  updatedAtMs: new Date("2026-06-08T08:00:00").getTime(),
  active: true,
  source: "backend",
  response: {
    weekLabel: "2026-06-08",
    status: "success",
    message: "Ready",
    days: [{
      dayLabel: "Mon",
      totalCalories: 1500,
      meals: [
        { mealLabel: "Breakfast", recipeId: "breakfast-1", title: "Egg Breakfast" },
        { mealLabel: "Lunch", recipeId: "lunch-1", title: "Chicken Lunch" }
      ]
    }],
    explanation: { avgProtein: 82, avgCarbs: 190, avgFats: 62 },
    groceryOutput: { weeklyBudgetPhp: 1500, finalGroceryEstimatePhp: 1200 }
  }
};

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("ProgressView Android interaction parity", () => {
  it("opens highlights and expands review analytics", () => {
    renderProgress();

    act(() => buttonContaining("Weekly Highlights").click());
    expect(container.querySelector('[role="dialog"][aria-label="Weekly Highlights"]')).toBeTruthy();
    act(() => button("Got it").click());
    expect(container.querySelector('[role="dialog"][aria-label="Weekly Highlights"]')).toBeNull();

    const adherence = buttonContaining("Weekly adherence");
    expect(adherence.getAttribute("aria-expanded")).toBe("false");
    act(() => adherence.click());
    expect(adherence.getAttribute("aria-expanded")).toBe("true");
    expect(container.textContent).toContain("Monday");
  });

  it("saves today's reflection and weekly review", () => {
    renderProgress();

    act(() => button("Check in").click());
    expect(container.querySelector('[role="dialog"][aria-label="Today check in"]')).toBeTruthy();
    act(() => button("Save Reflection").click());
    expect(container.textContent).toContain("Reflection saved for today");

    act(() => button("Review week").click());
    const spend = container.querySelector<HTMLInputElement>('input[placeholder="PHP 0"]');
    if (!spend) throw new Error("Weekly spend input not found");
    act(() => {
      spend.value = "1100";
      spend.dispatchEvent(new Event("input", { bubbles: true }));
    });
    act(() => button("Too expensive").click());
    act(() => button("Save Review").click());

    expect(container.textContent).toContain("Weekly review saved.");
    act(() => buttonContaining("Weekly savings").click());
    expect(container.textContent).toContain("Actual");
  });

  it("opens Support from the progress CTA", () => {
    const onOpenSupport = vi.fn();
    renderProgress({ onOpenSupport });

    act(() => buttonContaining("Open Support").click());
    expect(onOpenSupport).toHaveBeenCalledOnce();
  });
});

function renderProgress(overrides: Partial<ComponentProps<typeof ProgressView>> = {}) {
  const props: ComponentProps<typeof ProgressView> = {
    snapshot,
    profile: { ...defaultProfile(), goal: "Weight Loss", targetWeightKg: 58 },
    mealLogState: { "plan-1:0:0:breakfast-1": "logged" },
    onOpenSupport: vi.fn(),
    ...overrides
  };
  act(() => root.render(<ProgressView {...props} />));
}

function button(label: string): HTMLButtonElement {
  const match = [...container.querySelectorAll<HTMLButtonElement>("button")]
    .find((item) => item.textContent?.trim() === label);
  if (!match) throw new Error(`Button not found: ${label}`);
  return match;
}

function buttonContaining(label: string): HTMLButtonElement {
  const match = [...container.querySelectorAll<HTMLButtonElement>("button")]
    .find((item) => item.textContent?.includes(label));
  if (!match) throw new Error(`Button not found containing: ${label}`);
  return match;
}
