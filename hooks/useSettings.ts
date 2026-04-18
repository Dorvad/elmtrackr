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
  daily_overtime_threshold_minutes: 480,
  weekly_overtime_threshold_minutes: 2400,
  weekend_days: DEFAULT_WEEKEND_DAYS,
  hourly_rate: null,
  onboarding_completed: false,
  onboarding_completed_at: null,
  features_travel_refunds: false,
  features_paid_projects: false,
  features_insights: true,
  features_clock_styles: true,
  clock_style: "classic",
};

type SaveableSettings = Partial<
  Pick<
    UserSettings,
    | "timezone"
    | "daily_overtime_threshold_minutes"
    | "weekly_overtime_threshold_minutes"
    | "weekend_days"
    | "hourly_rate"
    | "onboarding_completed"
    | "onboarding_completed_at"
    | "features_travel_refunds"
    | "features_paid_projects"
    | "features_insights"
    | "features_clock_styles"
    | "clock_style"
  >
>;

export interface UseSettingsReturn {
  settings: UserSettings | null;
  loading: boolean;
  error: string | null;
  saveSettings: (data: SaveableSettings) => Promise<void>;
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
        const { data: created, error: insertErr } = await supabase
          .from("user_settings")
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          .insert({ user_id: userId, ...DEFAULT_SETTINGS } as any)
          .select()
          .single();
        if (!insertErr && mounted) setSettings(created as UserSettings);
      }
      if (mounted) setLoading(false);
    })();
    return () => { mounted = false; };
  }, []);

  const saveSettings = useCallback(
    async (updates: SaveableSettings) => {
      if (!settings) return;
      const { data, error: err } = await supabase
        .from("user_settings")
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .update(updates as any)
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
