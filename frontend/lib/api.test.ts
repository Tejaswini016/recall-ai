import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError, apiFetch, errorMessage, query, setUnauthorizedHandler } from "./api";
import { clearToken, setToken } from "./auth";

function mockResponse(status: number, body: unknown) {
  return Promise.resolve(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
}

describe("apiFetch", () => {
  beforeEach(() => {
    clearToken();
  });
  afterEach(() => {
    vi.restoreAllMocks();
    setUnauthorizedHandler(null);
  });

  it("sends the bearer token and parses JSON", async () => {
    setToken("abc.def.ghi");
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() => mockResponse(200, { id: 1 }));

    const result = await apiFetch<{ id: number }>("/api/auth/me");

    expect(result).toEqual({ id: 1 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/auth\/me$/);
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer abc.def.ghi");
  });

  it("turns the backend error body into an ApiRequestError with field errors", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(() =>
      mockResponse(400, {
        status: 400,
        error: "VALIDATION_ERROR",
        message: "Request validation failed",
        fieldErrors: { email: "must be a well-formed email address" },
      }),
    );

    const error = await apiFetch<never>("/api/auth/register", { method: "POST", body: {} }).catch((e: unknown) => e as ApiRequestError);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error.status).toBe(400);
    expect(error.code).toBe("VALIDATION_ERROR");
    expect(error.fieldErrors.email).toMatch(/well-formed/);
    expect(errorMessage(error)).toBe("Request validation failed");
  });

  it("clears the token and notifies on 401 when a token was sent", async () => {
    setToken("expired");
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    vi.spyOn(globalThis, "fetch").mockImplementation(() =>
      mockResponse(401, { status: 401, error: "UNAUTHORIZED", message: "Authentication is required" }),
    );

    await expect(apiFetch("/api/decks")).rejects.toMatchObject({ status: 401 });
    expect(handler).toHaveBeenCalledOnce();
    expect(document.cookie).not.toContain("recallai_token=expired");
  });

  it("reports network failures with a friendly message", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));

    const error = await apiFetch<never>("/api/decks").catch((e: unknown) => e as ApiRequestError);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error.status).toBe(0);
    expect(errorMessage(error)).toMatch(/could not reach/i);
  });

  it("returns undefined for 204 responses", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(() => mockResponse(204, undefined));

    await expect(apiFetch("/api/decks/1", { method: "DELETE" })).resolves.toBeUndefined();
  });
});

describe("query", () => {
  it("skips empty values and encodes the rest", () => {
    expect(query({ q: "cell biology", page: 0, tag: "", deckId: undefined, size: null })).toBe("?q=cell+biology&page=0");
    expect(query({})).toBe("");
  });
});
