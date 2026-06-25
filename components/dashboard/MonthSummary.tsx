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
    { label: "Regular",  value: report.regular_minutes,  color: "bg-[#5B4DF2]", dotColor: "#5B4DF2" },
    { label: "Overtime", value: report.overtime_minutes, color: "bg-[#FF9E7D]", dotColor: "#FF9E7D" },
    { label: "Weekend",  value: report.weekend_minutes,  color: "bg-[#8B5CF6]", dotColor: "#8B5CF6" },
  ];

  return (
    <div className="flex flex-col gap-3">
      {/* Section header */}
      <div className="flex items-center justify-between px-1">
        <h2
          className="text-xs font-bold uppercase"
          style={{ color: "var(--au-faint)", letterSpacing: "0.16em" }}
        >
          {monthName} Summary
        </h2>
        <span className="text-xs font-medium" style={{ color: "var(--au-faint)" }}>
          {report.shift_count} shift{report.shift_count !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Distribution card */}
      <div className="rounded-3xl bg-white border border-white/80 au-card p-4 animate-fade-in-up stagger-1">
        <div className="flex items-start justify-between mb-3">
          <div>
            <p className="text-xs font-bold uppercase mb-0.5" style={{ color: "var(--au-faint)", letterSpacing: "0.14em" }}>
              Hours Distribution
            </p>
            <p className="text-2xl font-extrabold tracking-tight" style={{ fontFamily: "var(--au-display)", color: "var(--au-ink)" }}>
              {formatHoursDecimal(report.total_minutes, 1)}
              <span className="text-base font-semibold ml-1" style={{ color: "var(--au-faint)" }}>h total</span>
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
