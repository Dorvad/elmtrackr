"use client";

export const dynamic = "force-dynamic";

import Link from "next/link";
import { useActiveShift } from "@/hooks/useActiveShift";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useToast } from "@/components/ui/Toast";
import { ClockWidget } from "@/components/dashboard/ClockWidget";
import { MonthSummary } from "@/components/dashboard/MonthSummary";
import { ShiftRow } from "@/components/shifts/ShiftRow";
import { PageSpinner } from "@/components/ui/Spinner";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { BottomNav } from "@/components/layout/BottomNav";
import { createClient } from "@/lib/supabase/client";

export default function DashboardPage() {
  const { activeShift, loading: clockLoading, error: clockError, clockIn, clockOut, refresh: refreshActive } = useActiveShift();
  const { shifts, loading: shiftsLoading, error: shiftsError, refresh: refreshShifts } = useShifts();
  const { settings, loading: settingsLoading } = useSettings();
  const { toast } = useToast();
  const supabase = createClient();

  const loading = clockLoading || shiftsLoading || settingsLoading;
  const recentShifts = shifts.slice(0, 5);

  async function handleClockIn() {
    try {
      await clockIn();
      await refreshShifts();
      toast("Clocked in", "success");
    } catch {
      toast("Failed to clock in", "error");
    }
  }

  async function handleClockOut() {
    try {
      await clockOut();
      await refreshShifts();
      toast("Clocked out", "success");
    } catch {
      toast("Failed to clock out", "error");
    }
  }

  async function handleSignOut() {
    await supabase.auth.signOut();
    window.location.href = "/auth/login";
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-24">
      {/* Header */}
      <div className="bg-white border-b border-gray-100 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-xl">⏱</span>
          <span className="font-bold text-gray-900">ElmTrackr</span>
        </div>
        <button
          onClick={handleSignOut}
          className="text-sm text-gray-400 hover:text-gray-600 transition-colors"
        >
          Sign out
        </button>
      </div>

      <div className="max-w-md mx-auto px-4 pt-4 flex flex-col gap-6">
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
          loading={clockLoading}
        />

        {/* Month summary */}
        {settings && !shiftsLoading && (
          <MonthSummary shifts={shifts} settings={settings} />
        )}
        {(shiftsLoading || settingsLoading) && !shifts.length && (
          <PageSpinner />
        )}

        {/* Recent shifts */}
        {!shiftsLoading && shifts.length > 0 && settings && (
          <div>
            <div className="flex items-center justify-between px-1 mb-2">
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide">
                Recent Shifts
              </h2>
              <Link
                href="/shifts"
                className="text-sm text-blue-600 font-medium hover:underline"
              >
                View all
              </Link>
            </div>
            <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              {recentShifts.map((shift) => (
                <ShiftRow key={shift.id} shift={shift} settings={settings} />
              ))}
            </div>
          </div>
        )}

        {/* Add shift manually */}
        <Link
          href="/shifts/new"
          className="flex items-center justify-center gap-2 rounded-2xl border-2 border-dashed border-gray-200 py-4 text-sm font-medium text-gray-400 hover:border-blue-300 hover:text-blue-500 transition-colors"
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          Add shift manually
        </Link>
      </div>

      <BottomNav />
    </div>
  );
}
