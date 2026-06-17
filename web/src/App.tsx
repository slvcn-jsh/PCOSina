import {
  AlertTriangle,
  Bookmark,
  Box,
  CalendarDays,
  CheckCircle2,
  Clock,
  HeartPulse,
  Info,
  LogOut,
  Plus,
  Save,
  Search,
  ShieldCheck,
  ShoppingCart,
  SlidersHorizontal,
  Trash2,
  User,
  Utensils,
  Wallet,
  WifiOff
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { CSSProperties, ReactNode } from "react";
import type {
  AppSession,
  GeneratePlanResponse,
  PantryEntry,
  PlanSnapshot,
  RecipeDetail,
  SyncSummary,
  UserProfile
} from "./types";
import { groceryDisplayItems, mealImage, moneyPhp, recipeMap, type GroceryDisplayItem } from "./domain/grocery";
import { defaultProfile, GUEST_UID, mergeProfilePatch, pantryNames, splitTokens, toBackendProfile } from "./domain/profile";
import { resolveOfflinePlanFallback } from "./domain/localFallback";
import { ApiError, apiBaseUrl, fetchRecipe, fetchRecipeCatalog, generatePlan } from "./services/api";
import {
  activatePlan,
  cacheRecipes,
  clearSession,
  clearUserWeekData,
  createPantryId,
  deletePantryEntry,
  getActivePlanSnapshot,
  getProfile,
  getSession,
  listCachedRecipes,
  listPantry,
  listPendingOperations,
  listPlanSnapshots,
  savePlanSnapshot,
  saveProfile,
  saveSession,
  upsertPantryEntry
} from "./storage/db";
import {
  firebaseConfigured,
  getGoogleRedirectUser,
  onFirebaseUserChanged,
  signInWithGoogle,
  signOutFirebase
} from "./services/firebase";
import { syncWithCloud } from "./services/cloudSync";

type View = "home" | "plan" | "grocery" | "progress" | "support" | "settings";
type MealLogStatus = "logged" | "skipped";
type NoticeTone = "info" | "success" | "warning" | "danger";

interface Notice {
  tone: NoticeTone;
  text: string;
}

const restrictionOptions = ["No Pork", "No Beef", "Vegetarian", "Pescatarian", "Lactose Intolerant", "Gluten-Free"];
const symptomOptions = ["Irregular cycles", "Weight management", "Cravings", "Fatigue", "Acne", "Bloating"];
const allergyOptions = ["peanut", "dairy", "egg", "fish", "shellfish", "soy", "gluten"];

export default function App() {
  const [session, setSession] = useState<AppSession | null>(null);
  const [profile, setProfile] = useState<UserProfile>(defaultProfile());
  const [pantry, setPantry] = useState<PantryEntry[]>([]);
  const [recipes, setRecipes] = useState<RecipeDetail[]>([]);
  const [plans, setPlans] = useState<PlanSnapshot[]>([]);
  const [activePlan, setActivePlan] = useState<PlanSnapshot | null>(null);
  const [selectedRecipeId, setSelectedRecipeId] = useState<string | null>(null);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [view, setView] = useState<View>("home");
  const [mealLogState, setMealLogState] = useState<Record<string, MealLogStatus>>({});
  const [notice, setNotice] = useState<Notice | null>(null);
  const [syncSummary, setSyncSummary] = useState<SyncSummary | null>(null);
  const [pendingCount, setPendingCount] = useState(0);
  const [busy, setBusy] = useState(false);
  const [online, setOnline] = useState(typeof navigator === "undefined" ? true : navigator.onLine);

  const uid = session?.uid || "";
  const recipesById = useMemo(() => recipeMap(recipes), [recipes]);
  const selectedRecipe = selectedRecipeId ? recipesById.get(selectedRecipeId) : undefined;

  useEffect(() => {
    let cancelled = false;
    getSession().then(async (stored) => {
      if (cancelled) return;
      setSession(stored);
      if (stored) await loadWorkspace(stored.uid);
    });
    const unsubscribe = onFirebaseUserChanged((user) => {
      if (!user) return;
      const next = firebaseUserSession(user);
      saveSession(next).then(() => {
        setSession(next);
        loadWorkspace(next.uid);
      });
    });
    return () => {
      cancelled = true;
      unsubscribe?.();
    };
  }, []);

  useEffect(() => {
    const onOnline = () => setOnline(true);
    const onOffline = () => setOnline(false);
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOffline);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOffline);
    };
  }, []);

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "auto" });
  }, [view]);

  async function loadWorkspace(nextUid: string) {
    const [storedProfile, storedPantry, cachedRecipes, storedPlans, storedActive, pending] = await Promise.all([
      getProfile(nextUid),
      listPantry(nextUid),
      listCachedRecipes(),
      listPlanSnapshots(nextUid),
      getActivePlanSnapshot(nextUid),
      listPendingOperations(nextUid)
    ]);
    setProfile(storedProfile);
    setPantry(storedPantry);
    setRecipes(cachedRecipes);
    setPlans(storedPlans);
    setActivePlan(storedActive);
    setPendingCount(pending.length);

    if (nextUid !== GUEST_UID) {
      const summary = await syncWithCloud(nextUid);
      setSyncSummary(summary);
      if (summary.status === "restored") {
        const [restoredProfile, restoredPantry, restoredPlans, restoredActive] = await Promise.all([
          getProfile(nextUid),
          listPantry(nextUid),
          listPlanSnapshots(nextUid),
          getActivePlanSnapshot(nextUid)
        ]);
        setProfile(restoredProfile);
        setPantry(restoredPantry);
        setPlans(restoredPlans);
        setActivePlan(restoredActive);
      }
    }

    if (online) {
      await refreshRecipeCatalog(false);
    }
  }

  async function refreshRecipeCatalog(showNotice = true) {
    try {
      const catalog = await fetchRecipeCatalog();
      await cacheRecipes(catalog);
      setRecipes(catalog);
      if (showNotice) setNotice({ tone: "success", text: `Recipes are ready on this phone (${catalog.length} recipes).` });
    } catch (error) {
      const cached = await listCachedRecipes();
      setRecipes(cached);
      if (showNotice) {
        setNotice({
          tone: "warning",
          text: cached.length
            ? "Couldn’t refresh recipes. Your saved recipes are still available."
            : formatError(error, "Recipes are not available right now.")
        });
      }
    }
  }

  async function continueAsGuest() {
    const next: AppSession = { uid: GUEST_UID, mode: "guest", updatedAtMs: Date.now() };
    await saveSession(next);
    setSession(next);
    await loadWorkspace(next.uid);
  }

  async function handleSignedIn(userSession: AppSession) {
    await saveSession(userSession);
    setSession(userSession);
    await loadWorkspace(userSession.uid);
    setNotice({ tone: "success", text: "Signed in. Your saved PCOSina data will be restored when available." });
  }

  async function signOut() {
    await signOutFirebase();
    await clearSession();
    setSession(null);
    setProfile(defaultProfile());
    setPantry([]);
    setPlans([]);
    setActivePlan(null);
    setNotice(null);
    setSyncSummary(null);
  }

  async function persistProfile(nextProfile = profile) {
    if (!uid) return;
    const completed = {
      ...nextProfile,
      pantryItems: pantryNames(pantry),
      isProfileCompleted: true,
      updatedAtMs: Date.now()
    };
    const saved = await saveProfile(uid, completed);
    setProfile(saved);
    setPendingCount((await listPendingOperations(uid)).length);
    if (session?.mode === "firebase") setSyncSummary(await syncWithCloud(uid));
    setNotice({ tone: "success", text: "Profile saved on this phone." });
  }

  async function addPantryItem(input: { name: string; quantity: string; expiryDate: string }) {
    if (!uid || !input.name.trim()) return;
    const entry: PantryEntry = {
      id: createPantryId(uid, input.name),
      name: input.name.trim(),
      quantity: input.quantity.trim() || undefined,
      expiryDate: input.expiryDate || undefined,
      updatedAtMs: Date.now()
    };
    await upsertPantryEntry(uid, entry);
    const nextPantry = await listPantry(uid);
    setPantry(nextPantry);
    const nextProfile = { ...profile, pantryItems: pantryNames(nextPantry), updatedAtMs: Date.now() };
    setProfile(await saveProfile(uid, nextProfile));
    setPendingCount((await listPendingOperations(uid)).length);
  }

  async function removePantryItem(id: string) {
    if (!uid) return;
    await deletePantryEntry(uid, id);
    const nextPantry = await listPantry(uid);
    setPantry(nextPantry);
    setProfile(await saveProfile(uid, { ...profile, pantryItems: pantryNames(nextPantry), updatedAtMs: Date.now() }));
    setPendingCount((await listPendingOperations(uid)).length);
  }

  async function handleGeneratePlan() {
    if (!uid) return;
    setBusy(true);
    setNotice(null);
    try {
      const request = {
        profile: toBackendProfile(profile, pantry),
        days: 7,
        mealsPerDay: 3,
        startDate: new Date().toISOString().slice(0, 10)
      };
      const response = await generatePlan(request);
      if (response.status === "success") {
        const snapshot = await savePlanSnapshot(uid, response, "backend");
        const [nextPlans, pending] = await Promise.all([listPlanSnapshots(uid), listPendingOperations(uid)]);
        setActivePlan(snapshot);
        setPlans(nextPlans);
        setPendingCount(pending.length);
        setView("plan");
        setNotice({ tone: "success", text: "Your weekly plan is ready and saved on this phone." });
        if (session?.mode === "firebase") setSyncSummary(await syncWithCloud(uid));
      } else {
        setActivePlan(temporaryPlanSnapshot(uid, response));
        setView("plan");
        setNotice({ tone: "warning", text: response.message || "No safe plan was found for the current hard constraints." });
      }
    } catch (error) {
      const fallback = await resolveOfflinePlanFallback(uid);
      const plannerError = formatError(error, "");
      if (fallback.kind === "saved-plan") {
        setActivePlan(fallback.snapshot);
        setView("plan");
        setNotice({ tone: "warning", text: `${fallback.message} ${plannerError}`.trim() });
      } else {
        setNotice({
          tone: "danger",
          text: online
            ? "PCOSina cannot reach the meal planner right now. Keep the PCOSina planner open on this computer, then try again."
            : fallback.message
        });
      }
    } finally {
      setBusy(false);
    }
  }

  async function openRecipe(recipeId: string) {
    setSelectedRecipeId(recipeId);
    if (recipesById.has(recipeId) || !online) return;
    try {
      const recipe = await fetchRecipe(recipeId);
      setRecipes((current) => {
        const existing = current.filter((item) => item.id !== recipe.id);
        return [...existing, recipe].sort((a, b) => a.title.localeCompare(b.title));
      });
    } catch {
      setNotice({ tone: "warning", text: "Recipe details are not ready yet. Try again after recipes finish loading." });
    }
  }

  async function activateSavedPlan(planId: string) {
    if (!uid) return;
    await activatePlan(uid, planId);
    const [nextPlans, nextActive] = await Promise.all([listPlanSnapshots(uid), getActivePlanSnapshot(uid)]);
    setPlans(nextPlans);
    setActivePlan(nextActive);
    setView("plan");
  }

  if (!session) {
    return <AuthGate onGuest={continueAsGuest} onSignedIn={handleSignedIn} />;
  }

  return (
    <div className="app-shell">
      <main className="workspace">
        {view !== "settings" ? (
          <AndroidTopHeader
            session={session}
            online={online}
            syncSummary={syncSummary}
            pendingCount={pendingCount}
            onSettings={() => setView("settings")}
            onNotifications={() => setNotificationsOpen(true)}
          />
        ) : null}
        {view !== "home" && view !== "settings" ? <AvatarHeader title={titleForView(view)} subtitle={subtitleForView(view, profile)} online={online} /> : null}

        {notice ? <StatusBanner tone={notice.tone} text={notice.text} /> : null}
        {!online ? (
          <StatusBanner
            tone="warning"
            text="You're offline. You can still open saved plans and grocery lists on this phone."
          />
        ) : null}

        {view === "home" ? (
          <HomeView
            profile={profile}
            activePlan={activePlan}
            recipesById={recipesById}
            mealLogState={mealLogState}
            online={online}
            busy={busy}
            onOpenRecipe={openRecipe}
            onOpenPlan={() => setView("plan")}
            onOpenProgress={() => setView("progress")}
            onOpenSettings={() => setView("settings")}
          />
        ) : null}
        {view === "plan" ? (
          <PlanView
            snapshot={activePlan}
            recipesById={recipesById}
            busy={busy}
            onGenerate={handleGeneratePlan}
            onOpenRecipe={openRecipe}
            onOpenGrocery={() => setView("grocery")}
            mealLogState={mealLogState}
            onMealStatusChange={(key, status) =>
              setMealLogState((current) => {
                const next = { ...current };
                if (current[key] === status) delete next[key];
                else next[key] = status;
                return next;
              })
            }
          />
        ) : null}
        {view === "grocery" ? (
          <GroceryView
            snapshot={activePlan}
            pantry={pantry}
            profile={profile}
            onAddPantry={addPantryItem}
          />
        ) : null}
        {view === "progress" ? (
          <ProgressView
            snapshot={activePlan}
            profile={profile}
            mealLogState={mealLogState}
            onOpenSupport={() => setView("support")}
          />
        ) : null}
        {view === "support" ? <SupportView onOpenMealPlan={() => setView("plan")} /> : null}
        {view === "settings" ? (
          <SettingsView
            profile={profile}
            setProfile={setProfile}
            pantry={pantry}
            plans={plans}
            session={session}
            pendingCount={pendingCount}
            onSaveProfile={(nextProfile) => persistProfile(nextProfile)}
            onBack={() => setView("home")}
            onClearWeekData={async () => {
              if (!uid) return;
              await clearUserWeekData(uid);
              setPlans([]);
              setActivePlan(null);
              setMealLogState({});
              setPendingCount((await listPendingOperations(uid)).length);
            }}
            onSignOut={signOut}
          />
        ) : null}
      </main>

      {view !== "settings" ? <nav className="bottom-nav" aria-label="Primary"><Nav view={view} onView={setView} compact /></nav> : null}

      {selectedRecipeId ? (
        <RecipeDetailsView
          recipe={selectedRecipe}
          recipeId={selectedRecipeId}
          snapshot={activePlan}
          mealLogState={mealLogState}
          onMealStatusChange={(key, status) =>
            setMealLogState((current) => {
              const next = { ...current };
              if (current[key] === status) delete next[key];
              else next[key] = status;
              return next;
            })
          }
          onClose={() => setSelectedRecipeId(null)}
          onOpenProgress={() => {
            setSelectedRecipeId(null);
            setView("progress");
          }}
        />
      ) : null}
      {notificationsOpen ? (
        <NotificationsView
          profile={profile}
          session={session}
          online={online}
          onClose={() => setNotificationsOpen(false)}
          onOpenSettings={() => {
            setNotificationsOpen(false);
            setView("settings");
          }}
        />
      ) : null}
    </div>
  );
}

