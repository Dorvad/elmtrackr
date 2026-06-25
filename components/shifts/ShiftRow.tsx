"use client";

import Link from "next/link";
import type { Shift, UserSettings, CompensationProfile } from "@/types";
import { netMinutes, formatMinutes } from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";
import { isOvernightShift } from "@/lib/shifts/overnight";
import { calculateShiftPay } from "@/lib/shifts/payroll";
import { resolveShiftCompensation } from "@/lib/compensation/profile";
import { formatCurrency } from "@/lib/compensation/currency";
import { checkRefundEligibility } from "@/lib/shifts/refund";

interface ShiftRowProps {
  shift: Shift;
  settings: UserSettings;
  profiles?: CompensationProfile[];
  animationIndex?: number;
  showRefunds?: boolean;
}

export function ShiftRow({ shift, settings, profiles, animationIndex = 0, showRefunds = true }: ShiftRowProps) {
  const net = netMinutes(shift);
  const isActive = shift.end_time === null;
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const resolved = resolveShiftCompensation(
    shift,
    settings,
    new Map((profiles ?? []).map((p) => [p.id, p]))
  );
  const weekendDays = resolved.rules_json.regular.weekendDays;
  const isWeekend =
    resolved.rules_json.weekend.enabled &&
    isWeekendDate(dateStr, weekendDays);
  const isOvernight = isOvernightShift(shift);
  const isSpecial = shift.is_special_day || isWeekend;

  const startDate = new Date(shift.start_time);
  const endDate = shift.end_time ? new Date(shift.end_time) : null;

  const formatTime = (d: Date) =>
    d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  const pay = calculateShiftPay(shift, { settings, profiles });
  const refundEligible = checkRefundEligibility(shift).eligible;
  const refundPending = refundEligible && shift.refund_action == null && !isActive;

  const stripeColor = isActive
    ? "#10b981"
    : isSpecial
    ? "var(--au-plum)"
    : isOvernight
    ? "var(--au-indigo)"
    : "var(--au-indigo)";
  const stripeOpacity = isActive ? 1 : isSpecial ? 1 : isOvernight ? 0.75 : 0.3;

  const delay = Math.min(animationIndex * 0.05, 0.3);

  return (
    <Link
      href={`/shifts/${shift.id}`}
      className="flex items-center gap-0 overflow-hidden transition-colors border-b last:border-0 animate-fade-in-up"
      style={{
        animationDelay: `${delay}s`,
        borderBottomColor: "var(--au-hair)",
      }}
      onMouseEnter={e => (e.currentTarget.style.background = "var(--au-surface-sub)")}
      onMouseLeave={e => (e.currentTarget.style.background = "")}
    >
      {/* Left color stripe */}
      <div
        className="w-1 self-stretch flex-shrink-0"
        style={{ background: stripeColor, opacity: stripeOpacity }}
      />

      {/* Date block */}
      <div className="flex-shrink-0 w-14 text-center py-3.5 pl-3">
        <p className="text-base font-extrabold leading-none" style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)" }}>
          {String(startDate.getUTCDate()).padStart(2, "0")}
        </p>
        <p className="text-[10px] font-semibold uppercase tracking-wide mt-0.5" style={{ color: "var(--au-faint)" }}>
          {startDate.toLocaleString("default", { weekday: "short" })}
        </p>
      </div>

      {/* Main info */}
      <div className="flex-1 min-w-0 py-3.5 px-2">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-sm font-semibold" style={{ color: "var(--au-ink)" }}>
            {formatTime(startDate)}
            {endDate ? ` — ${formatTime(endDate)}` : ""}
          </span>
        </div>
        <div className="flex items-center gap-1.5 mt-1 flex-wrap">
          {isActive && (
            <span className="rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide" style={{ background: "rgba(16,185,129,0.12)", color: "#10b981" }}>
              Live
            </span>
          )}
          {shift.is_special_day && (
            <span className="rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide" style={{ background: "var(--au-weekend-bg)", color: "var(--au-plum)" }}>
              Holiday
            </span>
          )}
          {isWeekend && !shift.is_special_day && (
            <span className="rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide" style={{ background: "var(--au-weekend-bg)", color: "var(--au-plum)" }}>
              Weekend
            </span>
          )}
          {isOvernight && (
            <span className="rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide" style={{ background: "var(--au-surface-sub)", color: "var(--au-indigo)" }}>
              Overnight
            </span>
          )}
          {shift.notes && (
            <span className="text-[10px] truncate max-w-[120px]" style={{ color: "var(--au-faint)" }}>
              {shift.notes}
            </span>
          )}
          {showRefunds && refundPending && (
            <span className="rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide" style={{ background: "var(--au-overtime-bg)", color: "var(--au-overtime-ink)" }}>
              Refund?
            </span>
          )}
          {showRefunds && shift.refund_action === "submitted" && (
            <span className="rounded-full text-[10px] font-bold px-2 py-0.5 uppercase tracking-wide" style={{ background: "rgba(16,185,129,0.12)", color: "#10b981" }}>
              Refund ✓
            </span>
          )}
        </div>
      </div>

      {/* Duration + pay */}
      <div className="flex-shrink-0 text-right py-3.5 pr-3">
        {isActive ? (
          <span className="text-sm font-bold" style={{ color: "#10b981" }}>●</span>
        ) : net !== null ? (
          <>
            <p className="text-sm font-bold" style={{ color: "var(--au-ink)" }}>{formatMinutes(net)}</p>
            {pay && (
              <p className="text-[10px] font-bold" style={{ color: "var(--au-indigo)" }}>{formatCurrency(pay.total_gross, pay.currency_code)}</p>
            )}
            {!pay && shift.break_minutes > 0 && (
              <p className="text-[10px]" style={{ color: "var(--au-faint)" }}>{shift.break_minutes}m brk</p>
            )}
          </>
        ) : null}
      </div>

      {/* Chevron */}
      <svg
        className="h-3.5 w-3.5 flex-shrink-0 mr-3"
        fill="none"
        stroke="currentColor"
        strokeWidth={2.5}
        viewBox="0 0 24 24"
        style={{ color: "var(--au-faint)" }}
      >
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
      </svg>
    </Link>
  );
}
