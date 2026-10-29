import { describe, expect, it } from "vitest";
import { subtreeMatches } from "./tree-search";

const payload = {
  server: { ssl: { protocol: "TLSv1.3" }, port: 8443 },
  payment: { gateways: ["stripe", "adyen"] },
};

describe("subtreeMatches", () => {
  it("finds a nested key below collapsed containers", () => {
    expect(subtreeMatches("$", payload, "protocol")).toBe(true);
    expect(subtreeMatches("server", payload.server, "protocol")).toBe(true);
    expect(subtreeMatches("payment", payload.payment, "protocol")).toBe(false);
  });

  it("finds nested leaf values, including array items, case-insensitively", () => {
    expect(subtreeMatches("payment", payload.payment, "ADYEN")).toBe(true);
    expect(subtreeMatches("server", payload.server, "8443")).toBe(true);
  });

  it("matches everything for an empty query", () => {
    expect(subtreeMatches("payment", payload.payment, "")).toBe(true);
  });
});
