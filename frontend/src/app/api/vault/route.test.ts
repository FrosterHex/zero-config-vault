import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { isAllowedPath } from "@/lib/server/proxy-policy";
import { SESSION_COOKIE, signSession } from "@/lib/server/session";
import { DELETE, GET, PATCH, POST, PUT, dynamic, maxDuration, runtime } from "./[...path]/route";

const ENV = {
  VAULT_API_URL: "http://vault.test:8080",
  VAULT_GUEST_READ_KEY: "guestRead",
  VAULT_GUEST_SANDBOX_KEY: "guestSandbox",
  VAULT_ADMIN_KEY: "adminKey",
  ADMIN_PASSWORD: "pw",
  SESSION_SECRET: "a".repeat(64),
};
const ORIGIN = "http://app.test";
const saved = { ...process.env };
let fetchMock: ReturnType<typeof vi.fn>;

function ctx(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}
function sentKey(): string {
  return new Headers(fetchMock.mock.calls[0][1].headers).get("x-api-key")!;
}

beforeEach(() => {
  Object.assign(process.env, ENV);
  fetchMock = vi.fn();
  fetchMock.mockImplementation(async () => new Response("{}", { status: 200 }));
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => {
  vi.unstubAllGlobals();
  for (const k of Object.keys(ENV)) {
    if (saved[k] === undefined) delete process.env[k];
    else process.env[k] = saved[k];
  }
});

describe("route config", () => {
  it("exports runtime settings", () => {
    expect(runtime).toBe("nodejs");
    expect(dynamic).toBe("force-dynamic");
    expect(maxDuration).toBe(60);
  });
});

describe("path policy", () => {
  it("rejects a segment containing a space", () => {
    expect(isAllowedPath(["config", "my app"])).toBe(false);
  });
  it("builds upstream URL from encoded segments", async () => {
    await GET(new Request(`${ORIGIN}/api/vault/config/my-app/x`), ctx("config", "my-app", "x"));
    expect(fetchMock.mock.calls[0][0]).toBe("http://vault.test:8080/api/v1/vault/config/my-app/x");
  });
});

describe("guest GET", () => {
  it("forwards with guestRead key, drops client key and apiKey param", async () => {
    const req = new Request(`${ORIGIN}/api/vault/config/a/b?x=1&apiKey=evil`, {
      headers: {
        "X-API-Key": "evil",
        Cookie: "foo=bar",
        Authorization: "Bearer z",
        Accept: "application/json",
      },
    });
    await GET(req, ctx("config", "a", "b"));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://vault.test:8080/api/v1/vault/config/a/b?x=1");
    const h = new Headers(init.headers);
    expect(h.get("x-api-key")).toBe("guestRead");
    expect(h.get("accept")).toBe("application/json");
    expect(h.get("cookie")).toBeNull();
    expect(h.get("authorization")).toBeNull();
  });
});

describe("guest writes", () => {
  it("POST with matching Origin uses guestSandbox", async () => {
    const req = new Request(`${ORIGIN}/api/vault/config/a`, {
      method: "POST",
      headers: { Origin: ORIGIN, "Content-Type": "application/json", "Content-Length": "2" },
      body: "{}",
    });
    await POST(req, ctx("config", "a"));
    expect(sentKey()).toBe("guestSandbox");
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("content-type")).toBe(
      "application/json",
    );
  });
  it.each([[undefined], ["http://evil.test"]])("POST with origin %s is 403", async (origin) => {
    const headers: Record<string, string> = { "Content-Length": "2" };
    if (origin) headers.Origin = origin;
    const res = await POST(
      new Request(`${ORIGIN}/api/vault/config/a`, { method: "POST", headers, body: "{}" }),
      ctx("config", "a"),
    );
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("POST over 262144 bytes is 413", async () => {
    const res = await POST(
      new Request(`${ORIGIN}/api/vault/config/a`, {
        method: "POST",
        headers: { Origin: ORIGIN, "Content-Length": "262145" },
        body: "{}",
      }),
      ctx("config", "a"),
    );
    expect(res.status).toBe(413);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("POST without Content-Length is 411", async () => {
    const res = await POST(
      new Request(`${ORIGIN}/api/vault/config/a`, {
        method: "POST",
        headers: { Origin: ORIGIN },
        body: "{}",
      }),
      ctx("config", "a"),
    );
    expect(res.status).toBe(411);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("PUT, PATCH, DELETE are exported", () => {
    expect([PUT, PATCH, DELETE].every((f) => typeof f === "function")).toBe(true);
  });
});

describe("blocked and disallowed", () => {
  it("guest /keys is 403 with message", async () => {
    const res = await GET(new Request(`${ORIGIN}/api/vault/keys`), ctx("keys"));
    expect(res.status).toBe(403);
    expect(typeof (await res.json()).message).toBe("string");
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("disallowed path is 404", async () => {
    const res = await GET(new Request(`${ORIGIN}/api/vault/secret`), ctx("secret"));
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("admin", () => {
  it("cookie selects admin key, also for keys path", async () => {
    const token = await signSession(Date.now() + 60_000, ENV.SESSION_SECRET);
    const res = await GET(
      new Request(`${ORIGIN}/api/vault/keys`, {
        headers: { Cookie: `x=1; ${SESSION_COOKIE}=${token}` },
      }),
      ctx("keys"),
    );
    expect(res.status).toBe(200);
    expect(sentKey()).toBe("adminKey");
  });
});

describe("response passthrough", () => {
  it("keeps status, headers and streams body; drops Set-Cookie", async () => {
    const upstream = new Response("data", {
      status: 201,
      headers: {
        "Content-Type": "text/plain",
        "Content-Disposition": "attachment; filename=a.txt",
        "Set-Cookie": "evil=1",
      },
    });
    fetchMock.mockResolvedValue(upstream);
    const res = await GET(new Request(`${ORIGIN}/api/vault/export/a`), ctx("export", "a"));
    expect(res.status).toBe(201);
    expect(res.headers.get("content-type")).toBe("text/plain");
    expect(res.headers.get("content-disposition")).toBe("attachment; filename=a.txt");
    expect(res.headers.get("set-cookie")).toBeNull();
    expect(res.body).toBe(upstream.body);
  });
});

describe("config", () => {
  it("missing VAULT_ADMIN_KEY is 500", async () => {
    delete process.env.VAULT_ADMIN_KEY;
    const res = await GET(new Request(`${ORIGIN}/api/vault/config/a`), ctx("config", "a"));
    expect(res.status).toBe(500);
    expect(JSON.stringify(await res.json())).toContain("Missing server configuration");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
