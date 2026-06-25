"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { Shift, UserSettings, CompensationProfile } from "@/types";
import {
  buildCompensationSnapshot,
  resolveShiftCompensation,
} from "@/lib/compensation/profile";

export interface UseShiftsReturn {
  shifts: Shift[];
  loading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  createShift: (
    data: Pick<
      Shift,
      | "start_time"
      | "end_time"
      | "break_minutes"
      | "notes"
      | "is_special_day"
      | "compensation_profile_id"
    >,
    options?: { settings?: UserSettings; profiles?: CompensationProfile[] }
  ) => Promise<Shift>;
  updateShift: (
    id: string,
    data: Partial<
      Pick<
        Shift,
        | "start_time"
        | "end_time"
        | "break_minutes"
        | "notes"
        | "is_special_day"
        | "refund_action"
        | "compensation_profile_id"
        | "compensation_snapshot_json"
      >
    >,
    options?: { settings?: UserSettings; profiles?: CompensationProfile[] }
  ) => Promise<Shift>;
  deleteShift: (id: string) => Promise<void>;
}

function buildSnapshotForShift(
  shift: Pick<Shift, "start_time" | "end_time" | "compensation_profile_id" | "compensation_snapshot_json">,
  settings: UserSettings,
  profiles: CompensationProfile[]
): Shift["compensation_snapshot_json"] | undefined {
  if (!shift.end_time) return undefined;
  const profilesMap = new Map(profiles.map((p) => [p.id, p]));
  const resolved = resolveShiftCompensation(
    shift as Shift,
    settings,
    profilesMap
  );
  return buildCompensationSnapshot(resolved);
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
  }, [supabase]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const createShift = useCallback(
    async (
      data: Pick<
        Shift,
        | "start_time"
        | "end_time"
        | "break_minutes"
        | "notes"
        | "is_special_day"
        | "compensation_profile_id"
      >,
      options?: { settings?: UserSettings; profiles?: CompensationProfile[] }
    ): Promise<Shift> => {
      let snapshot: Shift["compensation_snapshot_json"] | undefined;
      if (data.end_time && options?.settings) {
        snapshot = buildSnapshotForShift(
          data as Shift,
          options.settings,
          options.profiles ?? []
        );
      }

      const { data: created, error: err } = await supabase
        .from("shifts")
        .insert({
          start_time: data.start_time,
          end_time: data.end_time ?? null,
          break_minutes: data.break_minutes,
          notes: data.notes ?? null,
          is_special_day: data.is_special_day ?? false,
          compensation_profile_id: data.compensation_profile_id ?? null,
          ...(snapshot && {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            compensation_snapshot_json: snapshot as any,
          }),
          user_id: (await supabase.auth.getUser()).data.user!.id,
        })
        .select()
        .single();
      if (err) throw new Error(err.message);
      await refresh();
      return created as Shift;
    },
    [refresh, supabase]
  );

  const updateShift = useCallback(
    async (
      id: string,
      data: Partial<
        Pick<
          Shift,
          | "start_time"
          | "end_time"
          | "break_minutes"
          | "notes"
          | "is_special_day"
          | "refund_action"
          | "compensation_profile_id"
          | "compensation_snapshot_json"
        >
      >,
      options?: { settings?: UserSettings; profiles?: CompensationProfile[] }
    ): Promise<Shift> => {
      let snapshotUpdate = data.compensation_snapshot_json;
      const existing = shifts.find((s) => s.id === id);
      const payAffectingKeys = [
        "start_time",
        "end_time",
        "break_minutes",
        "is_special_day",
        "compensation_profile_id",
      ] as const;
      const shouldRebuildSnapshot =
        options?.settings &&
        existing?.end_time &&
        payAffectingKeys.some((key) => key in data);

      if (shouldRebuildSnapshot) {
        const merged = { ...existing, ...data } as Shift;
        snapshotUpdate = buildSnapshotForShift(
          merged,
          options.settings!,
          options.profiles ?? []
        ) ?? null;
      }

      const { data: updated, error: err } = await supabase
        .from("shifts")
        .update({
          ...data,
          ...("end_time" in data && { end_time: data.end_time ?? null }),
          ...("notes" in data && { notes: data.notes ?? null }),
          ...(snapshotUpdate !== undefined && {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            compensation_snapshot_json: snapshotUpdate as any,
          }),
        })
        .eq("id", id)
        .select()
        .single();
      if (err) throw new Error(err.message);
      await refresh();
      return updated as Shift;
    },
    [refresh, shifts, supabase]
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
    [supabase, refresh]
  );

  return { shifts, loading, error, refresh, createShift, updateShift, deleteShift };
}
