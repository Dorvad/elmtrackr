"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { createClient } from "@/lib/supabase/client";
import type { UserSettings } from "@/types";
import { DEFAULT_WEEKEND_DAYS } from "@/lib/shifts/weekend";

// ── Types ─────────────────────────────────────────────────────────────────────

export type SaveableSettings = Partial<
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

// ── Defaults ──────────────────────────────────────────────────────────────────

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

// ── localStorage cache ────────────────────────────────────────────────────────

const CACHE_KEY = "elmtrackr-settings-v1";

function readCache(): UserSettings | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as UserSettings;
    if (typeof parsed?.id !== "string") return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeCache(s: UserSettings) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(s));
  } catch {}
}

function clearCache() {
  try {
    localStorage.removeItem(CACHE_KEY);
  } catch {}
}

// ── Context ───────────────────────────────────────────────────────────────────

const SettingsContext = createContext<UseSettingsReturn>({
  settings: null,
  loading: true,
  error: null,
  saveSettings: async () => {},
});

// ── Provider ──────────────────────────────────────────────────────────────────

export function SettingsProvider({ children }: { children: ReactNode }) {
  // Initialize synchronously from localStorage to avoid flash on refresh
  const [settings, setSettings] = useState<UserSettings | null>(readCache);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState<string | null>(null);

  const supabase = useMemo(() => createClient(), []);

  useEffect(() => {
    let mounted = true;
    (async () => {
      const { data: userData } = await supabase.auth.getUser();
      const userId = userData.user?.id;
      if (!userId) {
        clearCache();
        if (mounted) { setSettings(null); setLoading(false); }
        return;
      }

      const { data, error: err } = await supabase
        .from("user_settings")
        .select("*")
        .eq("user_id", userId)
        .maybeSingle();

      if (!mounted) return;
      if (err) { setError(err.message); setLoading(false); return; }

      if (data) {
        const s = data as UserSettings;
        setSettings(s);
        writeCache(s);
      } else {
        const { data: created, error: insertErr } = await supabase
          .from("user_settings")
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          .insert({ user_id: userId, ...DEFAULT_SETTINGS } as any)
          .select()
          .single();
        if (!insertErr && mounted) {
          const s = created as UserSettings;
          setSettings(s);
          writeCache(s);
        }
      }
      if (mounted) setLoading(false);
    })();
    return () => { mounted = false; };
  }, [supabase]);

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
      const s = data as UserSettings;
      setSettings(s);
      writeCache(s);
    },
    [settings, supabase]
  );

  const value = useMemo(
    () => ({ settings, loading, error, saveSettings }),
    [settings, loading, error, saveSettings]
  );

  return (
    <SettingsContext.Provider value={value}>
      {children}
    </SettingsContext.Provider>
  );
}

// ── Hook ──────────────────────────────────────────────────────────────────────

export function useSettings(): UseSettingsReturn {
  return useContext(SettingsContext);
}
