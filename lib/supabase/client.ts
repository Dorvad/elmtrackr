import { createBrowserClient } from "@supabase/ssr";
import type { Database } from "./database.types";
import { getSupabaseConfig, isMissingConfig } from "./config";

export { isMissingConfig };

// Always constructs a client (using placeholders if config is absent so that
// SSR prerendering doesn't throw). Actual auth calls will fail gracefully and
// the login page's isMissingConfig() check surfaces a clear setup message.
export function createClient() {
  const { url, key } = getSupabaseConfig();
  return createBrowserClient<Database>(url, key);
}