function AndroidTopHeader({
  session,
  online,
  syncSummary,
  pendingCount,
  onSettings,
  onNotifications
}: {
  session: AppSession;
  online: boolean;
  syncSummary: SyncSummary | null;
  pendingCount: number;
  onSettings: () => void;
  onNotifications: () => void;
}) {
  return (
    <header className="android-top-header">
      <BrandMark />
      <div className="android-header-actions">
        <button className="image-icon-button" type="button" onClick={onNotifications} title="Notifications">
          <img src="/images/pcosina-header-notification.png" alt="" />
        </button>
        <button className="image-icon-button" type="button" onClick={onSettings} title="Settings">
          <img src="/images/pcosina-header-settings.png" alt="" />
        </button>
        <span className={online ? "connection-pill online" : "connection-pill offline"}>
          {online ? "Online" : "Offline"}
        </span>
      </div>
      <div className="header-sync-line">
        <span>{session.mode === "guest" ? "Guest mode" : session.email || "Google account"}</span>
        <span>{pendingCount ? `${pendingCount} saved change${pendingCount === 1 ? "" : "s"}` : "Everything saved on this phone"}</span>
        {syncSummary?.status === "failed" ? <span>Save will retry later</span> : null}
      </div>
    </header>
  );
}

export function NotificationsView({
  profile,
  session,
  online,
  onClose,
  onOpenSettings
}: {
  profile: UserProfile;
  session: AppSession;
  online: boolean;
  onClose: () => void;
  onOpenSettings: () => void;
}) {
  const reminderKey = `pcosina-reminders:${session.uid}`;
  let reminders: Partial<SettingsReminderPreferences> = {};
  try {
    reminders = JSON.parse(localStorage.getItem(reminderKey) || "{}");
  } catch {
    reminders = {};
  }
  const master = Boolean(reminders.master);
  const meals = Boolean(reminders.meals);
  return (
    <div className="android-notifications-backdrop">
      <section className="android-notifications-screen" role="dialog" aria-modal="true" aria-label="Notifications">
        <AndroidTopHeader session={session} online={online} syncSummary={null} pendingCount={0} onSettings={onOpenSettings} onNotifications={() => undefined} />
        <button className="android-notifications-back" type="button" aria-label="Back" onClick={onClose}>‹</button>
        <section className="avatar-header">
          <img className="avatar-header-art" src={avatarImage(profile.avatarId)} alt="" />
          <div className="avatar-header-copy"><p className="eyebrow">Good food, good mood.</p><h1>Notifications</h1><p>Meal reminders, grocery updates, and weekly planning nudges.</p><span className="date-pill"><CalendarDays size={15} />{new Date().toLocaleDateString("en-PH", { month: "short", day: "2-digit" })}</span></div>
        </section>
        <div className="android-notification-list">
          <NotificationSummary title={master ? "Reminders are on" : "Reminders are paused"} body={master ? "PCOSina reminder preferences are active in this browser." : "Turn reminders back on from Settings when you want meal or weekly planning nudges."} badge={master ? "Active" : "Paused"} />
          <NotificationSummary title="Browser notifications" body={"Reminder preferences are saved locally. Browser delivery depends on Safari notification permission and PWA installation support."} badge={typeof Notification !== "undefined" && Notification.permission === "granted" ? "Ready" : "Needs action"} />
          <NotificationSummary title="Meal schedule" body={`Breakfast ${formatWebReminderTime(reminders.breakfast || "08:00")} | Lunch ${formatWebReminderTime(reminders.lunch || "12:00")} | Dinner ${formatWebReminderTime(reminders.dinner || "18:00")}`} badge={meals ? "Meals" : "Off"} />
          <NotificationSummary title="Next scheduled" body={!master ? "Reminders are paused. No browser notification is scheduled while the master switch is off." : meals ? "Meal reminder times are saved on this device." : "No reminder type is enabled yet."} badge={master && meals ? "Saved" : "None"} />
          <NotificationSummary title="Recent activity" body="No notifications delivered yet on this device. Activity appears after supported browser notifications are enabled." badge="Delivered only" />
        </div>
      </section>
    </div>
  );
}

function NotificationSummary({ title, body, badge }: { title: string; body: string; badge: string }) {
  return <article className="android-notification-card"><span><img src="/images/pcosina-header-notification.png" alt="" /></span><div><header><strong>{title}</strong><b>{badge}</b></header><p>{body}</p></div></article>;
}

function formatWebReminderTime(value: string): string {
  const [hourRaw, minute = "00"] = value.split(":");
  const hour = Number(hourRaw);
  if (!Number.isFinite(hour)) return value;
  const suffix = hour >= 12 ? "PM" : "AM";
  return `${hour % 12 || 12}:${minute} ${suffix}`;
}

function AvatarHeader({ title, subtitle, online }: { title: string; subtitle: string; online: boolean }) {
  const today = new Date().toLocaleDateString("en-PH", {
    weekday: "long",
    month: "short",
    day: "numeric"
  });
  return (
    <section className="avatar-header">
      <img className="avatar-header-art" src="/images/avatar-doctor-dog.png" alt="" />
      <div className="avatar-header-copy">
        <p className="eyebrow">Good food, good mood.</p>
        <h1>{title}</h1>
        <p>{subtitle}</p>
        <span className="date-pill">
          <CalendarDays size={15} />
          {today}
        </span>
      </div>
      <span className={online ? "avatar-status online" : "avatar-status offline"}>
        {online ? "Online and ready." : "Offline mode."}
      </span>
    </section>
  );
}

