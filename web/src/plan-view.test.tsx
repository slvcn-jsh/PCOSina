import { act, useState, type ComponentProps } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PlanView } from "./App";
import type { PlanSnapshot, RecipeDetail } from "./types";

Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

const recipes: RecipeDetail[] = [
  recipe("breakfast-1", "Egg Breakfast", "Breakfast"),
  recipe("breakfast-2", "Oat Breakfast", "Breakfast"),
  recipe("lunch-1", "Chicken Lunch", "Lunch"),
  recipe("lunch-2", "Fish Lunch", "Lunch"),
  recipe("dinner-1", "Vegetable Dinner", "Dinner"),
  recipe("dinner-2", "Tofu Dinner", "Dinner")
];

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
    days: [
      {
        dayLabel: "Mon",
        totalCalories: 1500,
        meals: [
          { mealLabel: "Breakfast", recipeId: "breakfast-1", title: "Egg Breakfast" },
          { mealLabel: "Lunch", recipeId: "lunch-1", title: "Chicken Lunch" },
          { mealLabel: "Dinner", recipeId: "dinner-1", title: "Vegetable Dinner" }
        ]
      }
    ],
    explanation: {
      targetCalories: 1800,
      targetProtein: 90,
      targetCarbs: 220,
      fiberMinTarget: 25
    }
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

describe("PlanView Android interaction parity", () => {
  it("opens recipe details from the meal card without an Open button", () => {
    const onOpenRecipe = vi.fn();
    renderPlan({ onOpenRecipe });

    expect(button("Open")).toBeNull();
    act(() => mealCard("Egg Breakfast").click());

    expect(onOpenRecipe).toHaveBeenCalledWith("breakfast-1");
  });

  it("confirms logging and locks handled meals from swapping", () => {
    const onMealStatusChange = vi.fn();
    act(() => root.render(<PlanHarness onMealStatusChange={onMealStatusChange} />));

    act(() => button("LOG")?.click());
    expect(container.textContent).toContain("Log this meal?");
    act(() => button("Log meal")?.click());

    expect(onMealStatusChange).toHaveBeenCalledWith(expect.any(String), "logged");
    expect(button("LOGGED")?.disabled).toBe(true);
    expect(swapButtons()[0]?.disabled).toBe(true);
  });

  it("supports skip undo and explicit swap selection", () => {
    const onMealStatusChange = vi.fn();
    renderPlan({ onMealStatusChange });

    act(() => buttons("SKIP")[1].click());
    expect(onMealStatusChange).toHaveBeenCalledWith(expect.any(String), "skipped");

    act(() => swapButtons()[2].click());
    expect(container.textContent).toContain("Swap Dinner");
    act(() => buttonContaining("Tofu Dinner")?.click());
    expect(container.textContent).toContain("Dinner swapped to Tofu Dinner.");
    expect(mealCard("Tofu Dinner")).toBeTruthy();
  });
});

function PlanHarness({ onMealStatusChange }: { onMealStatusChange: ReturnType<typeof vi.fn> }) {
  const [mealLogState, setMealLogState] = useState<Record<string, "logged" | "skipped">>({});
  return (
    <PlanView
      {...defaultProps()}
      mealLogState={mealLogState}
      onMealStatusChange={(key, status) => {
        onMealStatusChange(key, status);
        setMealLogState((current) => ({ ...current, [key]: status }));
      }}
    />
  );
}

function renderPlan(overrides: Partial<ComponentProps<typeof PlanView>> = {}) {
  const props: ComponentProps<typeof PlanView> = {
    ...defaultProps(),
    ...overrides
  };
  act(() => root.render(<PlanView {...props} />));
}

function defaultProps(): ComponentProps<typeof PlanView> {
  return {
    snapshot,
    recipesById: new Map(recipes.map((item) => [item.id, item])),
    busy: false,
    onGenerate: vi.fn(),
    onOpenRecipe: vi.fn(),
    onOpenGrocery: vi.fn(),
    mealLogState: {},
    onMealStatusChange: vi.fn()
  };
}

function mealCard(title: string): HTMLElement {
  const card = [...container.querySelectorAll<HTMLElement>(".android-plan-meal")]
    .find((item) => item.textContent?.includes(title));
  if (!card) throw new Error(`Meal card not found: ${title}`);
  return card;
}

function button(label: string): HTMLButtonElement | null {
  return buttons(label)[0] ?? null;
}

function buttonContaining(label: string): HTMLButtonElement | null {
  return [...container.querySelectorAll<HTMLButtonElement>("button")]
    .find((item) => item.textContent?.includes(label)) ?? null;
}

function buttons(label: string): HTMLButtonElement[] {
  return [...container.querySelectorAll<HTMLButtonElement>("button")]
    .filter((item) => item.textContent?.trim() === label);
}

function swapButtons(): HTMLButtonElement[] {
  return [...container.querySelectorAll<HTMLButtonElement>(".android-swap-action")];
}

function recipe(id: string, title: string, mealType: string): RecipeDetail {
  return {
    id,
    title,
    mealType,
    calories: 500,
    proteinGrams: 25,
    carbsGrams: 45,
    fiberGrams: 8,
    minutes: 25,
    tags: [mealType],
    ingredients: [],
    steps: []
  };
}
