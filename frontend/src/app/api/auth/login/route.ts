import { readServerConfig } from "@/lib/server/config";
import { passwordMatches, signSession, SESSION_TTL_MS } from "@/lib/server/session";
import { buildSessionCookie, SESSION_MAX_AGE_SECONDS } from "@/lib/server/session-cookie";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const json = (status: number, body: unknown) => Response.json(body, { status });

export async function POST(req: Request): Promise<Response> {
  let config;
  try {
    config = readServerConfig();
  } catch (e) {
    return json(500, { message: (e as Error).message });
  }
  let password: unknown;
  try {
    password = (await req.json())?.password;
  } catch {
    password = undefined;
  }
  const ok = typeof password === "string" && (await passwordMatches(password, config.adminPassword));
  if (!ok) {
    await new Promise((r) => setTimeout(r, 500));
    return json(401, { message: "Invalid password" });
  }
  const token = await signSession(Date.now() + SESSION_TTL_MS, config.sessionSecret);
  return new Response(null, {
    status: 204,
    headers: { "Set-Cookie": buildSessionCookie(token, req.url, SESSION_MAX_AGE_SECONDS) },
  });
}
