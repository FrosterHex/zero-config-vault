import { describe, expect, it } from "vitest";
import { STOPS_S, STOPS_X, STOPS_Y, clamp, hexToVec3, sample } from "./config";

describe("planet scroll keyframes", () => {
  it("hits each keyframe value exactly at its stop", () => {
    expect(sample(STOPS_X, 0)).toBe(0);
    expect(sample(STOPS_X, 0.32)).toBeCloseTo(-3.1);
    expect(sample(STOPS_X, 0.64)).toBeCloseTo(3.2);
    expect(sample(STOPS_Y, 1)).toBeCloseTo(0.15);
  });

  it("eases between stops with a smoothstep (midpoint is the average)", () => {
    expect(sample(STOPS_S, 0.16)).toBeCloseTo((2.15 + 1.0) / 2);
  });

  it("clamps progress outside 0..1 to the end stops", () => {
    expect(sample(STOPS_Y, -0.5)).toBeCloseTo(-4.5);
    expect(sample(STOPS_Y, 2)).toBeCloseTo(0.15);
  });
});

describe("helpers", () => {
  it("converts a hex colour to a 0..1 vector", () => {
    expect(hexToVec3("#3a6cff").toArray()).toEqual([0x3a / 255, 0x6c / 255, 1]);
  });

  it("clamps", () => {
    expect(clamp(5, 0, 1)).toBe(1);
    expect(clamp(-1, 0, 1)).toBe(0);
  });
});
