import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, post, resetCsrf, setSessionExpiredHandler } from "./api";

const response = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status });
beforeEach(() => {
  resetCsrf();
  setSessionExpiredHandler(() => {});
});
afterEach(() => vi.unstubAllGlobals());
describe("session HTTP transport", () => {
  it("shares a CSRF fetch and sends the server header with cookies on writes", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        response({ token: "server-token", headerName: "X-CSRF-TOKEN" }),
      )
      .mockImplementation(async () => response({ ok: true }));
    vi.stubGlobal("fetch", fetcher);
    await Promise.all([post("/api/a", { value: 1 }), post("/api/b")]);
    expect(fetcher).toHaveBeenCalledTimes(3);
    const options = fetcher.mock.calls[1][1] as RequestInit;
    expect(options.credentials).toBe("same-origin");
    expect(new Headers(options.headers).get("X-CSRF-TOKEN")).toBe(
      "server-token",
    );
    expect(options.body).toBe('{"value":1}');
  });
  it("does not replay failed writes and refreshes CSRF only on the next explicit request", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        response({ token: "old", headerName: "X-CSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(
        response(
          { code: "CSRF_INVALID", message: "Phiên CSRF không hợp lệ" },
          403,
        ),
      )
      .mockResolvedValueOnce(
        response({ token: "new", headerName: "X-CSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(response({ ok: true }));
    vi.stubGlobal("fetch", fetcher);
    await expect(post("/api/a")).rejects.toMatchObject({
      status: 403,
      code: "CSRF_INVALID",
    });
    expect(fetcher).toHaveBeenCalledTimes(2);
    await post("/api/a");
    expect(
      new Headers(fetcher.mock.calls[3][1].headers).get("X-CSRF-TOKEN"),
    ).toBe("new");
  });
  it("expires a revoked session and reports offline errors", async () => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(response({ code: "UNAUTHENTICATED" }, 401))
        .mockRejectedValue(new TypeError("Failed to fetch")),
    );
    await expect(api("/api/auth/me")).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledOnce();
    await expect(api("/api/student/classes")).rejects.toMatchObject({
      code: "OFFLINE",
    });
  });
  it("ends a blocked account session without treating resource denials as logout", async () => {
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(response({ code: "FORBIDDEN" }, 403))
        .mockResolvedValueOnce(
          response(
            {
              code: "ACCOUNT_INDEFINITE_BANNED",
              message: "Tài khoản đã bị khóa.",
            },
            403,
          ),
        ),
    );
    await expect(api("/api/teaching/classes")).rejects.toMatchObject({
      status: 403,
    });
    expect(expired).not.toHaveBeenCalled();
    await expect(api("/api/auth/me")).rejects.toMatchObject({ status: 403 });
    expect(expired).toHaveBeenCalledWith("Tài khoản đã bị khóa.");
  });
});
