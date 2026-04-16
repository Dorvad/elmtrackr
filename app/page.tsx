"use client";

export const dynamic = "force-dynamic";

import { useState } from "react";
import Link from "next/link";
import { useActiveShift } from "@/hooks/useActiveShift";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useProfile } from "@/hooks/useProfile";
import { useToast } from "@/components/ui/Toast";
import { ClockWidget } from "@/components/dashboard/ClockWidget";
import { EditStartTimeModal } from "@/components/dashboard/EditStartTimeModal";
import { MonthSummary } from "@/components/dashboard/MonthSummary";
import { ShiftRow } from "@/components/shifts/ShiftRow";
import { PageSpinner } from "@/components/ui/Spinner";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { BottomNav } from "@/components/layout/BottomNav";
import { createClient } from "@/lib/supabase/client";
import { sumMonthlyPay, formatCurrency } from "@/lib/shifts/payroll";
import { filterShiftsByMonth } from "@/lib/shifts/aggregation";

export default function DashboardPage() {
  const {
    activeShift,
    loading: clockLoading,
    error: clockError,
    clockIn,
    clockOut,
    updateStartTime,
    refresh: refreshActive,
  } = useActiveShift();
  const {
    shifts,
    loading: shiftsLoading,
    error: shiftsError,
    refresh: refreshShifts,
  } = useShifts();
  const { settings, loading: settingsLoading } = useSettings();
  const { profile } = useProfile();
  const { toast } = useToast();
  const supabase = createClient();

  const [showEditStartModal, setShowEditStartModal] = useState(false);

  const recentShifts = shifts.slice(0, 5);

  async function handleClockIn() {
    try {
      await clockIn();
      await refreshShifts();
      toast("Shift started", "success");
    } catch {
      toast("Failed to clock in", "error");
    }
  }

  async function handleClockOut() {
    try {
      await clockOut();
      await refreshShifts();
      toast("Shift ended", "success");
    } catch {
      toast("Failed to clock out", "error");
    }
  }

  async function handleEditStartTime(newStartIso: string) {
    await updateStartTime(newStartIso);
    await refreshShifts();
    toast("Start time updated", "success");
  }

  async function handleSignOut() {
    await supabase.auth.signOut();
    window.location.href = "/auth/login";
  }

  const now = new Date();
  const greetingBase =
    now.getHours() < 12 ? "Good morning" :
    now.getHours() < 18 ? "Good afternoon" : "Good evening";
  const firstName = profile?.full_name?.trim().split(/\s+/)[0];
  const greeting = firstName ? `${greetingBase}, ${firstName}` : greetingBase;

  const thisMonthShifts = settings
    ? filterShiftsByMonth(shifts, now.getUTCFullYear(), now.getUTCMonth() + 1)
    : [];
  const monthPay = settings?.hourly_rate
    ? sumMonthlyPay(thisMonthShifts, settings)
    : null;

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
      {/* Header */}
      <div className="px-4 pt-12 pb-4 flex items-center justify-between animate-fade-in">
        <div>
          <p className="text-xs font-semibold text-gray-400 uppercase tracking-widest">
            {greeting}
          </p>
          <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight mt-0.5">
            ElmTrackr
          </h1>
        </div>
        <button
          onClick={handleSignOut}
          className="h-9 w-9 rounded-2xl bg-white border border-gray-100 shadow-sm flex items-center justify-center text-gray-400 hover:text-gray-600 hover:border-gray-200 transition-all"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
        </button>
      </div>

      <div className="max-w-md mx-auto px-4 flex flex-col gap-5">
        {(clockError || shiftsError) && (
          <ErrorMessage
            message={clockError ?? shiftsError ?? "Unknown error"}
            onRetry={() => { refreshActive(); refreshShifts(); }}
          />
        )}

        {/* Clock widget */}
        <ClockWidget
          activeShift={activeShift}
          onClockIn={handleClockIn}
          onClockOut={handleClockOut}
          onEditStartTime={activeShift ? () => setShowEditStartModal(true) : undefined}
          loading={clockLoading}
          dailyThresholdMinutes={settings?.daily_overtime_threshold_minutes ?? 480}
        />

        {/* Loading skeleton */}
        {(shiftsLoading || settingsLoading) && !shifts.length && (
          <PageSpinner />
        )}

        {/* Month summary */}
        {settings && !shiftsLoading && (
          <MonthSummary shifts={shifts} settings={settings} />
        )}

        {/* Monthly gross pay widget */}
        {monthPay && monthPay.total_gross > 0 && (
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-3">
            <p className="text-xs font-bold text-gray-400 uppercase tracking-widest mb-2">
              This Month · Gross Pay
            </p>
            <div className="flex items-end gap-3">
              <p className="text-3xl font-extrabold text-indigo-600 tracking-tight">
                {formatCurrency(monthPay.total_gross)}
              </p>
              <p className="text-xs text-gray-400 mb-1 font-medium">before tax</p>
            </div>
            {(monthPay.overtime_gross > 0 || monthPay.special_gross > 0) && (
              <div className="flex gap-3 mt-2">
                {monthPay.regular_gross > 0 && (
                  <div>
                    <p className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">Regular</p>
                    <p className="text-xs font-bold text-gray-700">{formatCurrency(monthPay.regular_gross)}</p>
                  </div>
                )}
                {monthPay.overtime_gross > 0 && (
                  <div>
                    <p className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">Overtime</p>
                    <p className="text-xs font-bold text-amber-600">{formatCurrency(monthPay.overtime_gross)}</p>
                  </div>
                )}
                {monthPay.special_gross > 0 && (
                  <div>
                    <p className="text-[10px] text-gray-400 font-semibold uppercase tracking-wide">Holiday</p>
                    <p className="text-xs font-bold text-violet-600">{formatCurrency(monthPay.special_gross)}</p>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* Recent shifts */}
        {!shiftsLoading && shifts.length > 0 && settings && (
          <div>
            <div className="flex items-center justify-between px-1 mb-2">
              <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest">
                Recent Shifts
              </h2>
              <Link
                href="/shifts"
                className="text-xs text-indigo-600 font-bold hover:text-indigo-700 transition-colors"
              >
                View all →
              </Link>
            </div>
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
              {recentShifts.map((shift, i) => (
                <ShiftRow
                  key={shift.id}
                  shift={shift}
                  settings={settings}
                  animationIndex={i}
                />
              ))}
            </div>
          </div>
        )}

        {/* Add shift manually */}
        <Link
          href="/shifts/new"
          className={[
            "flex items-center justify-center gap-2 rounded-2xl py-4",
            "border-2 border-dashed border-gray-200 hover:border-indigo-300",
            "text-sm font-semibold text-gray-400 hover:text-indigo-500",
            "transition-all duration-200 animate-fade-in-up stagger-5",
          ].join(" ")}
        >
          <span className="h-5 w-5 rounded-lg bg-gray-100 flex items-center justify-center text-gray-400 group-hover:bg-indigo-50">
            +
          </span>
          Add shift manually
        </Link>
      </div>

      <BottomNav />

      {/* Edit start time modal — rendered outside the scrollable container */}
      {showEditStartModal && activeShift && (
        <EditStartTimeModal
          activeShift={activeShift}
          settings={settings}
          onSave={handleEditStartTime}
          onClose={() => setShowEditStartModal(false)}
        />
      )}
    </div>
  );
}
