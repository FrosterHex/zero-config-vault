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
    if (/[/\\%?#]/.test(s)) return false;
    for (let i = 0; i < s.length; i++) {
      const c = s.charCodeAt(i);
      if (c <= 0x1f || c === 0x7f) return false;
    }
  }
  return ALLOWED_FIRST_SEGMENTS.has(segments[0]);
}

export function isGuestBlocked(segments: string[]): boolean {
  return segments[0] === "keys" || (segments[0] === "stream" && segments.length === 1);
}

export function isSameOrigin(origin: string | null, requestUrl: string, host?: string | null): boolean {
  if (!origin) return false;
  try {
    const from = new URL(origin);
    const to = new URL(requestUrl);
    // In the Docker image the server sees its bind address (0.0.0.0) in the request URL, so the
    // Host header the browser sent is what identifies this site.
    return from.protocol === to.protocol && from.host === (host || to.host);
  } catch {
    return false;
  }
}