export function HomeView({
  profile,
  activePlan,
  recipesById,
  mealLogState,
  online,
  busy,
  onOpenRecipe,
  onOpenPlan,
  onOpenProgress,
  onOpenSettings
}: {
  profile: UserProfile;
  activePlan: PlanSnapshot | null;
  recipesById: Map<string, RecipeDetail>;
  mealLogState: Record<string, MealLogStatus>;
  online: boolean;
  busy: boolean;
  onOpenRecipe: (recipeId: string) => void;
  onOpenPlan: () => void;
  onOpenProgress: () => void;
  onOpenSettings: () => void;
}) {
  const [goalInfo, setGoalInfo] = useState<string | null>(null);
  const plan = activePlan?.response;
  const hasPlan = plan?.status === "success" && plan.days.length > 0;
  const todayIndex = activePlan && hasPlan ? selectedPlanDayIndex(activePlan) : 0;
  const todayPlan = hasPlan ? plan.days[todayIndex] : undefined;
  const todayMeals = todayPlan?.meals ?? [];
  const goals = homeGoalLabels(profile.goal);
  const tips = homeGoalTips(profile.goal);
  const todayStatuses = activePlan
    ? todayMeals.map((meal, mealIndex) => mealLogState[buildMealSlotKey(activePlan.planId, todayIndex, mealIndex, meal.recipeId)])
    : [];
  const completedToday = todayStatuses.filter((status) => status === "logged").length;
  const handledToday = todayStatuses.filter(Boolean).length;
  const nextMealIndex = todayStatuses.findIndex((status) => !status);
  const nextMeal = nextMealIndex >= 0 ? todayMeals[nextMealIndex] : undefined;
  const welcomeSubline = !hasPlan
    ? "You're one thoughtful step away from your first weekly meal plan."
    : nextMeal
      ? `You're doing well today! ${nextMeal.mealLabel} is your next focus.`
      : handledToday
        ? "You're doing well today! Ready for your next goal?"
        : "Your saved plan and progress are ready when you are.";
  const primaryTitle = !profile.isProfileCompleted
    ? "Finish your profile first"
    : !goals.length
      ? "Choose the goals you want to follow"
      : !hasPlan
        ? "Ready to start your meal plan?"
        : nextMeal
          ? "Your next meal is ready"
          : "Keep your week moving";
  const primaryMessage = !profile.isProfileCompleted
    ? "Complete your profile so planning can respect your goals, food rules, and weekly budget."
    : !goals.length
      ? "Choose at least one goal so the planner knows what to prioritize."
      : !hasPlan && online
        ? "Let's generate a weekly plan that unlocks groceries, progress, and your daily meal flow."
        : !hasPlan
          ? "Internet is required once to generate your first week. Saved plans remain available offline."
          : nextMeal
            ? `Open ${nextMeal.mealLabel.toLowerCase()} and keep today's routine visible.`
            : "Review your progress or reopen this week's plan whenever you need a quick reset.";
  const primaryLabel = !profile.isProfileCompleted
    ? "Open Profile"
    : !goals.length
      ? "Choose Goals"
      : !hasPlan
        ? "Go to Plan"
        : nextMeal
          ? `Open ${nextMeal.mealLabel}`
          : "Open Progress";
  const runPrimaryAction = () => {
    if (!profile.isProfileCompleted || !goals.length) onOpenSettings();
    else if (!hasPlan) onOpenPlan();
    else if (nextMeal) onOpenRecipe(nextMeal.recipeId);
    else onOpenProgress();
  };
  const weekEntries = Array.from({ length: 7 }, (_, dayIndex) => {
    const day = hasPlan ? plan.days[dayIndex] : undefined;
    const statuses = activePlan && day
      ? day.meals.map((meal, mealIndex) => mealLogState[buildMealSlotKey(activePlan.planId, dayIndex, mealIndex, meal.recipeId)])
      : [];
    const logged = statuses.filter((status) => status === "logged").length;
    const planned = day?.meals.length ?? 0;
    return {
      label: shortDayLabel(day?.dayLabel, dayIndex),
      progress: planned ? logged / planned : 0,
      state: !planned ? "empty" : logged >= planned ? "complete" : logged > 0 ? "partial" : "pending"
    };
  });

  return (
    <section className="android-home-layout">
      <button className="android-home-welcome" type="button" onClick={onOpenSettings}>
        <img src="/images/avatar-doctor-dog.png" alt="" />
        <div>
          <p className="eyebrow">Good food, good mood.</p>
          <h1>Welcome, {profile.displayName || "there"}!</h1>
          <p>{welcomeSubline}</p>
        </div>
        <time>{new Date().toLocaleDateString("en-PH", { month: "short" }).toUpperCase()}<strong>{new Date().getDate()}</strong></time>
      </button>

      <section className="android-home-goal-tip">
        <article className="android-home-goals">
          <header><img src="/images/pcosina-goal.png" alt="" /><h2>Your Goals</h2></header>
          {goals.length ? (
            <div>{goals.map((goal) => <button type="button" key={goal} onClick={() => setGoalInfo(goal)}><span>✓</span>{goal}<Info size={13} /></button>)}</div>
          ) : <p>Choose at least one goal so the planner knows what to prioritize.</p>}
        </article>
        <article className="android-home-tips">
          <header><img src="/images/pcosina-tip.png" alt="" /><h2>Daily Tips</h2></header>
          <p>{tips[0]}</p>
          <small>{tips[1]}</small>
        </article>
      </section>

      <HomeSectionHeader image="/images/pcosina-clipboard-home.png" title="Your Meal Plan for Today" subtitle={hasPlan ? "Good food, good mood." : "Create a plan to reveal today's assigned meals."} />

      {hasPlan && todayMeals.length ? (
        <section className="android-home-meals">
          {todayMeals.map((meal, mealIndex) => {
            const recipe = recipesById.get(meal.recipeId);
            const status = todayStatuses[mealIndex];
            return (
              <button className={`${mealToneClass(meal.mealLabel)} ${status ? "handled" : ""}`} type="button" key={`${meal.mealLabel}-${meal.recipeId}`} onClick={() => onOpenRecipe(meal.recipeId)}>
                <span><img src={mealImage(meal.mealLabel)} alt="" /><strong>{meal.mealLabel}</strong></span>
                <b>{meal.title}</b>
                {recipe?.calories ? <em>{Math.round(recipe.calories)} <small>kcal</small></em> : <em>Open recipe</em>}
              </button>
            );
          })}
        </section>
      ) : (
        <section className="android-home-empty">
          <h2>{hasPlan ? "No meals assigned today" : "No plan yet"}</h2>
          <p>{hasPlan ? "Your week is saved, but there are no meal cards for today's date yet." : online ? "Generate your first week to fill today's meals and grocery list." : "Connect once to generate your first week."}</p>
          {online || hasPlan ? <button className="primary-button" type="button" onClick={onOpenPlan} disabled={busy}>{busy ? "Opening" : "Open Meal Plan"}</button> : null}
        </section>
      )}

      <HomeSectionHeader image="/images/pcosina-calendar.png" title="Your Weekly Progress" subtitle={hasPlan ? `${completedToday}/${todayMeals.length} meals logged today.` : "Generate one week first, then your day-by-day progress will appear here."} />

      <section className="android-home-week">
        {weekEntries.map((entry, index) => (
          <div key={`${entry.label}-${index}`}>
            <span className={entry.state}>
              {entry.state === "complete" ? "✓" : entry.state === "partial" ? `${Math.round(entry.progress * 100)}%` : <img src="/images/pcosina-weekly-progress-lock.png" alt="" />}
            </span>
            <small>{entry.label}</small>
          </div>
        ))}
      </section>

      <section className="android-home-primary">
        <div>
          <h2>{primaryTitle}</h2>
          <p>{primaryMessage}</p>
          <button type="button" onClick={runPrimaryAction} disabled={busy}>{busy ? "Opening" : primaryLabel}</button>
        </div>
        <span><img src="/images/pcosina-next-meal.png" alt="" /></span>
      </section>

      {goalInfo ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setGoalInfo(null)}>
          <section className="android-home-goal-dialog" role="dialog" aria-modal="true" aria-label={`${goalInfo} goal information`} onClick={(event) => event.stopPropagation()}>
            <i><Info size={28} /></i>
            <h2>{goalInfo.toUpperCase()}</h2>
            <p>{homeGoalInfo(goalInfo)}</p>
            <button type="button" onClick={() => setGoalInfo(null)}>OKAY</button>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function HomeSectionHeader({ image, title, subtitle }: { image: string; title: string; subtitle: string }) {
  return <header className="android-home-section-head"><img src={image} alt="" /><div><h2>{title}</h2><p>{subtitle}</p></div></header>;
}

function AuthGate({ onGuest, onSignedIn }: { onGuest: () => void; onSignedIn: (session: AppSession) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const configured = firebaseConfigured();

  useEffect(() => {
    let cancelled = false;
    if (!configured) return;
    getGoogleRedirectUser()
      .then((user) => {
        if (!user || cancelled) return;
        onSignedIn(firebaseUserSession(user));
      })
      .catch((err) => {
        if (!cancelled) setError(formatError(err, "Google sign-in could not be completed."));
      });
    return () => {
      cancelled = true;
    };
  }, [configured, onSignedIn]);

  async function submitGoogle() {
    setBusy(true);
    setError(null);
    try {
      const user = await signInWithGoogle();
      if (user) onSignedIn(firebaseUserSession(user));
    } catch (err) {
      setError(formatError(err, "Google sign-in failed."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-screen">
      <section className="auth-panel">
        <div className="auth-art">
          <img className="auth-snacks" src="/images/auth-snacks-background.png" alt="" />
          <img className="auth-heart" src="/images/login-heart-hands.png" alt="" />
          <img className="auth-watermark" src="/images/login-ownership-watermark.png" alt="" />
        </div>
        <div className="auth-content">
          <BrandMark />
          <p className="eyebrow">Welcome back</p>
          <h1>PCOSina</h1>
          <p className="auth-copy">Take the first step toward smarter PCOS nutrition.</p>
          <button className="google-button wide" type="button" onClick={submitGoogle} disabled={!configured || busy}>
            <span className="google-mark" aria-hidden="true">G</span>
            <span>{busy ? "Working" : "Continue with Google"}</span>
          </button>
          <button className="icon-text-button primary wide" type="button" onClick={onGuest}>
            <User size={18} />
            <span>Continue as guest</span>
          </button>
          {!configured ? <p className="field-note">Google sign-in needs the PCOSina Firebase web setup on this computer.</p> : null}
          {error ? <p className="error-copy">{friendlyAuthError(error)}</p> : null}
        </div>
      </section>
    </main>
  );
}

function BrandMark() {
  return (
    <div className="brand-mark">
      <img src="/images/pcosina-logo.png" alt="" />
      <div>
        <strong>PCOSina</strong>
        <span>Take the first step toward smarter PCOS nutrition.</span>
      </div>
    </div>
  );
}

function Nav({ view, onView, compact = false }: { view: View; onView: (view: View) => void; compact?: boolean }) {
  const items: Array<{ view: View; label: string; icon: string; center?: boolean }> = [
    { view: "plan", label: "Plan", icon: "/images/pcosina-nav-plan.png" },
    { view: "grocery", label: "Grocery", icon: "/images/pcosina-nav-grocery.png" },
    { view: "home", label: "Home", icon: "/images/pcosina-nav-home.png", center: true },
    { view: "progress", label: "Progress", icon: "/images/pcosina-nav-progress.png" },
    { view: "support", label: "Support", icon: "/images/pcosina-nav-support.png" }
  ];
  return (
    <div className={compact ? "nav compact" : "nav"}>
      {items.map((item) => (
        <button
          key={item.view}
          type="button"
          className={`${view === item.view ? "active" : ""}${item.center ? " center" : ""}`}
          onClick={() => onView(item.view)}
          title={item.label}
        >
          <span className="nav-icon-shell">
            <img src={item.icon} alt="" />
          </span>
          <span>{item.label}</span>
        </button>
      ))}
    </div>
  );
}

function StatusBanner({ tone, text }: { tone: NoticeTone; text: string }) {
  const Icon = tone === "success" ? CheckCircle2 : tone === "danger" ? AlertTriangle : tone === "warning" ? WifiOff : Info;
  return (
    <div className={`status-banner ${tone}`}>
      <Icon size={18} />
      <span>{text}</span>
    </div>
  );
}

function ProfileView({
  profile,
  setProfile,
  onSave
}: {
  profile: UserProfile;
  setProfile: (profile: UserProfile) => void;
  onSave: () => void;
}) {
  const patch = (next: Partial<UserProfile>) => setProfile(mergeProfilePatch(profile, next));
  return (
    <section className="section-grid profile-grid">
      <div className="panel">
        <div className="panel-heading">
          <HeartPulse size={20} />
          <h2>Profile</h2>
        </div>
        <div className="form-grid">
          <label>
            Name
            <input value={profile.displayName} onChange={(event) => patch({ displayName: event.target.value })} />
          </label>
          <label>
            Age
            <input type="number" min={0} value={profile.age} onChange={(event) => patch({ age: numberValue(event.target.value) })} />
          </label>
          <label>
            Height cm
            <input
              type="number"
              min={0}
              value={profile.heightCm}
              onChange={(event) => patch({ heightCm: numberValue(event.target.value) })}
            />
          </label>
          <label>
            Weight kg
            <input
              type="number"
              min={0}
              value={profile.weightKg}
              onChange={(event) => patch({ weightKg: numberValue(event.target.value) })}
            />
          </label>
          <label>
            Weekly budget PHP
            <input
              type="number"
              min={0}
              value={profile.weeklyBudgetPhp}
              onChange={(event) => patch({ weeklyBudgetPhp: numberValue(event.target.value) })}
            />
          </label>
          <label>
            Max cooking min
            <input
              type="number"
              min={0}
              max={240}
              value={profile.maxCookingTimeMinutes}
              onChange={(event) => patch({ maxCookingTimeMinutes: numberValue(event.target.value) })}
            />
          </label>
          <label>
            Goal
            <select value={profile.goal} onChange={(event) => patch({ goal: event.target.value })}>
              <option>General Health</option>
              <option>Weight Loss</option>
              <option>Symptom Management</option>
              <option>Budget Friendly</option>
            </select>
          </label>
          <label>
            Activity
            <select value={profile.activityLevel} onChange={(event) => patch({ activityLevel: event.target.value })}>
              <option>Sedentary</option>
              <option>Lightly Active</option>
              <option>Moderately Active</option>
              <option>Very Active</option>
            </select>
          </label>
          <label>
            Variety
            <select value={profile.varietyPreference} onChange={(event) => patch({ varietyPreference: event.target.value })}>
              <option>Balanced</option>
              <option>High Variety</option>
              <option>Repeat Friendly</option>
            </select>
          </label>
          <label>
            Priority
            <select value={profile.planningPriority} onChange={(event) => patch({ planningPriority: event.target.value })}>
              <option>Balanced</option>
              <option>Budget</option>
              <option>Nutrition</option>
              <option>Pantry Use</option>
            </select>
          </label>
        </div>
      </div>

      <div className="panel">
        <div className="panel-heading">
          <ShieldCheck size={20} />
          <h2>Food Rules</h2>
        </div>
        <ChipGroup
          label="Restrictions"
          options={restrictionOptions}
          selected={profile.dietaryRestrictions}
          onChange={(dietaryRestrictions) => patch({ dietaryRestrictions })}
        />
        <ChipGroup label="Symptoms" options={symptomOptions} selected={profile.symptoms} onChange={(symptoms) => patch({ symptoms })} />
        <ChipGroup label="Common allergies" options={allergyOptions} selected={profile.allergies} onChange={(allergies) => patch({ allergies })} />
        <TokenEditor label="Other allergies" value={profile.allergies} onChange={(allergies) => patch({ allergies })} />
        <TokenEditor
          label="Excluded ingredients"
          value={profile.excludedIngredients}
          onChange={(excludedIngredients) => patch({ excludedIngredients })}
        />
        <button className="icon-text-button primary wide" type="button" onClick={onSave}>
          <Save size={18} />
          <span>Save profile</span>
        </button>
      </div>
    </section>
  );
}

function PantryView({
  pantry,
  onAdd,
  onRemove
}: {
  pantry: PantryEntry[];
  onAdd: (input: { name: string; quantity: string; expiryDate: string }) => void;
  onRemove: (id: string) => void;
}) {
  const [name, setName] = useState("");
  const [quantity, setQuantity] = useState("");
  const [expiryDate, setExpiryDate] = useState("");
  return (
    <section className="section-grid pantry-grid">
      <div className="panel pantry-entry-panel">
        <div className="panel-heading">
          <Box size={20} />
          <h2>Pantry Item</h2>
        </div>
        <div className="form-grid single">
          <label>
            Ingredient
            <input value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label>
            Quantity
            <input value={quantity} onChange={(event) => setQuantity(event.target.value)} placeholder="500 g, 3 pcs" />
          </label>
          <label>
            Expiry
            <input type="date" value={expiryDate} onChange={(event) => setExpiryDate(event.target.value)} />
          </label>
          <button
            className="icon-text-button primary wide"
            type="button"
            onClick={() => {
              onAdd({ name, quantity, expiryDate });
              setName("");
              setQuantity("");
              setExpiryDate("");
            }}
            disabled={!name.trim()}
          >
            <Plus size={18} />
            <span>Add item</span>
          </button>
        </div>
      </div>
      <div className="list-panel">
        {pantry.length === 0 ? (
          <EmptyState icon={Box} title="No pantry items" text="Add ingredients here so PCOSina can plan around what you already have." />
        ) : (
          pantry.map((item) => (
            <article className="list-row" key={item.id}>
              <div>
                <strong>{item.name}</strong>
                <span>{[item.quantity, item.expiryDate ? `Expires ${item.expiryDate}` : ""].filter(Boolean).join(" | ") || "Name only"}</span>
              </div>
              <button className="icon-button danger" type="button" onClick={() => onRemove(item.id)} title="Remove item">
                <Trash2 size={18} />
              </button>
            </article>
          ))
        )}
      </div>
    </section>
  );
}

export function PlanView({
  snapshot,
  recipesById,
  busy,
  onGenerate,
  onOpenRecipe,
  onOpenGrocery,
  mealLogState,
  onMealStatusChange
}: {
  snapshot: PlanSnapshot | null;
  recipesById: Map<string, RecipeDetail>;
  busy: boolean;
  onGenerate: () => void;
  onOpenRecipe: (recipeId: string) => void;
  onOpenGrocery: () => void;
  mealLogState: Record<string, MealLogStatus>;
  onMealStatusChange: (key: string, status: MealLogStatus) => void;
}) {
  const [selectedDayIndex, setSelectedDayIndex] = useState(() => snapshot ? selectedPlanDayIndex(snapshot) : 0);
  const [swappedMeals, setSwappedMeals] = useState<Record<string, { recipeId: string; title: string }>>({});
  const [swapMessage, setSwapMessage] = useState<string | null>(null);
  const [swapTarget, setSwapTarget] = useState<{
    slotKey: string;
    mealLabel: string;
    currentRecipeId: string;
    currentTitle: string;
  } | null>(null);
  const [logPrompt, setLogPrompt] = useState<{
    slotKey: string;
    mealLabel: string;
    title: string;
  } | null>(null);
  useEffect(() => {
    if (snapshot) setSelectedDayIndex(selectedPlanDayIndex(snapshot));
  }, [snapshot?.planId]);
  if (!snapshot) {
    return (
      <EmptyState
        icon={CalendarDays}
        title="No saved plan"
        text="Generate a weekly plan and it will stay available on this phone."
        action={
          <button className="icon-text-button primary" type="button" onClick={onGenerate} disabled={busy}>
            <Utensils size={18} />
            <span>{busy ? "Planning" : "Generate plan"}</span>
          </button>
        }
      />
    );
  }

  const plan = snapshot.response;
  if (plan.status === "no-safe-plan") {
    return <NoSafePlan plan={plan} />;
  }

  const dayIndex = Math.min(selectedDayIndex, Math.max(plan.days.length - 1, 0));
  const selectedDay = plan.days[dayIndex];
  const selectedMeals = selectedDay?.meals ?? [];
  const dayProtein = selectedMeals.reduce((sum, meal) => sum + (recipesById.get(meal.recipeId)?.proteinGrams ?? 0), 0);
  const dayCarbs = selectedMeals.reduce((sum, meal) => sum + (recipesById.get(meal.recipeId)?.carbsGrams ?? 0), 0);
  const dayFiber = selectedMeals.reduce((sum, meal) => sum + (recipesById.get(meal.recipeId)?.fiberGrams ?? 0), 0);
  const planWeekStart = startOfPlanWeek(snapshot);
  const planEndDate = new Date(planWeekStart);
  planEndDate.setDate(planWeekStart.getDate() + 6);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const finalDayIndex = Math.max(0, plan.days.length - 1);
  const finalMealsHandled = (plan.days[finalDayIndex]?.meals ?? []).every((meal, mealIndex) => {
    const key = buildMealSlotKey(snapshot.planId, finalDayIndex, mealIndex, meal.recipeId);
    return mealLogState[key] === "logged" || mealLogState[key] === "skipped";
  });
  const renewalEligible = today > planEndDate || (today.getTime() === planEndDate.getTime() && finalMealsHandled);
  const renewalLabel = renewalEligible
    ? "Start next week"
    : today.getTime() === planEndDate.getTime()
      ? "Finish final day first"
      : `Available after ${planEndDate.toLocaleDateString("en-PH", { month: "short", day: "numeric" })}`;

  const swapOptions = swapTarget
    ? [...recipesById.values()]
        .filter((recipe) => recipe.id !== swapTarget.currentRecipeId)
        .filter((recipe) => {
          const token = swapTarget.mealLabel.toLowerCase();
          return recipe.mealType?.toLowerCase().includes(token) || recipe.tags.some((tag) => tag.toLowerCase().includes(token));
        })
        .sort((a, b) => a.title.localeCompare(b.title))
        .slice(0, 5)
    : [];

  function applySwap(recipe: RecipeDetail) {
    if (!swapTarget) return;
    setSwappedMeals((current) => ({
      ...current,
      [swapTarget.slotKey]: { recipeId: recipe.id, title: recipe.title }
    }));
    setSwapMessage(`${swapTarget.mealLabel} swapped to ${recipe.title}.`);
    setSwapTarget(null);
  }

  return (
    <section className="plan-layout android-plan">
      <MealPlanWeekStrip snapshot={snapshot} days={plan.days} selectedDayIndex={dayIndex} onSelectDay={setSelectedDayIndex} />
      <section className="android-plan-meals">
        <div className="meal-list">
          {selectedMeals.map((meal, mealIndex) => {
            const slotKey = buildMealSlotKey(snapshot.planId, dayIndex, mealIndex, meal.recipeId);
            const replacement = swappedMeals[slotKey];
            const currentRecipeId = replacement?.recipeId ?? meal.recipeId;
            const currentTitle = replacement?.title ?? meal.title;
            const recipe = recipesById.get(currentRecipeId);
            const status = mealLogState[slotKey];
            const handled = status === "logged" || status === "skipped";
            return (
              <article
                className={`meal-outline-card android-plan-meal ${mealToneClass(meal.mealLabel)} ${status || ""}`}
                key={`${selectedDay?.dayLabel}-${meal.mealLabel}`}
                role="button"
                tabIndex={0}
                onClick={() => onOpenRecipe(currentRecipeId)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") onOpenRecipe(currentRecipeId);
                }}
              >
                <img src={mealImage(meal.mealLabel)} alt="" />
                <div className="android-meal-copy">
                  <span>{meal.mealLabel} • Tap for more details</span>
                  <strong>{currentTitle}</strong>
                </div>
                <div className="meal-actions">
                  <button
                    className={status === "logged" ? "selected" : ""}
                    type="button"
                    disabled={handled}
                    onClick={(event) => {
                      event.stopPropagation();
                      if (!handled) setLogPrompt({ slotKey, mealLabel: meal.mealLabel, title: currentTitle });
                    }}
                  >
                    {status === "logged" ? "LOGGED" : status === "skipped" ? "SKIPPED" : "LOG"}
                  </button>
                  {status !== "logged" ? (
                  <button
                    className={status === "skipped" ? "selected muted" : "muted"}
                    type="button"
                    onClick={(event) => {
                      event.stopPropagation();
                      onMealStatusChange(slotKey, "skipped");
                      setSwapMessage(status === "skipped" ? `${meal.mealLabel} is available again.` : `${meal.mealLabel} skipped. You can continue to the next meal.`);
                    }}
                  >
                    {status === "skipped" ? "UNDO" : "SKIP"}
                  </button>
                  ) : null}
                </div>
                <button
                  className="android-swap-action"
                  type="button"
                  disabled={handled}
                  onClick={(event) => {
                    event.stopPropagation();
                    if (handled) {
                      setSwapMessage("Handled meals are locked and cannot be swapped.");
                    } else {
                      setSwapTarget({ slotKey, mealLabel: meal.mealLabel, currentRecipeId, currentTitle });
                    }
                  }}
                >
                  <span>⇄</span>
                  {handled ? "Locked" : "Swap"}
                </button>
                {recipe ? (
                  <span className="android-meal-a11y-detail">
                    {recipe.calories ?? "--"} kcal, {recipe.proteinGrams ?? "--"}g protein, {recipe.minutes ?? "--"} minutes
                  </span>
                ) : null}
              </article>
            );
          })}
        </div>
      </section>
      {swapMessage ? <p className="inline-feedback android-plan-feedback">{swapMessage}</p> : null}

      <section className="daily-summary-card android-daily-summary">
        <h2>Estimated daily intake</h2>
        <div className="android-summary-content">
          <div className="ring-meter android-calorie-ring" style={{ "--meter-progress": Math.min(1, (selectedDay?.totalCalories ?? 0) / Math.max(1, plan.explanation?.targetCalories ?? 1800)) } as CSSProperties}>
            <strong>{selectedDay?.totalCalories ?? 0}</strong>
            <span>KCAL</span>
          </div>
          <div className="macro-bars">
            <strong className="android-macro-title">Macronutrients</strong>
            <MetricBar label="Protein" value={`${Math.round(dayProtein)}g`} progress={dayProtein / Math.max(1, plan.explanation?.targetProtein ?? 90)} tone="pink" />
            <MetricBar label="Carbs" value={`${Math.round(dayCarbs)}g`} progress={dayCarbs / Math.max(1, plan.explanation?.targetCarbs ?? 260)} tone="gold" />
            <MetricBar label="Fiber" value={`${Math.round(dayFiber)}g`} progress={dayFiber / Math.max(1, plan.explanation?.fiberMinTarget ?? 30)} tone="green" />
          </div>
        </div>
      </section>

      <section className="shopping-card android-plan-shopping">
        <h2>Ready to shop?</h2>
        <p>Your grocery list updates automatically from this plan. A new weekly plan remains available after you finish the current week.</p>
        <div className="android-shopping-actions">
          <button className="primary-button" type="button" onClick={onOpenGrocery}>Go to Grocery</button>
          <button type="button" onClick={onGenerate} disabled={busy || !renewalEligible}>{busy ? "Planning" : renewalLabel}</button>
        </div>
        <img src="/images/pcosina-ready-to-shop.png" alt="" />
      </section>

      {logPrompt ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setLogPrompt(null)}>
          <section className="android-modal android-plan-dialog" role="dialog" aria-modal="true" aria-label="Log this meal" onClick={(event) => event.stopPropagation()}>
            <h2>Log this meal?</h2>
            <p>Mark {logPrompt.mealLabel} • {logPrompt.title} as eaten? Logged meals cannot be swapped from this plan.</p>
            <div className="android-dialog-actions">
              <button type="button" onClick={() => setLogPrompt(null)}>Cancel</button>
              <button className="primary-button" type="button" onClick={() => {
                onMealStatusChange(logPrompt.slotKey, "logged");
                setSwapMessage(`${logPrompt.mealLabel} logged.`);
                setLogPrompt(null);
              }}>Log meal</button>
            </div>
          </section>
        </div>
      ) : null}

      {swapTarget ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setSwapTarget(null)}>
          <section className="android-modal android-plan-dialog" role="dialog" aria-modal="true" aria-label={`Swap ${swapTarget.mealLabel}`} onClick={(event) => event.stopPropagation()}>
            <div>
              <h2>Swap {swapTarget.mealLabel}</h2>
              <p>{swapTarget.currentTitle}</p>
            </div>
            <div className="android-swap-options">
              {swapOptions.length ? swapOptions.map((option) => (
                <button type="button" key={option.id} onClick={() => applySwap(option)}>
                  <strong>{option.title}</strong>
                  <span>{option.mealType || swapTarget.mealLabel}</span>
                </button>
              )) : <p>No alternative meals are available right now.</p>}
            </div>
            <div className="android-dialog-actions">
              <button className="primary-button" type="button" onClick={() => setSwapTarget(null)}>Close</button>
            </div>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function NoSafePlan({ plan }: { plan: GeneratePlanResponse }) {
  return (
    <section className="panel no-safe">
      <div className="panel-heading">
        <AlertTriangle size={20} />
        <h2>No Safe Plan</h2>
      </div>
      <p>{plan.message}</p>
      <div className="tag-row">
        {(plan.machineReasonCodes || []).map((code) => (
          <span className="tag warning" key={code}>
            {code}
          </span>
        ))}
      </div>
      <div className="guidance-grid">
        <GuidanceList title="Guidance" items={plan.humanGuidance || []} />
        <GuidanceList title="Possible relaxations" items={plan.suggestedRelaxations || []} />
      </div>
    </section>
  );
}

function GroceryView({
  snapshot,
  pantry,
  profile,
  onAddPantry
}: {
  snapshot: PlanSnapshot | null;
  pantry: PantryEntry[];
  profile: UserProfile;
  onAddPantry: (input: { name: string; quantity: string; expiryDate: string }) => Promise<void>;
}) {
  const [selectedCategoryIndex, setSelectedCategoryIndex] = useState(0);
  const [checkedItems, setCheckedItems] = useState<Set<string>>(new Set());
  const [pantryOptOut, setPantryOptOut] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState("");
  const [filterOpen, setFilterOpen] = useState(false);
  const [filterScope, setFilterScope] = useState<"all" | "need" | "covered">("all");
  const [filterCategories, setFilterCategories] = useState<Set<string>>(new Set());
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(new Set());
  const [pantryOpen, setPantryOpen] = useState(false);
  const [addPantryOpen, setAddPantryOpen] = useState(false);
  const [pantryDraft, setPantryDraft] = useState({ name: "", quantity: "", expiryDate: "" });
  const plan = snapshot?.response;
  const items = groceryDisplayItems(plan, pantry);
  if (!plan || plan.status !== "success") {
    return <EmptyState icon={ShoppingCart} title="No grocery list yet" text="Generate a meal plan first, then your shopping list will appear here." />;
  }

  const filteredItems = items.filter((item) => {
    const key = item.key || item.name;
    const covered = checkedItems.has(key) || (item.pantryMatched && !pantryOptOut.has(key));
    const searchMatches = !searchQuery.trim() || item.name.toLowerCase().includes(searchQuery.trim().toLowerCase());
    const scopeMatches = filterScope === "all" || (filterScope === "need" ? !covered : covered);
    const categoryMatches = filterCategories.size === 0 || filterCategories.has(item.category || "Others");
    return searchMatches && scopeMatches && categoryMatches;
  });
  const grouped = groupGroceryItems(filteredItems);
  const categories = grouped.map(([category]) => category);
  const safeCategoryIndex = Math.min(selectedCategoryIndex, Math.max(categories.length - 1, 0));
  const [selectedCategory, selectedItems] = grouped[safeCategoryIndex] ?? ["Grocery", []];
  const coveredCount = items.filter((item) => {
    const key = item.key || item.name;
    return checkedItems.has(key) || (item.pantryMatched && !pantryOptOut.has(key));
  }).length;
  const progress = items.length ? coveredCount / items.length : 0;
  const remainingCount = Math.max(0, items.length - coveredCount);
  const estimate = plan.groceryOutput?.finalGroceryEstimatePhp ?? plan.groceryOutput?.estimatedTotalPhp ?? 0;
  const budget = plan.groceryOutput?.weeklyBudgetPhp || plan.groceryOutput?.userBudgetPhp || profile.weeklyBudgetPhp || 0;
  const budgetProgress = budget ? estimate / budget : 0;
  const budgetDelta = budget ? budget - estimate : 0;
  const withinBudget = budget > 0 && budgetDelta >= 0;
  const selectedExpanded = expandedCategories.has(selectedCategory);
  const displayedItems = selectedExpanded ? selectedItems : selectedItems.slice(0, 2);
  const selectedCoveredCount = selectedItems.filter((item) => {
    const key = item.key || item.name;
    return checkedItems.has(key) || (item.pantryMatched && !pantryOptOut.has(key));
  }).length;
  const allCategories = ["Produce", "Meat/Seafood", "Eggs & Dairy", "Dry Goods", "Spices & Condiments", "Others"];

  function toggleItem(item: GroceryDisplayItem) {
    const key = item.key || item.name;
    if (item.pantryMatched) {
      setPantryOptOut((current) => {
        const next = new Set(current);
        if (next.has(key)) next.delete(key);
        else next.add(key);
        return next;
      });
      return;
    }
    setCheckedItems((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  async function shareList() {
    const text = `PCOSina Grocery List\n\n${items.map((item) => `• ${item.name} (${item.quantity || "As needed"})${item.pantryMatched ? " • pantry match" : ""}`).join("\n")}`;
    if (navigator.share) {
      await navigator.share({ title: "PCOSina Grocery List", text }).catch(() => undefined);
    } else {
      await navigator.clipboard?.writeText(text);
    }
  }

  async function savePantryDraft() {
    if (!pantryDraft.name.trim()) return;
    await onAddPantry(pantryDraft);
    setPantryDraft({ name: "", quantity: "", expiryDate: "" });
    setAddPantryOpen(false);
  }

  return (
    <section className="grocery-layout android-grocery">
      <section className={withinBudget ? "android-budget-card within" : budget ? "android-budget-card over" : "android-budget-card"}>
        <div className="android-budget-head">
          <img src="/images/pcosina-grocery-budget.png" alt="" />
          <div>
            <div className="android-budget-title">
              <strong>{budget ? (withinBudget ? "Within the Budget" : "Over the Budget") : "Budget not set"}</strong>
              {budget ? <img src={withinBudget ? "/images/pcosina-budgeting-like.png" : "/images/pcosina-budgeting-disliked.png"} alt="" /> : null}
            </div>
            <span className="android-status-pill">
              {budget ? (withinBudget ? `Within Budget! ${moneyPhp(budgetDelta)} left.` : `Over Budget by ${moneyPhp(Math.abs(budgetDelta))}.`) : "Budget not set yet."}
            </span>
          </div>
        </div>
        <strong className="android-budget-label">Total Estimated Spending</strong>
        <h2>{budget ? `${moneyPhp(estimate)} / ${moneyPhp(budget)}` : moneyPhp(estimate)}</h2>
        {budget ? (
          <div className="android-budget-track">
            <i style={{ transform: `scaleX(${Math.min(1, budgetProgress)})` }} />
          </div>
        ) : <p>Set a weekly grocery budget in your profile to compare plan costs.</p>}
      </section>

      <section className="android-grocery-progress">
        <div className="android-progress-copy">
          <div>
            <h2>Grocery Progress</h2>
            <p>Built for your primary-user plan and synced with your saved pantry.</p>
          </div>
          <span>{Math.round(progress * 100)}% checked</span>
        </div>
        <div className="android-progress-summary">
          <div className="ring-meter grocery-left-ring" style={{ "--meter-progress": Math.max(0, 1 - progress) } as CSSProperties}>
            <strong>{remainingCount}/{Math.max(1, items.length)}</strong>
            <span>items left</span>
          </div>
          <div>
            <strong>{remainingCount === 0 ? "All items checked" : "Shop what is still missing"}</strong>
            <p>Use pantry matches first, then buy only the remaining budgeted ingredients for this week’s plan.</p>
          </div>
        </div>
        <div className="android-grocery-actions">
          <button type="button" onClick={shareList}>
            <span><img src="/images/pcosina-send.png" alt="" /></span>
            Share Grocery List?
          </button>
          <button type="button" onClick={() => setPantryOpen(true)}>
            <span><img src="/images/pcosina-cart.png" alt="" /></span>
            View Pantry List?
          </button>
        </div>
      </section>

      <section className="android-kitchen-hub">
        <div className="android-kitchen-title">
          <img src="/images/grocery-kitchen-hub.png" alt="" />
          <div>
            <h2>Your Kitchen Hub</h2>
            <p>{profile.goal.toLowerCase().includes("weight") ? "Prioritize high-fiber staples and steady-carb swaps before extras." : "Use pantry matches first so your grocery list stays practical and budget-aware."}</p>
          </div>
        </div>
        <label className="android-grocery-search">
          <Search size={20} />
          <input value={searchQuery} onChange={(event) => { setSearchQuery(event.target.value); setSelectedCategoryIndex(0); }} placeholder="Search ingredients" />
          <button className={filterOpen || filterScope !== "all" || filterCategories.size ? "active" : ""} type="button" onClick={() => setFilterOpen((value) => !value)} aria-label="Open grocery filters">
            <SlidersHorizontal size={18} />
          </button>
        </label>
        {filterOpen ? (
          <div className="android-filter-panel">
            <div className="android-filter-row">
              {([["all", "All items"], ["need", "Need to buy"], ["covered", "Bought/Pantry"]] as const).map(([value, label]) => (
                <button className={filterScope === value ? "selected" : ""} type="button" key={value} onClick={() => { setFilterScope(value); setSelectedCategoryIndex(0); }}>{label}</button>
              ))}
            </div>
            <div className="android-filter-row categories">
              {allCategories.map((category) => (
                <button className={filterCategories.has(category) ? "selected" : ""} type="button" key={category} onClick={() => {
                  setFilterCategories((current) => {
                    const next = new Set(current);
                    if (next.has(category)) next.delete(category);
                    else next.add(category);
                    return next;
                  });
                  setSelectedCategoryIndex(0);
                }}>{category}</button>
              ))}
            </div>
          </div>
        ) : null}
      </section>

      <GroceryCategoryPicker
        category={selectedCategory}
        index={safeCategoryIndex}
        total={categories.length}
        onPrevious={() => setSelectedCategoryIndex((current) => categories.length ? (current - 1 + categories.length) % categories.length : 0)}
        onNext={() => setSelectedCategoryIndex((current) => categories.length ? (current + 1) % categories.length : 0)}
      />
      <div className="android-category-card">
        {filteredItems.length === 0 ? (
          <EmptyState icon={ShoppingCart} title="No ingredients match" text="Clear a filter or try a broader search term." />
        ) : (
          <>
            <div className="android-category-head">
              <span>{groceryCategoryEmoji(selectedCategory)}</span>
              <div>
                <h2>{selectedCategory}</h2>
                <p>{selectedCoveredCount} of {selectedItems.length} items bought/covered</p>
              </div>
              {selectedItems.length > 2 ? <button type="button" onClick={() => setExpandedCategories((current) => {
                const next = new Set(current);
                if (next.has(selectedCategory)) next.delete(selectedCategory);
                else next.add(selectedCategory);
                return next;
              })}>{selectedExpanded ? "−" : "+"}</button> : null}
            </div>
            {displayedItems.map((item) => {
            const key = item.key || item.name;
            const pantryCovered = item.pantryMatched && !pantryOptOut.has(key);
            const checked = pantryCovered || checkedItems.has(key);
            return (
            <article className={checked ? "android-grocery-item checked" : "android-grocery-item"} key={key} onClick={() => toggleItem(item)}>
              <button className="check-circle" type="button" aria-label={checked ? "Mark as not bought" : "Mark as bought"}>
                {checked ? <CheckCircle2 size={18} /> : null}
              </button>
              <div>
                <strong>{item.name}</strong>
                <span>{item.quantity || "As needed"}</span>
              </div>
              <div className="row-meta">
                <strong>{checked ? moneyPhp(0) : moneyPhp(item.estimatedCostPhp)}</strong>
                <span className="android-item-status">{pantryCovered ? "In Pantry" : checked ? "Bought" : "To Buy"}</span>
              </div>
            </article>
            );
            })}
            {selectedItems.length > 2 ? (
              <button className="android-expand-category" type="button" onClick={() => setExpandedCategories((current) => {
                const next = new Set(current);
                if (next.has(selectedCategory)) next.delete(selectedCategory);
                else next.add(selectedCategory);
                return next;
              })}>{selectedExpanded ? "Show less" : `View all ${selectedItems.length} items`}</button>
            ) : null}
          </>
        )}
      </div>

      <section className="android-pantry-cta">
        <div>
          <strong>Add an item to the pantry</strong>
          <span>Use the + button to save a staple.</span>
        </div>
        <button type="button" onClick={() => setAddPantryOpen(true)} aria-label="Add pantry item"><Plus size={26} /></button>
      </section>

      {pantryOpen ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setPantryOpen(false)}>
          <section className="android-modal" role="dialog" aria-modal="true" aria-label="Pantry List" onClick={(event) => event.stopPropagation()}>
            <div className="android-modal-head"><h2>Pantry List</h2><button type="button" onClick={() => setPantryOpen(false)}>×</button></div>
            {pantry.length ? pantry.map((entry) => <div className="android-pantry-row" key={entry.id}><strong>{entry.name}</strong><span>{entry.quantity || "Saved pantry item"}</span></div>) : <p>Your pantry is empty.</p>}
            <button className="primary-button" type="button" onClick={() => { setPantryOpen(false); setAddPantryOpen(true); }}>Add pantry item</button>
          </section>
        </div>
      ) : null}
      {addPantryOpen ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setAddPantryOpen(false)}>
          <section className="android-modal" role="dialog" aria-modal="true" aria-label="Add pantry item" onClick={(event) => event.stopPropagation()}>
            <div className="android-modal-head"><h2>Add Pantry Item</h2><button type="button" onClick={() => setAddPantryOpen(false)}>×</button></div>
            <label>Pantry item<input value={pantryDraft.name} onChange={(event) => setPantryDraft({ ...pantryDraft, name: event.target.value })} /></label>
            <label>Amount and unit<input value={pantryDraft.quantity} onChange={(event) => setPantryDraft({ ...pantryDraft, quantity: event.target.value })} placeholder="e.g. 500 g" /></label>
            <label>Use-by date<input type="date" value={pantryDraft.expiryDate} onChange={(event) => setPantryDraft({ ...pantryDraft, expiryDate: event.target.value })} /></label>
            <button className="primary-button" type="button" disabled={!pantryDraft.name.trim()} onClick={savePantryDraft}>Save pantry item</button>
          </section>
        </div>
      ) : null}
    </section>
  );
}

export function ProgressView({
  snapshot,
  profile,
  mealLogState,
  onOpenSupport
}: {
  snapshot: PlanSnapshot | null;
  profile: UserProfile;
  mealLogState: Record<string, MealLogStatus>;
  onOpenSupport: () => void;
}) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [highlightsOpen, setHighlightsOpen] = useState(false);
  const [checkInOpen, setCheckInOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [energyLevel, setEnergyLevel] = useState(3);
  const [moodLevel, setMoodLevel] = useState(3);
  const [cravingsLevel, setCravingsLevel] = useState(3);
  const [notes, setNotes] = useState("");
  const [weightInput, setWeightInput] = useState("");
  const [weeklyNotes, setWeeklyNotes] = useState("");
  const [weeklySpendInput, setWeeklySpendInput] = useState("");
  const [feedbackTags, setFeedbackTags] = useState<Set<string>>(new Set());
  const [savedCheckIn, setSavedCheckIn] = useState(false);
  const [savedReview, setSavedReview] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [selectedDate, setSelectedDate] = useState(() => localDateToken(new Date()));
  const plan = snapshot?.response;
  const plannedMeals = plan?.status === "success" ? plan.days.flatMap((day) => day.meals) : [];
  const currentLogs = snapshot
    ? Object.entries(mealLogState).filter(([key]) => key.startsWith(`${snapshot.planId}:`))
    : [];
  const loggedCount = currentLogs.filter(([, status]) => status === "logged").length;
  const skippedCount = currentLogs.filter(([, status]) => status === "skipped").length;
  const adherence = plannedMeals.length ? loggedCount / plannedMeals.length : 0;
  const bmi = profile.heightCm > 0 ? profile.weightKg / Math.pow(profile.heightCm / 100, 2) : 0;
  const bmiLabel = bmi ? bmi.toFixed(1) : "--";
  const budget = plan?.status === "success"
    ? plan.groceryOutput?.weeklyBudgetPhp || plan.groceryOutput?.userBudgetPhp || profile.weeklyBudgetPhp
    : profile.weeklyBudgetPhp;
  const cost = plan?.status === "success"
    ? plan.groceryOutput?.finalGroceryEstimatePhp ?? plan.groceryOutput?.estimatedTotalPhp ?? 0
    : 0;
  const reportedSpend = Number(weeklySpendInput);
  const effectiveCost = Number.isFinite(reportedSpend) && reportedSpend > 0 ? reportedSpend : cost;
  const savings = budget ? budget - effectiveCost : 0;
  const protein = Math.round(Number(plan?.explanation?.avgProtein ?? 0));
  const carbs = Math.round(Number(plan?.explanation?.avgCarbs ?? 0));
  const fats = Math.round(Number(plan?.explanation?.avgFats ?? 0));
  const planWeekStart = snapshot ? startOfPlanWeek(snapshot) : null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const daysCompleted = plan?.status === "success" && snapshot
    ? plan.days.filter((day, dayIndex) =>
        day.meals.length > 0 && day.meals.every((meal, mealIndex) =>
          mealLogState[buildMealSlotKey(snapshot.planId, dayIndex, mealIndex, meal.recipeId)] === "logged"
        )
      ).length
    : 0;
  const calendarCells = useMemo(() => {
    const first = new Date(visibleMonth);
    const mondayIndex = (first.getDay() + 6) % 7;
    const gridStart = new Date(first);
    gridStart.setDate(first.getDate() - mondayIndex);
    return Array.from({ length: 42 }, (_, index) => {
      const date = new Date(gridStart);
      date.setDate(gridStart.getDate() + index);
      date.setHours(0, 0, 0, 0);
      let planned = 0;
      let logged = 0;
      let skipped = 0;
      if (snapshot && plan?.status === "success" && planWeekStart) {
        const dayIndex = Math.round((date.getTime() - planWeekStart.getTime()) / 86_400_000);
        const day = plan.days[dayIndex];
        if (day) {
          planned = day.meals.length;
          day.meals.forEach((meal, mealIndex) => {
            const status = mealLogState[buildMealSlotKey(snapshot.planId, dayIndex, mealIndex, meal.recipeId)];
            if (status === "logged") logged += 1;
            if (status === "skipped") skipped += 1;
          });
        }
      }
      const future = date.getTime() > today.getTime();
      const status = future ? "future" : planned && logged === planned ? "complete" : logged || skipped ? "partial" : planned && date < today ? "missed" : localDateToken(date) === localDateToken(today) ? "today" : "none";
      return { date, planned, logged, skipped, status };
    });
  }, [mealLogState, plan, planWeekStart, snapshot, today.getTime(), visibleMonth]);
  const selectedCalendarDay = calendarCells.find((day) => localDateToken(day.date) === selectedDate);
  const toggleExpanded = (key: string) => setExpanded((current) => ({ ...current, [key]: !current[key] }));

  return (
    <section className="android-progress-layout">
      <section className="android-progress-overview">
        <article className="android-progress-bmi">
          <p className="eyebrow">Target Your BMI</p>
          <div className="android-bmi-gauge" style={{ "--bmi-position": `${Math.min(100, Math.max(0, ((bmi || 18.5) - 15) / 20 * 100))}%` } as CSSProperties}>
            <span />
          </div>
          <strong>{bmiLabel}</strong>
          <div className="android-bmi-scale"><i /><i /><i /><i /></div>
          <small>{bmi ? bmiCategory(bmi) : "Add height and weight in Settings."}</small>
        </article>
        <article className="android-progress-summary-card">
          <p className="eyebrow">Weekly Meal Summary</p>
          <div className="android-summary-metrics">
            {[
              ["Logged", loggedCount, plannedMeals.length ? loggedCount / plannedMeals.length : 0],
              ["Planned", plannedMeals.length, plannedMeals.length ? 1 : 0],
              ["Adherence", `${Math.round(adherence * 100)}%`, adherence],
              ["Days", daysCompleted, daysCompleted / 7]
            ].map(([label, value, progress]) => (
              <div key={label}>
                <span className="android-mini-ring" style={{ "--meter-progress": progress } as CSSProperties}><b>{value}</b></span>
                <small>{label}</small>
              </div>
            ))}
          </div>
        </article>
      </section>

      {profile.goal.toLowerCase().includes("weight") || profile.targetWeightKg ? (
        <section className="android-weight-support">
          <img src="/images/pcosina-weekly-progress-lock.png" alt="" />
          <div>
            <p className="eyebrow">Weight support</p>
            <h2>{profile.targetWeightKg ? `${profile.weightKg} kg toward ${profile.targetWeightKg} kg` : "Keep changes gradual"}</h2>
            <p>Use trends as a reflection tool, not as a diagnosis or daily score.</p>
          </div>
        </section>
      ) : null}

      <button className="android-highlights-launcher" type="button" onClick={() => setHighlightsOpen(true)}>
        <img src="/images/pcosina-weekly-highlight.png" alt="" />
        <span><small>Weekly Highlights</small><strong>{loggedCount ? `${loggedCount} meals completed this week` : "Start logging to see your highlights"}</strong></span>
        <b>Open</b>
      </button>

      <header className="android-progress-section-head">
        <p className="eyebrow">For review</p>
        <h2>What happened</h2>
        <p>These tracking cards help you review the week. They do not automatically change your next meal plan.</p>
      </header>

      <section className="android-progress-calendar">
        <div className="android-calendar-title">
          <img src="/images/pcosina-calendar.png" alt="" />
          <div><p className="eyebrow">History Calendar</p><h2>{visibleMonth.toLocaleDateString(undefined, { month: "long", year: "numeric" })}</h2></div>
          <div>
            <button type="button" aria-label="Previous month" onClick={() => setVisibleMonth((date) => new Date(date.getFullYear(), date.getMonth() - 1, 1))}>‹</button>
            <button type="button" aria-label="Next month" onClick={() => setVisibleMonth((date) => new Date(date.getFullYear(), date.getMonth() + 1, 1))}>›</button>
          </div>
        </div>
        <div className="android-calendar-weekdays">{["M", "T", "W", "T", "F", "S", "S"].map((day, index) => <span key={`${day}-${index}`}>{day}</span>)}</div>
        <div className="android-calendar-grid">
          {calendarCells.map((day) => (
            <button
              className={`${day.status} ${localDateToken(day.date) === selectedDate ? "selected" : ""} ${day.date.getMonth() !== visibleMonth.getMonth() ? "outside" : ""}`}
              key={localDateToken(day.date)}
              type="button"
              aria-label={day.date.toLocaleDateString()}
              onClick={() => setSelectedDate(localDateToken(day.date))}
            >
              {day.date.getDate()}<i />
            </button>
          ))}
        </div>
        <div className="android-calendar-legend"><span className="complete">Complete</span><span className="partial">Partial</span><span className="missed">Missed</span><span className="future">Future</span></div>
        <div className="android-calendar-detail">
          <strong>{selectedCalendarDay?.date.toLocaleDateString(undefined, { weekday: "long", month: "short", day: "numeric" })}</strong>
          <span>{selectedCalendarDay?.planned ? `${selectedCalendarDay.logged}/${selectedCalendarDay.planned} meals completed${selectedCalendarDay.skipped ? ` · ${selectedCalendarDay.skipped} skipped` : ""}` : "No planned meals or reflections for this day."}</span>
        </div>
      </section>

      <section className="android-progress-dropdowns">
        <ProgressDropdown title="Weekly adherence" value={`${Math.round(adherence * 100)}%`} subtitle={`${loggedCount}/${plannedMeals.length} meals complete this week`} image="/images/pcosina-weekly-adherence.png" open={Boolean(expanded.adherence)} onToggle={() => toggleExpanded("adherence")}>
          {plan?.status === "success" && snapshot ? plan.days.map((day, dayIndex) => {
            const completed = day.meals.filter((meal, mealIndex) => mealLogState[buildMealSlotKey(snapshot.planId, dayIndex, mealIndex, meal.recipeId)] === "logged").length;
            return <MetricBar key={day.dayLabel} label={formatDayTitle(day.dayLabel, dayIndex)} value={`${completed}/${day.meals.length}`} progress={day.meals.length ? completed / day.meals.length : 0} tone="pink" />;
          }) : <p>No active plan yet.</p>}
        </ProgressDropdown>
        <ProgressDropdown title="Weekly savings" value={budget ? moneyPhp(savings) : "--"} subtitle={savings >= 0 ? "Estimated under budget" : "Estimated over budget"} image="/images/pcosina-grocery-budget.png" open={Boolean(expanded.savings)} onToggle={() => toggleExpanded("savings")}>
          <div className="android-spend-stats"><span><small>Budget</small><strong>{moneyPhp(budget)}</strong></span><span><small>Estimate</small><strong>{moneyPhp(cost)}</strong></span><span><small>Actual</small><strong>{reportedSpend > 0 ? moneyPhp(reportedSpend) : "Not added"}</strong></span></div>
        </ProgressDropdown>
        <ProgressDropdown title="Average macros" value={protein ? `${protein}g protein` : "--"} subtitle="Daily plan average and guide ranges" image="/images/pcosina-average-daily-macros.png" open={Boolean(expanded.macros)} onToggle={() => toggleExpanded("macros")}>
          <MetricBar label="Protein" value={`${protein || "--"}g`} progress={protein / 90} tone="pink" />
          <MetricBar label="Carbs" value={`${carbs || "--"}g`} progress={carbs / 260} tone="gold" />
          <MetricBar label="Fats" value={`${fats || "--"}g`} progress={fats / 80} tone="pink" />
        </ProgressDropdown>
        <ProgressDropdown title="Symptom trends" value={savedCheckIn ? "1 check-in" : "No check-ins"} subtitle="Energy, mood, cravings, and profile symptoms" image="/images/pcosina-activity.png" open={Boolean(expanded.symptoms)} onToggle={() => toggleExpanded("symptoms")}>
          {savedCheckIn ? <><MetricBar label="Energy" value={`${energyLevel}/5`} progress={energyLevel / 5} tone="pink" /><MetricBar label="Mood" value={`${moodLevel}/5`} progress={moodLevel / 5} tone="gold" /><MetricBar label="Cravings" value={`${cravingsLevel}/5`} progress={cravingsLevel / 5} tone="pink" /></> : <p>Complete today’s reflection to start seeing trends.</p>}
        </ProgressDropdown>
      </section>

      <section className="android-progress-actions">
        <div>
          <p className="eyebrow">Today’s progress</p>
          <h2>{savedCheckIn ? "Reflection saved for today" : "How did today feel?"}</h2>
          <p>Use Check in for today’s reflection, then Review week for spending notes and plan feedback.</p>
        </div>
        <button className="primary-button" type="button" onClick={() => setCheckInOpen(true)}>Check in</button>
        <button className="secondary-button" type="button" onClick={() => setReviewOpen(true)}>Review week</button>
        {savedReview ? <p className="inline-feedback">Weekly review saved.</p> : null}
      </section>

      <button className="android-progress-support" type="button" onClick={onOpenSupport}>
        <span><strong>Need help with your progress?</strong><small>Open Support for guidance and feedback.</small></span>
        <b>Open Support</b>
      </button>

      {highlightsOpen ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setHighlightsOpen(false)}>
          <section className="android-modal android-progress-dialog" role="dialog" aria-modal="true" aria-label="Weekly Highlights" onClick={(event) => event.stopPropagation()}>
            <div className="android-modal-head"><div><p className="eyebrow">Weekly Highlights</p><h2>How Your Week Went</h2></div><button type="button" aria-label="Close highlights" onClick={() => setHighlightsOpen(false)}>×</button></div>
            <img className="android-dialog-art" src="/images/pcosina-weekly-highlight.png" alt="" />
            <div className="android-week-nodes">{["M", "T", "W", "T", "F", "S", "S"].map((day, index) => <span className={index < daysCompleted ? "complete" : ""} key={`${day}-${index}`}>{day}</span>)}</div>
            <div className="android-highlight-row"><strong>{Math.round(adherence * 100)}% adherence</strong><span>{loggedCount} meals completed from your current plan.</span></div>
            <div className="android-highlight-row"><strong>{budget ? moneyPhp(Math.abs(savings)) : "--"} budget difference</strong><span>{savings >= 0 ? "Your current estimate stays within the weekly budget." : "Review actual spend and grocery choices."}</span></div>
            <button className="primary-button" type="button" onClick={() => setHighlightsOpen(false)}>Got it</button>
          </section>
        </div>
      ) : null}

      {checkInOpen ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setCheckInOpen(false)}>
          <section className="android-modal android-progress-dialog" role="dialog" aria-modal="true" aria-label="Today check in" onClick={(event) => event.stopPropagation()}>
            <div className="android-modal-head"><div><p className="eyebrow">Today</p><h2>Check in</h2></div><button type="button" aria-label="Close check in" onClick={() => setCheckInOpen(false)}>×</button></div>
            <LevelPicker label="Energy" value={energyLevel} onChange={setEnergyLevel} />
            <LevelPicker label="Mood" value={moodLevel} onChange={setMoodLevel} />
            <LevelPicker label="Cravings" value={cravingsLevel} onChange={setCravingsLevel} reverse />
            <label>Reflection notes<textarea rows={3} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Anything you noticed today?" /></label>
            <label>Weight (optional)<input inputMode="decimal" value={weightInput} onChange={(event) => setWeightInput(event.target.value)} placeholder={`${profile.weightKg} kg`} /></label>
            <button className="primary-button" type="button" onClick={() => { setSavedCheckIn(true); setCheckInOpen(false); }}>Save Reflection</button>
          </section>
        </div>
      ) : null}

      {reviewOpen ? (
        <div className="android-modal-backdrop" role="presentation" onClick={() => setReviewOpen(false)}>
          <section className="android-modal android-progress-dialog" role="dialog" aria-modal="true" aria-label="Weekly Review" onClick={(event) => event.stopPropagation()}>
            <div className="android-modal-head"><div><p className="eyebrow">For review</p><h2>Weekly Review</h2></div><button type="button" aria-label="Close weekly review" onClick={() => setReviewOpen(false)}>×</button></div>
            <label>How did this week go?<textarea rows={3} value={weeklyNotes} onChange={(event) => setWeeklyNotes(event.target.value)} placeholder="Add a short weekly note." /></label>
            <label>Actual grocery spend<input inputMode="decimal" value={weeklySpendInput} onChange={(event) => setWeeklySpendInput(event.target.value)} placeholder="PHP 0" /></label>
            <fieldset className="android-feedback-tags"><legend>Plan feedback</legend>{["Too repetitive", "Too expensive", "Too hard to cook"].map((tag) => <button className={feedbackTags.has(tag) ? "selected" : ""} key={tag} type="button" onClick={() => setFeedbackTags((current) => { const next = new Set(current); if (next.has(tag)) next.delete(tag); else next.add(tag); return next; })}>{tag}</button>)}</fieldset>
            <button className="primary-button" type="button" onClick={() => { setSavedReview(true); setReviewOpen(false); }}>Save Review</button>
          </section>
        </div>
      ) : null}
    </section>
  );
}

