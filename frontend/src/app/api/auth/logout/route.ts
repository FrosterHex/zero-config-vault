import { buildSessionCookie } from "@/lib/server/session-cookie";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function POST(req: Request): Promise<Response> {
  return new Response(null, {
    status: 204,
    headers: { "Set-Cookie": buildSessionCookie("", req.url, 0) },
  });
}
