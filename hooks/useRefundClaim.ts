"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";
import type { RefundClaim, RefundProvider } from "@/types";

export interface SaveClaimData {
  provider: RefundProvider;
  amount: number;
  ride_at: string; // ISO 8601
  notes?: string | null;
  receiptFile?: File | null;
}

export function useRefundClaim(shiftId: string) {
  const supabase = createClient();
  const [claim, setClaim] = useState<RefundClaim | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    const { data, error: err } = await supabase
      .from("refund_claims")
      .select("*")
      .eq("shift_id", shiftId)
      .maybeSingle();
    if (err) setError(err.message);
    else setClaim(data as RefundClaim | null);
    setLoading(false);
  }, [shiftId]);

  useEffect(() => { load(); }, [load]);

  const uploadReceipt = useCallback(
    async (file: File, userId: string): Promise<string> => {
      const ext = file.name.split(".").pop() ?? "jpg";
      const path = `${userId}/${shiftId}/${Date.now()}.${ext}`;
      const { error: uploadErr } = await supabase.storage
        .from("refund-receipts")
        .upload(path, file, { upsert: true });
      if (uploadErr) throw new Error(uploadErr.message);
      return path;
    },
    [shiftId]
  );

  const saveClaim = useCallback(
    async (data: SaveClaimData): Promise<void> => {
      setSaving(true);
      setError(null);
      try {
        const { data: userData } = await supabase.auth.getUser();
        const userId = userData.user!.id;

        let receiptPath = claim?.receipt_path ?? null;
        if (data.receiptFile) {
          receiptPath = await uploadReceipt(data.receiptFile, userId);
        }

        const insertPayload = {
          shift_id: shiftId,
          user_id: userId,
          provider: data.provider,
          amount: data.amount,
          ride_at: data.ride_at,
          notes: data.notes ?? null,
          receipt_path: receiptPath,
        };
        const updatePayload = {
          provider: data.provider,
          amount: data.amount,
          ride_at: data.ride_at,
          notes: data.notes ?? null,
          receipt_path: receiptPath,
        };

        if (claim) {
          const { data: updated, error: err } = await supabase
            .from("refund_claims")
            .update(updatePayload)
            .eq("id", claim.id)
            .select()
            .single();
          if (err) throw new Error(err.message);
          setClaim(updated as RefundClaim);
        } else {
          const { data: created, error: err } = await supabase
            .from("refund_claims")
            .insert(insertPayload)
            .select()
            .single();
          if (err) throw new Error(err.message);
          setClaim(created as RefundClaim);
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : "Failed to save claim";
        setError(msg);
        throw err;
      } finally {
        setSaving(false);
      }
    },
    [claim, shiftId, uploadReceipt]
  );

  const deleteClaim = useCallback(async (): Promise<void> => {
    if (!claim) return;
    setSaving(true);
    try {
      if (claim.receipt_path) {
        await supabase.storage.from("refund-receipts").remove([claim.receipt_path]);
      }
      const { error: err } = await supabase
        .from("refund_claims")
        .delete()
        .eq("id", claim.id);
      if (err) throw new Error(err.message);
      setClaim(null);
    } finally {
      setSaving(false);
    }
  }, [claim]);

  const getReceiptUrl = useCallback(
    async (): Promise<string | null> => {
      if (!claim?.receipt_path) return null;
      const { data } = await supabase.storage
        .from("refund-receipts")
        .createSignedUrl(claim.receipt_path, 60);
      return data?.signedUrl ?? null;
    },
    [claim]
  );

  return { claim, loading, saving, error, saveClaim, deleteClaim, getReceiptUrl, reload: load };
}

// All claims for the current user, sorted by ride_at (used for analytics)
export function useAllRefundClaims() {
  const supabase = createClient();
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
  const supabase = createClient();
  const [claims, setClaims] = useState<RefundClaim[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function fetch() {
      setLoading(true);
      const from = new Date(year, month - 1, 1).toISOString();
      const to = new Date(year, month, 1).toISOString();
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
