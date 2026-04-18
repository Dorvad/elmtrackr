"use client";

import { useState, FormEvent, useRef } from "react";
import { Shift, RefundProvider } from "@/types";
import { checkRefundEligibility, getRefundStatus } from "@/lib/shifts/refund";
import { useRefundClaim } from "@/hooks/useRefundClaim";
import { useToast } from "@/components/ui/Toast";

const PROVIDERS: RefundProvider[] = ["Lime", "Dott", "Other"];

const STATUS_COLORS: Record<string, string> = {
  emerald: "bg-emerald-50 text-emerald-700 border-emerald-100",
  amber: "bg-amber-50 text-amber-700 border-amber-100",
  violet: "bg-violet-50 text-violet-700 border-violet-100",
  gray: "bg-gray-100 text-gray-500 border-gray-200",
};

function toLocalDateTimeInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

interface Props {
  shift: Shift;
  onActionChange: (action: Shift["refund_action"]) => Promise<void>;
}

export function RefundSection({ shift, onActionChange }: Props) {
  const eligibility = checkRefundEligibility(shift);
  const status = getRefundStatus(shift);
  const { claim, loading, saving, saveClaim, deleteClaim } = useRefundClaim(shift.id);
  const { toast } = useToast();

  const [showForm, setShowForm] = useState(false);
  const [provider, setProvider] = useState<RefundProvider>("Lime");
  const [amount, setAmount] = useState("");
  const [rideAt, setRideAt] = useState(() =>
    shift.end_time ? toLocalDateTimeInput(shift.end_time) : ""
  );
  const [notes, setNotes] = useState("");
  const [receiptFile, setReceiptFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [formInitialised, setFormInitialised] = useState(false);

  // Populate form from existing claim once loaded
  if (!loading && claim && !formInitialised) {
    setProvider(claim.provider as RefundProvider);
    setAmount(String(claim.amount));
    setRideAt(toLocalDateTimeInput(claim.ride_at));
    setNotes(claim.notes ?? "");
    setFormInitialised(true);
  }

  if (!eligibility.eligible) return null;

  async function handleActionClick(action: Shift["refund_action"]) {
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
      await saveClaim({
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
    try {
      await onActionChange("submitted");
    } catch {
      // Claim is saved — status badge will sync on next reload
    }
    setShowForm(false);
    toast("Refund claim saved", "success");
  }

  async function handleDelete() {
    try {
      await deleteClaim();
      await onActionChange(null);
      setFormInitialised(false);
      setAmount("");
      setNotes("");
      setReceiptFile(null);
      toast("Claim removed", "success");
    } catch {
      toast("Failed to remove claim", "error");
    }
  }

  const colorClass = STATUS_COLORS[status.color] ?? STATUS_COLORS.violet;

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest">
            Travel Refund
          </h2>
          {status.label && (
            <span
              className={`inline-block mt-1 px-2 py-0.5 rounded-full text-xs font-bold border ${colorClass}`}
            >
              {status.label}
            </span>
          )}
        </div>
      </div>

      {/* Eligibility reasons */}
      <div className="flex flex-wrap gap-1.5 mb-4">
        {eligibility.reasons.map((r) => (
          <span
            key={r}
            className="px-2 py-0.5 rounded-full bg-violet-50 text-violet-700 text-xs font-medium border border-violet-100"
          >
            {r}
          </span>
        ))}
      </div>

      {/* Action buttons — shown when no action taken yet */}
      {shift.refund_action == null && !showForm && (
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={() => setShowForm(true)}
            className="w-full rounded-xl bg-indigo-600 text-white text-sm font-bold py-2.5 hover:bg-indigo-700 active:scale-[0.98] transition-all"
          >
            Add Receipt Claim
          </button>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => handleActionClick("no_ride_taken")}
              className="flex-1 rounded-xl border border-gray-200 text-gray-500 text-sm font-semibold py-2 hover:bg-gray-50 transition-all"
            >
              No ride taken
            </button>
            <button
              type="button"
              onClick={() => handleActionClick("remind_later")}
              className="flex-1 rounded-xl border border-amber-200 text-amber-600 text-sm font-semibold py-2 hover:bg-amber-50 transition-all"
            >
              Remind me later
            </button>
          </div>
        </div>
      )}

      {/* Already resolved — show what was chosen, allow re-opening */}
      {shift.refund_action === "no_ride_taken" && (
        <button
          type="button"
          onClick={() => handleActionClick(null)}
          className="text-xs text-gray-400 underline hover:text-gray-600"
        >
          Undo — actually need a refund?
        </button>
      )}

      {shift.refund_action === "remind_later" && (
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setShowForm(true)}
            className="flex-1 rounded-xl bg-indigo-600 text-white text-sm font-bold py-2.5 hover:bg-indigo-700 transition-all"
          >
            Add Receipt Now
          </button>
          <button
            type="button"
            onClick={() => handleActionClick("no_ride_taken")}
            className="rounded-xl border border-gray-200 text-gray-500 text-sm font-semibold px-3 py-2 hover:bg-gray-50 transition-all"
          >
            No ride
          </button>
        </div>
      )}

      {shift.refund_action === "submitted" && !showForm && (
        <div className="text-sm text-gray-600 space-y-1">
          {loading ? (
            <div className="h-4 w-32 bg-gray-100 rounded animate-pulse" />
          ) : claim ? (
            <>
              <p>
                <span className="font-semibold">{claim.provider}</span> — ₪{claim.amount.toFixed(2)}
              </p>
              <p className="text-gray-400 text-xs">
                {new Date(claim.ride_at).toLocaleString()}
              </p>
              {claim.notes && <p className="text-xs text-gray-500">{claim.notes}</p>}
              <div className="flex gap-2 mt-3">
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
          ) : (
            <button
              type="button"
              onClick={() => setShowForm(true)}
              className="text-xs text-indigo-600 font-semibold underline"
            >
              Add receipt details
            </button>
          )}
        </div>
      )}

      {/* Claim form */}
      {showForm && (
        <form onSubmit={handleSubmit} className="flex flex-col gap-3 mt-1">
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
                      : "bg-gray-100 text-gray-500 hover:bg-gray-200",
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
              onChange={(e) => setAmount(e.target.value)}
              className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm text-gray-900 bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          {/* Ride date/time */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">Ride date &amp; time</label>
            <input
              type="datetime-local"
              value={rideAt}
              onChange={(e) => setRideAt(e.target.value)}
              className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm text-gray-900 bg-gray-50 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          {/* Notes */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">Notes (optional)</label>
            <textarea
              rows={2}
              placeholder="Route, trip ID, reason…"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full rounded-xl border border-gray-200 px-4 py-2.5 text-sm text-gray-900 bg-gray-50 resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>

          {/* Receipt upload */}
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-semibold text-gray-600">
              Receipt photo (optional)
            </label>
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*,application/pdf"
              className="hidden"
              onChange={(e) => setReceiptFile(e.target.files?.[0] ?? null)}
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="w-full rounded-xl border border-dashed border-gray-300 py-3 text-sm text-gray-500 hover:border-indigo-400 hover:text-indigo-600 transition-colors"
            >
              {receiptFile ? receiptFile.name : "Tap to attach photo or PDF"}
            </button>
          </div>

          <div className="flex gap-2 pt-1">
            <button
              type="submit"
              disabled={saving}
              className="flex-1 rounded-xl bg-indigo-600 text-white text-sm font-bold py-2.5 hover:bg-indigo-700 disabled:opacity-60 transition-all"
            >
              {saving ? "Saving…" : "Save Claim"}
            </button>
            <button
              type="button"
              onClick={() => setShowForm(false)}
              className="rounded-xl border border-gray-200 text-gray-500 text-sm font-semibold px-4 py-2.5 hover:bg-gray-50 transition-all"
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
