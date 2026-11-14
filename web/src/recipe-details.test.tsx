import { act, type ComponentProps } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationsView, RecipeDetailsView } from "./App";
import { defaultProfile } from "./domain/profile";
import type { PlanSnapshot, RecipeDetail } from "./types";

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  localStorage.clear();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("RecipeDetailsView Android interaction parity", () => {
  it("renders nutrition, ingredients, and ordered cooking steps", () => {
    renderRecipe();

    expect(container.textContent).toContain("Nutrition per person");
    expect(container.textContent).toContain("2 item(s)");
    expect(container.textContent).toContain("Cooking Steps");
    expect(container.textContent).toContain("Do this next");
  });

  it("logs a planned meal and saves the follow-up check-in", () => {
    const onMealStatusChange = vi.fn();
    renderRecipe({ onMealStatusChange });

    act(() => button("Log Breakfast for today").click());
    expect(onMealStatusChange).toHaveBeenCalledWith(expect.stringContaining("breakfast-1"), "logged");
    expect(container.querySelector('[role="dialog"][aria-label="Meal check in"]')).toBeTruthy();
    act(() => button("Save check-in").click());
    expect(container.textContent).toContain("Meal check-in saved.");
  });

  it("locks logging when the recipe is not in today's plan", () => {
    renderRecipe({ recipeId: "other-recipe" });

    expect(container.textContent).toContain("Not in today’s plan");
    expect(button("Logging unavailable").disabled).toBe(true);
  });
});

describe("NotificationsView Android interaction parity", () => {
  it("shows reminder summaries and opens Settings", () => {
    localStorage.setItem("pcosina-reminders:user-1", JSON.stringify({ master: true, meals: true, breakfast: "07:30" }));
    const onOpenSettings = vi.fn();
    act(() => root.render(
      <NotificationsView
        profile={{ ...defaultProfile(), displayName: "Ana" }}
        session={{ uid: "user-1", mode: "firebase", email: "ana@example.com", updatedAtMs: Date.now() }}
        online
        onClose={vi.fn()}
        onOpenSettings={onOpenSettings}
      />
    ));

    expect(container.textContent).toContain("Reminders are on");
    expect(container.textContent).toContain("Breakfast 7:30 AM");
    act(() => titleButton("Settings").click());
    expect(onOpenSettings).toHaveBeenCalledOnce();
  });
});

function renderRecipe(overrides: Partial<ComponentProps<typeof RecipeDetailsView>> = {}) {
  const props: ComponentProps<typeof RecipeDetailsView> = {
    recipe,
    recipeId: "breakfast-1",
    snapshot: currentWeekSnapshot(),
    mealLogState: {},
    onMealStatusChange: vi.fn(),
    onClose: vi.fn(),
    onOpenProgress: vi.fn(),
    ...overrides
  };
  act(() => root.render(<RecipeDetailsView {...props} />));
}

const recipe: RecipeDetail = {
  id: "breakfast-1",
  title: "Egg and Vegetable Breakfast",
  mealType: "Breakfast",
  calories: 420,
  proteinGrams: 28,
  carbsGrams: 38,
  fiberGrams: 8,
  sodiumMg: 430,
  sugarGrams: 5,
  minutes: 20,
  nutritionConfidence: "high",
  nutritionReviewStatus: "verified",
  tags: [],
  ingredients: [
    { name: "Egg", quantity: "2 pieces" },
    { name: "Tomato", quantity: "1 medium" }
  ],
  steps: ["Prepare the vegetables.", "Cook the eggs and serve."]
};

function currentWeekSnapshot(): PlanSnapshot {
  const monday = new Date();
  const offset = monday.getDay() === 0 ? -6 : 1 - monday.getDay();
  monday.setDate(monday.getDate() + offset);
  monday.setHours(0, 0, 0, 0);
  return {
    planId: "plan-recipe",
    uid: "user-1",
    generatedAtMs: monday.getTime(),
    updatedAtMs: monday.getTime(),
    active: true,
    source: "backend",
    response: {
      weekLabel: [
        monday.getFullYear(),
        String(monday.getMonth() + 1).padStart(2, "0"),
        String(monday.getDate()).padStart(2, "0")
      ].join("-"),
      status: "success",
      message: "Ready",
      days: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((dayLabel) => ({
        dayLabel,
        totalCalories: 1500,
        meals: [{ mealLabel: "Breakfast", recipeId: "breakfast-1", title: recipe.title }]
      }))
    }
  };
}

function button(label: string): HTMLButtonElement {
  const match = [...container.querySelectorAll<HTMLButtonElement>("button")]
    .find((item) => item.textContent?.trim() === label);
  if (!match) throw new Error(`Button not found: ${label}`);
  return match;
}

function titleButton(title: string): HTMLButtonElement {
  const match = container.querySelector<HTMLButtonElement>(`button[title="${title}"]`);
  if (!match) throw new Error(`Button not found with title: ${title}`);
  return match;
}