function ProgressDropdown({
  title,
  value,
  subtitle,
  image,
  open,
  onToggle,
  children
}: {
  title: string;
  value: string;
  subtitle: string;
  image: string;
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <article className={`android-progress-dropdown ${open ? "open" : ""}`}>
      <button type="button" aria-expanded={open} onClick={onToggle}>
        <img src={image} alt="" />
        <span><small>For review</small><strong>{title}</strong><em>{subtitle}</em></span>
        <b>{value}</b>
        <i>{open ? "−" : "+"}</i>
      </button>
      {open ? <div className="android-progress-dropdown-body">{children}</div> : null}
    </article>
  );
}

function SupportView({ onOpenMealPlan }: { onOpenMealPlan: () => void }) {
  const [feedbackText, setFeedbackText] = useState("");
  const [feedbackStatus, setFeedbackStatus] = useState<string | null>(null);
  const trimmed = feedbackText.trim();
  return (
    <section className="support-layout">
      <section className="support-feedback-card">
        <div className="panel-heading">
          <img src="/images/pcosina-email.png" alt="" />
          <h2>Need a hand?</h2>
        </div>
        <p>Tell us if planning, logging, or grocery shopping feels harder than it should be.</p>
        <textarea
          value={feedbackText}
          onChange={(event) => {
            setFeedbackText(event.target.value.slice(0, 2000));
            setFeedbackStatus(null);
          }}
          placeholder="Share the screen, step, or issue."
          rows={4}
        />
        <button
          className="icon-text-button primary wide"
          type="button"
          disabled={!trimmed}
          onClick={() => {
            setFeedbackText("");
            setFeedbackStatus("Feedback saved. Thank you.");
          }}
        >
          <span>Send feedback now</span>
        </button>
        {feedbackStatus ? <p className="inline-feedback">{feedbackStatus}</p> : null}
      </section>

      <section className="support-directory-card">
        <div className="panel-heading">
          <img src="/images/pcosina-book.png" alt="" />
          <h2>App Directory</h2>
        </div>
        <p>Quick guide to navigating the PCOSina application.</p>
        <div className="support-directory-grid">
          <SupportDirectoryTile icon="/images/pcosina-nav-home.png" label="Home" description="Dashboard for today's focus." />
          <SupportDirectoryTile icon="/images/pcosina-nav-progress.png" label="Progress" description="Track meals, check-ins, budget, and body trends." />
          <SupportDirectoryTile icon="/images/pcosina-nav-plan.png" label="Plan" description="Follow your weekly PCOS-friendly meal plan." />
          <SupportDirectoryTile icon="/images/pcosina-nav-support.png" label="Support" description="Access help and send feedback." />
          <SupportDirectoryTile icon="/images/pcosina-nav-grocery.png" label="Grocery" description="Manage shopping and pantry coverage." wide />
        </div>
      </section>

      <section className="support-fresh-card">
        <div>
          <h2>Open this week's plan</h2>
          <p>Review meals, grocery, and progress from one place.</p>
          <button className="icon-text-button primary" type="button" onClick={onOpenMealPlan}>
            <span>Open Plan</span>
          </button>
        </div>
        <img src="/images/pcosina-activity.png" alt="" />
      </section>
    </section>
  );
}

