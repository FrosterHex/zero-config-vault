import { describe, expect, it } from "vitest";
import { toBars, toEnvRows } from "./landing-content";

const vc = { "a:production": 6, "a:staging": 2, "b:production": 2, "c:development": 1 };

describe("toBars", () => {
  it("sums versions per application, largest first, as a share of the largest", () => {
    expect(toBars(vc)).toEqual([
      { label: "a", pct: 100 },
      { label: "b", pct: 25 },
      { label: "c", pct: 13 },
    ]);
  });

  it("keeps at most `max` applications and a minimum visible height", () => {
    const many = Object.fromEntries(Array.from({ length: 10 }, (_, i) => [`app${i}:production`, i === 0 ? 100 : 1]));
    const bars = toBars(many, 7);
    expect(bars).toHaveLength(7);
    expect(bars[6].pct).toBe(8);
  });

  it("returns nothing without data", () => {
    expect(toBars(undefined)).toEqual([]);
  });
});

describe("toEnvRows", () => {
  it("gives each environment's share of all versions, in a fixed order", () => {
    expect(toEnvRows(vc)).toEqual([
      { label: "production", pct: 73 },
      { label: "staging", pct: 18 },
      { label: "development", pct: 9 },
    ]);
  });

  it("returns nothing without data", () => {
    expect(toEnvRows({})).toEqual([]);
    expect(toEnvRows(undefined)).toEqual([]);
  });
});
