"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Profile } from "@/types";

export interface UseProfileReturn {
  profile: Profile | null;
  loading: boolean;
  updateProfile: (data: { full_name: string | null }) => Promise<void>;
}

export function useProfile(): UseProfileReturn {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);

  const supabase = createClient();

  useEffect(() => {
    let mounted = true;
    (async () => {
      const { data: userData } = await supabase.auth.getUser();
      const userId = userData.user?.id;
      if (!userId) { setLoading(false); return; }

      const { data, error } = await supabase
        .from("profiles")
        .select("*")
        .eq("id", userId)
        .maybeSingle();

      if (mounted && !error && data) setProfile(data as Profile);
      if (mounted) setLoading(false);
    })();
    return () => { mounted = false; };
  }, []);

  const updateProfile = useCallback(
    async (data: { full_name: string | null }) => {
      const { data: userData, error: userError } = await supabase.auth.getUser();
      const user = userData.user;
      if (userError || !user) throw new Error("Not signed in");

      if (!profile) {
        const email = user.email;
        if (!email) throw new Error("Account email is missing");

        const { data: upserted, error } = await supabase
          .from("profiles")
          .upsert({ id: user.id, email, full_name: data.full_name })
          .select()
          .single();
        if (error) throw new Error(error.message);
        setProfile(upserted as Profile);
        return;
      }

      const { data: updated, error } = await supabase
        .from("profiles")
        .update(data)
        .eq("id", profile.id)
        .select()
        .single();
      if (error) throw new Error(error.message);
      setProfile(updated as Profile);
    },
    [profile, supabase]
  );

  return { profile, loading, updateProfile };
}
