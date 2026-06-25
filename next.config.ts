import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY must be set
  // in .env.local (copy .env.local.example). The build uses placeholders only
  // so CI/CD can run `next build` without real credentials. The app will show
  // a clear setup message at runtime if the real values are missing.
  env: {
    NEXT_PUBLIC_SUPABASE_URL:
      process.env.NEXT_PUBLIC_SUPABASE_URL ?? "__MISSING__",
    NEXT_PUBLIC_SUPABASE_ANON_KEY:
      process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "__MISSING__",
  },
};

export default nextConfig;
