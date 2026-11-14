import { act, type ComponentProps } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SettingsView } from "./App";
import { defaultProfile } from "./domain/profile";

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

describe("SettingsView Android interaction parity", () => {
  it("edits and saves the account name and avatar", () => {
    const setProfile = vi.fn();
    const onSaveProfile = vi.fn();
    renderSettings({ setProfile, onSaveProfile });

    act(() => iconButton("Edit name and avatar").click());
    const input = container.querySelector<HTMLInputElement>('[role="dialog"][aria-label="Name and Avatar"] input');
    if (!input) throw new Error("Name input not found");
    act(() => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set?.call(input, "Mia");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    act(() => iconButton("Lab cat").click());
    act(() => button("Save Changes").click());

    expect(setProfile).toHaveBeenCalledWith(expect.objectContaining({ displayName: "Mia", avatarId: "cat" }));
    expect(onSaveProfile).toHaveBeenCalledWith(expect.objectContaining({ displayName: "Mia", avatarId: "cat" }));
  });

  it("persists reminder controls per account", () => {
    renderSettings();

    act(() => buttonContaining("Reminders").click());
    const master = checkbox("Turn reminders on");
    act(() => master.click());
    expect(master.checked).toBe(true);
    expect(JSON.parse(localStorage.getItem("pcosina-reminders:user-1") || "{}").master).toBe(true);

    act(() => button("Meals").click());
    const mealReminder = checkbox("Meal time reminders");
    act(() => mealReminder.click());
    expect(mealReminder.checked).toBe(true);
  });

  it("confirms clear-history and sign-out actions", async () => {
    const onClearWeekData = vi.fn(async () => undefined);
    const onSignOut = vi.fn();
    renderSettings({ onClearWeekData, onSignOut });

    act(() => buttonContaining("Account").click());
    act(() => buttonContaining("Clear saved week data").click());
    expect(container.querySelector('[role="dialog"][aria-label="Clear meal history?"]')).toBeTruthy();
    await act(async () => button("Clear history").click());
    expect(onClearWeekData).toHaveBeenCalledOnce();
    expect(container.textContent).toContain("Meal history cleared.");

    act(() => buttonContaining("Sign out on this phone").click());
    act(() => button("Sign out").click());
    expect(onSignOut).toHaveBeenCalledOnce();
  });

  it("uses the Android back action", () => {
    const onBack = vi.fn();
    renderSettings({ onBack });
    act(() => iconButton("Back").click());
    expect(onBack).toHaveBeenCalledOnce();
  });
});

function renderSettings(overrides: Partial<ComponentProps<typeof SettingsView>> = {}) {
  const props: ComponentProps<typeof SettingsView> = {
    profile: { ...defaultProfile(), displayName: "Ana", goal: "Weight Loss", isProfileCompleted: true },
    setProfile: vi.fn(),
    pantry: [],
    plans: [],
    session: { uid: "user-1", mode: "firebase", email: "ana@example.com", updatedAtMs: Date.now() },
    pendingCount: 0,
    onSaveProfile: vi.fn(),
    onBack: vi.fn(),
    onClearWeekData: vi.fn(async () => undefined),
    onSignOut: vi.fn(),
    ...overrides
  };
  act(() => root.render(<SettingsView {...props} />));
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

function iconButton(label: string): HTMLButtonElement {
  const match = container.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`);
  if (!match) throw new Error(`Icon button not found: ${label}`);
  return match;
}

function checkbox(label: string): HTMLInputElement {
  const row = [...container.querySelectorAll<HTMLLabelElement>(".android-settings-toggle")]
    .find((item) => item.textContent?.includes(label));
  const input = row?.querySelector<HTMLInputElement>('input[type="checkbox"]');
  if (!input) throw new Error(`Checkbox not found: ${label}`);
  return input;
}
