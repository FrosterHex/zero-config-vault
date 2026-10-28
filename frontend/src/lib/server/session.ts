// Server-only: never import this module from a client component.
export type Role = "guest" | "admin";

export const SESSION_COOKIE = "vault_session";
export const SESSION_TTL_MS = 8 * 60 * 60 * 1000;

const encoder = new TextEncoder();
const decoder = new TextDecoder();

function toBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64Url(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]*$/.test(value)) throw new Error("invalid base64url");
  const padded = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(padded + "=".repeat((4 - (padded.length % 4)) % 4));
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

function hmacKey(secret: string, usage: "sign" | "verify"): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    [usage],
  );
}

export async function signSession(exp: number, secret: string): Promise<string> {
  const payload = toBase64Url(encoder.encode(JSON.stringify({ role: "admin", exp })));
  const key = await hmacKey(secret, "sign");
  const mac = await crypto.subtle.sign("HMAC", key, encoder.encode(payload));
  return `${payload}.${toBase64Url(new Uint8Array(mac))}`;
}

export async function verifySession(
  token: string | undefined,
  secret: string,
  now: number = Date.now(),
): Promise<Role> {
  try {
    if (!token) return "guest";
    const parts = token.split(".");
    if (parts.length !== 2) return "guest";
    const [payload, signature] = parts;
    const key = await hmacKey(secret, "verify");
    // subtle.verify compares the MAC in constant time.
    const valid = await crypto.subtle.verify(
      "HMAC",
      key,
      fromBase64Url(signature),
      encoder.encode(payload),
    );
    if (!valid) return "guest";
    const claims = JSON.parse(decoder.decode(fromBase64Url(payload)));
    if (claims?.role !== "admin" || typeof claims.exp !== "number") return "guest";
    return now <= claims.exp ? "admin" : "guest";
  } catch {
    return "guest";
  }
}

export async function passwordMatches(input: string, expected: string): Promise<boolean> {
  const [a, b] = await Promise.all(
    [input, expected].map(async (s) =>
      new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(s))),
    ),
  );
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}
