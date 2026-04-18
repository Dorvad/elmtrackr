"use client";

import { useState } from "react";
import Link from "next/link";
import { Shift } from "@/types";
import { checkRefundEligibility, getRefundStatus, shiftMonthKey } from "@/lib/shifts/refund";
import { useMonthlyRefundClaims } from "@/hooks/useRefundClaim";
import { exportRefundPdf, ExportRow } from "@/lib/shifts/refund-export";
import { useProfile } from "@/hooks/useProfile";

interface Props {
  shifts: Shift[];
}

function monthLabel(key: string): string {
  const [year, month] = key.split("-").map(Number);
  return new Date(year, month - 1, 1).toLocaleString("en-US", {
    month: "long",
    year: "numeric",
  });
}

function StatPill({ label, value, color }: { label: string; value: string | number; color: string }) {
  return (
    <div className={`flex-1 rounded-xl p-3 text-center ${color}`}>
      <p className="text-lg font-extrabold">{value}</p>
      <p className="text-[10px] font-semibold uppercase tracking-wide opacity-70">{label}</p>
    </div>
  );
}

function MonthSection({ monthKey, shifts }: { monthKey: string; shifts: Shift[] }) {
  const [year, month] = monthKey.split("-").map(Number);
  const { claims, loading } = useMonthlyRefundClaims(year, month);
  const { profile } = useProfile();
  const [exporting, setExporting] = useState(false);

  const eligibleShifts = shifts.filter(
    (s) => s.end_time && shiftMonthKey(s) === monthKey && checkRefundEligibility(s).eligible
  );

  const submitted = eligibleShifts.filter((s) => s.refund_action === "submitted");
  const pending = eligibleShifts.filter((s) => s.refund_action == null);
  const noRide = eligibleShifts.filter((s) => s.refund_action === "no_ride_taken");
  const remindLater = eligibleShifts.filter((s) => s.refund_action === "remind_later");

  const totalAmount = claims.reduce((sum, c) => sum + c.amount, 0);

  async function handleExport() {
    setExporting(true);
    try {
      const rows: ExportRow[] = submitted
        .map((s) => {
          const claim = claims.find((c) => c.shift_id === s.id);
          return claim ? { shift: s, claim } : null;
        })
        .filter((r): r is ExportRow => r !== null);

      if (rows.length === 0) {
        alert("No submitted claims with receipt data to export.");
        return;
      }
      await exportRefundPdf(rows, year, month, profile?.full_name);
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 mb-4">
      {/* Month header */}
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-sm font-bold text-gray-800">{monthLabel(monthKey)}</h3>
        {submitted.length > 0 && (
          <button
            type="button"
            onClick={handleExport}
            disabled={exporting || loading}
            className="flex items-center gap-1.5 rounded-xl border border-indigo-200 text-indigo-600 text-xs font-bold px-3 py-1.5 hover:bg-indigo-50 disabled:opacity-50 transition-all"
          >
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 10v6m0 0l-3-3m3 3l3-3M3 17V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
            </svg>
            {exporting ? "Exporting…" : "Export PDF"}
          </button>
        )}
      </div>

      {/* Stats row */}
      <div className="flex gap-2 mb-4">
        <StatPill
          label="Submitted"
          value={submitted.length}
          color="bg-emerald-50 text-emerald-700"
        />
        <StatPill
          label="Pending"
          value={pending.length}
          color={pending.length > 0 ? "bg-violet-50 text-violet-700" : "bg-gray-50 text-gray-400"}
        />
        <StatPill
          label="Total ₪"
          value={loading ? "…" : totalAmount > 0 ? `₪${totalAmount.toFixed(0)}` : "—"}
          color="bg-indigo-50 text-indigo-700"
        />
      </div>

      {/* Shift list */}
      <div className="flex flex-col divide-y divide-gray-50">
        {eligibleShifts.map((shift) => {
          const status = getRefundStatus(shift);
          const claim = claims.find((c) => c.shift_id === shift.id);
          const endDate = shift.end_time ? new Date(shift.end_time) : null;

          const badgeColor =
            status.color === "emerald"
              ? "bg-emerald-100 text-emerald-700"
              : status.color === "amber"
              ? "bg-amber-100 text-amber-700"
              : status.color === "gray"
              ? "bg-gray-100 text-gray-500"
              : "bg-violet-100 text-violet-700";

          return (
            <div key={shift.id} className="flex items-center gap-3 py-2.5">
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gray-800 truncate">
                  {endDate
                    ? endDate.toLocaleDateString("en-US", {
                        weekday: "short",
                        day: "numeric",
                        month: "short",
                      })
                    : "—"}
                </p>
                {claim && (
                  <p className="text-xs text-gray-400 mt-0.5">
                    {claim.provider} · ₪{claim.amount.toFixed(2)}
                  </p>
                )}
              </div>
              <span className={`rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide ${badgeColor}`}>
                {status.label || "Pending"}
              </span>
              <Link
                href={`/shifts/${shift.id}`}
                className="text-xs text-indigo-500 font-semibold hover:text-indigo-700 flex-shrink-0"
              >
                View
              </Link>
            </div>
          );
        })}
      </div>

      {/* Pending reminder */}
      {pending.length > 0 && (
        <div className="mt-3 rounded-xl bg-violet-50 border border-violet-100 px-3 py-2">
          <p className="text-xs text-violet-700 font-semibold">
            {pending.length} shift{pending.length > 1 ? "s" : ""} still need{pending.length === 1 ? "s" : ""} a refund decision.
          </p>
        </div>
      )}

      {/* Dismiss summary */}
      {noRide.length > 0 || remindLater.length > 0 ? (
        <p className="text-[10px] text-gray-400 mt-2">
          {noRide.length > 0 && `${noRide.length} marked "no ride"${remindLater.length > 0 ? " · " : ""}`}
          {remindLater.length > 0 && `${remindLater.length} set to remind later`}
        </p>
      ) : null}
    </div>
  );
}

export function RefundReview({ shifts }: Props) {
  // Get all months that have eligible shifts
  const seen = new Set<string>();
  for (const s of shifts) {
    if (s.end_time && checkRefundEligibility(s).eligible) {
      seen.add(shiftMonthKey(s));
    }
  }
  const months = Array.from(seen).sort().reverse();

  const allEligible = shifts.filter((s) => s.end_time && checkRefundEligibility(s).eligible);
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
    <div>
      {/* Overall summary */}
      {totalPending > 0 && (
        <div className="rounded-2xl bg-violet-50 border border-violet-100 px-4 py-3 mb-4 flex items-center gap-3">
          <div className="flex-1">
            <p className="text-sm font-bold text-violet-800">
              {totalPending} unresolved refund{totalPending > 1 ? "s" : ""}
            </p>
            <p className="text-xs text-violet-600 mt-0.5">
              Open each shift to submit a claim, mark no ride, or remind later.
            </p>
          </div>
        </div>
      )}

      {months.map((key) => (
        <MonthSection key={key} monthKey={key} shifts={shifts} />
      ))}
    </div>
  );
}
