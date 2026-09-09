import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { TOKEN_COOKIE } from "@/lib/auth";

const PROTECTED_PREFIXES = ["/dashboard", "/decks", "/study", "/quiz", "/analytics", "/settings"];
const AUTH_PAGES = ["/login", "/register"];

/**
 * Optimistic routing only: a missing token sends the visitor to /login, a present token
 * skips the auth pages. The API validates every token; this just avoids flashes of the
 * wrong page.
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const hasToken = Boolean(request.cookies.get(TOKEN_COOKIE)?.value);

  if (!hasToken && PROTECTED_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`))) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname);
    return NextResponse.redirect(login);
  }
  if (hasToken && (AUTH_PAGES.includes(pathname) || pathname === "/")) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/", "/login", "/register", "/dashboard/:path*", "/decks/:path*", "/study/:path*", "/quiz/:path*",
    "/analytics/:path*", "/settings/:path*"],
};
