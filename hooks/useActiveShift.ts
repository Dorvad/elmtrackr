"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Shift } from "@/types";

export interface UseActiveShiftReturn {
  activeShift: Shift | null;
  loading: boolean;
  error: string | null;
  clockIn: () => Promise<void>;
  clockOut: () => Promise<void>;
  /** Update the start time of the currently active shift. Timer keeps running. */
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
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const clockIn = useCallback(async () => {
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
      });
      if (err) throw new Error(err.message);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Clock in failed.");
      throw err;
    } finally {
      setLoading(false);
    }
  }, [activeShift, refresh]);

  const updateStartTime = useCallback(async (newStartIso: string) => {
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
  }, [activeShift, refresh]);

  const clockOut = useCallback(async () => {
    if (!activeShift) throw new Error("Not clocked in.");
    setLoading(true);
    setError(null);
    try {
      const { error: err } = await supabase
        .from("shifts")
        .update({ end_time: new Date().toISOString() })
        .eq("id", activeShift.id);
      if (err) throw new Error(err.message);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Clock out failed.");
      throw err;
    } finally {
      setLoading(false);
    }
  }, [activeShift, refresh]);

  return { activeShift, loading, error, clockIn, clockOut, updateStartTime, refresh };
}
