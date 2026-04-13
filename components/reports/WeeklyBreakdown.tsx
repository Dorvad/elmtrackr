"use client";

import type { WeekData } from "@/lib/shifts/insights";
import { formatMinutes, formatHoursDecimal } from "@/lib/shifts/duration";
import { formatCurrency } from "@/lib/shifts/payroll";

interface WeeklyBreakdownProps {
  weeks: WeekData[];
  prevMonthLabel: string;
}

export function WeeklyBreakdown({ weeks, prevMonthLabel }: WeeklyBreakdownProps) {
  const maxMinutes = Math.max(...weeks.map((w) => w.minutes), 1);
  const hasPay = weeks.some((w) => w.pay !== null && w.pay > 0);
  // Only show "vs Month" label if at least one week has prev data
  const hasPrevData = weeks.some((w) => w.prevMonthMinutes > 0);

  return (
    <div>
      <div className="flex items-center justify-between px-1 mb-2">
        <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          Weekly Breakdown
        </h2>
        {hasPrevData && (
          <span className="text-[10px] text-gray-400 font-medium">
            vs {prevMonthLabel}
          </span>
        )}
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden divide-y divide-gray-50">
        {weeks.map((week, i) => {
          const pct = maxMinutes > 0 ? (week.minutes / maxMinutes) * 100 : 0;

          // Only compute / show delta when there is real previous-month data
          const showDelta = week.prevMonthMinutes > 0;
          const delta = week.minutes - week.prevMonthMinutes;
          const deltaMins = Math.abs(delta);

          return (
            <div
              key={week.label}
              className="px-4 py-3 animate-fade-in-up"
              style={{ animationDelay: `${i * 0.06}s` }}
            >
              {/* Row header */}
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-extrabold text-gray-700">
                    {week.label}
                  </span>
                  <span className="text-[10px] text-gray-400 font-medium">
                    days {week.dayRange}
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  {/* Delta — only rendered when prev month has data */}
                  {showDelta && (
                    <span
                      className={[
                        "text-[10px] font-bold flex items-center gap-0.5",
                        delta > 0
                          ? "text-emerald-500"
                          : delta < 0
                          ? "text-red-400"
                          : "text-gray-300",
                      ].join(" ")}
                    >
                      {delta > 0 ? "↑" : delta < 0 ? "↓" : "—"}
                      {deltaMins > 0 && formatMinutes(deltaMins)}
                    </span>
                  )}

                  <span className="text-sm font-extrabold text-gray-800">
                    {week.minutes > 0
                      ? formatHoursDecimal(week.minutes, 1) + "h"
                      : "—"}
                  </span>
                </div>
              </div>

              {/* Hours bar */}
              <div className="h-2 rounded-full bg-gray-100 overflow-hidden mb-1.5">
                <div
                  className="h-full rounded-full bg-indigo-500 transition-all duration-700"
                  style={{ width: `${pct}%` }}
                />
              </div>

              {/* Sub-row: OT + pay */}
              {(week.overtimeMinutes > 0 || hasPay) && (
                <div className="flex items-center gap-3 mt-1">
                  {week.overtimeMinutes > 0 && (
                    <div className="flex items-center gap-1">
                      <span className="h-1.5 w-1.5 rounded-full bg-amber-400 flex-shrink-0" />
                      <span className="text-[10px] text-amber-600 font-semibold">
                        {formatMinutes(week.overtimeMinutes)} OT
                      </span>
                    </div>
                  )}
                  {week.pay !== null && week.pay > 0 && (
                    <div className="flex items-center gap-1 ml-auto">
                      <span className="text-[10px] text-indigo-500 font-bold">
                        {formatCurrency(week.pay)}
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
