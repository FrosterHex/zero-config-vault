// Server-only: never import this module from a client component.
import { SESSION_COOKIE, SESSION_TTL_MS } from "./session";

export function readSessionCookie(req: Request): string | undefined {
  const header = req.headers.get("cookie");
  if (!header) return undefined;
  for (const part of header.split(";")) {
    const i = part.indexOf("=");
    if (i > 0 && part.slice(0, i).trim() === SESSION_COOKIE) return part.slice(i + 1).trim();
  }
  return undefined;
}

function isLocalHttp(requestUrl: string): boolean {
  const u = new URL(requestUrl);
  return u.protocol === "http:" && (u.hostname === "localhost" || u.hostname === "127.0.0.1");
}

export function buildSessionCookie(value: string, requestUrl: string, maxAgeSeconds: number): string {
  const attrs = [`${SESSION_COOKIE}=${value}`, "Path=/", "HttpOnly", "SameSite=Lax", `Max-Age=${maxAgeSeconds}`];
  if (!isLocalHttp(requestUrl)) attrs.push("Secure");
  return attrs.join("; ");
}

export const SESSION_MAX_AGE_SECONDS = Math.floor(SESSION_TTL_MS / 1000);
