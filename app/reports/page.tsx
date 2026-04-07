"use client";

export const dynamic = "force-dynamic";

import { useState } from "react";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { StatCard } from "@/components/ui/StatCard";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { filterShiftsByMonth, buildMonthlyReport } from "@/lib/shifts/aggregation";
import { formatHoursDecimal, formatMinutes } from "@/lib/shifts/duration";
import { netMinutes } from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";
import { isOvernightShift } from "@/lib/shifts/overnight";
import { generateMonthlyCSV, downloadCSV } from "@/lib/shifts/csv";
import type { Shift, UserSettings } from "@/types";

export default function ReportsPage() {
  const { shifts, loading: shiftsLoading, error } = useShifts();
  const { settings, loading: settingsLoading } = useSettings();
  const { toast } = useToast();

  const now = new Date();
  const [selectedYear, setSelectedYear] = useState(now.getUTCFullYear());
  const [selectedMonth, setSelectedMonth] = useState(now.getUTCMonth() + 1);

  function prevMonth() {
    if (selectedMonth === 1) { setSelectedYear(y => y - 1); setSelectedMonth(12); }
    else setSelectedMonth(m => m - 1);
  }
  function nextMonth() {
    const isCurrentOrFuture = selectedYear > now.getUTCFullYear() ||
      (selectedYear === now.getUTCFullYear() && selectedMonth >= now.getUTCMonth() + 1);
    if (isCurrentOrFuture) return;
    if (selectedMonth === 12) { setSelectedYear(y => y + 1); setSelectedMonth(1); }
    else setSelectedMonth(m => m + 1);
  }

  const isCurrentMonth =
    selectedYear === now.getUTCFullYear() && selectedMonth === now.getUTCMonth() + 1;

  const monthLabel = new Date(Date.UTC(selectedYear, selectedMonth - 1, 1))
    .toLocaleString("default", { month: "long", year: "numeric" });

  const loading = shiftsLoading || settingsLoading;

  const monthShifts = settings
    ? filterShiftsByMonth(shifts, selectedYear, selectedMonth)
    : [];

  const report =
    settings && monthShifts.length > 0
      ? buildMonthlyReport(selectedYear, selectedMonth, monthShifts, settings)
      : null;

  function handleExportCSV() {
    if (!settings || monthShifts.length === 0) return;
    const csv = generateMonthlyCSV(monthShifts, settings, selectedYear, selectedMonth);
    const filename = `elmtrackr-${selectedYear}-${String(selectedMonth).padStart(2, "0")}.csv`;
    downloadCSV(csv, filename);
    toast("CSV exported", "success");
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-24">
      <PageHeader title="Monthly Report" />

      {/* Month picker */}
      <div className="flex items-center justify-between px-4 py-2 bg-white border-b border-gray-100">
        <button
          onClick={prevMonth}
          className="p-2 rounded-lg hover:bg-gray-100 transition-colors"
        >
          <svg className="h-4 w-4 text-gray-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <span className="text-sm font-semibold text-gray-700">{monthLabel}</span>
        <button
          onClick={nextMonth}
          disabled={isCurrentMonth}
          className="p-2 rounded-lg hover:bg-gray-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <svg className="h-4 w-4 text-gray-600" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      <div className="max-w-md mx-auto px-4 pt-4 flex flex-col gap-4">
        {error && <ErrorMessage message={error} />}
        {loading && <PageSpinner />}

        {!loading && !report && (
          <EmptyState
            title="No completed shifts"
            description="Complete some shifts this month to see your report."
          />
        )}

        {!loading && report && settings && (
          <>
            {/* Summary stats */}
            <div className="grid grid-cols-2 gap-3">
              <StatCard
                label="Total Hours"
                value={formatHoursDecimal(report.total_minutes, 1) + "h"}
                sub={`${report.shift_count} shift${report.shift_count !== 1 ? "s" : ""}`}
                accent
              />
              <StatCard
                label="Regular"
                value={formatHoursDecimal(report.regular_minutes, 1) + "h"}
              />
              <StatCard
                label="Overtime"
                value={formatHoursDecimal(report.overtime_minutes, 1) + "h"}
              />
              <StatCard
                label="Weekend"
                value={formatHoursDecimal(report.weekend_minutes, 1) + "h"}
              />
            </div>

            {/* Thresholds info */}
            <Card padding="sm">
              <p className="text-xs text-gray-500 text-center">
                Daily OT after {formatMinutes(settings.daily_overtime_threshold_minutes)} · Weekly OT after {formatMinutes(settings.weekly_overtime_threshold_minutes)}
              </p>
            </Card>

            {/* Export button */}
            <Button
              variant="secondary"
              fullWidth
              onClick={handleExportCSV}
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              Export CSV
            </Button>

            {/* Shift breakdown table */}
            <div>
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide px-1 mb-2">
                Shift Breakdown
              </h2>
              <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
                {monthShifts
                  .filter((s) => s.end_time !== null)
                  .sort(
                    (a, b) =>
                      new Date(a.start_time).getTime() -
                      new Date(b.start_time).getTime()
                  )
                  .map((shift) => (
                    <ShiftReportRow
                      key={shift.id}
                      shift={shift}
                      settings={settings}
                    />
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

function ShiftReportRow({
  shift,
  settings,
}: {
  shift: Shift;
  settings: UserSettings;
}) {
  const net = netMinutes(shift) ?? 0;
  const otMins = Math.max(0, net - settings.daily_overtime_threshold_minutes);
  const regularMins = Math.max(0, net - otMins);
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const isWeekend = isWeekendDate(dateStr, settings.weekend_days);
  const isOvernight = isOvernightShift(shift);

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  return (
    <div className="px-4 py-3 border-b border-gray-100 last:border-0">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="text-sm font-medium text-gray-800">
            {new Date(shift.start_time).toLocaleDateString([], {
              weekday: "short",
              month: "short",
              day: "numeric",
            })}
          </p>
          <p className="text-xs text-gray-400 mt-0.5">
            {formatTime(shift.start_time)}
            {shift.end_time ? ` – ${formatTime(shift.end_time)}` : ""}
            {shift.break_minutes > 0 ? ` · ${shift.break_minutes}m break` : ""}
          </p>
          <div className="flex gap-1 mt-1 flex-wrap">
            {isWeekend && (
              <span className="rounded-full bg-orange-100 text-orange-700 text-xs px-1.5 py-0.5 font-medium">
                Weekend
              </span>
            )}
            {isOvernight && (
              <span className="rounded-full bg-purple-100 text-purple-700 text-xs px-1.5 py-0.5 font-medium">
                Overnight
              </span>
            )}
          </div>
        </div>
        <div className="text-right flex-shrink-0">
          <p className="text-sm font-semibold text-gray-800">
            {formatMinutes(net)}
          </p>
          {otMins > 0 && (
            <p className="text-xs text-amber-600 font-medium">
              +{formatMinutes(otMins)} OT
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
