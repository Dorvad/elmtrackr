const DEFAULT_SITE_URL = "https://elmtrackr.vercel.app";

export function getSiteOrigin(): string {
  if (typeof window !== "undefined") {
    return window.location.origin;
  }
  return process.env.NEXT_PUBLIC_SITE_URL ?? DEFAULT_SITE_URL;
}

/** PKCE callback used after email confirmation and password-reset links. */
export function getAuthCallbackUrl(nextPath?: string): string {
  const callback = `${getSiteOrigin()}/auth/callback`;
  if (!nextPath) return callback;
  return `${callback}?next=${encodeURIComponent(nextPath)}`;
}

/** Redirect target for password-reset emails (web). */
export function getPasswordResetRedirectUrl(): string {
  return getAuthCallbackUrl("/auth/reset-password");
}