function SupportDirectoryTile({
  icon,
  label,
  description,
  wide = false
}: {
  icon: string;
  label: string;
  description: string;
  wide?: boolean;
}) {
  return (
    <div className={wide ? "support-directory-tile wide" : "support-directory-tile"}>
      <span>
        <img src={icon} alt="" />
        {label}
      </span>
      <p>{description}</p>
    </div>
  );
}

interface SettingsReminderPreferences {
  master: boolean;
  meals: boolean;
  breakfast: string;
  lunch: string;
  dinner: string;
  planReady: boolean;
  grocery: boolean;
  weekly: boolean;
  weeklyDay: string;
  weeklyTime: string;
  checkIn: boolean;
  comeBack: boolean;
  quiet: boolean;
  quietStart: string;
  quietEnd: string;
}

export function SettingsView({
  profile,
  setProfile,
  pantry,
  plans,
  session,
  pendingCount,
  onSaveProfile,
  onBack,
  onClearWeekData,
  onSignOut
}: {
  profile: UserProfile;
  setProfile: (profile: UserProfile) => void;
  pantry: PantryEntry[];
  plans: PlanSnapshot[];
  session: AppSession;
  pendingCount: number;
  onSaveProfile: (profile?: UserProfile) => void;
  onBack: () => void;
  onClearWeekData: () => Promise<void>;
  onSignOut: () => void;
}) {
  const [focus, setFocus] = useState<"profile" | "reminders" | "account">("profile");
  const [reminderFocus, setReminderFocus] = useState<"control" | "meals" | "week" | "routine">("control");
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [profileEditorOpen, setProfileEditorOpen] = useState(false);
  const [clearOpen, setClearOpen] = useState(false);
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [avatarDraft, setAvatarDraft] = useState(profile.avatarId || "doctor_dog");
  const [nameDraft, setNameDraft] = useState(profile.displayName);
  const reminderKey = `pcosina-reminders:${session.uid}`;
  const [reminders, setReminders] = useState<SettingsReminderPreferences>(() => {
    const fallback: SettingsReminderPreferences = {
      master: false,
      meals: false,
      breakfast: "08:00",
      lunch: "12:00",
      dinner: "18:00",
      planReady: true,
      grocery: false,
      weekly: false,
      weeklyDay: "Monday",
      weeklyTime: "09:00",
      checkIn: false,
      comeBack: false,
      quiet: false,
      quietStart: "21:00",
      quietEnd: "07:00"
    };
    try {
      return { ...fallback, ...JSON.parse(localStorage.getItem(reminderKey) || "{}") } as SettingsReminderPreferences;
    } catch {
      return fallback;
    }
  });
  useEffect(() => {
    localStorage.setItem(reminderKey, JSON.stringify(reminders));
  }, [reminderKey, reminders]);
  const updateReminder = (patch: Partial<SettingsReminderPreferences>) => setReminders((current) => ({ ...current, ...patch }));
  const profileSteps = [
    profile.age >= 18 && profile.heightCm >= 120 && profile.weightKg >= 35,
    profile.symptoms.length > 0 || profile.isProfileCompleted,
    profile.weeklyBudgetPhp > 0 && profile.maxCookingTimeMinutes >= 10
  ];
  const saveAvatar = () => {
    const next = mergeProfilePatch(profile, { displayName: nameDraft.trim(), avatarId: avatarDraft });
    setProfile(next);
    onSaveProfile(next);
    setAvatarOpen(false);
    setFeedback("Name and avatar saved.");
  };

  return (
    <section className="android-settings-layout">
      <header className="android-settings-hero">
        <button type="button" aria-label="Back" onClick={onBack}>‹</button>
        <div><h1>ACCOUNT AND SETTINGS</h1><p>Personalize your PCOS journey. Manage your profile, set meal reminders, and customize your app experience.</p></div>
        <img src="/images/pcosina-settings-gear.png" alt="" />
      </header>

      <section className="android-settings-profile-band">
        <button type="button" aria-label="Edit name and avatar" onClick={() => { setNameDraft(profile.displayName); setAvatarDraft(profile.avatarId || "doctor_dog"); setAvatarOpen(true); }}>
          <img src={avatarImage(profile.avatarId)} alt="" /><span>✎</span>
        </button>
        <strong>{profile.displayName || "Your account"}</strong>
        <small>{session.mode === "guest" ? "Guest account on this phone" : session.email || "Google account"}</small>
      </section>

      {feedback ? <div className="android-settings-feedback">{feedback}</div> : null}

      <nav className="android-settings-tabs" aria-label="Settings sections">
        <button className={focus === "profile" ? "selected" : ""} type="button" onClick={() => setFocus("profile")}><User size={16} />Profile</button>
        <button className={focus === "reminders" ? "selected" : ""} type="button" onClick={() => setFocus("reminders")}><Clock size={16} />Reminders</button>
        <button className={focus === "account" ? "selected" : ""} type="button" onClick={() => setFocus("account")}><ShieldCheck size={16} />Account</button>
      </nav>

      <div className="android-settings-content">
      {focus === "profile" ? (
        <section className="android-settings-section">
          <header><h2>Profile</h2><p>{profile.isProfileCompleted ? "Your profile is ready to guide meals, groceries, and reminders." : "Finish your profile so planning feels more personal."}</p></header>
          <div className="android-profile-progress">
            <strong>Profile setup progress</strong>
            <div>{profileSteps.map((complete, index) => <span className={complete ? "complete" : ""} key={index}>{complete ? "✓" : index + 1}</span>)}</div>
          </div>
          <SettingsSummaryRow label="Main goal" value={profile.goal || "Not set"} />
          <SettingsSummaryRow label="Weekly budget" value={profile.weeklyBudgetPhp ? `${moneyPhp(profile.weeklyBudgetPhp)} / week` : "Not set"} />
          <SettingsSummaryRow label="Max cooking time" value={`${profile.maxCookingTimeMinutes} min max`} />
          <SettingsSummaryRow label="Food rules" value={profile.dietaryRestrictions.length ? `${profile.dietaryRestrictions.length} saved` : "None saved"} />
          <SettingsSummaryRow label="Allergies" value={profile.allergies.length ? `${profile.allergies.length} saved` : "None saved"} danger={profile.allergies.length > 0} />
          <SettingsSummaryRow label="Pantry saved" value={pantry.length ? `${pantry.length} saved` : "No pantry saved"} />
          <SettingsSummaryRow label="Planning priority" value={profile.planningPriority || "Balanced"} />
          <SettingsSummaryRow label="Variety" value={profile.varietyPreference || "Balanced"} />
          <button className="android-settings-primary" type="button" onClick={() => setProfileEditorOpen(true)}>Edit my profile</button>
          <p className="android-settings-note">This is where you update food rules, cooking limits, pantry details, and budget choices.</p>
        </section>
      ) : null}

      {focus === "reminders" ? (
        <>
          <nav className="android-reminder-tabs">
            {(["control", "meals", "week", "routine"] as const).map((tab) => <button className={reminderFocus === tab ? "selected" : ""} type="button" key={tab} onClick={() => setReminderFocus(tab)}>{tab[0].toUpperCase() + tab.slice(1)}</button>)}
          </nav>
          <section className="android-settings-section">
            {reminderFocus === "control" ? <>
              <header><h2>Reminder control</h2><p>Choose whether PCOSINA can nudge you on this phone and keep notifications respectful.</p></header>
              <SettingsToggle label="Turn reminders on" description="These reminders stay on this phone. Lock-screen text stays generic for privacy." checked={reminders.master} onChange={(master) => updateReminder({ master })} />
              <div className="android-settings-inline">{reminders.master ? "Reminders are ready in this browser." : "Reminders are paused on this phone."}</div>
            </> : null}
            {reminderFocus === "meals" ? <>
              <header><h2>Meal reminders</h2><p>Pick meal nudges that fit your day instead of chasing ideal times.</p></header>
              <SettingsToggle label="Meal time reminders" description="Breakfast, lunch, and dinner reminders." checked={reminders.meals} disabled={!reminders.master} onChange={(meals) => updateReminder({ meals })} />
              <SettingsTimeRow label="Breakfast time" value={reminders.breakfast} disabled={!reminders.master || !reminders.meals} onChange={(breakfast) => updateReminder({ breakfast })} />
              <SettingsTimeRow label="Lunch time" value={reminders.lunch} disabled={!reminders.master || !reminders.meals} onChange={(lunch) => updateReminder({ lunch })} />
              <SettingsTimeRow label="Dinner time" value={reminders.dinner} disabled={!reminders.master || !reminders.meals} onChange={(dinner) => updateReminder({ dinner })} />
            </> : null}
            {reminderFocus === "week" ? <>
              <header><h2>Weekly planning nudges</h2><p>Keep planning reminders helpful without making them feel noisy.</p></header>
              <SettingsToggle label="Plan is ready" description="Tells you when a new week finishes loading." checked={reminders.planReady} disabled={!reminders.master} onChange={(planReady) => updateReminder({ planReady })} />
              <SettingsToggle label="Grocery update alerts" description="Shown when you refresh grocery data yourself." checked={reminders.grocery} disabled={!reminders.master} onChange={(grocery) => updateReminder({ grocery })} />
              <SettingsToggle label="New week reminder" description="A weekly nudge to create your next plan." checked={reminders.weekly} disabled={!reminders.master} onChange={(weekly) => updateReminder({ weekly })} />
              <label className="android-settings-select">Weekly reminder day<select value={reminders.weeklyDay} disabled={!reminders.master || !reminders.weekly} onChange={(event) => updateReminder({ weeklyDay: event.target.value })}>{["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"].map((day) => <option key={day}>{day}</option>)}</select></label>
              <SettingsTimeRow label="Weekly reminder time" value={reminders.weeklyTime} disabled={!reminders.master || !reminders.weekly} onChange={(weeklyTime) => updateReminder({ weeklyTime })} />
            </> : null}
            {reminderFocus === "routine" ? <>
              <header><h2>Check-in reminders</h2><p>Keep check-ins supportive, lightweight, and easy to ignore when you need quiet.</p></header>
              <SettingsToggle label="Daily check-in reminder" description="A gentle reminder to log how your meals and day felt." checked={reminders.checkIn} disabled={!reminders.master} onChange={(checkIn) => updateReminder({ checkIn })} />
              <SettingsToggle label="Come-back reminder" description="A quick check-in after a few quiet days." checked={reminders.comeBack} disabled={!reminders.master} onChange={(comeBack) => updateReminder({ comeBack })} />
              <SettingsToggle label="Do not disturb hours" description="Pauses reminders during the hours you choose." checked={reminders.quiet} disabled={!reminders.master} onChange={(quiet) => updateReminder({ quiet })} />
              <SettingsTimeRow label="Quiet hours start" value={reminders.quietStart} disabled={!reminders.master || !reminders.quiet} onChange={(quietStart) => updateReminder({ quietStart })} />
              <SettingsTimeRow label="Quiet hours end" value={reminders.quietEnd} disabled={!reminders.master || !reminders.quiet} onChange={(quietEnd) => updateReminder({ quietEnd })} />
            </> : null}
          </section>
        </>
      ) : null}

      {focus === "account" ? (
        <section className="android-settings-section destructive">
          <header><h2>Account actions</h2><p>Clear saved week data or sign out only when needed.</p></header>
          <button className="android-settings-action" type="button" onClick={() => setClearOpen(true)}><Clock size={22} /><span><strong>Clear saved week data</strong><small>Removes saved plans, grocery snapshots, and progress logs for this account.</small></span><b>›</b></button>
          <button className="android-settings-action" type="button" onClick={() => setLogoutOpen(true)}><LogOut size={22} /><span><strong>Sign out on this phone</strong><small>You can sign back in later. This does not delete this phone’s saved account data.</small></span><b>›</b></button>
          <div className="android-settings-inline">{plans.length} saved plan{plans.length === 1 ? "" : "s"} · {pendingCount ? `${pendingCount} change${pendingCount === 1 ? "" : "s"} waiting` : "everything saved locally"}</div>
        </section>
      ) : null}
      <footer>PCOSINA • Calm planning, private reminders, and easy routines</footer>
      </div>

      {avatarOpen ? <div className="android-modal-backdrop" role="presentation" onClick={() => setAvatarOpen(false)}><section className="android-modal android-avatar-dialog" role="dialog" aria-modal="true" aria-label="Name and Avatar" onClick={(event) => event.stopPropagation()}><div className="android-modal-head"><h2>Name and Avatar</h2><button type="button" onClick={() => setAvatarOpen(false)}>×</button></div><label>Edit your name<input value={nameDraft} maxLength={32} onChange={(event) => setNameDraft(event.target.value)} /></label><strong>Choose your avatar</strong><div className="android-avatar-grid">{avatarOptions.map((avatar) => <button className={avatarDraft === avatar.id ? "selected" : ""} type="button" key={avatar.id} aria-label={avatar.label} onClick={() => setAvatarDraft(avatar.id)}><img src={avatar.image} alt="" /><span>{avatarDraft === avatar.id ? "✓" : ""}</span></button>)}</div><button className="primary-button" type="button" onClick={saveAvatar}>Save Changes</button></section></div> : null}
      {profileEditorOpen ? <div className="android-modal-backdrop" role="presentation" onClick={() => setProfileEditorOpen(false)}><section className="android-settings-editor" role="dialog" aria-modal="true" aria-label="Edit profile" onClick={(event) => event.stopPropagation()}><div className="android-modal-head"><h2>Edit my profile</h2><button type="button" onClick={() => setProfileEditorOpen(false)}>×</button></div><ProfileView profile={profile} setProfile={setProfile} onSave={() => { onSaveProfile(); setProfileEditorOpen(false); setFeedback("Profile saved on this phone."); }} /></section></div> : null}
      {clearOpen ? <ConfirmDialog title="Clear meal history?" text="This removes saved plans, grocery snapshots, and local adherence logs for this account. Your profile settings stay saved." confirmLabel="Clear history" destructive onCancel={() => setClearOpen(false)} onConfirm={async () => { await onClearWeekData(); setClearOpen(false); setFeedback("Meal history cleared."); }} /> : null}
      {logoutOpen ? <ConfirmDialog title="Logout?" text="You will need to sign in again. This does not delete this phone's saved account data." confirmLabel="Sign out" onCancel={() => setLogoutOpen(false)} onConfirm={onSignOut} /> : null}
    </section>
  );
}

