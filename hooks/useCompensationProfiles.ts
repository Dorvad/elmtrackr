"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { CompensationProfile, RegionCode } from "@/types";
import {
  buildMigrationProfileFromSettings,
  createProfileFromPreset,
  profileToLegacySettingsUpdates,
} from "@/lib/compensation/profile";
import { formatSupabaseError } from "@/lib/supabase/schema-errors";
import type { UserSettings } from "@/types";

export interface UseCompensationProfilesReturn {
  profiles: CompensationProfile[];
  defaultProfile: CompensationProfile | null;
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  createFromPreset: (
    regionCode: RegionCode,
    overrides?: {
      name?: string;
      currencyCode?: string;
      timezone?: string;
      baseHourlyRate?: number | null;
    }
  ) => Promise<CompensationProfile>;
  updateProfile: (
    id: string,
    updates: Partial<
      Pick<
        CompensationProfile,
        | "name"
        | "region_code"
        | "currency_code"
        | "timezone"
        | "base_hourly_rate"
        | "rules_json"
        | "stacking_policy"
        | "is_default"
        | "is_archived"
      >
    >
  ) => Promise<CompensationProfile>;
  ensureMigrated: (settings: UserSettings) => Promise<CompensationProfile | null>;
}

function parseProfile(row: Record<string, unknown>): CompensationProfile {
  return {
    ...row,
    rules_json:
      typeof row.rules_json === "string"
        ? JSON.parse(row.rules_json)
        : row.rules_json,
  } as CompensationProfile;
}

export function useCompensationProfiles(): UseCompensationProfilesReturn {
  const [profiles, setProfiles] = useState<CompensationProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const supabase = useMemo(() => createClient(), []);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    const { data, error: err } = await supabase
      .from("compensation_profiles")
      .select("*")
      .eq("is_archived", false)
      .order("is_default", { ascending: false })
      .order("created_at", { ascending: true });

    if (err) {
      setError(formatSupabaseError(err.message));
      setProfiles([]);
    } else {
      setProfiles((data ?? []).map((r) => parseProfile(r as Record<string, unknown>)));
    }
    setLoading(false);
  }, [supabase]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const defaultProfile = useMemo(
    () => profiles.find((p) => p.is_default) ?? profiles[0] ?? null,
    [profiles]
  );

  const createFromPreset = useCallback(
    async (
      regionCode: RegionCode,
      overrides?: {
        name?: string;
        currencyCode?: string;
        timezone?: string;
        baseHourlyRate?: number | null;
      }
    ): Promise<CompensationProfile> => {
      const { data: userData } = await supabase.auth.getUser();
      const userId = userData.user?.id;
      if (!userId) throw new Error("Not authenticated");

      await supabase
        .from("compensation_profiles")
        .update({ is_default: false })
        .eq("user_id", userId);

      const payload = createProfileFromPreset(userId, regionCode, overrides);
      const { data, error: err } = await supabase
        .from("compensation_profiles")
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .insert(payload as any)
        .select()
        .single();
      if (err) throw new Error(formatSupabaseError(err.message));

      const profile = parseProfile(data as Record<string, unknown>);
      const legacyUpdates = profileToLegacySettingsUpdates(profile);
      await supabase
        .from("user_settings")
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .update(legacyUpdates as any)
        .eq("user_id", userId);

      await refresh();
      return profile;
    },
    [supabase, refresh]
  );

  const updateProfile = useCallback(
    async (
      id: string,
      updates: Partial<
        Pick<
          CompensationProfile,
          | "name"
          | "region_code"
          | "currency_code"
          | "timezone"
          | "base_hourly_rate"
          | "rules_json"
          | "stacking_policy"
          | "is_default"
          | "is_archived"
        >
      >
    ): Promise<CompensationProfile> => {
      if (updates.is_default) {
        const { data: userData } = await supabase.auth.getUser();
        const userId = userData.user?.id;
        if (userId) {
          await supabase
            .from("compensation_profiles")
            .update({ is_default: false })
            .eq("user_id", userId);
        }
      }

      const { data, error: err } = await supabase
        .from("compensation_profiles")
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .update(updates as any)
        .eq("id", id)
        .select()
        .single();
      if (err) throw new Error(formatSupabaseError(err.message));

      const profile = parseProfile(data as Record<string, unknown>);

      if (profile.is_default) {
        const legacyUpdates = profileToLegacySettingsUpdates(profile);
        await supabase
          .from("user_settings")
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          .update(legacyUpdates as any)
          .eq("user_id", profile.user_id);
      }

      await refresh();
      return profile;
    },
    [supabase, refresh]
  );

  const ensureMigrated = useCallback(
    async (settings: UserSettings): Promise<CompensationProfile | null> => {
      const { data: userData } = await supabase.auth.getUser();
      const userId = userData.user?.id;
      if (!userId) return null;

      const { data: existing } = await supabase
        .from("compensation_profiles")
        .select("*")
        .eq("user_id", userId)
        .eq("is_archived", false)
        .order("is_default", { ascending: false })
        .order("created_at", { ascending: true })
        .limit(1);

      if (existing && existing.length > 0) {
        await refresh();
        return parseProfile(existing[0] as Record<string, unknown>);
      }

      const payload = buildMigrationProfileFromSettings(userId, settings);
      const { data, error: err } = await supabase
        .from("compensation_profiles")
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .insert(payload as any)
        .select()
        .single();
      if (err) {
        console.warn("Compensation profile migration failed:", err.message);
        setError(formatSupabaseError(err.message));
        return null;
      }

      const profile = parseProfile(data as Record<string, unknown>);
      const legacyUpdates = profileToLegacySettingsUpdates(profile);
      await supabase
        .from("user_settings")
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        .update(legacyUpdates as any)
        .eq("user_id", userId);

      await refresh();
      return profile;
    },
    [supabase, refresh]
  );

  return {
    profiles,
    defaultProfile,
    loading,
    error,
    refresh,
    createFromPreset,
    updateProfile,
    ensureMigrated,
  };
}
