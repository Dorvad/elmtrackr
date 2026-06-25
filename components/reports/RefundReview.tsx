"use client";

import { useState } from "react";
import Link from "next/link";
import type { Shift, UserSettings, RefundClaim, CompensationProfile } from "@/types";
import { checkRefundEligibility, getRefundStatus, shiftMonthKey } from "@/lib/shifts/refund";
import { useAllRefundClaims } from "@/hooks/useRefundClaim";
import { exportRefundPdf, ExportRow } from "@/lib/shifts/refund-export";
import { useProfile } from "@/hooks/useProfile";
import { createClient } from "@/lib/supabase/client";
import { RefundAnalytics } from "./RefundAnalytics";

interface Props {
  shifts: Shift[];
  settings?: UserSettings | null;
  profiles?: CompensationProfile[];
}

function monthLabel(key: string): string {
  const [year, month] = key.split("-").map(Number);
  return new Date(year, month - 1, 1).toLocaleString("en-US", {
    month: "long",
    year: "numeric",
  });
}

function StatPill({
  label,
  value,
  color,
}: {
  label: string;
  value: string | number;
  color: string;
}) {
  return (
    <div className={`flex-1 rounded-xl p-3 text-center ${color}`}>
      <p className="text-lg font-extrabold">{value}</p>
      <p className="text-[10px] font-semibold uppercase tracking-wide opacity-70">{label}</p>
    </div>
  );
}

