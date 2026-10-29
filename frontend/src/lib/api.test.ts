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
  it("delivers named CONFIG_CHANGE stream events to the subscriber", () => {
    const listeners: Record<string, (e: MessageEvent) => void> = {};
    const close = vi.fn();
    let instance: FakeEventSource | undefined;
    class FakeEventSource {
      onmessage: ((e: MessageEvent) => void) | null = null;
      constructor(public url: string) {
        // eslint-disable-next-line @typescript-eslint/no-this-alias
        instance = this;
      }
      addEventListener(name: string, fn: (e: MessageEvent) => void) {
        listeners[name] = fn;
      }
      close = close;
    }
    vi.stubGlobal("EventSource", FakeEventSource);
    const seen: unknown[] = [];
    const off = vaultApi.subscribeToStream("playground", "development", (e) => seen.push(e));

    listeners["CONFIG_CHANGE"](new MessageEvent("CONFIG_CHANGE", { data: '{"version":3}' }));
    expect(seen).toEqual([{ version: 3 }]);
    expect(seen).toHaveLength(1);

    // An unnamed event arrives through onmessage; each event fires onMessage exactly once.
    instance!.onmessage!(new MessageEvent("message", { data: '{"version":4}' }));
    expect(seen).toEqual([{ version: 3 }, { version: 4 }]);
    expect(seen).toHaveLength(2);

    off();
    expect(close).toHaveBeenCalled();
  });

  describe("stream reconnect", () => {
    class FakeES {
      static CLOSED = 2;
      static instances: FakeES[] = [];
      readyState = 0;
      onopen: (() => void) | null = null;
      onerror: ((e: Event) => void) | null = null;
      onmessage: ((e: MessageEvent) => void) | null = null;
      close = vi.fn();
      constructor(public url: string) {
        FakeES.instances.push(this);
      }
      addEventListener() {}
    }
    beforeEach(() => {
      FakeES.instances = [];
      vi.stubGlobal("EventSource", FakeES);
    });

    it("resubscribes with doubling backoff after the stream closes, and resets on open", () => {
      const onError = vi.fn();
      vaultApi.subscribeToStream("a", "e", () => {}, onError);
      expect(FakeES.instances).toHaveLength(1);

      FakeES.instances[0].readyState = FakeES.CLOSED;
      FakeES.instances[0].onerror!(new Event("error"));
      expect(onError).toHaveBeenCalledTimes(1);
      vi.advanceTimersByTime(999);
      expect(FakeES.instances).toHaveLength(1);
      vi.advanceTimersByTime(1);
      expect(FakeES.instances).toHaveLength(2);

      FakeES.instances[1].readyState = FakeES.CLOSED;
      FakeES.instances[1].onerror!(new Event("error"));
      vi.advanceTimersByTime(1999);
      expect(FakeES.instances).toHaveLength(2);
      vi.advanceTimersByTime(1);
      expect(FakeES.instances).toHaveLength(3);

      // Open resets the backoff back to 1 s.
      FakeES.instances[2].onopen!();
      FakeES.instances[2].readyState = FakeES.CLOSED;
      FakeES.instances[2].onerror!(new Event("error"));
      vi.advanceTimersByTime(1000);
      expect(FakeES.instances).toHaveLength(4);
    });

    it("caps the backoff at 8 seconds", () => {
      vaultApi.subscribeToStream("a", "e", () => {});
      for (let i = 0; i < 5; i++) {
        const es = FakeES.instances[FakeES.instances.length - 1];
        es.readyState = FakeES.CLOSED;
        es.onerror!(new Event("error"));
        vi.advanceTimersByTime(8000);
      }
      const before = FakeES.instances.length;
      FakeES.instances[before - 1].readyState = FakeES.CLOSED;
      FakeES.instances[before - 1].onerror!(new Event("error"));
      vi.advanceTimersByTime(7999);
      expect(FakeES.instances).toHaveLength(before);
      vi.advanceTimersByTime(1);
      expect(FakeES.instances).toHaveLength(before + 1);
    });

    it("does not reconnect while the browser is still retrying itself", () => {
      vaultApi.subscribeToStream("a", "e", () => {});
      FakeES.instances[0].readyState = 0;
      FakeES.instances[0].onerror!(new Event("error"));
      vi.advanceTimersByTime(20000);
      expect(FakeES.instances).toHaveLength(1);
    });

    it("unsubscribing during the wait cancels the reconnect", () => {
      const off = vaultApi.subscribeToStream("a", "e", () => {});
      FakeES.instances[0].readyState = FakeES.CLOSED;
      FakeES.instances[0].onerror!(new Event("error"));
      off();
      vi.advanceTimersByTime(20000);
      expect(FakeES.instances).toHaveLength(1);
      expect(FakeES.instances[0].close).toHaveBeenCalled();
    });
  });
});
