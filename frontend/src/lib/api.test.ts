import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi, onWakingChange, vaultApi } from "./api";

const json = (status: number, body: unknown = {}) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("vault api client", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("retries a GET through 503, 503, 200 and toggles the waking flag", async () => {
    fetchMock
      .mockResolvedValueOnce(json(503))
      .mockResolvedValueOnce(json(503))
      .mockResolvedValueOnce(json(200, { ok: true }));
    const events: boolean[] = [];
    const off = onWakingChange((w) => events.push(w));

    const pending = vaultApi.getStats();
    await vi.advanceTimersByTimeAsync(10_000);
    await expect(pending).resolves.toMatchObject({ ok: true });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(events).toEqual([true, false]);
    off();
  });

  it("gives up after about 90 seconds with a waking-up message", async () => {
    fetchMock.mockImplementation(async () => json(503));
    const events: boolean[] = [];
    const off = onWakingChange((w) => events.push(w));

    const pending = vaultApi.getStats();
    const assertion = expect(pending).rejects.toThrow(/waking/i);
    await vi.advanceTimersByTimeAsync(120_000);
    await assertion;

    expect(events).toEqual([true, false]);
    off();
  });

  it("does not report not-waking while another request is still waking", async () => {
    fetchMock
      .mockResolvedValueOnce(json(503))
      .mockResolvedValueOnce(json(503))
      .mockResolvedValueOnce(json(200, { a: 1 }))
      .mockResolvedValueOnce(json(503))
      .mockResolvedValueOnce(json(200, { b: 1 }))
      .mockResolvedValue(json(200, {}));
    const events: boolean[] = [];
    const off = onWakingChange((w) => events.push(w));

    const p1 = vaultApi.getStats();
    const p2 = vaultApi.getTelemetryStats();
    await vi.advanceTimersByTimeAsync(30_000);
    await Promise.all([p1, p2]);

    expect(events).toEqual([true, false]);
    off();
  });

  it("rejects a POST 502 immediately without retrying", async () => {
    fetchMock.mockResolvedValue(json(502));
    await expect(vaultApi.createConfig("playground", "development", {})).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("never sends an API key", async () => {
    fetchMock.mockImplementation(async () => json(200, {}));
    await vaultApi.getLatestConfig("a", "b");
    await vaultApi.createConfig("a", "b", {});
    await vaultApi.uploadBinary("a", "b", new File(["x"], "x.txt"));
    await authApi.session();
    await vaultApi.exportConfig("a", "b", "json").catch(() => undefined);

    for (const [url, init] of fetchMock.mock.calls) {
      expect(String(url)).not.toMatch(/apiKey/i);
      expect(new Headers(init?.headers).has("X-API-Key")).toBe(false);
    }
    expect(vaultApi.getBinaryDownloadUrl("a", "b", "f.bin", 2)).toBe("/api/vault/binaries/a/b/f.bin?version=2");
  });

  it("maps the session response to a role and falls back to guest elsewhere", async () => {
    fetchMock.mockResolvedValueOnce(json(200, { role: "admin" }));
    await expect(authApi.session()).resolves.toBe("admin");
    fetchMock.mockResolvedValueOnce(json(200, { role: "weird" }));
    await expect(authApi.session()).resolves.toBe("guest");
  });
  it("builds the audit log URL from only the given filters", async () => {
    fetchMock.mockImplementation(async () => json(200, []));
    await vaultApi.getAuditLog();
    await vaultApi.getAuditLog({ limit: 50 });
    await vaultApi.getAuditLog({ limit: 10, app: "playground", env: "development" });

    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual([
      "/api/vault/audit",
      "/api/vault/audit?limit=50",
      "/api/vault/audit?limit=10&app=playground&env=development",
    ]);
  });
});
