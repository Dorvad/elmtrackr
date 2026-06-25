"use client";

import type { MonthInsights } from "@/lib/shifts/insights";
import { formatMinutes } from "@/lib/shifts/duration";
import { formatCurrency } from "@/lib/shifts/payroll";

interface QuickStatsProps {
  insights: MonthInsights;
}

interface StatItem {
  icon: string;
  label: string;
  value: string;
  sub?: string;
  accent: string; // tailwind text color
  bg: string;     // tailwind bg color
}

export function QuickStats({ insights }: QuickStatsProps) {
  const {
    weekendShiftCount,
    overtimeShiftCount,
    avgShiftMinutes,
    avgPayPerShift,
    longestShift,
    highestEarningShift,
  } = insights;

  const longestDate = longestShift
    ? new Date(longestShift.shift.start_time).toLocaleDateString([], {
        month: "short",
        day: "numeric",
      })
    : null;

  const bestDate = highestEarningShift
    ? new Date(highestEarningShift.shift.start_time).toLocaleDateString([], {
        month: "short",
        day: "numeric",
      })
    : null;

  const items: StatItem[] = [
    {
      icon: "🌅",
      label: "Weekend Shifts",
      value: String(weekendShiftCount),
      sub: weekendShiftCount === 1 ? "shift" : "shifts",
      accent: "text-violet-700",
      bg: "bg-violet-50 border-violet-100",
    },
    {
      icon: "⏱",
      label: "Avg Shift Length",
      value: avgShiftMinutes > 0 ? formatMinutes(Math.round(avgShiftMinutes)) : "—",
      sub: "per shift",
      accent: "text-indigo-700",
      bg: "bg-indigo-50 border-indigo-100",
    },
    {
      icon: "🔥",
      label: "Overtime Shifts",
      value: String(overtimeShiftCount),
      sub: overtimeShiftCount === 1 ? "shift" : "shifts",
      accent: "text-amber-700",
      bg: "bg-amber-50 border-amber-100",
    },
    {
      icon: "📏",
      label: "Longest Shift",
      value: longestShift ? formatMinutes(longestShift.minutes) : "—",
      sub: longestDate ?? undefined,
      accent: "text-emerald-700",
      bg: "bg-emerald-50 border-emerald-100",
    },
    {
      icon: "💰",
      label: "Avg Pay / Shift",
      value: avgPayPerShift != null ? formatCurrency(avgPayPerShift) : "—",
      sub: avgPayPerShift != null ? "per shift" : "set rate to unlock",
      accent: "text-indigo-700",
      bg: "bg-indigo-50 border-indigo-100",
    },
    {
      icon: "🏆",
      label: "Best Shift",
      value: highestEarningShift ? formatCurrency(highestEarningShift.amount) : "—",
      sub: bestDate ?? (highestEarningShift ? undefined : "set rate to unlock"),
      accent: "text-violet-700",
      bg: "bg-violet-50 border-violet-100",
    },
  ];

  return (
    <div>
      <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest px-1 mb-2">
        Shift Insights
      </h2>
      <div className="grid grid-cols-2 gap-3">
        {items.map((item, i) => (
          <div
            key={item.label}
            className={[
              "rounded-2xl border p-3.5 flex flex-col gap-1 shadow-sm animate-fade-in-up",
              item.bg,
              `stagger-${Math.min(i + 1, 4)}`,
            ].join(" ")}
          >
            <div className="flex items-center gap-1.5">
              <span className="text-base leading-none">{item.icon}</span>
              <span className="text-[10px] font-bold uppercase tracking-widest text-gray-400">
                {item.label}
              </span>
            </div>
            <span className={`text-xl font-extrabold leading-tight tracking-tight ${item.accent}`}>
              {item.value}
            </span>
            {item.sub && (
              <span className="text-[10px] text-gray-400 font-medium">{item.sub}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
