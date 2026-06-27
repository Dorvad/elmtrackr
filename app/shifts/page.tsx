"use client";

export const dynamic = "force-dynamic";

import Link from "next/link";
import { useState } from "react";
import { useShifts } from "@/hooks/useShifts";
import { useSettings } from "@/hooks/useSettings";
import { useCompensationProfiles } from "@/hooks/useCompensationProfiles";
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
  const { profiles } = useCompensationProfiles();

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
    <div className="min-h-screen pb-28" style={{ background: "var(--au-bg)" }}>
      {/* Header */}
      <div className="px-5 pt-12 pb-4 flex items-center justify-between animate-fade-in">
        <h1
          className="text-3xl font-bold tracking-tight"
          style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)", letterSpacing: "-0.02em" }}
        >
          All shifts
        </h1>
        <Link href="/shifts/new">
          <Button size="sm">+ New</Button>
        </Link>
      </div>

      {/* Month picker */}
      <div
        className="mx-4 mb-4 rounded-3xl flex items-center justify-between px-3 py-2.5 animate-fade-in-up border border-white/80 au-card bg-white"
      >
        <button
          onClick={prevMonth}
          aria-label="Previous month"
          className="h-12 w-12 rounded-xl flex items-center justify-center transition-colors hover:opacity-60"
          style={{ background: "var(--au-surface-sub)" }}
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24" style={{ color: "var(--au-ink-2)" }}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <div className="text-center">
          <p className="text-sm font-bold" style={{ color: "var(--au-ink)" }}>{monthLabel}</p>
          {totalMins > 0 && (
            <p className="text-xs font-semibold mt-0.5" style={{ color: "var(--au-indigo)" }}>
              {formatHoursDecimal(totalMins, 1)}h · {filtered.filter((s) => s.end_time).length} shifts
            </p>
          )}
        </div>
        <button
          onClick={nextMonth}
          aria-label="Next month"
          disabled={isCurrentMonth}
          className="h-12 w-12 rounded-xl flex items-center justify-center transition-colors hover:opacity-60 disabled:opacity-20 disabled:cursor-not-allowed"
          style={{ background: "var(--au-surface-sub)" }}
        >
          <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2.5} viewBox="0 0 24 24" style={{ color: "var(--au-ink-2)" }}>
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
          <div className="rounded-3xl overflow-hidden border border-white/80 au-card bg-white">
            {filtered.map((shift, i) => (
              <ShiftRow
                key={shift.id}
                shift={shift}
                settings={settings}
                profiles={profiles}
                animationIndex={i}
              />
            ))}
          </div>
        )}

        {/* Add manually button */}
        {!loading && (
          <div className="mt-4">
            <Link
              href="/shifts/new"
              className="flex items-center justify-center gap-2 rounded-3xl py-4 transition-all duration-200"
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
        )}
      </div>

      <BottomNav />
    </div>
  );
}
