import type { Shift, UserSettings } from "@/types";
import {
  filterShiftsByMonth,
  buildMonthlyReport,
} from "@/lib/shifts/aggregation";
import { formatMinutes, formatHoursDecimal } from "@/lib/shifts/duration";
import { StatCard } from "@/components/ui/StatCard";

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

  return (
    <div>
      <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide px-1 mb-2">
        {monthName} Summary
      </h2>
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
    </div>
  );
}
