"use client";

export const dynamic = "force-dynamic";

import Link from "next/link";
import { useState } from "react";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { ShiftRow } from "@/components/shifts/ShiftRow";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { Button } from "@/components/ui/Button";
import { filterShiftsByMonth } from "@/lib/shifts/aggregation";
import { formatHoursDecimal } from "@/lib/shifts/duration";
import { netMinutes } from "@/lib/shifts/duration";

export default function ShiftsPage() {
  const { shifts, loading, error, refresh } = useShifts();
  const { settings } = useSettings();

  const now = new Date();
  const [selectedYear, setSelectedYear] = useState(now.getUTCFullYear());
  const [selectedMonth, setSelectedMonth] = useState(now.getUTCMonth() + 1);

  const filtered = filterShiftsByMonth(shifts, selectedYear, selectedMonth);
  const totalMins = filtered
    .filter((s) => s.end_time)
    .reduce((sum, s) => sum + (netMinutes(s) ?? 0), 0);

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

  const monthLabel = new Date(Date.UTC(selectedYear, selectedMonth - 1, 1))
    .toLocaleString("default", { month: "long", year: "numeric" });

  const isCurrentMonth =
    selectedYear === now.getUTCFullYear() && selectedMonth === now.getUTCMonth() + 1;

  return (
    <div className="min-h-screen pb-28" style={{ background: "var(--color-surface)" }}>
      {/* Header */}
      <div className="px-4 pt-12 pb-4 flex items-center justify-between animate-fade-in">
        <h1 className="text-2xl font-extrabold text-gray-900 tracking-tight">Shifts</h1>
        <Link href="/shifts/new">
          <Button size="sm">+ New</Button>
        </Link>
      </div>

      {/* Month picker */}
      <div className="mx-4 mb-4 bg-white rounded-2xl border border-gray-100 shadow-sm flex items-center justify-between px-3 py-2 animate-fade-in-up">
        <button
          onClick={prevMonth}
          className="h-8 w-8 rounded-xl flex items-center justify-center hover:bg-gray-100 transition-colors"
        >
          <svg className="h-4 w-4 text-gray-500" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div className="text-center">
          <p className="text-sm font-bold text-gray-800">{monthLabel}</p>
          {totalMins > 0 && (
            <p className="text-xs text-indigo-500 font-semibold mt-0.5">
              {formatHoursDecimal(totalMins, 1)}h · {filtered.filter((s) => s.end_time).length} shifts
            </p>
          )}
        </div>
        <button
          onClick={nextMonth}
          disabled={isCurrentMonth}
          className="h-8 w-8 rounded-xl flex items-center justify-center hover:bg-gray-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <svg className="h-4 w-4 text-gray-500" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      <div className="max-w-md mx-auto px-4">
        {error && <ErrorMessage message={error} onRetry={refresh} />}
        {loading && <PageSpinner />}
        {!loading && !error && filtered.length === 0 && (
          <EmptyState
            title="No shifts this month"
            description="Clock in from the home screen or add a shift manually."
            action={<Link href="/shifts/new"><Button size="sm">Add shift</Button></Link>}
          />
        )}
        {!loading && filtered.length > 0 && settings && (
          <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
            {filtered.map((shift, i) => (
              <ShiftRow
                key={shift.id}
                shift={shift}
                settings={settings}
                animationIndex={i}
              />
            ))}
          </div>
        )}
      </div>

      <BottomNav />
    </div>
  );
}
