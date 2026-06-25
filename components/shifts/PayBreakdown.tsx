"use client";

import type { Shift, UserSettings, CompensationProfile } from "@/types";
import { calculateShiftPay } from "@/lib/shifts/payroll";
import { formatCurrency } from "@/lib/compensation/currency";
import { formatMinutes } from "@/lib/shifts/duration";
import { COMPENSATION_ESTIMATE_NOTE } from "@/lib/compensation/constants";

interface PayBreakdownProps {
  shift: Shift;
  settings: UserSettings;
  profiles?: CompensationProfile[];
}

export function PayBreakdown({ shift, settings, profiles }: PayBreakdownProps) {
  const breakdown = calculateShiftPay(shift, { settings, profiles });
  if (!breakdown) return null;

  return (
    <div className="rounded-2xl border border-gray-100 bg-white shadow-sm overflow-hidden animate-fade-in-up">
      <div className={[
        "flex items-center justify-between px-4 py-3 border-b border-gray-100",
        breakdown.is_special ? "bg-violet-50" : "bg-indigo-50",
      ].join(" ")}>
        <div className="flex items-center gap-2">
          <span className="text-base">💰</span>
          <div>
            <p className={[
              "text-xs font-bold uppercase tracking-widest",
              breakdown.is_special ? "text-violet-600" : "text-indigo-600",
            ].join(" ")}>
              Estimated Pay
            </p>
            {breakdown.profile_name && (
              <p className="text-[10px] text-gray-400">{breakdown.profile_name}</p>
            )}
          </div>
        </div>
        <p className={[
          "text-lg font-extrabold tracking-tight",
          breakdown.is_special ? "text-violet-700" : "text-indigo-700",
        ].join(" ")}>
          {formatCurrency(breakdown.total_gross, breakdown.currency_code)}
        </p>
      </div>

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
            <p className="text-sm font-bold text-gray-800">
              {formatCurrency(bracket.amount, breakdown.currency_code)}
            </p>
          </div>
        ))}
      </div>

      <div className="px-4 py-2.5 bg-gray-50 border-t border-gray-100">
        <p className="text-[10px] text-gray-400 font-medium text-center">
          {COMPENSATION_ESTIMATE_NOTE}
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
