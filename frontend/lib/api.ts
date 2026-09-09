import { API_URL } from "@/lib/env";
import { clearToken, getToken } from "@/lib/auth";
import type { ApiError } from "@/types";

/** Thrown for every non-2xx response, carrying the backend's structured error body. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, body: Partial<ApiError> | null) {
    super(body?.message ?? defaultMessage(status));
    this.name = "ApiRequestError";
    this.status = status;
    this.code = body?.error ?? "UNKNOWN";
    this.fieldErrors = body?.fieldErrors ?? {};
  }
}

function defaultMessage(status: number): string {
  if (status === 0) return "Could not reach the server. Check your connection.";
  if (status === 401) return "Your session has expired. Please log in again.";
  if (status === 429) return "Too many requests. Please wait a moment.";
  if (status >= 500) return "The server had a problem. Please try again.";
  return "The request failed.";
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  /** Multipart upload; takes precedence over body. */
  formData?: FormData;
  signal?: AbortSignal;
}

let onUnauthorized: (() => void) | null = null;

/** Lets the auth provider react (log out, redirect) when any call returns 401. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  let body: BodyInit | undefined;
  if (options.formData) {
    body = options.formData;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }

  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method: options.method ?? "GET",
      headers,
      body,
      signal: options.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    throw new ApiRequestError(0, null);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  const parsed = text ? safeJson(text) : null;
  if (!response.ok) {
    if (response.status === 401 && token) {
      clearToken();
      onUnauthorized?.();
    }
    throw new ApiRequestError(response.status, parsed as Partial<ApiError> | null);
  }
  return parsed as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** Builds a query string, skipping undefined, null and empty values. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const s = search.toString();
  return s ? `?${s}` : "";
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong.";
}
