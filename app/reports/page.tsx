"use client";

export const dynamic = "force-dynamic";

import { useState } from "react";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { StatCard } from "@/components/ui/StatCard";
import { Button } from "@/components/ui/Button";
import { SegmentedBar, SegmentLegend } from "@/components/ui/SegmentedBar";
import { filterShiftsByMonth, buildMonthlyReport } from "@/lib/shifts/aggregation";
import { formatHoursDecimal, formatMinutes, netMinutes } from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";
import { isOvernightShift } from "@/lib/shifts/overnight";
import { generateMonthlyCSV, downloadCSV } from "@/lib/shifts/csv";
import { sumMonthlyPay, calculateShiftPay, formatCurrency } from "@/lib/shifts/payroll";
import type { Shift, UserSettings } from "@/types";

export default function ReportsPage() {
  const { shifts, loading: shiftsLoading, error } = useShifts();
  const { settings, loading: settingsLoading } = useSettings();
  const { toast } = useToast();

  const now = new Date();
  const [selectedYear, setSelectedYear] = useState(now.getUTCFullYear());
  const [selectedMonth, setSelectedMonth] = useState(now.getUTCMonth() + 1);

  function prevMonth() {
    if (selectedMonth === 1) { setSelectedYear((y) => y - 1); setSelectedMonth(12); }
    else setSelectedMonth((m) => m - 1);
  }
  function nextMonth() {
    const isCurrentOrFuture =
      selectedYear > now.getUTCFullYear() ||
      (selectedYear === now.getUTCFullYear() && selectedMonth >= now.getUTCMonth() + 1);
    if (isCurrentOrFuture) return;
    if (selectedMonth === 12) { setSelectedYear((y) => y + 1); setSelectedMonth(1); }
    else setSelectedMonth((m) => m + 1);
  }

  const isCurrentMonth =
    selectedYear === now.getUTCFullYear() && selectedMonth === now.getUTCMonth() + 1;

  const monthLabel = new Date(Date.UTC(selectedYear, selectedMonth - 1, 1))
    .toLocaleString("default", { month: "long", year: "numeric" });

  const loading = shiftsLoading || settingsLoading;
  const monthShifts = settings ? filterShiftsByMonth(shifts, selectedYear, selectedMonth) : [];
  const report = settings && monthShifts.length > 0
    ? buildMonthlyReport(selectedYear, selectedMonth, monthShifts, settings)
    : null;

  const segments = report
    ? [
        { label: "Regular",  value: report.regular_minutes,  color: "bg-indigo-500", dotColor: "#4f46e5" },
        { label: "Overtime", value: report.overtime_minutes, color: "bg-amber-400",  dotColor: "#f59e0b" },
        { label: "Weekend",  value: report.weekend_minutes,  color: "bg-violet-500", dotColor: "#8b5cf6" },
      ]
    : [];

  const monthPay = settings?.hourly_rate && monthShifts.length > 0
    ? sumMonthlyPay(monthShifts.filter((s) => s.end_time !== null), settings)
    : null;

  function handleExportCSV() {
    if (!settings || monthShifts.length === 0) return;
    const csv = generateMonthlyCSV(monthShifts, settings, selectedYear, selectedMonth);
    const filename = `elmtrackr-${selectedYear}-${String(selectedMonth).padStart(2, "0")}.csv`;
    downloadCSV(csv, filename);
    toast("CSV exported", "success");
  }

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
      {/* Header */}
      <div className="px-4 pt-12 pb-4 animate-fade-in">
        <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">Reports</h1>
      </div>

      {/* Month picker */}
      <div className="mx-4 mb-4 bg-white rounded-2xl border border-gray-100 shadow-sm flex items-center justify-between px-3 py-2 animate-fade-in-up">
        <button onClick={prevMonth} className="h-8 w-8 rounded-xl flex items-center justify-center hover:bg-gray-100 transition-colors">
          <svg className="h-4 w-4 text-gray-500" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <span className="text-sm font-bold text-gray-800">{monthLabel}</span>
        <button onClick={nextMonth} disabled={isCurrentMonth} className="h-8 w-8 rounded-xl flex items-center justify-center hover:bg-gray-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed">
          <svg className="h-4 w-4 text-gray-500" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      <div className="max-w-md mx-auto px-4 flex flex-col gap-4">
        {error && <ErrorMessage message={error} />}
        {loading && <PageSpinner />}

        {!loading && !report && (
          <EmptyState title="No completed shifts" description="Complete some shifts to see your report." />
        )}

        {!loading && report && settings && (
          <>
            {/* Distribution card */}
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-1">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="text-xs text-gray-400 font-semibold uppercase tracking-wide">Hours Distribution</p>
                  <p className="text-3xl font-extrabold text-gray-900 mt-0.5 tracking-tight">
                    {formatHoursDecimal(report.total_minutes, 1)}
                    <span className="text-base font-semibold text-gray-400 ml-1">h</span>
                  </p>
                  <p className="text-xs text-gray-400 mt-0.5">{report.shift_count} shift{report.shift_count !== 1 ? "s" : ""}</p>
                </div>
                <Button variant="secondary" size="sm" onClick={handleExportCSV}>
                  <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                  CSV
                </Button>
              </div>
              <SegmentedBar segments={segments} />
              <SegmentLegend segments={segments} />
            </div>

            {/* Stat grid */}
            <div className="grid grid-cols-2 gap-3">
              <StatCard label="Total"    value={formatHoursDecimal(report.total_minutes, 1) + "h"}    variant="primary"  stagger={1} />
              <StatCard label="Regular"  value={formatHoursDecimal(report.regular_minutes, 1) + "h"}  variant="default"  stagger={2} />
              <StatCard label="Overtime" value={formatHoursDecimal(report.overtime_minutes, 1) + "h"} variant="overtime" stagger={3} />
              <StatCard label="Weekend"  value={formatHoursDecimal(report.weekend_minutes, 1) + "h"}  variant="weekend"  stagger={4} />
            </div>

            {/* Gross pay card */}
            {monthPay && monthPay.total_gross > 0 && (
              <div className="bg-white rounded-2xl border border-indigo-100 shadow-sm p-4 animate-fade-in-up">
                <p className="text-xs font-bold text-indigo-400 uppercase tracking-widest mb-2">
                  Gross Pay · Before Tax
                </p>
                <p className="text-3xl font-extrabold text-indigo-600 tracking-tight mb-3">
                  {formatCurrency(monthPay.total_gross)}
                </p>
                <div className="grid grid-cols-3 gap-2">
                  <div className="rounded-xl bg-indigo-50 p-2.5 text-center">
                    <p className="text-[10px] text-indigo-400 font-bold uppercase tracking-wide">Regular</p>
                    <p className="text-sm font-extrabold text-indigo-700 mt-0.5">{formatCurrency(monthPay.regular_gross)}</p>
                  </div>
                  <div className="rounded-xl bg-amber-50 p-2.5 text-center">
                    <p className="text-[10px] text-amber-400 font-bold uppercase tracking-wide">Overtime</p>
                    <p className="text-sm font-extrabold text-amber-700 mt-0.5">{formatCurrency(monthPay.overtime_gross)}</p>
                  </div>
                  <div className="rounded-xl bg-violet-50 p-2.5 text-center">
                    <p className="text-[10px] text-violet-400 font-bold uppercase tracking-wide">Holiday</p>
                    <p className="text-sm font-extrabold text-violet-700 mt-0.5">{formatCurrency(monthPay.special_gross)}</p>
                  </div>
                </div>
              </div>
            )}

            {/* Thresholds */}
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm px-4 py-3 flex items-center justify-center gap-4 text-xs text-gray-400 font-medium animate-fade-in-up stagger-5">
              <span>Daily OT after <span className="text-gray-600 font-semibold">{formatMinutes(settings.daily_overtime_threshold_minutes)}</span></span>
              <span className="h-3 w-px bg-gray-200" />
              <span>Weekly OT after <span className="text-gray-600 font-semibold">{formatMinutes(settings.weekly_overtime_threshold_minutes)}</span></span>
            </div>

            {/* Shift breakdown */}
            <div>
              <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest px-1 mb-2">
                Shift Breakdown
              </h2>
              <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
                {monthShifts
                  .filter((s) => s.end_time !== null)
                  .sort((a, b) => new Date(a.start_time).getTime() - new Date(b.start_time).getTime())
                  .map((shift, i) => (
                    <ShiftReportRow key={shift.id} shift={shift} settings={settings} index={i} />
                  ))}
              </div>
            </div>
          </>
        )}
      </div>

      <BottomNav />
    </div>
  );
}

