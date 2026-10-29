import { readServerConfig } from "@/lib/server/config";
import {
  GUEST_MAX_BODY_BYTES,
  isAllowedPath,
  isGuestBlocked,
  isSameOrigin,
  selectKey,
} from "@/lib/server/proxy-policy";
import { verifySession } from "@/lib/server/session";
import { readSessionCookie } from "@/lib/server/session-cookie";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
export const maxDuration = 60;

type Ctx = { params: Promise<{ path: string[] }> };

const json = (status: number, message: string) => Response.json({ message }, { status });

async function proxy(req: Request, { params }: Ctx): Promise<Response> {
  let config;
  try {
    config = readServerConfig();
  } catch (e) {
    return json(500, (e as Error).message);
  }

  const { path } = await params;
  if (!isAllowedPath(path)) return json(404, "Not found");

  const role = await verifySession(readSessionCookie(req), config.sessionSecret);
  if (role === "guest" && isGuestBlocked(path)) return json(403, "Admin login required");

  const method = req.method.toUpperCase();
  const isRead = method === "GET" || method === "HEAD";
  if (!isRead) {
    if (!isSameOrigin(req.headers.get("origin"), req.url)) {
      return json(403, "Cross-origin request refused");
    }
    if (role === "guest") {
      const length = req.headers.get("content-length");
      if (length === null) return json(411, "Content-Length required");
      const n = Number(length);
      if (!Number.isFinite(n) || n < 0) return json(400, "Invalid Content-Length");
      if (n > GUEST_MAX_BODY_BYTES) return json(413, "Request body too large");
    }
  }

  const query = new URLSearchParams(new URL(req.url).search);
  query.delete("apiKey");
  const qs = query.toString();
  const url =
    `${config.apiUrl}/api/v1/vault/${path.map(encodeURIComponent).join("/")}` +
    (qs ? `?${qs}` : "");

  const headers = new Headers({ "X-API-Key": selectKey(role, method, config.keys) });
  for (const name of ["content-type", "accept", "last-event-id"]) {
    const value = req.headers.get(name);
    if (value) headers.set(name, value);
  }

  let upstream: Response;
  try {
    upstream = await fetch(url, {
      method,
      headers,
      body: isRead ? undefined : req.body,
      // Node fetch needs duplex for streamed bodies; the DOM typings omit it.
      duplex: "half",
      signal: req.signal,
      cache: "no-store",
    } as RequestInit);
  } catch {
    return json(502, "Vault backend unreachable");
  }

  const out = new Headers();
  for (const name of ["content-type", "content-disposition", "cache-control", "content-length"]) {
    const value = upstream.headers.get(name);
    if (value) out.set(name, value);
  }
  return new Response(upstream.body, { status: upstream.status, headers: out });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