const avatarOptions = [
  { id: "doctor_dog", label: "Doctor pup", image: "/images/avatar-doctor-dog.png" },
  { id: "cat", label: "Lab cat", image: "/images/avatar-cat.png" },
  { id: "chick", label: "Checklist chick", image: "/images/avatar-chick.png" },
  { id: "frog", label: "Frog guide", image: "/images/avatar-frog.png" },
  { id: "koala", label: "Care koala", image: "/images/avatar-koala.png" },
  { id: "pug", label: "Pug coach", image: "/images/avatar-pug.png" }
];

function avatarImage(id?: string): string {
  return avatarOptions.find((avatar) => avatar.id === id)?.image || avatarOptions[0].image;
}

function SettingsSummaryRow({ label, value, danger = false }: { label: string; value: string; danger?: boolean }) {
  return <div className={`android-settings-summary-row ${danger ? "danger" : ""}`}><Info size={19} /><span>{label}</span><strong>{value}</strong></div>;
}

function SettingsToggle({ label, description, checked, disabled = false, onChange }: { label: string; description: string; checked: boolean; disabled?: boolean; onChange: (checked: boolean) => void }) {
  return <label className={`android-settings-toggle ${disabled ? "disabled" : ""}`}><span><strong>{label}</strong><small>{description}</small></span><input type="checkbox" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} /><i /></label>;
}

