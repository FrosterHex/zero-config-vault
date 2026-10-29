import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { SESSION_COOKIE, signSession } from "@/lib/server/session";
import { POST as login } from "./login/route";
import { POST as logout } from "./logout/route";
import { GET as session } from "./session/route";

const ENV = {
  VAULT_API_URL: "http://vault.test:8080",
  VAULT_GUEST_READ_KEY: "g",
  VAULT_GUEST_SANDBOX_KEY: "s",
  VAULT_ADMIN_KEY: "a",
  ADMIN_PASSWORD: "hunter2",
  SESSION_SECRET: "b".repeat(64),
};
const saved = { ...process.env };

beforeEach(() => {
  Object.assign(process.env, ENV);
});
afterEach(() => {
  for (const k of Object.keys(ENV)) {
    if (saved[k] === undefined) delete process.env[k];
    else process.env[k] = saved[k];
  }
});

function loginReq(password: unknown, url = "https://app.test/api/auth/login") {
  return new Request(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password }),
  });
}

describe("login", () => {
  it("correct password gives 204 and hardened cookie", async () => {
    const res = await login(loginReq("hunter2"));
    expect(res.status).toBe(204);
    const c = res.headers.get("set-cookie")!;
    expect(c).toContain(`${SESSION_COOKIE}=`);
    expect(c).toContain("HttpOnly");
    expect(c).toContain("SameSite=Lax");
    expect(c).toContain("Secure");
  });
  it("omits Secure on localhost http", async () => {
    const res = await login(loginReq("hunter2", "http://localhost:3000/api/auth/login"));
    expect(res.headers.get("set-cookie")).not.toContain("Secure");
  });
  it("wrong password gives 401 without cookie", async () => {
    const res = await login(loginReq("nope"));
    expect(res.status).toBe(401);
    expect(res.headers.get("set-cookie")).toBeNull();
  });
  it("bad body gives 401", async () => {
    const res = await login(
      new Request("https://app.test/api/auth/login", { method: "POST", body: "x" }),
    );
    expect(res.status).toBe(401);
  });
});

describe("logout", () => {
  it("clears the cookie", async () => {
    const res = await logout(new Request("https://app.test/api/auth/logout", { method: "POST" }));
    expect(res.status).toBe(204);
    const c = res.headers.get("set-cookie")!;
    expect(c).toContain(`${SESSION_COOKIE}=;`);
    expect(c).toContain("Max-Age=0");
  });
});

describe("session", () => {
  it("guest without cookie", async () => {
    const res = await session(new Request("https://app.test/api/auth/session"));
    expect(await res.json()).toEqual({ role: "guest" });
  });
  it("admin with valid cookie", async () => {
    const t = await signSession(Date.now() + 60_000, ENV.SESSION_SECRET);
    const res = await session(
      new Request("https://app.test/api/auth/session", {
        headers: { Cookie: `${SESSION_COOKIE}=${t}` },
      }),
    );
    expect(await res.json()).toEqual({ role: "admin" });
  });
});
