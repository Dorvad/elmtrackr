"use client";

import Link from "next/link";
import type { Shift, UserSettings } from "@/types";
import { netMinutes, formatMinutes } from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";
import { isOvernightShift } from "@/lib/shifts/overnight";
import { calculateShiftPay, formatCurrency } from "@/lib/shifts/payroll";

interface ShiftRowProps {
  shift: Shift;
  settings: UserSettings;
  animationIndex?: number;
}

export function ShiftRow({ shift, settings, animationIndex = 0 }: ShiftRowProps) {
  const net = netMinutes(shift);
  const isActive = shift.end_time === null;
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const isWeekend = isWeekendDate(dateStr, settings.weekend_days);
  const isOvernight = isOvernightShift(shift);
  const isSpecial = shift.is_special_day || isWeekend;

  const startDate = new Date(shift.start_time);
  const endDate = shift.end_time ? new Date(shift.end_time) : null;

  const formatTime = (d: Date) =>
    d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  const pay = calculateShiftPay(shift, settings);

  // Left stripe color
  const stripeColor = isActive
    ? "bg-emerald-400"
    : isSpecial
    ? "bg-violet-400"
    : isOvernight
    ? "bg-indigo-400"
    : "bg-indigo-200";

  const delay = Math.min(animationIndex * 0.05, 0.3);

  return (
    <Link
      href={`/shifts/${shift.id}`}
      className="flex items-center gap-0 overflow-hidden hover:bg-gray-50 active:bg-gray-100 transition-colors border-b border-gray-100 last:border-0 animate-fade-in-up"
      style={{ animationDelay: `${delay}s` }}
    >
      {/* Left color stripe */}
      <div className={`w-1 self-stretch flex-shrink-0 ${stripeColor}`} />

      {/* Date block */}
      <div className="flex-shrink-0 w-14 text-center py-3.5 pl-3">
        <p className="text-base font-extrabold text-gray-900 leading-none">
          {String(startDate.getUTCDate()).padStart(2, "0")}
        </p>
        <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide mt-0.5">
          {startDate.toLocaleString("default", { weekday: "short" })}
        </p>
      </div>

      {/* Main info */}
      <div className="flex-1 min-w-0 py-3.5 px-2">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-sm font-semibold text-gray-800">
            {formatTime(startDate)}
            {endDate ? ` — ${formatTime(endDate)}` : ""}
          </span>
        </div>
        <div className="flex items-center gap-1.5 mt-1 flex-wrap">
          {isActive && (
            <span className="rounded-full bg-emerald-100 text-emerald-700 text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide">
              Live
            </span>
          )}
          {shift.is_special_day && (
            <span className="rounded-full bg-violet-100 text-violet-700 text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide">
              Holiday
            </span>
          )}
          {isWeekend && !shift.is_special_day && (
            <span className="rounded-full bg-violet-100 text-violet-700 text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide">
              Weekend
            </span>
          )}
          {isOvernight && (
            <span className="rounded-full bg-indigo-100 text-indigo-700 text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide">
              Overnight
            </span>
          )}
          {shift.notes && (
            <span className="text-[10px] text-gray-400 truncate max-w-[120px]">
              {shift.notes}
            </span>
          )}
        </div>
      </div>

      {/* Duration + pay */}
      <div className="flex-shrink-0 text-right py-3.5 pr-3">
        {isActive ? (
          <span className="text-sm font-bold text-emerald-600">●</span>
        ) : net !== null ? (
          <>
            <p className="text-sm font-bold text-gray-800">{formatMinutes(net)}</p>
            {pay && (
              <p className="text-[10px] font-bold text-indigo-500">{formatCurrency(pay.total_gross)}</p>
            )}
            {!pay && shift.break_minutes > 0 && (
              <p className="text-[10px] text-gray-400">{shift.break_minutes}m brk</p>
            )}
          </>
        ) : null}
      </div>

      {/* Chevron */}
      <svg
        className="h-3.5 w-3.5 text-gray-300 flex-shrink-0 mr-3"
        fill="none"
        stroke="currentColor"
        strokeWidth={2.5}
        viewBox="0 0 24 24"
      >
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
      </svg>
    </Link>
  );
}
