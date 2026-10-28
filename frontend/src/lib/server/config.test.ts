import { describe, expect, it } from "vitest";
import { readServerConfig } from "./config";

const full = {
  VAULT_API_URL: "http://api:8080/",
  VAULT_GUEST_READ_KEY: "r",
  VAULT_GUEST_SANDBOX_KEY: "s",
  VAULT_ADMIN_KEY: "a",
  ADMIN_PASSWORD: "pw",
  SESSION_SECRET: "sec",
} as unknown as NodeJS.ProcessEnv;

describe("readServerConfig", () => {
  it("reads values and strips the trailing slash", () => {
    expect(readServerConfig(full)).toEqual({
      apiUrl: "http://api:8080",
      keys: { guestRead: "r", guestSandbox: "s", admin: "a" },
      adminPassword: "pw",
      sessionSecret: "sec",
    });
  });

  it("names every missing variable", () => {
    const env = { ...full, VAULT_ADMIN_KEY: "", SESSION_SECRET: undefined };
    expect(() => readServerConfig(env)).toThrow(
      "Missing server configuration: VAULT_ADMIN_KEY, SESSION_SECRET",
    );
  });
});
