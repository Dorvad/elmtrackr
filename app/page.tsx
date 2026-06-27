"use client";

export const dynamic = "force-dynamic";

import { useState } from "react";
import Link from "next/link";
import { useActiveShift } from "@/hooks/useActiveShift";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";
import { useProfile } from "@/hooks/useProfile";
import { useToast } from "@/components/ui/Toast";
import { ClockWidget } from "@/components/dashboard/ClockWidget";
import { EditStartTimeModal } from "@/components/dashboard/EditStartTimeModal";
import { FirstClockInCelebration } from "@/components/dashboard/FirstClockInCelebration";
import { MonthSummary } from "@/components/dashboard/MonthSummary";
import { ShiftRow } from "@/components/shifts/ShiftRow";
import { PageSpinner } from "@/components/ui/Spinner";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { BottomNav } from "@/components/layout/BottomNav";
import { createClient } from "@/lib/supabase/client";
import { sumMonthlyPay } from "@/lib/shifts/payroll";
import { formatCurrency } from "@/lib/compensation/currency";
import { COMPENSATION_ESTIMATE_NOTE } from "@/lib/compensation/constants";
import { filterShiftsByMonth } from "@/lib/shifts/aggregation";
import { countUnresolvedRefunds } from "@/lib/shifts/refund";
import { hasCelebratedFirstClockIn, markFirstClockInCelebrated } from "@/lib/firstClockIn";

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
  const { profiles, defaultProfile, loading: profilesLoading } = useCompensationProfiles();
  const { profile } = useProfile();
  const { toast } = useToast();
  const supabase = createClient();

  const [showEditStartModal, setShowEditStartModal] = useState(false);
  const [refundBannerDismissed, setRefundBannerDismissed] = useState(false);
  const [showFirstClockInCelebration, setShowFirstClockInCelebration] = useState(false);

  const recentShifts = shifts.slice(0, 5);
  const isFirstRun = !clockLoading && !shiftsLoading && shifts.length === 0 && !activeShift;

  async function handleClockIn() {
    const isFirstClockIn = shifts.length === 0 && !hasCelebratedFirstClockIn();
    try {
      await clockIn(defaultProfile?.id ?? settings?.default_compensation_profile_id);
      await refreshShifts();
      if (isFirstClockIn) {
        markFirstClockInCelebrated();
        setShowFirstClockInCelebration(true);
      } else {
        toast("Shift started", "success");
      }
    } catch {
      toast("Failed to clock in", "error");
    }
  }

  async function handleClockOut() {
    if (!settings) {
      toast("Settings not loaded yet", "error");
      return;
    }
    if (profilesLoading) {
      toast("Loading compensation rules…", "error");
      return;
    }
    try {
      await clockOut({ settings, profiles });
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
  const greeting = firstName ? `${greetingBase} · ${firstName.toUpperCase()}` : greetingBase.toUpperCase();

  const thisMonthShifts = settings
    ? filterShiftsByMonth(shifts, now.getUTCFullYear(), now.getUTCMonth() + 1)
    : [];
  const monthPay = settings
    ? sumMonthlyPay(thisMonthShifts, { settings, profiles })
    : null;
  const hasPayEstimate = monthPay && monthPay.total_gross > 0;

  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const isMonthEnd = now.getDate() >= daysInMonth - 4;
  const unresolvedRefunds = countUnresolvedRefunds(shifts);
  const showRefundBanner =
    isMonthEnd &&
    unresolvedRefunds > 0 &&
    !refundBannerDismissed &&
    (settings?.features_travel_refunds ?? false);

  const monthLabel = now.toLocaleString("default", { month: "long" }).toUpperCase();

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--au-bg)" }}>
      {/* Header */}
      <div className="px-5 pt-12 pb-4 flex items-center justify-between animate-fade-in">
        <div>
          <p
            className="text-xs font-bold uppercase tracking-widest"
            style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}
          >
            {greeting}
          </p>
          <h1
            className="text-3xl font-bold mt-1 flex items-center gap-2"
            style={{
              fontFamily: "var(--au-display)",
              color: "var(--au-ink)",
              letterSpacing: "-0.03em",
            }}
          >
            <span
              className="inline-flex items-center justify-center rounded-[7px] flex-shrink-0"
              style={{
                width: 22,
                height: 22,
                background: "var(--au-grad)",
                boxShadow: "0 6px 14px -6px rgba(91,77,242,0.7)",
              }}
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth={2.4} strokeLinecap="round" strokeLinejoin="round">
                <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
              </svg>
            </span>
            elmtrackr
          </h1>
        </div>
        <button
          onClick={handleSignOut}
          className="h-10 w-10 rounded-full flex items-center justify-center transition-all"
          style={{
            background: "white",
            boxShadow: "0 6px 16px -8px rgba(40,30,90,0.25)",
            color: "var(--au-ink-2)",
          }}
        >
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
            <path d="M9 21H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3M16 17l5-5-5-5M21 12H9" />
          </svg>
        </button>
      </div>

      <div className="max-w-md mx-auto px-4 flex flex-col gap-4">
        {(clockError || shiftsError) && (
          <ErrorMessage
            message={clockError ?? shiftsError ?? "Unknown error"}
            onRetry={() => { refreshActive(); refreshShifts(); }}
          />
        )}

        {/* Refund reminder banner */}
        {showRefundBanner && (
          <div
            className="rounded-3xl px-4 py-3 flex items-start gap-3 animate-fade-in-up border border-white/80 au-card"
            style={{ background: "var(--au-weekend-bg)" }}
          >
            <div className="flex-1">
              <p className="text-sm font-bold" style={{ color: "var(--au-plum)" }}>
                {unresolvedRefunds} travel refund{unresolvedRefunds > 1 ? "s" : ""} pending
              </p>
              <p className="text-xs mt-0.5" style={{ color: "var(--au-ink-2)" }}>
                Month end is near — don't forget to file your transport claims.
              </p>
              <Link
                href="/reports"
                className="inline-block mt-2 text-xs font-bold underline hover:opacity-80"
                style={{ color: "var(--au-plum)" }}
              >
                Review refunds →
              </Link>
            </div>
            <button
              type="button"
              onClick={() => setRefundBannerDismissed(true)}
              className="mt-0.5 flex-shrink-0 hover:opacity-60 transition-opacity"
              aria-label="Dismiss"
              style={{ color: "var(--au-plum)" }}
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        )}

        {isFirstRun && (
          <div
            className="rounded-3xl px-4 py-4 animate-fade-in-up border border-white/80 au-card"
            style={{ background: "var(--au-surface-sub)" }}
          >
            <p className="text-sm font-bold mb-1" style={{ color: "var(--au-ink)" }}>
              You&apos;re ready
            </p>
            <p className="text-sm leading-relaxed" style={{ color: "var(--au-ink-2)" }}>
              Clock in once. See hours, pay estimate, and overtime instantly.
            </p>
          </div>
        )}

        {/* Clock widget */}
        <ClockWidget
          activeShift={activeShift}
          onClockIn={handleClockIn}
          onClockOut={handleClockOut}
          onEditStartTime={activeShift ? () => setShowEditStartModal(true) : undefined}
          loading={clockLoading}
          dailyThresholdMinutes={settings?.daily_overtime_threshold_minutes ?? 480}
          clockStyle={settings?.clock_style ?? "classic"}
        />

        {/* Loading skeleton */}
        {(shiftsLoading || settingsLoading) && !shifts.length && <PageSpinner />}

        {/* Month summary */}
        {settings && !shiftsLoading && (
          <MonthSummary shifts={shifts} settings={settings} />
        )}

        {/* Monthly gross pay */}
        {hasPayEstimate && (
          <div className="rounded-3xl p-4 border border-white/80 au-card bg-white animate-fade-in-up stagger-3">
            <p
              className="text-xs font-bold uppercase mb-2"
              style={{ color: "var(--au-faint)", letterSpacing: "0.14em" }}
            >
              This Month · Estimated Gross
            </p>
            <div className="flex items-end gap-3 mb-1">
              <p
                className="text-3xl font-extrabold tracking-tight"
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
            </div>
            <p className="text-[10px] mb-3" style={{ color: "var(--au-faint)" }}>{COMPENSATION_ESTIMATE_NOTE}</p>
            {(monthPay!.overtime_gross > 0 || monthPay!.special_gross > 0) && (
              <div className="flex gap-2">
                {monthPay!.regular_gross > 0 && (
                  <div className="flex-1 rounded-[15px] p-2.5" style={{ background: "var(--au-surface-sub)" }}>
                    <p className="text-[9px] font-bold uppercase tracking-wide mb-0.5" style={{ color: "var(--au-faint)" }}>Regular</p>
                    <p className="text-sm font-bold" style={{ color: "var(--au-ink)" }}>{formatCurrency(monthPay!.regular_gross, monthPay!.currency_code)}</p>
                  </div>
                )}
                {monthPay!.overtime_gross > 0 && (
                  <div className="flex-1 rounded-[15px] p-2.5" style={{ background: "var(--au-overtime-bg)" }}>
                    <p className="text-[9px] font-bold uppercase tracking-wide mb-0.5" style={{ color: "var(--au-overtime-ink)" }}>Overtime</p>
                    <p className="text-sm font-bold" style={{ color: "var(--au-peach-deep)" }}>{formatCurrency(monthPay!.overtime_gross, monthPay!.currency_code)}</p>
                  </div>
                )}
                {monthPay!.special_gross > 0 && (
                  <div className="flex-1 rounded-[15px] p-2.5" style={{ background: "var(--au-weekend-bg)" }}>
                    <p className="text-[9px] font-bold uppercase tracking-wide mb-0.5" style={{ color: "var(--au-plum)" }}>Holiday</p>
                    <p className="text-sm font-bold" style={{ color: "var(--au-plum)" }}>{formatCurrency(monthPay!.special_gross, monthPay!.currency_code)}</p>
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
              <h2
                className="text-xs font-bold uppercase"
                style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}
              >
                Recent Shifts
              </h2>
              <Link
                href="/shifts"
                className="text-xs font-bold hover:opacity-70 transition-opacity"
                style={{ color: "var(--au-indigo)" }}
              >
                View all →
              </Link>
            </div>
            <div className="rounded-3xl overflow-hidden border border-white/80 au-card bg-white">
              {recentShifts.map((shift, i) => (
                <ShiftRow
                  key={shift.id}
                  shift={shift}
                  settings={settings}
                  profiles={profiles}
                  animationIndex={i}
                />
              ))}
            </div>
          </div>
        )}

        {/* Projects shortcut */}
        {settings?.features_paid_projects && (
          <Link
            href="/projects"
            className="flex items-center gap-3 rounded-3xl px-4 py-3.5 border border-white/80 au-card bg-white hover:au-card-glow transition-all animate-fade-in-up stagger-4"
          >
            <div
              className="h-9 w-9 rounded-xl flex items-center justify-center flex-shrink-0"
              style={{ background: "var(--au-weekend-bg)" }}
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24" style={{ color: "var(--au-plum)" }}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
              </svg>
            </div>
            <div className="flex-1">
              <p className="text-sm font-semibold" style={{ color: "var(--au-ink)" }}>Projects</p>
              <p className="text-xs" style={{ color: "var(--au-faint)" }}>Track shifts by project</p>
            </div>
            <svg className="h-4 w-4 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24" style={{ color: "var(--au-faint)" }}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </Link>
        )}

        {/* Add shift manually */}
        <Link
          href="/shifts/new"
          className="flex items-center justify-center gap-2 rounded-3xl py-4 transition-all duration-200 animate-fade-in-up stagger-5"
          style={{
            border: `1.5px dashed var(--au-hair)`,
            background: "rgba(255,255,255,0.5)",
            color: "var(--au-indigo)",
          }}
        >
          <span
            className="h-5 w-5 rounded-lg flex items-center justify-center text-xs font-bold"
            style={{ background: "var(--au-surface-sub)", color: "var(--au-indigo)" }}
          >
            +
          </span>
          <span className="text-sm font-semibold">Add shift manually</span>
        </Link>
      </div>

      <BottomNav />

      {showEditStartModal && activeShift && (
        <EditStartTimeModal
          activeShift={activeShift}
          settings={settings}
          profiles={profiles}
          onSave={handleEditStartTime}
          onClose={() => setShowEditStartModal(false)}
        />
      )}

      {showFirstClockInCelebration && (
        <FirstClockInCelebration onDismiss={() => setShowFirstClockInCelebration(false)} />
      )}
    </div>
  );
}
