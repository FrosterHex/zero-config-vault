/** Landing page copy (true Zero-Config Vault content in the Ascend layout) and live-data helpers. */

export type IconName = "workflow" | "analytics" | "leads" | "bolt" | "shield" | "globe";

export const NAV_LINKS = [
  { label: "Features", href: "#features" },
  { label: "Live view", href: "#solutions" },
  { label: "Stats", href: "#stats" },
  { label: "Get started", href: "#cta" },
];

export const BUILT_ON = ["MongoDB", "Atlas", "Spring Boot", "Next.js", "Render", "Vercel"];

export const FEATURES: { icon: IconName; title: string; body: string }[] = [
  {
    icon: "workflow",
    title: "Versions & rollback",
    body: "Every change is a new version in MongoDB. Roll back in one click: the old content returns as a fresh version, so history is never lost.",
  },
  {
    icon: "analytics",
    title: "Live diff & stats",
    body: "Compare any two versions key by key, and watch aggregation-pipeline stats and time-series telemetry update as the vault is used.",
  },
  {
    icon: "leads",
    title: "Scoped API keys",
    body: "Keys are stored as hashes and limited to chosen apps, environments and roles. Secrets stay masked unless a key may reveal them.",
  },
  {
    icon: "bolt",
    title: "Change-stream updates",
    body: "A MongoDB change stream pushes exactly one live event per new version to every subscribed service, and resumes after network drops.",
  },
  {
    icon: "shield",
    title: "Envelope encryption",
    body: "Each secret is encrypted with AES-256-GCM under its own data key before it reaches the database, and hashed with SHA-512 for integrity.",
  },
  {
    icon: "globe",
    title: "Binaries & export",
    body: "Store certificates and keystores in GridFS with their own versions, and export any configuration as JSON, YAML or .env.",
  },
];

export const FOOTER_COLUMNS = [
  { title: "Dashboard", links: [{ label: "Configurations", href: "/configs" }, { label: "Diff", href: "/diff" }, { label: "Export", href: "/export" }] },
  { title: "Storage", links: [{ label: "Binaries", href: "/binaries" }, { label: "Audit log", href: "/audit" }, { label: "API keys", href: "/access" }] },
  { title: "Project", links: [{ label: "Overview", href: "/dashboard" }, { label: "NoSQL deep dive", href: "/nosql" }] },
];

const ENV_ORDER = ["production", "staging", "development"];

function split(key: string): [string, string] {
  const i = key.lastIndexOf(":");
  return i < 0 ? [key, ""] : [key.slice(0, i), key.slice(i + 1)];
}

/** Versions per application (from /stats versionCounts "app:env" keys), largest first, as a share of the largest. */
export function toBars(versionCounts: Record<string, number> | undefined, max = 7): { label: string; pct: number }[] {
  if (!versionCounts) return [];
  const perApp = new Map<string, number>();
  for (const [key, count] of Object.entries(versionCounts)) {
    const [app] = split(key);
    perApp.set(app, (perApp.get(app) ?? 0) + count);
  }
  const sorted = [...perApp.entries()].sort((a, b) => b[1] - a[1]).slice(0, max);
  const largest = sorted[0]?.[1] ?? 0;
  if (largest === 0) return [];
  // A minimum height keeps small applications visible as a bar.
  return sorted.map(([label, count]) => ({ label, pct: Math.max(8, Math.round((count / largest) * 100)) }));
}

/** Each environment's share of all stored versions, production first. */
export function toEnvRows(versionCounts: Record<string, number> | undefined): { label: string; pct: number }[] {
  if (!versionCounts) return [];
  const perEnv = new Map<string, number>();
  let total = 0;
  for (const [key, count] of Object.entries(versionCounts)) {
    const [, env] = split(key);
    perEnv.set(env, (perEnv.get(env) ?? 0) + count);
    total += count;
  }
  if (total === 0) return [];
  const rank = (env: string) => (ENV_ORDER.includes(env) ? ENV_ORDER.indexOf(env) : ENV_ORDER.length);
  return [...perEnv.entries()]
    .sort((a, b) => rank(a[0]) - rank(b[0]) || b[1] - a[1])
    .map(([label, count]) => ({ label, pct: Math.round((count / total) * 100) }));
}
