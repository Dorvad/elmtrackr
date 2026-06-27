import type { SupabaseClient } from "@supabase/supabase-js";

/** Permanently deletes the signed-in user's cloud data and auth record. */
export async function deleteOwnAccount(supabase: SupabaseClient): Promise<void> {
  const { error } = await supabase.rpc("delete_own_account");
  if (error) throw new Error(error.message);
  await supabase.auth.signOut();
}
