import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Provide build-time fallback env vars so Supabase client doesn't throw
  // when env vars aren't set during `next build`. Real values must be
  // provided in .env.local (or your deployment environment) at runtime.
  env: {
    NEXT_PUBLIC_SUPABASE_URL:
      process.env.NEXT_PUBLIC_SUPABASE_URL ??
      "https://placeholder.supabase.co",
    NEXT_PUBLIC_SUPABASE_ANON_KEY:
      process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ??
      "placeholder-anon-key",
  },
};

export default nextConfig;
