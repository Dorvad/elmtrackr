import { createBrowserClient } from "@supabase/ssr";
import type { Database } from "./database.types";

export function isMissingConfig(): boolean {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL ?? "";
  const key = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? "";
  return !url || url === "__MISSING__" || !key || key === "__MISSING__";
}

export function createClient() {
  if (isMissingConfig()) {
    throw new Error(
      "Supabase is not configured. Copy .env.local.example to .env.local and add your project URL and anon key."
    );
  }
  return createBrowserClient<Database>(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!
  );
}
