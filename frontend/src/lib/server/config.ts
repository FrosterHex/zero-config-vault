// Server-only: never import this module from a client component.
import type { VaultKeys } from "./proxy-policy";

export type ServerConfig = {
  apiUrl: string;
  keys: VaultKeys;
  adminPassword: string;
  sessionSecret: string;
};

const REQUIRED = [
  "VAULT_API_URL",
  "VAULT_GUEST_READ_KEY",
  "VAULT_GUEST_SANDBOX_KEY",
  "VAULT_ADMIN_KEY",
  "ADMIN_PASSWORD",
  "SESSION_SECRET",
] as const;

export function readServerConfig(env: NodeJS.ProcessEnv = process.env): ServerConfig {
  const missing = REQUIRED.filter((name) => !env[name]);
  if (missing.length > 0) {
    throw new Error(`Missing server configuration: ${missing.join(", ")}`);
  }
  return {
    apiUrl: env.VAULT_API_URL!.replace(/\/$/, ""),
    keys: {
      guestRead: env.VAULT_GUEST_READ_KEY!,
      guestSandbox: env.VAULT_GUEST_SANDBOX_KEY!,
      admin: env.VAULT_ADMIN_KEY!,
    },
    adminPassword: env.ADMIN_PASSWORD!,
    sessionSecret: env.SESSION_SECRET!,
  };
}
