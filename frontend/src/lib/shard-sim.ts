/**
 * Teaching simulation of the sharding design in SHARDING.md: shard key
 * { environment: 1, applicationName: "hashed" }, production on two PROD shards, development and
 * staging on one NONPROD shard, and dr in its own zone. The hash is a stand-in for MongoDB's
 * 64-bit hashed-index value, which this page cannot compute.
 */
export interface ShardRoute {
  hashHex: string;
  zone: string;
  targetShard: string;
  zoneRange: string;
}

const ZONE_SHARDS: Record<string, { zone: string; shards: string[] }> = {
  production: { zone: "PROD", shards: ["shard-01", "shard-02"] },
  staging: { zone: "NONPROD", shards: ["shard-03"] },
  development: { zone: "NONPROD", shards: ["shard-03"] },
  dr: { zone: "DR", shards: ["dr-shard-01"] },
};

export function simulateShardRoute(applicationName: string, environment: string): ShardRoute {
  let hash = 0;
  for (let i = 0; i < applicationName.length; i++) {
    hash = (hash << 5) - hash + applicationName.charCodeAt(i);
    hash |= 0;
  }
  const absHash = Math.abs(hash);
  const placement = ZONE_SHARDS[environment] ?? ZONE_SHARDS.development;
  return {
    hashHex: "0x" + absHash.toString(16).padStart(8, "0").toUpperCase(),
    zone: placement.zone,
    // Within a zone, the hashed application name decides which shard owns the chunk.
    targetShard: placement.shards[absHash % placement.shards.length],
    zoneRange: `{ environment: "${environment}", applicationName: MinKey } → { environment: "${environment}", applicationName: MaxKey }`,
  };
}
