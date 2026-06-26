"use client";

import React, { useState, FormEvent, useRef, useEffect } from "react";
import type { Shift, RefundClaim, RefundProvider } from "@/types";
import {
  checkRefundEligibility,
  checkToWorkEligibility,
  getRefundStatus,
} from "@/lib/shifts/refund";
import { useRefundClaim, type RefundDirection, type SaveClaimData } from "@/hooks/useRefundClaim";
import { useToast } from "@/components/ui/Toast";

const PROVIDERS: RefundProvider[] = ["Lime", "Dott", "Bird", "Taxi", "Other"];

function toLocalDateTimeInput(iso: string): string {
  const d   = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// ── Single-direction claim card ────────────────────────────────────────────

interface ClaimCardProps {
  direction: RefundDirection;
  label: string;
  defaultTime: string;
  eligibilityReasons: string[];
  claim: RefundClaim | null;
  loading: boolean;
  saving: boolean;
  onSave: (data: SaveClaimData) => Promise<void>;
  onDelete: () => Promise<void>;
  // from_work only — manages shift-level refund_action
  shiftAction?: Shift["refund_action"];
  onActionChange?: ((action: Shift["refund_action"]) => Promise<void>) | null;
}

function ClaimCard({
  direction,
  label,
  defaultTime,
  eligibilityReasons,
  claim,
  loading,
  saving,
  onSave,
  onDelete,
  shiftAction,
  onActionChange,
}: ClaimCardProps) {
  const { toast } = useToast();
  const [showForm, setShowForm] = useState(false);
  const [provider, setProvider]     = useState<RefundProvider>("Lime");
  const [amount, setAmount]         = useState("");
  const [rideAt, setRideAt]         = useState(() => toLocalDateTimeInput(defaultTime));
  const [notes, setNotes]           = useState("");
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Populate form from existing claim when loaded
  useEffect(() => {
    if (claim) {
      setProvider(claim.provider as RefundProvider);
      setAmount(String(claim.amount));
      setRideAt(toLocalDateTimeInput(claim.ride_at));
      setNotes(claim.notes ?? "");
    }
  }, [claim]);

  async function handleActionClick(action: Shift["refund_action"]) {
    if (!onActionChange) return;
    try {
      await onActionChange(action);
      toast(
        action === "no_ride_taken"
          ? "Marked: no ride taken"
          : action === "remind_later"
          ? "We'll remind you at month end"
          : "Action saved",
        "success"
      );
    } catch {
      toast("Failed to save", "error");
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      toast("Enter a valid amount", "error");
      return;
    }
    if (!rideAt) {
      toast("Enter the ride date & time", "error");
      return;
    }
    try {
      await onSave({
        provider,
        amount: parsedAmount,
        ride_at: new Date(rideAt).toISOString(),
        notes: notes.trim() || null,
        receiptFile,
      });
    } catch (err) {
      toast(err instanceof Error ? err.message : "Failed to save claim", "error");
      return;
    }
    if (onActionChange) {
      try { await onActionChange("submitted"); } catch { /* badge syncs on reload */ }
    }
    setShowForm(false);
    setReceiptFile(null);
    toast("Ride claim saved", "success");
  }

  async function handleDelete() {
    try {
      await onDelete();
      if (onActionChange) {
        try { await onActionChange(null); } catch { /* ignore */ }
      }
      setAmount("");
      setNotes("");
      setReceiptFile(null);
      toast("Claim removed", "success");
    } catch {
      toast("Failed to remove claim", "error");
    }
  }

  const isFromWork = direction === "from_work";

  return (
    <div className="rounded-xl border border-gray-100 bg-gray-50 p-3">
      {/* Direction header */}
      <div className="flex items-center gap-2 mb-2">
        <span className="text-xs font-bold text-gray-500 uppercase tracking-wide">
          {direction === "to_work" ? "→" : "←"} {label}
        </span>
        {/* Eligibility badges */}
        {eligibilityReasons.map((r) => (
          <span
            key={r}
            className="px-2 py-0.5 rounded-full bg-orange-50 text-orange-700 text-[10px] font-medium border border-orange-100"
          >
            {r}
          </span>
        ))}
      </div>

      {/* ── No claim yet ── */}
      {!claim && !showForm && (
        <>
          {isFromWork && shiftAction == null && (
            <div className="flex flex-col gap-2">
              <button
                type="button"
                onClick={() => setShowForm(true)}
                className="w-full rounded-xl bg-indigo-600 text-white text-sm font-bold py-2 hover:bg-indigo-700 active:scale-[0.98] transition-all"
              >
                Add receipt claim
              </button>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => handleActionClick("no_ride_taken")}
                  className="flex-1 rounded-xl border border-gray-200 text-gray-500 text-sm font-semibold py-1.5 hover:bg-white transition-all"
                >
                  No ride taken
                </button>
                <button
                  type="button"
                  onClick={() => handleActionClick("remind_later")}
                  className="flex-1 rounded-xl border border-amber-200 text-amber-600 text-sm font-semibold py-1.5 hover:bg-amber-50 transition-all"
                >
                  Remind me later
                </button>
              </div>
            </div>
          )}

          {isFromWork && shiftAction === "no_ride_taken" && (
            <div className="flex items-center gap-2">
              <span className="text-xs text-gray-400">No ride taken</span>
              <button
                type="button"
                onClick={() => handleActionClick(null)}
                className="text-xs text-gray-400 underline hover:text-gray-600"
              >
                Undo
              </button>
            </div>
          )}

          {isFromWork && shiftAction === "remind_later" && (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setShowForm(true)}
                className="flex-1 rounded-xl bg-indigo-600 text-white text-sm font-bold py-2 hover:bg-indigo-700 transition-all"
              >
                Add receipt now
              </button>
              <button
                type="button"
                onClick={() => handleActionClick("no_ride_taken")}
                className="rounded-xl border border-gray-200 text-gray-500 text-sm font-semibold px-3 py-2 hover:bg-white transition-all"
              >
                No ride
              </button>
            </div>
          )}

          {/* to_work or from_work after submitted action cleared */}
          {(!isFromWork || (isFromWork && shiftAction === "submitted")) && (
            <button
              type="button"
              onClick={() => setShowForm(true)}
              className="w-full rounded-xl bg-indigo-600 text-white text-sm font-bold py-2 hover:bg-indigo-700 active:scale-[0.98] transition-all"
            >
              Add receipt claim
            </button>
          )}
        </>
      )}

      {/* ── Claim exists ── */}
      {claim && !showForm && (
        <div className="text-sm text-gray-600 space-y-1">
          {loading ? (
            <div className="h-4 w-32 bg-gray-200 rounded animate-pulse" />
          ) : (
            <>
              <div className="flex items-center justify-between">
                <p>
                  <span className="font-semibold">{claim.provider}</span>
                  {" — "}₪{claim.amount.toFixed(2)}
                </p>
                <span className="rounded-full bg-emerald-100 text-emerald-700 text-[10px] font-bold px-2 py-0.5">
                  Submitted
                </span>
              </div>
              <p className="text-gray-400 text-xs">
                {new Date(claim.ride_at).toLocaleString()}
              </p>
              {claim.receipt_path && (
                <p className="text-xs text-indigo-500 font-medium">Receipt attached</p>
              )}
              {claim.notes && <p className="text-xs text-gray-500">{claim.notes}</p>}
              <div className="flex gap-3 mt-2">
                <button
                  type="button"
                  onClick={() => setShowForm(true)}
                  className="text-xs text-indigo-600 font-semibold underline hover:text-indigo-800"
                >
                  Edit
                </button>
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={saving}
                  className="text-xs text-red-400 font-semibold underline hover:text-red-600"
                >
                  Delete
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {/* ── Claim form ── */}
      {showForm && (
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          {/* Receipt upload */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">
              Receipt photo{" "}
              <span className="font-normal text-gray-400">(optional)</span>
            </label>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setReceiptFile(e.target.files?.[0] ?? null)}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className={[
                "w-full rounded-xl border-2 border-dashed py-3 text-sm transition-colors",
                receiptFile
                  ? "border-indigo-300 bg-indigo-50 text-indigo-700 font-semibold"
                  : "border-gray-300 text-gray-500 hover:border-indigo-400 hover:text-indigo-600",
              ].join(" ")}
            >
              {receiptFile
                ? `📎 ${receiptFile.name} — tap to replace`
                : "Tap to attach a photo"}
            </button>
          </div>

          {/* Provider */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">Provider</label>
            <div className="flex gap-2">
              {PROVIDERS.map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setProvider(p)}
                  className={[
                    "flex-1 rounded-xl py-2 text-sm font-bold transition-all",
                    provider === p
                      ? "bg-indigo-600 text-white shadow-md shadow-indigo-500/25"
                      : "bg-white text-gray-500 border border-gray-200 hover:bg-gray-50",
                  ].join(" ")}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          {/* Amount */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">Amount (₪)</label>
            <input
              type="number"
              min={0.01}
              step={0.01}
              placeholder="e.g. 12.50"
              value={amount}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setAmount(e.target.value)}
              className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          {/* Ride date/time */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">
              Ride date &amp; time
            </label>
            <input
              type="datetime-local"
              value={rideAt}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setRideAt(e.target.value)}
              className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm text-gray-900 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          {/* Notes */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">
              Notes (optional)
            </label>
            <textarea
              rows={2}
              placeholder="Route, trip ID, reason…"
              value={notes}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setNotes(e.target.value)}
              className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm text-gray-900 bg-white resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saving}
              className="flex-1 rounded-xl bg-indigo-600 text-white text-sm font-bold py-2.5 hover:bg-indigo-700 disabled:opacity-60 transition-all"
            >
              {saving ? "Saving…" : "Save Claim"}
            </button>
            <button
              type="button"
              onClick={() => { setShowForm(false); setReceiptFile(null); }}
              className="rounded-xl border border-gray-200 text-gray-500 text-sm font-semibold px-4 py-2.5 hover:bg-white transition-all"
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

// ── RefundSection (shell) ──────────────────────────────────────────────────

interface Props {
  shift: Shift;
  onActionChange: (action: Shift["refund_action"]) => Promise<void>;
}

export function RefundSection({ shift, onActionChange }: Props) {
  const fromEligibility = checkRefundEligibility(shift);
  const toEligibility   = checkToWorkEligibility(shift);
  const status          = getRefundStatus(shift);

  const { claims, loading, saving, saveClaim, deleteClaim } = useRefundClaim(shift.id);

  if (!fromEligibility.eligible && !toEligibility.eligible) return null;

  const STATUS_COLORS: Record<string, string> = {
    emerald: "bg-emerald-50 text-emerald-700 border-emerald-100",
    amber:   "bg-amber-50 text-amber-700 border-amber-100",
    orange:  "bg-orange-50 text-orange-700 border-orange-100",
    gray:    "bg-gray-100 text-gray-500 border-gray-200",
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          Travel Refund
        </h2>
        {status.label && (
          <span
            className={`inline-block px-2 py-0.5 rounded-full text-xs font-bold border ${STATUS_COLORS[status.color] ?? STATUS_COLORS.orange}`}
          >
            {status.label}
          </span>
        )}
      </div>

      <div className="flex flex-col gap-3">
        {toEligibility.eligible && (
          <ClaimCard
            direction="to_work"
            label="Ride to work"
            defaultTime={shift.start_time}
            eligibilityReasons={toEligibility.reasons}
            claim={claims.to_work}
            loading={loading}
            saving={saving === "to_work"}
            onSave={(data) => saveClaim("to_work", data)}
            onDelete={() => deleteClaim("to_work")}
            onActionChange={onActionChange}
          />
        )}

        {fromEligibility.eligible && (
          <ClaimCard
            direction="from_work"
            label="Ride from work"
            defaultTime={shift.end_time ?? shift.start_time}
            eligibilityReasons={fromEligibility.reasons}
            claim={claims.from_work}
            loading={loading}
            saving={saving === "from_work"}
            onSave={(data) => saveClaim("from_work", data)}
            onDelete={() => deleteClaim("from_work")}
            shiftAction={shift.refund_action}
            onActionChange={onActionChange}
          />
        )}
      </div>
    </div>
  );
}
