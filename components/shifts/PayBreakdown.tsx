"use client";

import type { Shift, UserSettings } from "@/types";
import { calculateShiftPay, formatCurrency } from "@/lib/shifts/payroll";
import { formatMinutes } from "@/lib/shifts/duration";

interface PayBreakdownProps {
  shift: Shift;
  settings: UserSettings;
}

export function PayBreakdown({ shift, settings }: PayBreakdownProps) {
  if (!settings.hourly_rate) return null;

  const breakdown = calculateShiftPay(shift, settings);
  if (!breakdown) return null;

  return (
    <div className="rounded-2xl border border-gray-100 bg-white shadow-sm overflow-hidden animate-fade-in-up">
      {/* Header */}
      <div className={[
        "flex items-center justify-between px-4 py-3 border-b border-gray-100",
        breakdown.is_special ? "bg-violet-50" : "bg-indigo-50",
      ].join(" ")}>
        <div className="flex items-center gap-2">
          <span className="text-base">💰</span>
          <p className={[
            "text-xs font-bold uppercase tracking-widest",
            breakdown.is_special ? "text-violet-600" : "text-indigo-600",
          ].join(" ")}>
            Pay Breakdown
          </p>
        </div>
        <p className={[
          "text-lg font-extrabold tracking-tight",
          breakdown.is_special ? "text-violet-700" : "text-indigo-700",
        ].join(" ")}>
          {formatCurrency(breakdown.total_gross)}
        </p>
      </div>

      {/* Bracket rows */}
      <div className="divide-y divide-gray-50">
        {breakdown.brackets.map((bracket, i) => (
          <div key={i} className="flex items-center justify-between px-4 py-2.5">
            <div className="flex items-center gap-2">
              <RateDot rate={bracket.rate} />
              <div>
                <p className="text-xs font-semibold text-gray-700">{bracket.label}</p>
                <p className="text-[10px] text-gray-400">{formatMinutes(bracket.minutes)}</p>
              </div>
            </div>
            <p className="text-sm font-bold text-gray-800">{formatCurrency(bracket.amount)}</p>
          </div>
        ))}
      </div>

      {/* Base rate note */}
      <div className="px-4 py-2.5 bg-gray-50 border-t border-gray-100">
        <p className="text-[10px] text-gray-400 font-medium text-center">
          Base rate: {formatCurrency(settings.hourly_rate)}/hr · Gross before tax
        </p>
      </div>
    </div>
  );
}

function RateDot({ rate }: { rate: number }) {
  const color =
    rate >= 2.0 ? "bg-red-400" :
    rate >= 1.75 ? "bg-orange-400" :
    rate >= 1.5 ? "bg-amber-400" :
    rate >= 1.25 ? "bg-yellow-400" :
    "bg-emerald-400";

  return <span className={`h-2 w-2 rounded-full flex-shrink-0 ${color}`} />;
}