function SettingsTimeRow({ label, value, disabled, onChange }: { label: string; value: string; disabled: boolean; onChange: (value: string) => void }) {
  return <label className="android-settings-time"><span>{label}</span><input type="time" value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)} /></label>;
}

function ConfirmDialog({ title, text, confirmLabel, destructive = false, onCancel, onConfirm }: { title: string; text: string; confirmLabel: string; destructive?: boolean; onCancel: () => void; onConfirm: () => void | Promise<void> }) {
  return <div className="android-modal-backdrop" role="presentation" onClick={onCancel}><section className="android-modal android-confirm-dialog" role="dialog" aria-modal="true" aria-label={title} onClick={(event) => event.stopPropagation()}><h2>{title}</h2><p>{text}</p><div><button type="button" onClick={onCancel}>Cancel</button><button className={destructive ? "destructive" : ""} type="button" onClick={onConfirm}>{confirmLabel}</button></div></section></div>;
}

function SavedPlansView({
  plans,
  activePlanId,
  pendingCount,
  onActivate,
  compact = false
}: {
  plans: PlanSnapshot[];
  activePlanId?: string;
  pendingCount: number;
  onActivate: (planId: string) => void;
  compact?: boolean;
}) {
  return (
    <section className={compact ? "saved-plans-compact" : "section-grid saved-grid"}>
      <div className="panel">
        <div className="panel-heading">
          <Bookmark size={20} />
          <h2>Saved Plans</h2>
        </div>
        <p className="muted-copy">{plans.length} saved plan{plans.length === 1 ? "" : "s"}. {pendingCount ? `${pendingCount} saved change${pendingCount === 1 ? "" : "s"} waiting` : "Everything is saved on this phone."}</p>
      </div>
      <div className="list-panel">
        {plans.length === 0 ? (
          <EmptyState icon={Bookmark} title="No saved plans" text="Plans you generate will appear here." />
        ) : (
          plans.map((plan) => (
            <article className="list-row" key={plan.planId}>
              <div>
                <strong>{plan.response.weekLabel}</strong>
                <span>
                  {new Date(plan.generatedAtMs).toLocaleString()}
                </span>
              </div>
              <button className="icon-text-button ghost" type="button" onClick={() => onActivate(plan.planId)}>
                <CalendarDays size={16} />
                <span>{activePlanId === plan.planId ? "Active" : "Open"}</span>
              </button>
            </article>
          ))
        )}
      </div>
    </section>
  );
}

export function RecipeDetailsView({
  recipe,
  recipeId,
  snapshot,
  mealLogState,
  onMealStatusChange,
  onClose,
  onOpenProgress
}: {
  recipe?: RecipeDetail;
  recipeId: string;
  snapshot: PlanSnapshot | null;
  mealLogState: Record<string, MealLogStatus>;
  onMealStatusChange: (key: string, status: MealLogStatus) => void;
  onClose: () => void;
  onOpenProgress: () => void;
}) {
  const [feedback, setFeedback] = useState<string | null>(null);
  const [checkInOpen, setCheckInOpen] = useState(false);
  const [energy, setEnergy] = useState(3);
  const [fullness, setFullness] = useState(3);
  const [cravings, setCravings] = useState(3);
  const [satisfaction, setSatisfaction] = useState(3);
  const [checkInNote, setCheckInNote] = useState("");
  const todayIndex = snapshot ? planDayIndexForDate(snapshot, new Date()) : -1;
  const todayDay = snapshot?.response.status === "success" && todayIndex >= 0 ? snapshot.response.days[todayIndex] : undefined;
  const mealIndex = todayDay?.meals.findIndex((meal) => meal.recipeId === recipeId) ?? -1;
  const plannedMeal = mealIndex >= 0 ? todayDay?.meals[mealIndex] : undefined;
  const mealLabel = plannedMeal?.mealLabel || recipe?.mealType || "Recipe";
  const slotKey = snapshot && plannedMeal && todayIndex >= 0
    ? buildMealSlotKey(snapshot.planId, todayIndex, mealIndex, recipeId)
    : "";
  const status = slotKey ? mealLogState[slotKey] : undefined;
  const canLog = Boolean(recipe && plannedMeal && slotKey && !status);
  const loggedToday = snapshot && todayDay
    ? todayDay.meals.filter((meal, index) => mealLogState[buildMealSlotKey(snapshot.planId, todayIndex, index, meal.recipeId)] === "logged").length
    : 0;

  function logMeal() {
    if (!canLog || !slotKey || !todayDay) return;
    onMealStatusChange(slotKey, "logged");
    setFeedback(`Logged ${mealLabel.toLowerCase()}. Today progress is now ${loggedToday + 1}/${todayDay.meals.length} meals.`);
    setCheckInOpen(true);
  }

  return (
    <div className="android-recipe-backdrop" role="presentation">
      <section className="android-recipe-screen" role="dialog" aria-modal="true" aria-label="Recipe details">
        {recipe ? (
          <>
            <div className="android-recipe-scroll">
              <header className="android-recipe-hero">
                <button type="button" aria-label="Back" onClick={onClose}>‹</button>
                <div>
                  <p>{mealLabel.toUpperCase()}</p>
                  <h1>{recipe.title}</h1>
                  <span>{mealLabel} • {recipe.minutes ?? 20} min</span>
                </div>
                <img src={mealImage(mealLabel)} alt="" />
              </header>

              <section className="android-recipe-nutrition">
                <h2>Nutrition per person</h2>
                <div>
                  <RecipeNutrient label="Calories" value={`${recipe.calories ?? 0}`} />
                  <RecipeNutrient label="Protein" value={`${recipe.proteinGrams ?? 0}g`} />
                  <RecipeNutrient label="Carbs" value={`${recipe.carbsGrams ?? 0}g`} />
                  <RecipeNutrient label="Fiber" value={`${recipe.fiberGrams ?? 0}g`} />
                </div>
                {recipe.sodiumMg != null || recipe.sugarGrams != null ? <div className="secondary"><RecipeNutrient label="Sodium" value={`${recipe.sodiumMg ?? 0}mg`} /><RecipeNutrient label="Sugar" value={`${recipe.sugarGrams ?? 0}g`} /></div> : null}
                <p>{recipeNutritionTrust(recipe)}</p>
              </section>

              <section className="android-recipe-section">
                <header><h2>Ingredients</h2><p>{recipe.ingredients.length} item(s) for the primary-user plan.</p></header>
                <div className="android-recipe-ingredients">
                  {recipe.ingredients.map((ingredient) => (
                    <div key={`${ingredient.name}-${ingredient.quantity}`}>
                      <span><ShoppingCart size={15} /></span><strong>{ingredient.name}</strong><b>{ingredient.quantity}</b>
                    </div>
                  ))}
                </div>
              </section>

              <section className="android-recipe-section">
                <header><h2>Cooking Steps</h2><p>{recipe.steps.length} step(s). Follow in order for the intended result.</p></header>
                <div className="android-recipe-steps">
                  {recipe.steps.map((step, index) => (
                    <article key={`${index}-${step}`}>
                      <div><b>{index + 1}</b><small>Step</small></div>
                      <span><em>Do this next</em><p>{step}</p></span>
                    </article>
                  ))}
                </div>
              </section>
            </div>

            <footer className="android-recipe-footer">
              {feedback ? <div className="android-recipe-feedback">{feedback}</div> : null}
              {!plannedMeal ? (
                <div className="android-recipe-locked"><ShieldCheck size={20} /><strong>Not in today’s plan</strong><span>You can only log meals that appear in today’s plan.</span></div>
              ) : null}
              <button type="button" disabled={!canLog} onClick={logMeal}>
                {status === "logged" ? <CheckCircle2 size={18} /> : <Utensils size={18} />}
                {status === "logged" ? `${mealLabel} already logged` : status === "skipped" ? `${mealLabel} skipped in Plan` : plannedMeal ? `Log ${mealLabel} for today` : "Logging unavailable"}
              </button>
              {status === "logged" ? <button className="secondary" type="button" onClick={onOpenProgress}>Open Progress</button> : null}
            </footer>

            {checkInOpen ? (
              <div className="android-modal-backdrop" role="presentation" onClick={() => setCheckInOpen(false)}>
                <section className="android-modal android-recipe-checkin" role="dialog" aria-modal="true" aria-label="Meal check in" onClick={(event) => event.stopPropagation()}>
                  <div className="android-modal-head"><div><p className="eyebrow">{mealLabel}</p><h2>How did this meal feel?</h2></div><button type="button" onClick={() => setCheckInOpen(false)}>×</button></div>
                  <LevelPicker label="Energy" value={energy} onChange={setEnergy} />
                  <LevelPicker label="Fullness" value={fullness} onChange={setFullness} />
                  <LevelPicker label="Cravings" value={cravings} onChange={setCravings} reverse />
                  <LevelPicker label="Satisfaction" value={satisfaction} onChange={setSatisfaction} />
                  <label>Meal note<textarea rows={3} value={checkInNote} onChange={(event) => setCheckInNote(event.target.value)} placeholder="Anything you noticed?" /></label>
                  <button className="primary-button" type="button" onClick={() => { setCheckInOpen(false); setFeedback("Meal check-in saved."); }}>Save check-in</button>
                </section>
              </div>
            ) : null}
          </>
        ) : (
          <div className="android-recipe-unavailable">
            <header className="android-recipe-hero"><button type="button" aria-label="Back" onClick={onClose}>‹</button><div><p>RECIPE</p><h1>Recipe unavailable</h1><span>Saved details are not ready yet.</span></div></header>
            <EmptyState icon={Info} title="Couldn’t load recipe" text={`Recipe ${recipeId} will show details after recipes finish loading.`} action={<button className="primary-button" type="button" onClick={onClose}>Go back</button>} />
          </div>
        )}
      </section>
    </div>
  );
}

