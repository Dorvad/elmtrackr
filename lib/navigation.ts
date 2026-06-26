/** Routes where the fixed bottom tab bar should be hidden (full-screen forms). */
export function shouldShowBottomNav(pathname: string): boolean {
  if (pathname === "/shifts/new") return false;
  if (/^\/shifts\/[^/]+$/.test(pathname)) return false;
  return true;
}
