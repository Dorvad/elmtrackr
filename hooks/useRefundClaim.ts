"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { RefundClaim, RefundDirection, RefundProvider } from "@/types";

export type { RefundDirection };

export interface SaveClaimData {
  provider: RefundProvider;
  amount: number;
  ride_at: string; // ISO 8601
  notes?: string | null;
  receiptFile?: File | null;
}

export type ShiftClaims = Record<RefundDirection, RefundClaim | null>;

export function useRefundClaim(shiftId: string) {
  const supabase = useMemo(() => createClient(), []);
  const [claims, setClaims] = useState<ShiftClaims>({ to_work: null, from_work: null });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<RefundDirection | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    const { data, error: err } = await supabase
      .from("refund_claims")
      .select("*")
      .eq("shift_id", shiftId);
    if (err) {
      setError(err.message);
    } else {
      const rows = (data as RefundClaim[]) ?? [];
      setClaims({
        to_work:   rows.find((r) => r.direction === "to_work")   ?? null,
        from_work: rows.find((r) => r.direction === "from_work") ?? null,
      });
    }
    setLoading(false);
  }, [shiftId]);

  useEffect(() => { load(); }, [load]);

  const uploadReceipt = useCallback(
    async (file: File, userId: string, direction: RefundDirection): Promise<string> => {
      const ext  = file.name.split(".").pop() ?? "jpg";
      const path = `${userId}/${shiftId}/${direction}/${Date.now()}.${ext}`;
      const { error: uploadErr } = await supabase.storage
        .from("refund-receipts")
        .upload(path, file, { upsert: true });
      if (uploadErr) throw new Error(uploadErr.message);
      return path;
    },
    [shiftId]
  );

  const saveClaim = useCallback(
    async (direction: RefundDirection, data: SaveClaimData): Promise<void> => {
      setSaving(direction);
      setError(null);
      try {
        const { data: userData } = await supabase.auth.getUser();
        const userId   = userData.user!.id;
        const existing = claims[direction];

        let receiptPath = existing?.receipt_path ?? null;
        if (data.receiptFile) {
          receiptPath = await uploadReceipt(data.receiptFile, userId, direction);
        }

        const base = {
          shift_id:     shiftId,
          user_id:      userId,
          direction,
          provider:     data.provider,
          amount:       data.amount,
          ride_at:      data.ride_at,
          notes:        data.notes ?? null,
          receipt_path: receiptPath,
        };

        if (existing) {
          const { data: updated, error: err } = await supabase
            .from("refund_claims")
            .update(base)
            .eq("id", existing.id)
            .select()
            .single();
          if (err) throw new Error(err.message);
          setClaims((prev: ShiftClaims) => ({ ...prev, [direction]: updated as RefundClaim }));
        } else {
          const { data: created, error: err } = await supabase
            .from("refund_claims")
            .insert(base)
            .select()
            .single();
          if (err) throw new Error(err.message);
          setClaims((prev: ShiftClaims) => ({ ...prev, [direction]: created as RefundClaim }));
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Failed to save claim";
        setError(msg);
        throw err;
      } finally {
        setSaving(null);
      }
    },
    [claims, shiftId, uploadReceipt]
  );

  const deleteClaim = useCallback(
    async (direction: RefundDirection): Promise<void> => {
      const existing = claims[direction];
      if (!existing) return;
      setSaving(direction);
      try {
        if (existing.receipt_path) {
          await supabase.storage.from("refund-receipts").remove([existing.receipt_path]);
        }
        const { error: err } = await supabase
          .from("refund_claims")
          .delete()
          .eq("id", existing.id);
        if (err) throw new Error(err.message);
        setClaims((prev: ShiftClaims) => ({ ...prev, [direction]: null }));
      } finally {
        setSaving(null);
      }
    },
    [claims]
  );

  const getReceiptUrl = useCallback(
    async (direction: RefundDirection): Promise<string | null> => {
      const existing = claims[direction];
      if (!existing?.receipt_path) return null;
      const { data } = await supabase.storage
        .from("refund-receipts")
        .createSignedUrl(existing.receipt_path, 60);
      return data?.signedUrl ?? null;
    },
    [claims]
  );

  return { claims, loading, saving, error, saveClaim, deleteClaim, getReceiptUrl, reload: load };
}

// All claims for the current user, sorted by ride_at (used for analytics)
export function useAllRefundClaims() {
  const supabase = useMemo(() => createClient(), []);
  const [claims, setClaims] = useState<RefundClaim[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      setLoading(true);
      const { data } = await supabase
        .from("refund_claims")
        .select("*")
        .order("ride_at", { ascending: true });
      setClaims((data as RefundClaim[]) ?? []);
      setLoading(false);
    }
    load();
  }, []);

  return { claims, loading };
}

// Lightweight hook for loading all claims for a given month (used in reports)
export function useMonthlyRefundClaims(year: number, month: number) {
  const supabase = useMemo(() => createClient(), []);
  const [claims, setClaims] = useState<RefundClaim[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetch() {
      setLoading(true);
      const from = new Date(year, month - 1, 1).toISOString();
      const to   = new Date(year, month,     1).toISOString();
      const { data } = await supabase
        .from("refund_claims")
        .select("*")
        .gte("ride_at", from)
        .lt("ride_at", to)
        .order("ride_at", { ascending: true });
      setClaims((data as RefundClaim[]) ?? []);
      setLoading(false);
    }
    fetch();
  }, [year, month]);

  return { claims, loading };
}
