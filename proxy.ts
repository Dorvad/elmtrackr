import { type NextRequest } from "next/server";
import { updateSession } from "@/lib/supabase/middleware";

export async function proxy(request: NextRequest) {
  return await updateSession(request);
}

export const config = {
  matcher: [
    /*
     * Match all request paths except static files and Next.js internals.
     * `.well-known` is excluded so Digital Asset Links (assetlinks.json) and
     * similar files are served publicly without the auth redirect below.
     */
    "/((?!_next/static|_next/image|favicon.ico|manifest.json|\\.well-known|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)",
  ],
};
