"use client";

import Link from "next/link";
import type { Shift, UserSettings } from "@/types";
import { netMinutes, formatMinutes } from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";
import { isOvernightShift } from "@/lib/shifts/overnight";

interface ShiftRowProps {
  shift: Shift;
  settings: UserSettings;
}

export function ShiftRow({ shift, settings }: ShiftRowProps) {
  const net = netMinutes(shift);
  const isActive = shift.end_time === null;
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const isWeekend = isWeekendDate(dateStr, settings.weekend_days);
  const isOvernight = isOvernightShift(shift);

  const startDate = new Date(shift.start_time);
  const endDate = shift.end_time ? new Date(shift.end_time) : null;

  const formatTime = (d: Date) =>
    d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  const formatDate = (d: Date) =>
    d.toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" });

  return (
    <Link
      href={`/shifts/${shift.id}`}
      className="flex items-center gap-3 px-4 py-3.5 bg-white hover:bg-gray-50 active:bg-gray-100 transition-colors border-b border-gray-100 last:border-0"
    >
      {/* Date column */}
      <div className="flex-shrink-0 w-12 text-center">
        <p className="text-lg font-bold text-gray-900 leading-tight">
          {startDate.getUTCDate()}
        </p>
        <p className="text-xs text-gray-400">
          {startDate.toLocaleString("default", { weekday: "short" })}
        </p>
      </div>

      {/* Main info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-sm font-medium text-gray-800">
            {formatTime(startDate)}
            {endDate ? ` – ${formatTime(endDate)}` : ""}
          </span>
          {isActive && (
            <span className="rounded-full bg-green-100 text-green-700 text-xs font-semibold px-2 py-0.5">
              Active
            </span>
          )}
          {isWeekend && (
            <span className="rounded-full bg-orange-100 text-orange-700 text-xs font-semibold px-2 py-0.5">
              Weekend
            </span>
          )}
          {isOvernight && (
            <span className="rounded-full bg-purple-100 text-purple-700 text-xs font-semibold px-2 py-0.5">
              Overnight
            </span>
          )}
        </div>
        {shift.notes && (
          <p className="mt-0.5 text-xs text-gray-400 truncate">{shift.notes}</p>
        )}
        {isOvernight && endDate && (
          <p className="text-xs text-gray-400">
            Ends {formatDate(endDate)}
          </p>
        )}
      </div>

      {/* Duration */}
      <div className="flex-shrink-0 text-right">
        {isActive ? (
          <span className="text-sm font-semibold text-green-600">—</span>
        ) : net !== null ? (
          <>
            <p className="text-sm font-semibold text-gray-800">
              {formatMinutes(net)}
            </p>
            {shift.break_minutes > 0 && (
              <p className="text-xs text-gray-400">{shift.break_minutes}m break</p>
            )}
          </>
        ) : null}
      </div>

      {/* Chevron */}
      <svg className="h-4 w-4 text-gray-300 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
      </svg>
    </Link>
  );
}
