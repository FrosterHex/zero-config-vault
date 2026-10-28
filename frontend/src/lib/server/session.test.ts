import { describe, expect, it } from "vitest";
import { passwordMatches, signSession, verifySession } from "./session";

const SECRET = "test-secret";
const b64url = (s: string) => Buffer.from(s).toString("base64url");

describe("session", () => {
  it("round trips to admin", async () => {
    const token = await signSession(Date.now() + 1000, SECRET);
    expect(await verifySession(token, SECRET)).toBe("admin");
  });

  it("treats an expired token as guest", async () => {
    const token = await signSession(1000, SECRET);
    expect(await verifySession(token, SECRET, 1001)).toBe("guest");
    expect(await verifySession(token, SECRET, 1000)).toBe("admin");
  });

  it("rejects a tampered payload", async () => {
    const token = await signSession(Date.now() + 1000, SECRET);
    const [payload, sig] = token.split(".");
    const forged = b64url(
      Buffer.from(payload, "base64url").toString().replace("admin", "admiN"),
    );
    expect(await verifySession(`${forged}.${sig}`, SECRET)).toBe("guest");
  });

  it("rejects a wrong secret", async () => {
    const token = await signSession(Date.now() + 1000, SECRET);
    expect(await verifySession(token, "other-secret")).toBe("guest");
  });

  it.each([undefined, "", "garbage", "a.b.c", "!!!.???"])(
    "treats %j as guest",
    async (token) => {
      expect(await verifySession(token, SECRET)).toBe("guest");
    },
  );

  it("compares passwords", async () => {
    expect(await passwordMatches("pw", "pw")).toBe(true);
    expect(await passwordMatches("pw", "pW")).toBe(false);
  });
});
