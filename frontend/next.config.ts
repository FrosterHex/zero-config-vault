import type { NextConfig } from "next";

// The planet models, cloud texture and Draco decoder rarely change, so browsers may reuse them for a
// week (and serve a stale copy for a day while refetching). Without this every visit revalidated them.
const STATIC_ASSET_CACHE = "public, max-age=604800, stale-while-revalidate=86400";

const nextConfig: NextConfig = {
  output: "standalone",
  async headers() {
    return ["/planet/:path*", "/draco/:path*"].map((source) => ({
      source,
      headers: [{ key: "Cache-Control", value: STATIC_ASSET_CACHE }],
    }));
  },
};

export default nextConfig;
