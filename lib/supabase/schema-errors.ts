/** User-facing hint when compensation_profiles table is missing in Supabase. */
export const COMPENSATION_SCHEMA_SETUP_HINT =
  "The compensation_profiles table is missing in Supabase. Open Dashboard → SQL Editor, run supabase/migrations/20250625000000_compensation_profiles.sql (or the full supabase/schema.sql), then wait a few seconds and refresh.";

export function isCompensationSchemaMissingError(message: string): boolean {
  const lower = message.toLowerCase();
  return (
    lower.includes("compensation_profiles") &&
    (lower.includes("schema cache") ||
      lower.includes("does not exist") ||
      lower.includes("could not find"))
  );
}

export function formatSupabaseError(message: string): string {
  if (isCompensationSchemaMissingError(message)) {
    return COMPENSATION_SCHEMA_SETUP_HINT;
  }
  return message;
}
