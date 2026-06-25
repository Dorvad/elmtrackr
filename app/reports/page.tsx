"use client";

export const dynamic = "force-dynamic";

import { useState } from "react";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";
import { useToast } from "@/components/ui/Toast";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { StatCard } from "@/components/ui/StatCard";
import { Button } from "@/components/ui/Button";
import { SegmentedBar, SegmentLegend } from "@/components/ui/SegmentedBar";
import { QuickStats } from "@/components/reports/QuickStats";
import { WeeklyBreakdown } from "@/components/reports/WeeklyBreakdown";
import { InsightOfTheDay } from "@/components/reports/InsightOfTheDay";
import { RefundReview } from "@/components/reports/RefundReview";
import {
  filterShiftsByMonth,
  buildMonthlyReport,
} from "@/lib/shifts/aggregation";
import {
  buildMonthInsights,
  buildWeeklyBreakdown,
  getDailyInsights,
} from "@/lib/shifts/insights";
import {
  formatHoursDecimal,
  formatMinutes,
  netMinutes,
} from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";
import { isOvernightShift } from "@/lib/shifts/overnight";
import { generateMonthlyCSV, downloadCSV } from "@/lib/shifts/csv";
import {
  sumMonthlyPay,
  calculateShiftPay,
} from "@/lib/shifts/payroll";
import { resolveShiftCompensation } from "@/lib/compensation/profile";
import { formatCurrency } from "@/lib/compensation/currency";
import { COMPENSATION_DISCLAIMER, COMPENSATION_ESTIMATE_NOTE } from "@/lib/compensation/constants";
import type { Shift, UserSettings, CompensationProfile } from "@/types";