function ShiftReportRow({ shift, settings, index }: { shift: Shift; settings: UserSettings; index: number }) {
  const net = netMinutes(shift) ?? 0;
  const otMins = Math.max(0, net - settings.daily_overtime_threshold_minutes);
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const isWeekend = isWeekendDate(dateStr, settings.weekend_days);
  const isOvernight = isOvernightShift(shift);
  const isSpecial = shift.is_special_day || isWeekend;

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  const stripe = isSpecial ? "bg-violet-400" : isOvernight ? "bg-indigo-400" : "bg-indigo-200";

  const payBreakdown = settings.hourly_rate ? calculateShiftPay(shift, settings) : null;

  return (
    <div
      className="flex items-center gap-0 border-b border-gray-100 last:border-0 animate-fade-in-up"
      style={{ animationDelay: `${index * 0.04}s` }}
    >
      <div className={`w-1 self-stretch flex-shrink-0 ${stripe}`} />
      <div className="flex-1 px-3 py-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-sm font-bold text-gray-800">
              {new Date(shift.start_time).toLocaleDateString([], {
                weekday: "short", month: "short", day: "numeric",
              })}
            </p>
            <p className="text-xs text-gray-400 mt-0.5">
              {formatTime(shift.start_time)}
              {shift.end_time ? ` — ${formatTime(shift.end_time)}` : ""}
              {shift.break_minutes > 0 ? ` · ${shift.break_minutes}m break` : ""}
            </p>
            <div className="flex gap-1 mt-1">
              {shift.is_special_day && <span className="rounded-full bg-violet-100 text-violet-700 text-[10px] font-bold px-1.5 py-0.5">Holiday</span>}
              {isWeekend && !shift.is_special_day && <span className="rounded-full bg-violet-100 text-violet-700 text-[10px] font-bold px-1.5 py-0.5">Weekend</span>}
              {isOvernight && <span className="rounded-full bg-indigo-100 text-indigo-700 text-[10px] font-bold px-1.5 py-0.5">Overnight</span>}
            </div>
          </div>
          <div className="text-right flex-shrink-0">
            <p className="text-sm font-bold text-gray-800">{formatMinutes(net)}</p>
            {otMins > 0 && !isSpecial && (
              <p className="text-xs text-amber-500 font-bold">+{formatMinutes(otMins)} OT</p>
            )}
            {payBreakdown && (
              <p className="text-xs font-bold text-indigo-500 mt-0.5">
                {formatCurrency(payBreakdown.total_gross)}
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

