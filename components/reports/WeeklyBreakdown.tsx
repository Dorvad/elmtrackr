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
  const hasPrevData = weeks.some((w) => w.prevMonthMinutes > 0);

  return (
    <div>
      <div className="flex items-center justify-between px-1 mb-2">
        <h2
          className="text-xs font-bold uppercase tracking-widest"
          style={{ color: "var(--au-faint)" }}
        >
          Weekly Breakdown
        </h2>
        {hasPrevData && (
          <span className="text-[10px] font-medium" style={{ color: "var(--au-faint)" }}>
            vs {prevMonthLabel}
          </span>
        )}
      </div>

      <div
        className="rounded-2xl overflow-hidden"
        style={{
          background: "var(--au-surface)",
          border: "1px solid var(--au-hair)",
          boxShadow: "0 2px 8px rgba(91,77,242,0.06)",
        }}
      >
        {weeks.map((week, i) => {
          const pct = maxMinutes > 0 ? (week.minutes / maxMinutes) * 100 : 0;
          const showDelta = week.prevMonthMinutes > 0;
          const delta = week.minutes - week.prevMonthMinutes;
          const deltaMins = Math.abs(delta);

          return (
            <div
              key={week.label}
              className="px-4 py-3 animate-fade-in-up"
              style={{
                borderBottom: i < weeks.length - 1 ? "1px solid var(--au-hair)" : "none",
                animationDelay: `${i * 0.06}s`,
              }}
            >
              {/* Row header */}
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-extrabold" style={{ color: "var(--au-ink)" }}>
                    {week.label}
                  </span>
                  <span className="text-[10px] font-medium" style={{ color: "var(--au-faint)" }}>
                    days {week.dayRange}
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  {showDelta && (
                    <span
                      className="text-[10px] font-bold flex items-center gap-0.5"
                      style={{
                        color: delta > 0 ? "var(--color-active)" : delta < 0 ? "var(--au-peach-deep)" : "var(--au-faint)",
                      }}
                    >
                      {delta > 0 ? "↑" : delta < 0 ? "↓" : "—"}
                      {deltaMins > 0 && formatMinutes(deltaMins)}
                    </span>
                  )}
                  <span className="text-sm font-extrabold" style={{ color: "var(--au-ink)" }}>
                    {week.minutes > 0
                      ? formatHoursDecimal(week.minutes, 1) + "h"
                      : "—"}
                  </span>
                </div>
              </div>

              {/* Hours bar */}
              <div
                className="h-2 rounded-full overflow-hidden mb-1.5"
                style={{ background: "var(--au-surface-sub)" }}
              >
                <div
                  className="h-full rounded-full transition-all duration-700"
                  style={{ width: `${pct}%`, background: "var(--au-indigo)" }}
                />
              </div>

              {/* Sub-row: OT + pay */}
              {(week.overtimeMinutes > 0 || hasPay) && (
                <div className="flex items-center gap-3 mt-1">
                  {week.overtimeMinutes > 0 && (
                    <div className="flex items-center gap-1">
                      <span
                        className="h-1.5 w-1.5 rounded-full flex-shrink-0"
                        style={{ background: "var(--au-peach)" }}
                      />
                      <span className="text-[10px] font-semibold" style={{ color: "var(--au-peach-deep)" }}>
                        {formatMinutes(week.overtimeMinutes)} OT
                      </span>
                    </div>
                  )}
                  {week.pay !== null && week.pay > 0 && (
                    <div className="flex items-center gap-1 ml-auto">
                      <span className="text-[10px] font-bold" style={{ color: "var(--au-indigo)" }}>
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
