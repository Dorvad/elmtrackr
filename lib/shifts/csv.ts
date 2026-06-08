import type { Shift, UserSettings } from "@/types";
import { formatHoursDecimal } from "./duration";
import { buildShiftBreakdown } from "./aggregation";
import { isOvernightShift } from "./overnight";

function escapeCsv(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return "";
  const str = String(value);
  // Wrap in quotes if contains comma, quote, or newline
  if (/[",\n\r]/.test(str)) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

function row(cols: (string | number | null | undefined)[]): string {
  return cols.map(escapeCsv).join(",");
}

function formatDatetime(iso: string | null): string {
  if (!iso) return "";
  return new Date(iso).toISOString().replace("T", " ").slice(0, 16);
}

/**
 * Generate CSV content for a list of shifts in a given month.
 * Each row is one shift. Overnight shifts get an extra column flagging them.
 */
export function generateMonthlyCSV(
  shifts: Shift[],
  settings: UserSettings,
  year: number,
  month: number
): string {
  const completedShifts = shifts
    .filter((s) => s.end_time !== null)
    .sort(
      (a, b) =>
        new Date(a.start_time).getTime() - new Date(b.start_time).getTime()
    );

  const headers = [
    "Date",
    "Start Time",
    "End Time",
    "Break (min)",
    "Total Hours",
    "Regular Hours",
    "Overtime Hours",
    "Weekend Hours",
    "Overnight",
    "Notes",
  ];

  const lines: string[] = [row(headers)];

  const allBreakdowns = completedShifts.map((s) =>
    buildShiftBreakdown(s, settings)
  );

  for (let i = 0; i < completedShifts.length; i++) {
    const shift = completedShifts[i];
    const breakdown = allBreakdowns[i];

    lines.push(
      row([
        new Date(shift.start_time).toISOString().slice(0, 10),
        formatDatetime(shift.start_time),
        formatDatetime(shift.end_time),
        shift.break_minutes,
        formatHoursDecimal(breakdown.total_minutes),
        formatHoursDecimal(breakdown.regular_minutes),
        formatHoursDecimal(breakdown.overtime_minutes),
        formatHoursDecimal(breakdown.weekend_minutes),
        isOvernightShift(shift) ? "Yes" : "No",
        shift.notes ?? "",
      ])
    );
  }

  // Summary row — regular + overtime + weekend === total (non-overlapping)
  const totalNet = allBreakdowns.reduce((sum, b) => sum + b.total_minutes, 0);
  const totalRegular = allBreakdowns.reduce((sum, b) => sum + b.regular_minutes, 0);
  const totalOt = allBreakdowns.reduce((sum, b) => sum + b.overtime_minutes, 0);
  const totalWeekend = allBreakdowns.reduce((sum, b) => sum + b.weekend_minutes, 0);

  lines.push(""); // blank separator
  lines.push(
    row([
      `TOTAL — ${year}-${String(month).padStart(2, "0")}`,
      "",
      "",
      "",
      formatHoursDecimal(totalNet),
      formatHoursDecimal(totalRegular),
      formatHoursDecimal(totalOt),
      formatHoursDecimal(totalWeekend),
      "",
      `${completedShifts.length} shifts`,
    ])
  );

  return lines.join("\n");
}

/**
 * Trigger a browser download of the CSV string.
 * Only call this from client-side code.
 */
export function downloadCSV(content: string, filename: string): void {
  const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
