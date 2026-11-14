import { act, type ComponentProps } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { HomeView } from "./App";
import { defaultProfile } from "./domain/profile";
import type { PlanSnapshot, RecipeDetail } from "./types";

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

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

describe("HomeView Android interaction parity", () => {
  it("opens goal information and dismisses it", () => {
    renderHome();

    act(() => buttonContaining("Weight Loss").click());
    expect(container.querySelector('[role="dialog"][aria-label="Weight Loss goal information"]')).toBeTruthy();
    expect(container.textContent).toContain("gradual weight management");
    act(() => button("OKAY").click());
    expect(container.querySelector('[role="dialog"]')).toBeNull();
  });

  it("opens today's meal card and next-meal primary action", () => {
    const onOpenRecipe = vi.fn();
    renderHome({ onOpenRecipe });

    act(() => buttonContaining("Egg Breakfast").click());
    expect(onOpenRecipe).toHaveBeenCalledWith("breakfast-1");

    act(() => button("Open Breakfast").click());
    expect(onOpenRecipe).toHaveBeenLastCalledWith("breakfast-1");
  });

  it("routes users without a plan to Meal Plan", () => {
    const onOpenPlan = vi.fn();
    renderHome({ activePlan: null, onOpenPlan });

    expect(container.textContent).toContain("No plan yet");
    act(() => button("Go to Plan").click());
    expect(onOpenPlan).toHaveBeenCalledOnce();
  });
});

function renderHome(overrides: Partial<ComponentProps<typeof HomeView>> = {}) {
  const props: ComponentProps<typeof HomeView> = {
    profile: {
      ...defaultProfile(),
      displayName: "Ana",
      goal: "Weight Loss",
      isProfileCompleted: true
    },
    activePlan: currentWeekSnapshot(),
    recipesById: new Map<string, RecipeDetail>([
      ["breakfast-1", recipe("breakfast-1", "Egg Breakfast", 410)],
      ["lunch-1", recipe("lunch-1", "Chicken Lunch", 520)],
      ["dinner-1", recipe("dinner-1", "Fish Dinner", 480)]
    ]),
    mealLogState: {},
    online: true,
    busy: false,
    onOpenRecipe: vi.fn(),
    onOpenPlan: vi.fn(),
    onOpenProgress: vi.fn(),
    onOpenSettings: vi.fn(),
    ...overrides
  };
  act(() => root.render(<HomeView {...props} />));
}

function currentWeekSnapshot(): PlanSnapshot {
  const monday = new Date();
  const offset = monday.getDay() === 0 ? -6 : 1 - monday.getDay();
  monday.setDate(monday.getDate() + offset);
  monday.setHours(0, 0, 0, 0);
  const labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  return {
    planId: "plan-home",
    uid: "user-1",
    generatedAtMs: monday.getTime(),
    updatedAtMs: monday.getTime(),
    active: true,
    source: "backend",
    response: {
      weekLabel: monday.toISOString().slice(0, 10),
      status: "success",
      message: "Ready",
      days: labels.map((dayLabel) => ({
        dayLabel,
        totalCalories: 1500,
        meals: [
          { mealLabel: "Breakfast", recipeId: "breakfast-1", title: "Egg Breakfast" },
          { mealLabel: "Lunch", recipeId: "lunch-1", title: "Chicken Lunch" },
          { mealLabel: "Dinner", recipeId: "dinner-1", title: "Fish Dinner" }
        ]
      }))
    }
  };
}

function recipe(id: string, title: string, calories: number): RecipeDetail {
  return { id, title, calories, tags: [], ingredients: [], steps: [] };
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
