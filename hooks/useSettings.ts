"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { UserSettings } from "@/types";
import { DEFAULT_WEEKEND_DAYS } from "@/lib/shifts/weekend";

const DEFAULT_SETTINGS: Omit<
  UserSettings,
  "id" | "user_id" | "created_at" | "updated_at"
> = {
  timezone: "UTC",
  daily_overtime_threshold_minutes: 480,   // 8h
  weekly_overtime_threshold_minutes: 2400, // 40h
  weekend_days: DEFAULT_WEEKEND_DAYS,
};

export interface UseSettingsReturn {
  settings: UserSettings | null;
  loading: boolean;
  error: string | null;
  saveSettings: (
    data: Partial<
      Pick<
        UserSettings,
        | "timezone"
        | "daily_overtime_threshold_minutes"
        | "weekly_overtime_threshold_minutes"
        | "weekend_days"
      >
    >
  ) => Promise<void>;
}

export function useSettings(): UseSettingsReturn {
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const supabase = createClient();

  useEffect(() => {
    let mounted = true;
    (async () => {
      const { data: userData } = await supabase.auth.getUser();
      const userId = userData.user?.id;
      if (!userId) { setLoading(false); return; }

      const { data, error: err } = await supabase
        .from("user_settings")
        .select("*")
        .eq("user_id", userId)
        .maybeSingle();

      if (!mounted) return;
      if (err) { setError(err.message); setLoading(false); return; }

      if (data) {
        setSettings(data as UserSettings);
      } else {
        // Row not yet created — create it now with defaults
        const { data: created, error: insertErr } = await supabase
          .from("user_settings")
          .insert({ user_id: userId, ...DEFAULT_SETTINGS })
          .select()
          .single();
        if (!insertErr && mounted) setSettings(created as UserSettings);
      }
      if (mounted) setLoading(false);
    })();
    return () => { mounted = false; };
  }, []);

  const saveSettings = useCallback(
    async (
      updates: Partial<
        Pick<
          UserSettings,
          | "timezone"
          | "daily_overtime_threshold_minutes"
          | "weekly_overtime_threshold_minutes"
          | "weekend_days"
        >
      >
    ) => {
      if (!settings) return;
      const { data, error: err } = await supabase
        .from("user_settings")
        .update(updates)
        .eq("id", settings.id)
        .select()
        .single();
      if (err) throw new Error(err.message);
      setSettings(data as UserSettings);
    },
    [settings]
  );

  return { settings, loading, error, saveSettings };
}
