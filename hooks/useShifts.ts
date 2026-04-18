"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Shift } from "@/types";

export interface UseShiftsReturn {
  shifts: Shift[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  createShift: (
    data: Pick<Shift, "start_time" | "end_time" | "break_minutes" | "notes" | "is_special_day">
  ) => Promise<Shift>;
  updateShift: (
    id: string,
    data: Partial<Pick<Shift, "start_time" | "end_time" | "break_minutes" | "notes" | "is_special_day" | "refund_action">>
  ) => Promise<Shift>;
  deleteShift: (id: string) => Promise<void>;
}

export function useShifts(): UseShiftsReturn {
  const [shifts, setShifts] = useState<Shift[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const supabase = createClient();

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    const { data, error: err } = await supabase
      .from("shifts")
      .select("*")
      .order("start_time", { ascending: false });
    if (err) {
      setError(err.message);
    } else {
      setShifts((data as Shift[]) ?? []);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const createShift = useCallback(
    async (
      data: Pick<Shift, "start_time" | "end_time" | "break_minutes" | "notes" | "is_special_day">
    ): Promise<Shift> => {
      const { data: created, error: err } = await supabase
        .from("shifts")
        .insert({
          start_time: data.start_time,
          end_time: data.end_time ?? null,
          break_minutes: data.break_minutes,
          notes: data.notes ?? null,
          is_special_day: data.is_special_day ?? false,
          // user_id is set by RLS / auth context
          user_id: (await supabase.auth.getUser()).data.user!.id,
        })
        .select()
        .single();
      if (err) throw new Error(err.message);
      await refresh();
      return created as Shift;
    },
    [refresh]
  );

  const updateShift = useCallback(
    async (
      id: string,
      data: Partial<
        Pick<Shift, "start_time" | "end_time" | "break_minutes" | "notes" | "is_special_day" | "refund_action">
      >
    ): Promise<Shift> => {
      const { data: updated, error: err } = await supabase
        .from("shifts")
        .update({
          ...data,
          ...("end_time" in data && { end_time: data.end_time ?? null }),
          ...("notes" in data && { notes: data.notes ?? null }),
        })
        .eq("id", id)
        .select()
        .single();
      if (err) throw new Error(err.message);
      await refresh();
      return updated as Shift;
    },
    [refresh]
  );

  const deleteShift = useCallback(
    async (id: string): Promise<void> => {
      const { error: err } = await supabase
        .from("shifts")
        .delete()
        .eq("id", id);
      if (err) throw new Error(err.message);
      await refresh();
    },
    [refresh]
  );

  return { shifts, loading, error, refresh, createShift, updateShift, deleteShift };
}
