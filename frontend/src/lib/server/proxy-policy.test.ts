import { describe, expect, it } from "vitest";
import {
  GUEST_MAX_BODY_BYTES,
  isAllowedPath,
  isGuestBlocked,
  isSameOrigin,
  selectKey,
} from "./proxy-policy";

const k = { guestRead: "r", guestSandbox: "s", admin: "a" };

describe("selectKey", () => {
  it("gives admin the admin key", () => {
    expect(selectKey("admin", "POST", k)).toBe(k.admin);
  });
  it.each(["GET", "HEAD"])("gives guest the read key for %s", (m) => {
    expect(selectKey("guest", m, k)).toBe(k.guestRead);
  });
  it.each(["POST", "PATCH", "PUT", "DELETE"])(
    "gives guest the sandbox key for %s",
    (m) => {
      expect(selectKey("guest", m, k)).toBe(k.guestSandbox);
    },
  );
});

describe("isAllowedPath", () => {
  it("accepts allowlisted paths", () => {
    expect(isAllowedPath(["config", "a", "b"])).toBe(true);
  });
  it("accepts a space inside a binary filename", () => {
    expect(isAllowedPath(["binaries", "app", "dev", "my cert.pem"])).toBe(true);
  });
  it.each([
    [[]],
    [["..", "x"]],
    [["config", "..", "keys"]],
    [["config", "a%2Fb"]],
    [["config", ""]],
    [["config", "."]],
    [["config", "a\\b"]],
    [["config", "a/b"]],
    [["config", "a?admin=1"]],
    [["config", "a#frag"]],
    [["config", "a\nb"]],
    [["config", "a\u0000b"]],
    [["config", "a\u007fb"]],
    [["admin"]],
  ])("rejects %j", (segments) => {
    expect(isAllowedPath(segments)).toBe(false);
  });
});

describe("isGuestBlocked", () => {
  it("blocks keys and bare stream", () => {
    expect(isGuestBlocked(["keys"])).toBe(true);
    expect(isGuestBlocked(["keys", "id"])).toBe(true);
    expect(isGuestBlocked(["stream"])).toBe(true);
  });
  it("allows other paths", () => {
    expect(isGuestBlocked(["stream", "a", "b"])).toBe(false);
    expect(isGuestBlocked(["config", "a"])).toBe(false);
  });
});

describe("isSameOrigin", () => {
  it("matches only the request origin", () => {
    const url = "https://x.app/api/vault/config";
    expect(isSameOrigin("https://x.app", url)).toBe(true);
    expect(isSameOrigin(null, url)).toBe(false);
    expect(isSameOrigin("https://evil.app", url)).toBe(false);
  });

  it("uses the Host header when the server sees its bind address", () => {
    const url = "http://0.0.0.0:3000/api/vault/config";
    expect(isSameOrigin("http://localhost:3000", url, "localhost:3000")).toBe(true);
    expect(isSameOrigin("http://evil.app", url, "localhost:3000")).toBe(false);
  });
});

it("caps guest bodies at 256 KiB", () => {
  expect(GUEST_MAX_BODY_BYTES).toBe(262144);
});
