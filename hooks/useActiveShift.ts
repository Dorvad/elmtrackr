"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Shift, UserSettings, CompensationProfile } from "@/types";
import {
  buildCompensationSnapshot,
  resolveShiftCompensation,
} from "@/lib/compensation/profile";

export interface UseActiveShiftReturn {
  activeShift: Shift | null;
  loading: boolean;
  error: string | null;
  clockIn: (compensationProfileId?: string | null) => Promise<void>;
  clockOut: (options?: {
    settings?: UserSettings;
    profiles?: CompensationProfile[];
  }) => Promise<void>;
  updateStartTime: (newStartIso: string) => Promise<void>;
  refresh: () => Promise<void>;
}

export function useActiveShift(): UseActiveShiftReturn {
  const [activeShift, setActiveShift] = useState<Shift | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const supabase = createClient();

  const refresh = useCallback(async () => {
    const { data, error: err } = await supabase
      .from("shifts")
      .select("*")
      .is("end_time", null)
      .order("start_time", { ascending: false })
      .limit(1)
      .maybeSingle();

    if (err) {
      setError(err.message);
    } else {
      setActiveShift((data as Shift | null) ?? null);
    }
  }, [supabase]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const clockIn = useCallback(
    async (compensationProfileId?: string | null) => {
      if (activeShift) throw new Error("Already clocked in.");
      setLoading(true);
      setError(null);
      try {
        const { data: user } = await supabase.auth.getUser();
        const { error: err } = await supabase.from("shifts").insert({
          user_id: user.user!.id,
          start_time: new Date().toISOString(),
          end_time: null,
          break_minutes: 0,
          notes: null,
          compensation_profile_id: compensationProfileId ?? null,
        });
        if (err) throw new Error(err.message);
        await refresh();
      } catch (err) {
        setError(err instanceof Error ? err.message : "Clock in failed.");
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [activeShift, refresh, supabase]
  );

  const updateStartTime = useCallback(
    async (newStartIso: string) => {
      if (!activeShift) throw new Error("No active shift to update.");
      setLoading(true);
      setError(null);
      try {
        const { error: err } = await supabase
          .from("shifts")
          .update({ start_time: newStartIso })
          .eq("id", activeShift.id);
        if (err) throw new Error(err.message);
        await refresh();
      } catch (err) {
        setError(err instanceof Error ? err.message : "Update failed.");
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [activeShift, refresh, supabase]
  );

  const clockOut = useCallback(
    async (options?: {
      settings?: UserSettings;
      profiles?: CompensationProfile[];
    }) => {
      if (!activeShift) throw new Error("Not clocked in.");
      setLoading(true);
      setError(null);
      try {
        const endTime = new Date().toISOString();
        let snapshot = null;

        if (options?.settings && options.profiles) {
          const profilesMap = new Map(options.profiles.map((p) => [p.id, p]));
          const resolved = resolveShiftCompensation(
            { ...activeShift, end_time: endTime },
            options.settings,
            profilesMap
          );
          snapshot = buildCompensationSnapshot(resolved);
        }

        const { error: err } = await supabase
          .from("shifts")
          .update({
            end_time: endTime,
            ...(snapshot && {
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              compensation_snapshot_json: snapshot as any,
            }),
          })
          .eq("id", activeShift.id);
        if (err) throw new Error(err.message);
        await refresh();
      } catch (err) {
        setError(err instanceof Error ? err.message : "Clock out failed.");
        throw err;
      } finally {
        setLoading(false);
      }
    },
    [activeShift, refresh, supabase]
  );

  return { activeShift, loading, error, clockIn, clockOut, updateStartTime, refresh };
}