function MonthSection({ monthKey, shifts, allClaims, claimsLoading }: {
  monthKey: string;
  shifts: Shift[];
  allClaims: RefundClaim[];
  claimsLoading: boolean;
}) {
  const [year, month] = monthKey.split("-").map(Number);
  const from = new Date(year, month - 1, 1).toISOString();
  const to   = new Date(year, month,     1).toISOString();
  const claims = allClaims.filter((c) => c.ride_at >= from && c.ride_at < to);
  const loading = claimsLoading;
  const { profile } = useProfile();
  const [exporting, setExporting] = useState(false);

  const eligibleShifts = shifts.filter(
    (s) => s.end_time && shiftMonthKey(s) === monthKey && checkRefundEligibility(s).eligible
  );

  const submitted    = eligibleShifts.filter((s) => s.refund_action === "submitted");
  const pending      = eligibleShifts.filter((s) => s.refund_action == null);
  const noRide       = eligibleShifts.filter((s) => s.refund_action === "no_ride_taken");
  const remindLater  = eligibleShifts.filter((s) => s.refund_action === "remind_later");
  const totalAmount  = claims.reduce((sum, c) => sum + c.amount, 0);

  async function handleExport() {
    setExporting(true);
    try {
      const baseRows: ExportRow[] = submitted
        .flatMap((s) => {
          const shiftClaims = claims.filter((c) => c.shift_id === s.id);
          return shiftClaims.map((claim) => ({ shift: s, claim }));
        });

      if (baseRows.length === 0) {
        alert("No submitted claims with receipt data to export.");
        return;
      }

      const supabase = createClient();
      const rows: ExportRow[] = await Promise.all(
        baseRows.map(async (row) => {
          if (!row.claim.receipt_path) return row;
          try {
            const { data } = await supabase.storage
              .from("refund-receipts")
              .createSignedUrl(row.claim.receipt_path, 120);
            if (!data?.signedUrl) return row;
            const res    = await fetch(data.signedUrl);
            const blob   = await res.blob();
            const dataUrl = await new Promise<string>((resolve, reject) => {
              const reader  = new FileReader();
              reader.onload = () => resolve(reader.result as string);
              reader.onerror = reject;
              reader.readAsDataURL(blob);
            });
            return { ...row, receiptDataUrl: dataUrl };
          } catch {
            return row;
          }
        })
      );

      await exportRefundPdf(rows, year, month, profile?.full_name);
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden mb-4">
      {/* Month header */}
      <div className="px-4 pt-4 pb-3 border-b border-gray-50">
        <h3 className="text-sm font-bold text-gray-800">{monthLabel(monthKey)}</h3>
      </div>

      <div className="p-4 flex flex-col gap-4">
        {/* Stats row */}
        <div className="flex gap-2">
          <StatPill
            label="Submitted"
            value={submitted.length}
            color="bg-emerald-50 text-emerald-700"
          />
          <StatPill
            label="Pending"
            value={pending.length}
            color={pending.length > 0 ? "bg-orange-50 text-orange-700" : "bg-gray-50 text-gray-400"}
          />
          <StatPill
            label="Total ₪"
            value={loading ? "…" : totalAmount > 0 ? `₪${totalAmount.toFixed(0)}` : "—"}
            color="bg-indigo-50 text-indigo-700"
          />
        </div>

        {/* Shift list */}
        <div className="flex flex-col divide-y divide-gray-50 -mx-1">
          {eligibleShifts.map((shift) => {
            const status      = getRefundStatus(shift);
            const shiftClaims = claims.filter((c) => c.shift_id === shift.id);
            const startDate   = new Date(shift.start_time);

            const badgeClass =
              status.color === "emerald" ? "bg-emerald-100 text-emerald-700" :
              status.color === "amber"   ? "bg-amber-100 text-amber-700" :
              status.color === "gray"    ? "bg-gray-100 text-gray-500" :
                                           "bg-orange-100 text-orange-700";

            return (
              <div key={shift.id} className="flex items-start gap-3 px-1 py-2.5">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-gray-800 truncate">
                    {startDate.toLocaleDateString("en-US", {
                      weekday: "short",
                      day: "numeric",
                      month: "short",
                    })}
                  </p>
                  {shiftClaims.length === 0 ? null : (
                    <div className="flex flex-col gap-0.5 mt-0.5">
                      {shiftClaims.map((c) => (
                        <p key={c.id} className="text-xs text-gray-400">
                          {c.direction === "to_work" ? "→" : "←"}{" "}
                          {c.provider} · ₪{c.amount.toFixed(2)}
                          {c.receipt_path && (
                            <span className="ml-1 text-indigo-400 font-medium">· receipt</span>
                          )}
                        </p>
                      ))}
                    </div>
                  )}
                </div>
                <span className={`rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide flex-shrink-0 mt-0.5 ${badgeClass}`}>
                  {status.label || "Pending"}
                </span>
                <Link
                  href={`/shifts/${shift.id}`}
                  className="text-xs text-indigo-500 font-semibold hover:text-indigo-700 flex-shrink-0 mt-0.5"
                >
                  View
                </Link>
              </div>
            );
          })}
        </div>

        {/* Pending nudge */}
        {pending.length > 0 && (
          <div className="rounded-xl bg-orange-50 border border-orange-100 px-3 py-2.5">
            <p className="text-xs text-orange-700 font-semibold">
              {pending.length} shift{pending.length > 1 ? "s" : ""} still need{pending.length === 1 ? "s" : ""} a refund decision.
            </p>
          </div>
        )}

        {/* Dismiss summary */}
        {(noRide.length > 0 || remindLater.length > 0) && (
          <p className="text-[10px] text-gray-400 -mt-1">
            {noRide.length > 0 && `${noRide.length} marked "no ride"${remindLater.length > 0 ? " · " : ""}`}
            {remindLater.length > 0 && `${remindLater.length} set to remind later`}
          </p>
        )}

        {/* Export PDF — bottom action, only when there's something to export */}
        {submitted.length > 0 && (
          <button
            type="button"
            onClick={handleExport}
            disabled={exporting || loading}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-indigo-600 text-white text-sm font-bold py-2.5 hover:bg-indigo-700 disabled:opacity-50 active:scale-[0.98] transition-all"
          >
            {exporting ? (
              <>
                <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
                </svg>
                Exporting…
              </>
            ) : (
              <>
                <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3M3 17V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
                </svg>
                Export PDF — {monthLabel(monthKey)}
              </>
            )}
          </button>
        )}
      </div>
    </div>
  );
}

export function RefundReview({ shifts, settings, profiles }: Props) {
  const { claims: allClaims, loading: claimsLoading } = useAllRefundClaims();

  const seen = new Set<string>();
  for (const s of shifts) {
    if (s.end_time && checkRefundEligibility(s).eligible) seen.add(shiftMonthKey(s));
  }
  const months = Array.from(seen).sort().reverse();

  const allEligible  = shifts.filter((s) => s.end_time && checkRefundEligibility(s).eligible);
  const totalPending = allEligible.filter((s) => s.refund_action == null).length;

  if (months.length === 0) {
    return (
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 text-center">
        <p className="text-sm text-gray-400">No eligible shifts yet.</p>
        <p className="text-xs text-gray-400 mt-1">
          Shifts ending after 23:30, on Friday nights, Saturdays, or holidays qualify for travel reimbursement.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">

      {/* Analytics section — shown once claims are loaded */}
      {!claimsLoading && allClaims.length > 0 && (
        <RefundAnalytics claims={allClaims} shifts={shifts} settings={settings} profiles={profiles} />
      )}

      {/* Divider before per-month breakdown */}
      {allClaims.length > 0 && (
        <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest px-1">
          Month by Month
        </h2>
      )}

      {/* Overall pending banner */}
      {totalPending > 0 && (
        <div className="rounded-2xl bg-orange-50 border border-orange-100 px-4 py-3 flex items-center gap-3">
          <div className="h-8 w-8 rounded-xl bg-orange-100 flex items-center justify-center flex-shrink-0">
            <svg className="h-4 w-4 text-orange-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
            </svg>
          </div>
          <div className="flex-1">
            <p className="text-sm font-bold text-orange-800">
              {totalPending} unresolved refund{totalPending > 1 ? "s" : ""}
            </p>
            <p className="text-xs text-orange-600 mt-0.5">
              Open each shift to submit a claim, mark no ride, or remind later.
            </p>
          </div>
        </div>
      )}

      {/* Per-month sections */}
      {months.map((key) => (
        <MonthSection key={key} monthKey={key} shifts={shifts} allClaims={allClaims} claimsLoading={claimsLoading} />
      ))}
    </div>
  );
}
