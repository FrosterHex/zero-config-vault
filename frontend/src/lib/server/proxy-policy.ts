// Server-only: never import this module from a client component.
import type { Role } from "./session";

export type VaultKeys = { guestRead: string; guestSandbox: string; admin: string };

export const GUEST_MAX_BODY_BYTES = 262144;

const ALLOWED_FIRST_SEGMENTS = new Set([
  "config",
  "export",
  "binaries",
  "stream",
  "keys",
  "stats",
  "dashboard",
  "audit",
]);

const READ_METHODS = new Set(["GET", "HEAD"]);

export function selectKey(role: Role, method: string, keys: VaultKeys): string {
  if (role === "admin") return keys.admin;
  return READ_METHODS.has(method.toUpperCase()) ? keys.guestRead : keys.guestSandbox;
}

export function isAllowedPath(segments: string[]): boolean {
  if (segments.length === 0) return false;
  for (const s of segments) {
    if (s === "" || s === "." || s === "..") return false;
    if (/[/\\%]/.test(s)) return false;
  }
  return ALLOWED_FIRST_SEGMENTS.has(segments[0]);
}

export function isGuestBlocked(segments: string[]): boolean {
  return segments[0] === "keys" || (segments[0] === "stream" && segments.length === 1);
}

export function isSameOrigin(origin: string | null, requestUrl: string): boolean {
  if (!origin) return false;
  try {
    return new URL(origin).origin === new URL(requestUrl).origin;
  } catch {
    return false;
  }
}
