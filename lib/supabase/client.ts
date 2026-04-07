import { createBrowserClient } from "@supabase/ssr";
import type { Database } from "./database.types";

const FALLBACK_SUPABASE_URL = "http://127.0.0.1:54321";
const FALLBACK_SUPABASE_ANON_KEY = "missing-supabase-anon-key";

export function isMissingConfig(): boolean {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
  const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";
  return !url || url === "__MISSING__" || !key || key === "__MISSING__";
}

function getSupabaseConfig() {
  if (isMissingConfig()) {
    return {
      url: FALLBACK_SUPABASE_URL,
      key: FALLBACK_SUPABASE_ANON_KEY,
    };
  }

  return {
    url: process.env.NEXT_PUBLIC_SUPABASE_URL!,
    key: process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
  };
}

export function createClient() {
  const { url, key } = getSupabaseConfig();
  return createBrowserClient<Database>(url, key);
}
