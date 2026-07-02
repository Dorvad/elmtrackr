import type { SupabaseClient } from "@supabase/supabase-js";

export const DUPLICATE_SHIFT_MESSAGE =
  "A shift already exists at this date and time.";

export function isDuplicateShiftError(error: {
  code?: string;
  message?: string;
}): boolean {
  return (
    error.code === "23505" ||
    (error.message?.includes("duplicate key") ?? false) ||
    (error.message?.includes("shifts_user_id_start_time_uidx") ?? false)
  );
}

export async function findShiftAtStartTime(
  supabase: SupabaseClient,
  userId: string,
  startTime: string
): Promise<{ id: string } | null> {
  const { data, error } = await supabase
    .from("shifts")
    .select("id")
    .eq("user_id", userId)
    .eq("start_time", startTime)
    .maybeSingle();

  if (error) throw new Error(error.message);
  return data;
}

export function duplicateShiftError(): Error {
  return new Error(DUPLICATE_SHIFT_MESSAGE);
}
