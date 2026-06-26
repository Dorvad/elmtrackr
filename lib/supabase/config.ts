const FALLBACK_SUPABASE_URL = "http://127.0.0.1:54321";
const FALLBACK_SUPABASE_ANON_KEY = "missing-supabase-anon-key";

function isMissingValue(value: string | undefined): boolean {
  return !value || value === "__MISSING__";
}

export function isMissingConfig(): boolean {
  return (
    isMissingValue(process.env.NEXT_PUBLIC_SUPABASE_URL) ||
    isMissingValue(process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY)
  );
}

export function getSupabaseConfig() {
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
