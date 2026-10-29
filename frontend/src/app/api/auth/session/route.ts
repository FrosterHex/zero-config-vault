import { readServerConfig } from "@/lib/server/config";
import { verifySession } from "@/lib/server/session";
import { readSessionCookie } from "@/lib/server/session-cookie";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: Request): Promise<Response> {
  let secret: string;
  try {
    secret = readServerConfig().sessionSecret;
  } catch (e) {
    return Response.json({ message: (e as Error).message }, { status: 500 });
  }
  const role = await verifySession(readSessionCookie(req), secret);
  return Response.json({ role }, { headers: { "Cache-Control": "no-store" } });
}
