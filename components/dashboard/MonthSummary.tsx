import type { Shift, UserSettings } from "@/types";
import {
  filterShiftsByMonth,
  buildMonthlyReport,
} from "@/lib/shifts/aggregation";
import { formatHoursDecimal } from "@/lib/shifts/duration";
import { StatCard } from "@/components/ui/StatCard";
import { SegmentedBar, SegmentLegend } from "@/components/ui/SegmentedBar";

interface MonthSummaryProps {
  shifts: Shift[];
  settings: UserSettings;
}

export function MonthSummary({ shifts, settings }: MonthSummaryProps) {
  const now = new Date();
  const year = now.getUTCFullYear();
  const month = now.getUTCMonth() + 1;

  const monthShifts = filterShiftsByMonth(shifts, year, month);
  const report = buildMonthlyReport(year, month, monthShifts, settings);

  const monthName = now.toLocaleString("default", { month: "long" });

  const segments = [
    {
      label: "Regular",
      value: report.regular_minutes,
      color: "bg-indigo-500",
      dotColor: "#4f46e5",
    },
    {
      label: "Overtime",
      value: report.overtime_minutes,
      color: "bg-amber-400",
      dotColor: "#f59e0b",
    },
    {
      label: "Weekend",
      value: report.weekend_minutes,
      color: "bg-violet-500",
      dotColor: "#8b5cf6",
    },
  ];

  return (
    <div className="flex flex-col gap-3">
      {/* Section header */}
      <div className="flex items-center justify-between px-1">
        <h2 className="text-xs font-bold text-gray-400 uppercase tracking-widest">
          {monthName} Summary
        </h2>
        <span className="text-xs text-gray-400 font-medium">
          {report.shift_count} shift{report.shift_count !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Distribution card */}
      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-4 animate-fade-in-up stagger-1">
        <div className="flex items-start justify-between mb-3">
          <div>
            <p className="text-xs text-gray-400 font-semibold uppercase tracking-wide">
              Hours Distribution
            </p>
            <p className="text-2xl font-extrabold text-gray-900 mt-0.5 tracking-tight">
              {formatHoursDecimal(report.total_minutes, 1)}
              <span className="text-base font-semibold text-gray-400 ml-1">h total</span>
            </p>
          </div>
        </div>
        <SegmentedBar segments={segments} />
        <SegmentLegend segments={segments} />
      </div>

      {/* Stat grid */}
      <div className="grid grid-cols-2 gap-3">
        <StatCard
          label="Total"
          value={formatHoursDecimal(report.total_minutes, 1) + "h"}
          variant="primary"
          stagger={1}
        />
        <StatCard
          label="Regular"
          value={formatHoursDecimal(report.regular_minutes, 1) + "h"}
          variant="default"
          stagger={2}
        />
        <StatCard
          label="Overtime"
          value={formatHoursDecimal(report.overtime_minutes, 1) + "h"}
          variant="overtime"
          stagger={3}
        />
        <StatCard
          label="Weekend"
          value={formatHoursDecimal(report.weekend_minutes, 1) + "h"}
          variant="weekend"
          stagger={4}
        />
      </div>
    </div>
  );
}
