import { initializeApp, getApp, getApps, type FirebaseApp } from "firebase/app";
import {
  getRedirectResult,
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signInWithRedirect,
  signOut as firebaseSignOut,
  type Auth,
  type User
} from "firebase/auth";
import {
  getFirestore,
  type Firestore
} from "firebase/firestore";
import {
  getToken as getAppCheckToken,
  initializeAppCheck,
  ReCaptchaV3Provider,
  type AppCheck
} from "firebase/app-check";

interface FirebaseServices {
  app: FirebaseApp;
  auth: Auth;
  firestore: Firestore;
  appCheck?: AppCheck;
}

let services: FirebaseServices | null | undefined;

export function firebaseConfigured(): boolean {
  return Boolean(
    import.meta.env.VITE_FIREBASE_API_KEY &&
      import.meta.env.VITE_FIREBASE_PROJECT_ID
  );
}

export function getFirebaseServices(): FirebaseServices | null {
  if (services !== undefined) return services;
  if (!firebaseConfigured()) {
    services = null;
    return services;
  }

  const app = getApps().length
    ? getApp()
    : initializeApp({
        apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
        authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || `${import.meta.env.VITE_FIREBASE_PROJECT_ID}.firebaseapp.com`,
        projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
        storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
        messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
        appId: import.meta.env.VITE_FIREBASE_APP_ID
      });

  const auth = getAuth(app);
  const firestore = getFirestore(app);
  const siteKey = import.meta.env.VITE_FIREBASE_APP_CHECK_SITE_KEY;
  let appCheck: AppCheck | undefined;
  if (siteKey && typeof window !== "undefined") {
    try {
      appCheck = initializeAppCheck(app, {
        provider: new ReCaptchaV3Provider(siteKey),
        isTokenAutoRefreshEnabled: true
      });
    } catch {
      appCheck = undefined;
    }
  }

  services = { app, auth, firestore, appCheck };
  return services;
}

export function currentFirebaseUser(): User | null {
  return getFirebaseServices()?.auth.currentUser ?? null;
}

export function onFirebaseUserChanged(callback: (user: User | null) => void): (() => void) | null {
  const auth = getFirebaseServices()?.auth;
  if (!auth) return null;
  return onAuthStateChanged(auth, callback);
}

export async function getGoogleRedirectUser(): Promise<User | null> {
  const auth = getFirebaseServices()?.auth;
  if (!auth) return null;
  const credential = await getRedirectResult(auth);
  return credential?.user ?? null;
}

export async function signInWithGoogle(): Promise<User | null> {
  const auth = getFirebaseServices()?.auth;
  if (!auth) throw new Error("Firebase web config is not available.");
  const provider = new GoogleAuthProvider();
  provider.setCustomParameters({ prompt: "select_account" });
  try {
    const credential = await signInWithPopup(auth, provider);
    return credential.user;
  } catch (error) {
    if (shouldUseRedirect(error)) {
      await signInWithRedirect(auth, provider);
      return null;
    }
    throw error;
  }
}

export async function signOutFirebase(): Promise<void> {
  const auth = getFirebaseServices()?.auth;
  if (auth) await firebaseSignOut(auth);
}

export async function getFirebaseIdTokenHeader(): Promise<Record<string, string>> {
  const user = currentFirebaseUser();
  if (!user) return {};
  const token = await user.getIdToken();
  return { Authorization: `Bearer ${token}` };
}

export async function getFirebaseAppCheckHeader(): Promise<Record<string, string>> {
  const appCheck = getFirebaseServices()?.appCheck;
  if (!appCheck) return {};
  const token = await getAppCheckToken(appCheck);
  return { "X-Firebase-AppCheck": token.token };
}

function shouldUseRedirect(error: unknown): boolean {
  const code = typeof error === "object" && error !== null && "code" in error ? String((error as { code: unknown }).code) : "";
  return [
    "auth/popup-blocked",
    "auth/popup-closed-by-user",
    "auth/cancelled-popup-request",
    "auth/operation-not-supported-in-this-environment"
  ].includes(code);
}
