import type { GeneratePlanRequest, GeneratePlanResponse, RecipeDetail } from "../types";
import { getFirebaseAppCheckHeader, getFirebaseIdTokenHeader } from "./firebase";
import { stableHash, stableStringify } from "../storage/db";

const SCHEMA_VERSION = "1.5.0";
const DEFAULT_API_PORT = "8000";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly detail?: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_PCOSINA_API_BASE_URL?.trim() || inferLocalApiBaseUrl();
  return configured.replace(/\/+$/, "");
}

export async function healthCheck(): Promise<boolean> {
  try {
    await requestJson("GET", "/health");
    return true;
  } catch {
    return false;
  }
}

export async function fetchRecipeCatalog(limit = 2000): Promise<RecipeDetail[]> {
  return requestJson<RecipeDetail[]>("GET", `/recipes/catalog?limit=${encodeURIComponent(String(limit))}`);
}

export async function fetchRecipe(recipeId: string): Promise<RecipeDetail> {
  return requestJson<RecipeDetail>("GET", `/recipe/${encodeURIComponent(recipeId)}`);
}

export async function generatePlan(request: GeneratePlanRequest): Promise<GeneratePlanResponse> {
  const idempotency = `web-${stableHash(stableStringify(request))}`;
  return requestJson<GeneratePlanResponse>("POST", "/generate-plan", request, {
    "Idempotency-Key": idempotency
  });
}

async function requestJson<T>(
  method: "GET" | "POST",
  path: string,
  body?: unknown,
  extraHeaders: Record<string, string> = {}
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-PCOSINA-Schema-Version": SCHEMA_VERSION,
    ...extraHeaders,
    ...(await getFirebaseIdTokenHeader()),
    ...(await getFirebaseAppCheckHeader())
  };
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  const text = await response.text();
  const payload = text ? parseJson(text) : null;
  if (!response.ok) {
    const detail = payload && typeof payload === "object" && "detail" in payload ? (payload as { detail: unknown }).detail : payload;
    const message = typeof detail === "string" ? detail : `Request failed with HTTP ${response.status}`;
    throw new ApiError(message, response.status, detail);
  }
  return payload as T;
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function inferLocalApiBaseUrl(): string {
  if (typeof window === "undefined") return `http://localhost:${DEFAULT_API_PORT}`;
  const { protocol, hostname } = window.location;
  if (!hostname || hostname === "localhost" || hostname === "127.0.0.1") {
    return `http://localhost:${DEFAULT_API_PORT}`;
  }
  return `${protocol}//${hostname}:${DEFAULT_API_PORT}`;
}