export default function ReportsPage() {
  const { shifts, loading: shiftsLoading, error } = useShifts();
  const { settings, loading: settingsLoading } = useSettings();
  const { profiles } = useCompensationProfiles();
  const { toast } = useToast();

  const now = new Date();
  const [selectedYear, setSelectedYear] = useState(now.getUTCFullYear());
  const [selectedMonth, setSelectedMonth] = useState(now.getUTCMonth() + 1);
  const [activeTab, setActiveTab] = useState<"hours" | "refunds">("hours");

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

  const prevYear = selectedMonth === 1 ? selectedYear - 1 : selectedYear;
  const prevMonth2 = selectedMonth === 1 ? 12 : selectedMonth - 1;
  const prevMonthLabel = new Date(Date.UTC(prevYear, prevMonth2 - 1, 1))
    .toLocaleString("default", { month: "long" });

  const loading = shiftsLoading || settingsLoading;

  const monthShifts = settings ? filterShiftsByMonth(shifts, selectedYear, selectedMonth) : [];
  const completedShifts = monthShifts.filter((s) => s.end_time !== null);
  const prevMonthShifts = settings
    ? filterShiftsByMonth(shifts, prevYear, prevMonth2).filter((s) => s.end_time !== null)
    : [];

  const report = settings && completedShifts.length > 0
    ? buildMonthlyReport(selectedYear, selectedMonth, monthShifts, settings)
    : null;

  const insights = settings && completedShifts.length > 0
    ? buildMonthInsights(completedShifts, settings, profiles)
    : null;

  const weeklyData = settings && completedShifts.length > 0
    ? buildWeeklyBreakdown(completedShifts, prevMonthShifts, settings, profiles)
    : null;

  const dailyInsights = settings && completedShifts.length > 0
    ? getDailyInsights(completedShifts, settings, report?.total_minutes ?? 0, profiles)
    : null;

  const segments = report
    ? [
        { label: "Regular",  value: report.regular_minutes,  color: "bg-[#5B4DF2]", dotColor: "#5B4DF2" },
        { label: "Overtime", value: report.overtime_minutes, color: "bg-[#FF9E7D]", dotColor: "#FF9E7D" },
        { label: "Weekend",  value: report.weekend_minutes,  color: "bg-[#8B5CF6]", dotColor: "#8B5CF6" },
      ]
    : [];

  const monthPay = settings && completedShifts.length > 0
    ? sumMonthlyPay(completedShifts, { settings, profiles })
    : null;
  const hasPayEstimate = monthPay && monthPay.total_gross > 0;

  function handleExportCSV() {
    if (!settings || monthShifts.length === 0) return;
    const csv = generateMonthlyCSV(monthShifts, settings, selectedYear, selectedMonth);
    const filename = `elmtrackr-${selectedYear}-${String(selectedMonth).padStart(2, "0")}.csv`;
    downloadCSV(csv, filename);
    toast("CSV exported", "success");
  }

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--au-bg)" }}>
      {/* Header */}
      <div className="px-5 pt-12 pb-4 animate-fade-in">
        <h1
          className="text-3xl font-bold tracking-tight"
          style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)", letterSpacing: "-0.02em" }}
        >
          Reports
        </h1>
      </div>

      {/* Tab switcher */}
      <div
        className="mx-4 mb-3 rounded-3xl flex p-1 gap-1 animate-fade-in-up border border-white/80 au-card bg-white"
      >
        <button
          onClick={() => setActiveTab("hours")}
          className="flex-1 rounded-2xl py-2 text-sm font-bold transition-all"
          style={
            activeTab === "hours"
              ? { background: "var(--au-grad)", color: "white", boxShadow: "0 6px 14px -6px rgba(91,77,242,0.5)" }
              : { background: "transparent", color: "var(--au-faint)" }
          }
        >
          Hours
        </button>
        {settings?.features_travel_refunds && (
          <button
            onClick={() => setActiveTab("refunds")}
            className="flex-1 rounded-2xl py-2 text-sm font-bold transition-all"
            style={
              activeTab === "refunds"
                ? { background: "var(--au-grad)", color: "white", boxShadow: "0 6px 14px -6px rgba(91,77,242,0.5)" }
                : { background: "transparent", color: "var(--au-faint)" }
            }
          >
            Travel Refunds
          </button>
        )}
      </div>

      {/* Month picker */}
      {activeTab === "hours" && (
        <div className="mx-4 mb-4 rounded-3xl flex items-center justify-between px-3 py-2.5 animate-fade-in-up border border-white/80 au-card bg-white">
          <button
            onClick={prevMonth}
            className="h-8 w-8 rounded-xl flex items-center justify-center hover:opacity-60 transition-opacity"
            style={{ background: "var(--au-surface-sub)" }}
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24" style={{ color: "var(--au-ink-2)" }}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <span className="text-sm font-bold" style={{ color: "var(--au-ink)" }}>{monthLabel}</span>
          <button
            onClick={nextMonth}
            disabled={isCurrentMonth}
            className="h-8 w-8 rounded-xl flex items-center justify-center hover:opacity-60 transition-opacity disabled:opacity-20 disabled:cursor-not-allowed"
            style={{ background: "var(--au-surface-sub)" }}
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24" style={{ color: "var(--au-ink-2)" }}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      )}

      <div className="max-w-md mx-auto px-4 flex flex-col gap-4">
        {error && <ErrorMessage message={error} />}
        {loading && <PageSpinner />}

        {!loading && activeTab === "refunds" && (
          <RefundReview shifts={shifts} settings={settings} profiles={profiles} />
        )}

        {!loading && activeTab === "hours" && !report && (
          <EmptyState title="No completed shifts" description="Complete some shifts to see your report." />
        )}

        {!loading && activeTab === "hours" && report && settings && (
          <>
            {/* Hours distribution */}
            <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up stagger-1">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="text-xs font-bold uppercase mb-1" style={{ color: "var(--au-faint)", letterSpacing: "0.14em" }}>
                    Hours Distribution
                  </p>
                  <p className="text-3xl font-extrabold tracking-tight" style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)", letterSpacing: "-0.02em" }}>
                    {formatHoursDecimal(report.total_minutes, 1)}
                    <span className="text-base font-semibold ml-1" style={{ color: "var(--au-faint)" }}>h</span>
                  </p>
                  <p className="text-xs mt-0.5" style={{ color: "var(--au-faint)" }}>
                    {report.shift_count} shift{report.shift_count !== 1 ? "s" : ""}
                  </p>
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

            {/* Summary stat grid */}
            <div className="grid grid-cols-2 gap-3">
              <StatCard label="Total"    value={formatHoursDecimal(report.total_minutes, 1) + "h"}    variant="primary"  stagger={1} />
              <StatCard label="Regular"  value={formatHoursDecimal(report.regular_minutes, 1) + "h"}  variant="default"  stagger={2} />
              <StatCard label="Overtime" value={formatHoursDecimal(report.overtime_minutes, 1) + "h"} variant="overtime" stagger={3} />
              <StatCard label="Weekend"  value={formatHoursDecimal(report.weekend_minutes, 1) + "h"}  variant="weekend"  stagger={4} />
            </div>

            {/* Estimated gross compensation */}
            {hasPayEstimate && (
              <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up">
                <p className="text-xs font-bold uppercase mb-2" style={{ color: "var(--au-faint)", letterSpacing: "0.14em" }}>
                  Estimated Gross Compensation
                </p>
                <p
                  className="text-3xl font-extrabold tracking-tight mb-1"
                  style={{
                    fontFamily: "var(--au-display)",
                    background: "var(--au-grad-text)",
                    WebkitBackgroundClip: "text",
                    backgroundClip: "text",
                    color: "transparent",
                  }}
                >
                  {formatCurrency(monthPay!.total_gross, monthPay!.currency_code)}
                </p>
                <p className="text-[10px] mb-3" style={{ color: "var(--au-faint)" }}>{COMPENSATION_ESTIMATE_NOTE}</p>
                <div className="grid grid-cols-3 gap-2">
                  <div className="rounded-2xl p-2.5 text-center" style={{ background: "var(--au-surface-sub)" }}>
                    <p className="text-[10px] font-bold uppercase tracking-wide mb-0.5" style={{ color: "var(--au-faint)" }}>Regular</p>
                    <p className="text-sm font-extrabold" style={{ color: "var(--au-indigo)" }}>{formatCurrency(monthPay!.regular_gross, monthPay!.currency_code)}</p>
                  </div>
                  <div className="rounded-2xl p-2.5 text-center" style={{ background: "var(--au-overtime-bg)" }}>
                    <p className="text-[10px] font-bold uppercase tracking-wide mb-0.5" style={{ color: "var(--au-overtime-ink)" }}>Overtime</p>
                    <p className="text-sm font-extrabold" style={{ color: "var(--au-peach-deep)" }}>{formatCurrency(monthPay!.overtime_gross, monthPay!.currency_code)}</p>
                  </div>
                  <div className="rounded-2xl p-2.5 text-center" style={{ background: "var(--au-weekend-bg)" }}>
                    <p className="text-[10px] font-bold uppercase tracking-wide mb-0.5" style={{ color: "var(--au-plum)" }}>Holiday</p>
                    <p className="text-sm font-extrabold" style={{ color: "var(--au-plum)" }}>{formatCurrency(monthPay!.special_gross, monthPay!.currency_code)}</p>
                  </div>
                </div>
                {monthPay!.night_gross > 0 && (
                  <div className="mt-2 rounded-2xl p-2.5 text-center" style={{ background: "var(--au-surface-sub)" }}>
                    <p className="text-[10px] font-bold uppercase tracking-wide mb-0.5" style={{ color: "var(--au-faint)" }}>Night</p>
                    <p className="text-sm font-extrabold" style={{ color: "var(--au-indigo)" }}>{formatCurrency(monthPay!.night_gross, monthPay!.currency_code)}</p>
                  </div>
                )}
                {monthPay!.deductions_gross > 0 && (
                  <div className="mt-3 pt-3 border-t" style={{ borderColor: "var(--au-hair)" }}>
                    <div className="flex justify-between text-xs mb-1">
                      <span style={{ color: "var(--au-faint)" }}>Est. deductions</span>
                      <span className="font-bold" style={{ color: "var(--au-ink)" }}>-{formatCurrency(monthPay!.deductions_gross, monthPay!.currency_code)}</span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="font-semibold" style={{ color: "var(--au-ink)" }}>Est. net</span>
                      <span className="font-extrabold" style={{ color: "var(--au-indigo)" }}>{formatCurrency(monthPay!.net_gross, monthPay!.currency_code)}</span>
                    </div>
                  </div>
                )}
                <p className="text-[10px] mt-3 leading-relaxed" style={{ color: "var(--au-faint)" }}>{COMPENSATION_DISCLAIMER}</p>
              </div>
            )}

            {/* Insights */}
            {settings?.features_insights && dailyInsights && (
              <InsightOfTheDay insights={dailyInsights} />
            )}

            {settings?.features_insights && insights && (
              <QuickStats insights={insights} currencyCode={monthPay?.currency_code} />
            )}

            {weeklyData && (
              <WeeklyBreakdown weeks={weeklyData} prevMonthLabel={prevMonthLabel} currencyCode={monthPay?.currency_code} />
            )}

            {/* Thresholds */}
            <div className="rounded-3xl bg-white border border-white/80 au-card px-4 py-3 flex items-center justify-center gap-4 text-xs font-medium animate-fade-in-up" style={{ color: "var(--au-faint)" }}>
              <span>
                Daily OT after{" "}
                <span className="font-semibold" style={{ color: "var(--au-ink)" }}>
                  {formatMinutes(settings.daily_overtime_threshold_minutes)}
                </span>
              </span>
              <span className="h-3 w-px" style={{ background: "var(--au-hair)" }} />
              <span>
                Weekly OT after{" "}
                <span className="font-semibold" style={{ color: "var(--au-ink)" }}>
                  {formatMinutes(settings.weekly_overtime_threshold_minutes)}
                </span>
              </span>
            </div>

            {/* Shift breakdown */}
            <div>
              <h2 className="text-xs font-bold uppercase px-1 mb-2" style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}>
                Shift Breakdown
              </h2>
              <div className="rounded-3xl overflow-hidden bg-white border border-white/80 au-card">
                {completedShifts
                  .slice()
                  .sort((a, b) => new Date(a.start_time).getTime() - new Date(b.start_time).getTime())
                  .map((shift, i) => (
                    <ShiftReportRow key={shift.id} shift={shift} settings={settings} profiles={profiles} index={i} />
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

// ── Shift report row ─────────────────────────────────────────────────────────

function ShiftReportRow({
  shift,
  settings,
  profiles,
  index,
}: {
  shift: Shift;
  settings: UserSettings;
  profiles: CompensationProfile[];
  index: number;
}) {
  const net = netMinutes(shift) ?? 0;
  const resolved = resolveShiftCompensation(
    shift,
    settings,
    new Map(profiles.map((p) => [p.id, p]))
  );
  const threshold = resolved.rules_json.regular.dailyStandardMinutes;
  const weekendDays = resolved.rules_json.regular.weekendDays;
  const otMins = Math.max(0, net - threshold);
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const isWeekend =
    resolved.rules_json.weekend.enabled &&
    isWeekendDate(dateStr, weekendDays);
  const isOvernight = isOvernightShift(shift);
  const isSpecial = shift.is_special_day || isWeekend;

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  const stripeColor = isSpecial ? "var(--au-plum)" : isOvernight ? "var(--au-indigo)" : "var(--au-indigo)";
  const stripeOpacity = isSpecial ? 1 : isOvernight ? 0.8 : 0.35;

  const payBreakdown = calculateShiftPay(shift, { settings, profiles });

  return (
    <div
      className="flex items-center gap-0 border-b last:border-0 animate-fade-in-up"
      style={{ borderBottomColor: "var(--au-hair)", animationDelay: `${index * 0.04}s` }}
    >
      <div className="w-1 self-stretch flex-shrink-0 rounded-l-sm" style={{ background: stripeColor, opacity: stripeOpacity }} />
      <div className="flex-1 px-3 py-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-sm font-bold" style={{ color: "var(--au-ink)" }}>
              {new Date(shift.start_time).toLocaleDateString([], { weekday: "short", month: "short", day: "numeric" })}
            </p>
            <p className="text-xs mt-0.5" style={{ color: "var(--au-faint)" }}>
              {formatTime(shift.start_time)}
              {shift.end_time ? ` — ${formatTime(shift.end_time)}` : ""}
              {shift.break_minutes > 0 ? ` · ${shift.break_minutes}m break` : ""}
            </p>
            <div className="flex gap-1 mt-1 flex-wrap">
              {shift.is_special_day && (
                <span className="rounded-full text-[10px] font-bold px-1.5 py-0.5" style={{ background: "var(--au-weekend-bg)", color: "var(--au-plum)" }}>Holiday</span>
              )}
              {isWeekend && !shift.is_special_day && (
                <span className="rounded-full text-[10px] font-bold px-1.5 py-0.5" style={{ background: "var(--au-weekend-bg)", color: "var(--au-plum)" }}>Weekend</span>
              )}
              {isOvernight && (
                <span className="rounded-full text-[10px] font-bold px-1.5 py-0.5" style={{ background: "var(--au-surface-sub)", color: "var(--au-indigo)" }}>Overnight</span>
              )}
            </div>
          </div>
          <div className="text-right flex-shrink-0">
            <p className="text-sm font-bold" style={{ color: "var(--au-ink)" }}>{formatMinutes(net)}</p>
            {otMins > 0 && !isSpecial && (
              <p className="text-xs font-bold" style={{ color: "var(--au-peach-deep)" }}>+{formatMinutes(otMins)} OT</p>
            )}
            {payBreakdown && (
              <p className="text-xs font-bold mt-0.5" style={{ color: "var(--au-indigo)" }}>{formatCurrency(payBreakdown.total_gross, payBreakdown.currency_code)}</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