function RecipeNutrient({ label, value }: { label: string; value: string }) {
  return <span><strong>{value}</strong><small>{label}</small></span>;
}

function ChipGroup({
  label,
  options,
  selected,
  onChange
}: {
  label: string;
  options: string[];
  selected: string[];
  onChange: (items: string[]) => void;
}) {
  return (
    <div className="chip-group">
      <span>{label}</span>
      <div className="chip-row">
        {options.map((option) => {
          const active = selected.includes(option);
          return (
            <button
              type="button"
              className={active ? "chip selected" : "chip"}
              key={option}
              onClick={() => onChange(active ? selected.filter((item) => item !== option) : [...selected, option])}
            >
              {option}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function TokenEditor({ label, value, onChange }: { label: string; value: string[]; onChange: (items: string[]) => void }) {
  return (
    <label className="token-editor">
      {label}
      <textarea value={value.join(", ")} onChange={(event) => onChange(splitTokens(event.target.value))} rows={3} />
    </label>
  );
}

function GuidanceList({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <h3>{title}</h3>
      {items.length ? (
        <ul className="guidance-list">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      ) : (
        <p className="muted-copy">No guidance returned.</p>
      )}
    </div>
  );
}

function MealPlanWeekStrip({
  snapshot,
  days,
  selectedDayIndex,
  onSelectDay
}: {
  snapshot: PlanSnapshot;
  days: Array<{ dayLabel: string }>;
  selectedDayIndex: number;
  onSelectDay: (index: number) => void;
}) {
  const weekStart = startOfPlanWeek(snapshot);
  const todayToken = localDateToken(new Date());
  return (
    <div className="week-strip" aria-label="Choose day">
      {days.map((day, index) => {
        const date = new Date(weekStart);
        date.setDate(weekStart.getDate() + index);
        const dateToken = localDateToken(date);
        return (
        <button
          key={`${day.dayLabel}-${index}`}
          type="button"
          className={`${index === selectedDayIndex ? "selected" : ""} ${dateToken === todayToken ? "today" : ""}`}
          onClick={() => onSelectDay(index)}
        >
          <span>{shortDayLabel(day.dayLabel, index)}</span>
          <strong>{date.getDate()}</strong>
          <i />
        </button>
        );
      })}
    </div>
  );
}

function MetricBar({ label, value, progress, tone }: { label: string; value: string; progress: number; tone: "pink" | "gold" | "green" }) {
  return (
    <div className={`metric-bar ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <i style={{ transform: `scaleX(${Math.max(0, Math.min(progress, 1))})` }} />
    </div>
  );
}

function GroceryCategoryPicker({
  category,
  index,
  total,
  onPrevious,
  onNext
}: {
  category: string;
  index: number;
  total: number;
  onPrevious: () => void;
  onNext: () => void;
}) {
  return (
    <div className="category-picker">
      <button type="button" onClick={onPrevious} disabled={index <= 0} aria-label="Previous grocery category">
        ‹
      </button>
      <div>
        <span>Browse category</span>
        <strong>{category}</strong>
        <small>{total ? `${index + 1} of ${total}` : "No items"}</small>
      </div>
      <button type="button" onClick={onNext} disabled={index >= total - 1} aria-label="Next grocery category">
        ›
      </button>
    </div>
  );
}

function LevelPicker({
  label,
  value,
  onChange,
  reverse = false
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  reverse?: boolean;
}) {
  return (
    <div className="level-picker">
      <span>{label}</span>
      <div>
        {[1, 2, 3, 4, 5].map((level) => (
          <button key={level} className={level === value ? "selected" : ""} type="button" onClick={() => onChange(level)}>
            {level}
          </button>
        ))}
      </div>
      <small>{reverse ? "Lower is easier" : "Higher is better"}</small>
    </div>
  );
}

function EmptyState({
  icon: Icon,
  title,
  text,
  action
}: {
  icon: LucideIcon;
  title: string;
  text: string;
  action?: JSX.Element;
}) {
  return (
    <section className="empty-state">
      <Icon size={28} />
      <h2>{title}</h2>
      <p>{text}</p>
      {action}
    </section>
  );
}

function Stat({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="stat">
      <Icon size={18} />
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function firebaseUserSession(
  user: { uid: string; email: string | null; displayName: string | null },
  fallbackEmail?: string
): AppSession {
  return {
    uid: user.uid,
    mode: "firebase",
    email: user.email ?? fallbackEmail,
    displayName: user.displayName ?? undefined,
    updatedAtMs: Date.now()
  };
}

function temporaryPlanSnapshot(uid: string, response: GeneratePlanResponse): PlanSnapshot {
  const now = Date.now();
  return {
    uid,
    planId: response.planId || response.requestId || `no-safe-plan-${now}`,
    generatedAtMs: response.timestamps?.completedAtMs || now,
    updatedAtMs: now,
    active: false,
    source: "backend",
    response
  };
}

function titleForView(view: View): string {
  switch (view) {
    case "home":
      return "Home";
    case "plan":
      return "Meal Plan";
    case "grocery":
      return "Grocery Pantry";
    case "progress":
      return "Progress";
    case "support":
      return "Support";
    case "settings":
      return "Account and Settings";
  }
}

function subtitleForView(view: View, profile: UserProfile): string {
  switch (view) {
    case "home":
      return `${profile.goal || "General Health"} meals shaped around your food rules.`;
    case "plan":
      return "Review your week, log today's meals, and sync groceries.";
    case "grocery":
      return "All your essentials, budgeted and in one place.";
    case "progress":
      return "Track meal adherence and self-reported progress.";
    case "support":
      return "Clear help for planning, logging, and sending feedback.";
    case "settings":
      return "Manage your profile, pantry, saved plans, and account.";
  }
}

function mealToneClass(mealLabel: string): string {
  const label = mealLabel.toLowerCase();
  if (label.includes("breakfast")) return "breakfast";
  if (label.includes("lunch")) return "lunch";
  if (label.includes("dinner")) return "dinner";
  return "balanced";
}

function shortDayLabel(dayLabel: string | undefined, index: number): string {
  const label = (dayLabel || "").slice(0, 3);
  return label || ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"][index % 7];
}

function formatDayTitle(dayLabel: string | undefined, index: number): string {
  const label = shortDayLabel(dayLabel, index);
  const names: Record<string, string> = {
    mon: "Monday",
    tue: "Tuesday",
    wed: "Wednesday",
    thu: "Thursday",
    fri: "Friday",
    sat: "Saturday",
    sun: "Sunday"
  };
  return names[label.toLowerCase()] || label;
}

function buildMealSlotKey(planId: string, dayIndex: number, mealIndex: number, recipeId: string): string {
  return `${planId}:${dayIndex}:${mealIndex}:${recipeId}`;
}

function startOfPlanWeek(snapshot: PlanSnapshot): Date {
  const weekLabelDate = snapshot.response.weekLabel.match(/\d{4}-\d{2}-\d{2}/)?.[0];
  const parsed = weekLabelDate ? new Date(`${weekLabelDate}T00:00:00`) : new Date(snapshot.generatedAtMs);
  if (Number.isNaN(parsed.getTime())) return new Date();
  const day = parsed.getDay();
  const mondayOffset = day === 0 ? -6 : 1 - day;
  parsed.setDate(parsed.getDate() + mondayOffset);
  parsed.setHours(0, 0, 0, 0);
  return parsed;
}

function selectedPlanDayIndex(snapshot: PlanSnapshot): number {
  const start = startOfPlanWeek(snapshot);
  const today = new Date();
  start.setHours(0, 0, 0, 0);
  today.setHours(0, 0, 0, 0);
  const index = Math.round((today.getTime() - start.getTime()) / 86_400_000);
  return index >= 0 && index <= 6 ? index : 0;
}

function planDayIndexForDate(snapshot: PlanSnapshot, date: Date): number {
  const start = startOfPlanWeek(snapshot);
  const target = new Date(date);
  start.setHours(0, 0, 0, 0);
  target.setHours(0, 0, 0, 0);
  const index = Math.round((target.getTime() - start.getTime()) / 86_400_000);
  return index >= 0 && index < snapshot.response.days.length ? index : -1;
}

function localDateToken(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0")
  ].join("-");
}

function bmiCategory(value: number): string {
  if (value < 18.5) return "Below the usual range. Use your care team's guidance.";
  if (value < 25) return "Within the usual range.";
  if (value < 30) return "Above the usual range. Progress should stay gradual.";
  return "High range. Keep support realistic and steady.";
}

function recipeNutritionTrust(recipe: RecipeDetail): string {
  const confidence = recipe.nutritionConfidence?.toLowerCase();
  const reviewed = recipe.nutritionReviewStatus?.toLowerCase();
  if (reviewed === "verified" || confidence === "high") return "Nutrition values are reviewed estimates per person.";
  if (confidence === "medium") return "Nutrition values are reasonable estimates and may vary with portions and brands.";
  return "Nutrition values are estimates. Actual portions, ingredients, and preparation can change the result.";
}

function homeGoalLabels(rawGoal: string): string[] {
  return rawGoal
    .split(/[,;+|]/)
    .map((goal) => goal.trim())
    .filter((goal) => goal && !["none", "not set", "choose goal"].includes(goal.toLowerCase()))
    .slice(0, 4);
}

function homeGoalTips(rawGoal: string): [string, string] {
  const goal = rawGoal.toLowerCase();
  if (goal.includes("weight")) return ["Build meals around vegetables and protein before adding starch.", "Steady portions are more useful than skipping meals."];
  if (goal.includes("fertility")) return ["Choose fiber-rich carbohydrates and varied protein sources.", "Consistent meals can support a steadier daily routine."];
  if (goal.includes("insulin") || goal.includes("blood sugar")) return ["Pair rice or bread with protein, vegetables, and healthy fat.", "Use the planned portions instead of removing carbohydrates entirely."];
  return ["Keep meals balanced with vegetables, protein, and familiar Filipino staples.", "Your saved plan is guidance, not a diagnosis or a daily score."];
}

function homeGoalInfo(goal: string): string {
  const normalized = goal.toLowerCase();
  if (normalized.includes("weight")) return "Supports gradual weight management through balanced portions, adequate protein, fiber, and realistic weekly planning.";
  if (normalized.includes("fertility")) return "Prioritizes varied, nutrient-dense meals and consistent eating patterns as general wellness support.";
  if (normalized.includes("insulin") || normalized.includes("blood sugar")) return "Emphasizes balanced carbohydrates, protein, fiber, and meal consistency to support everyday blood-sugar awareness.";
  return "Shapes meal selection around balanced nutrition, practical cooking, your food rules, and your weekly budget.";
}

function groupGroceryItems(items: GroceryDisplayItem[]): Array<[string, GroceryDisplayItem[]]> {
  const order = ["Produce", "Meat/Seafood", "Eggs & Dairy", "Dry Goods", "Spices & Condiments", "Others", "Other"];
  const grouped = new Map<string, GroceryDisplayItem[]>();
  items.forEach((item) => {
    const category = item.category || "Other";
    grouped.set(category, [...(grouped.get(category) ?? []), item]);
  });
  return [...grouped.entries()].sort((a, b) => {
    const left = order.indexOf(a[0]);
    const right = order.indexOf(b[0]);
    return (left === -1 ? 99 : left) - (right === -1 ? 99 : right) || a[0].localeCompare(b[0]);
  });
}

function groceryCategoryEmoji(category: string): string {
  switch (category) {
    case "Produce":
      return "🌽";
    case "Meat/Seafood":
      return "🥩";
    case "Eggs & Dairy":
      return "🥚";
    case "Dry Goods":
      return "🌾";
    case "Spices & Condiments":
      return "🧂";
    case "Canned/Packaged":
      return "🥫";
    case "Beverages":
      return "🥤";
    default:
      return "🧺";
  }
}

function friendlyPriceSource(value: unknown): string {
  const raw = String(value || "").trim();
  if (!raw) return "Estimated from PCOSina's grocery guide.";
  if (raw.toLowerCase().includes("planner") || raw.toLowerCase().includes("catalog")) {
    return "Estimated from PCOSina's grocery guide.";
  }
  return raw;
}

function friendlyAuthError(error: string): string {
  const lower = error.toLowerCase();
  if (lower.includes("unauthorized-domain")) {
    return "Google sign-in needs this phone-testing address to be allowed in Firebase.";
  }
  if (lower.includes("popup")) {
    return "Google sign-in was blocked. Try again and allow the sign-in window.";
  }
  if (lower.includes("firebase web config")) {
    return "Google sign-in needs the PCOSina Firebase web setup on this computer.";
  }
  return error;
}

function numberValue(value: string): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message || fallback;
  return fallback;
}
