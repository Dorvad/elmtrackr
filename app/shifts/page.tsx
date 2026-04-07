"use client";

export const dynamic = "force-dynamic";

import Link from "next/link";
import { useState } from "react";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { ShiftRow } from "@/components/shifts/ShiftRow";
import { BottomNav } from "@/components/layout/BottomNav";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageSpinner } from "@/components/ui/Spinner";
import { EmptyState } from "@/components/ui/EmptyState";
import { ErrorMessage } from "@/components/ui/ErrorMessage";
import { Button } from "@/components/ui/Button";
import { filterShiftsByMonth } from "@/lib/shifts/aggregation";

export default function ShiftsPage() {
  const { shifts, loading, error, refresh } = useShifts();
  const { settings } = useSettings();

  const now = new Date();
  const [selectedYear, setSelectedYear] = useState(now.getUTCFullYear());
  const [selectedMonth, setSelectedMonth] = useState(now.getUTCMonth() + 1);

  const filtered = filterShiftsByMonth(shifts, selectedYear, selectedMonth);

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

  const monthLabel = new Date(Date.UTC(selectedYear, selectedMonth - 1, 1))
    .toLocaleString("default", { month: "long", year: "numeric" });

  const isCurrentMonth =
    selectedYear === now.getUTCFullYear() && selectedMonth === now.getUTCMonth() + 1;

  return (
    <div className="min-h-screen bg-gray-50 pb-24">
      <PageHeader
        title="Shifts"
        action={
          <Link href="/shifts/new">
            <Button size="sm">+ New</Button>
          </Link>
        }
      />

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

      <div className="max-w-md mx-auto px-4 pt-4">
        {error && <ErrorMessage message={error} onRetry={refresh} />}
        {loading && <PageSpinner />}
        {!loading && !error && filtered.length === 0 && (
          <EmptyState
            title="No shifts this month"
            description="Clock in from the home screen or add a shift manually."
            action={
              <Link href="/shifts/new">
                <Button size="sm">Add shift</Button>
              </Link>
            }
          />
        )}
        {!loading && filtered.length > 0 && settings && (
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            {filtered.map((shift) => (
              <ShiftRow key={shift.id} shift={shift} settings={settings} />
            ))}
          </div>
        )}
      </div>

      <BottomNav />
    </div>
  );
}
