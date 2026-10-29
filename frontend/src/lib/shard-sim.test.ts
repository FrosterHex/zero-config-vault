import { describe, expect, it } from "vitest";
import { simulateShardRoute } from "./shard-sim";

describe("simulateShardRoute", () => {
  it("keeps production on the PROD shards", () => {
    for (const app of ["payment-gateway", "auth-service", "order-service", "inventory-service"]) {
      const route = simulateShardRoute(app, "production");
      expect(route.zone).toBe("PROD");
      expect(["shard-01", "shard-02"]).toContain(route.targetShard);
    }
  });

  it("never routes development or staging to a production shard", () => {
    for (const env of ["development", "staging"]) {
      const route = simulateShardRoute("payment-gateway", env);
      expect(route.zone).toBe("NONPROD");
      expect(route.targetShard).toBe("shard-03");
    }
  });

  it("puts dr in its own zone and gives the same app the same shard every time", () => {
    expect(simulateShardRoute("auth-service", "dr").zone).toBe("DR");
    expect(simulateShardRoute("auth-service", "production")).toEqual(simulateShardRoute("auth-service", "production"));
  });
});
