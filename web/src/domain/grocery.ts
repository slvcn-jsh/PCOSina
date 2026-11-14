import type { GeneratePlanResponse, GroceryOutputItem, PantryEntry, RecipeDetail } from "../types";

export interface GroceryDisplayItem extends GroceryOutputItem {
  pantryMatched: boolean;
}

export function moneyPhp(value: number | null | undefined): string {
  if (typeof value !== "number" || Number.isNaN(value)) return "PHP --";
  return `PHP ${Math.round(value).toLocaleString("en-PH")}`;
}

export function groceryItemsFromPlan(plan: GeneratePlanResponse | null | undefined): GroceryOutputItem[] {
  return plan?.groceryOutput?.items?.filter((item) => item.name.trim()) ?? [];
}

export function groceryDisplayItems(
  plan: GeneratePlanResponse | null | undefined,
  pantryEntries: PantryEntry[]
): GroceryDisplayItem[] {
  const pantryNames = pantryEntries.map((entry) => normalizeName(entry.name));
  return groceryItemsFromPlan(plan)
    .map((item) => ({
      ...item,
      pantryMatched: pantryNames.some((name) => name && namesMatch(name, normalizeName(item.name)))
    }))
    .sort((a, b) => (a.category || "Other").localeCompare(b.category || "Other") || a.name.localeCompare(b.name));
}

export function recipeMap(recipes: RecipeDetail[]): Map<string, RecipeDetail> {
  return new Map(recipes.map((recipe) => [recipe.id, recipe]));
}

export function mealImage(mealLabel: string): string {
  const label = mealLabel.toLowerCase();
  if (label.includes("breakfast")) return "/images/meal-breakfast.png";
  if (label.includes("lunch")) return "/images/meal-lunch.png";
  if (label.includes("dinner")) return "/images/meal-dinner.png";
  return "/images/pcosina-logo.png";
}

function normalizeName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

function namesMatch(left: string, right: string): boolean {
  if (left === right) return true;
  const leftTokens = new Set(left.split(" ").filter(Boolean));
  const rightTokens = new Set(right.split(" ").filter(Boolean));
  if (leftTokens.size === 0 || rightTokens.size === 0) return false;
  const leftContainsRight = [...rightTokens].every((token) => leftTokens.has(token));
  const rightContainsLeft = [...leftTokens].every((token) => rightTokens.has(token));
  return leftContainsRight || rightContainsLeft;
}
