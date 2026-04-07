import type {
  Shift,
  MonthlyReport,
  WeeklyTotals,
  ShiftBreakdown,
  UserSettings,
} from "@/types";
import { netMinutes } from "./duration";
import { splitShiftByDay } from "./overnight";
import { annotateWeekendSegments, totalWeekendMinutes } from "./weekend";
import { combinedOvertimeMinutes } from "./overtime";

/**
 * Group shifts into ISO weeks.
 * ISO week: Monday–Sunday. We use a simple Monday-based bucketing.
 */
export function groupByWeek(shifts: Shift[]): WeeklyTotals[] {
  const map = new Map<string, Shift[]>();

  for (const shift of shifts) {
    const weekStart = getMonday(new Date(shift.start_time));
    const key = weekStart.toISOString().slice(0, 10);
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(shift);
  }

  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([week_start, wShifts]) => ({
      week_start,
      total_minutes: wShifts.reduce((s, sh) => s + (netMinutes(sh) ?? 0), 0),
      shifts: wShifts,
    }));
}

function getMonday(date: Date): Date {
  const d = new Date(date);
  const day = d.getUTCDay(); // 0=Sun
  const diff = day === 0 ? -6 : 1 - day; // roll back to Monday
  d.setUTCDate(d.getUTCDate() + diff);
  d.setUTCHours(0, 0, 0, 0);
  return d;
}

/**
 * Build a full ShiftBreakdown for a single shift (handles overnight splits,
 * weekend annotation).
 */
export function buildShiftBreakdown(
  shift: Shift,
  settings: Pick<
    UserSettings,
    | "weekend_days"
    | "daily_overtime_threshold_minutes"
    | "weekly_overtime_threshold_minutes"
  >
): ShiftBreakdown {
  const net = netMinutes(shift) ?? 0;
  const rawSegments = splitShiftByDay(shift);
  const segments = annotateWeekendSegments(rawSegments, settings.weekend_days);
  const weekendMins = totalWeekendMinutes(segments);
  const otMins = Math.max(
    0,
    net - settings.daily_overtime_threshold_minutes
  );
  const regularMins = Math.max(0, net - otMins);

  return {
    total_minutes: net,
    regular_minutes: regularMins,
    overtime_minutes: otMins,
    weekend_minutes: weekendMins,
    segments,
  };
}

/**
 * Aggregate all shifts for a given month into a MonthlyReport.
 * `shifts` should be filtered to only include shifts that START in the target month.
 */
export function buildMonthlyReport(
  year: number,
  month: number, // 1-12
  shifts: Shift[],
  settings: UserSettings
): MonthlyReport {
  const completedShifts = shifts.filter((s) => s.end_time !== null);
  const weeks = groupByWeek(completedShifts);

  const shiftBreakdowns = completedShifts.map((s) =>
    buildShiftBreakdown(s, settings)
  );

  const totalMinutes = shiftBreakdowns.reduce(
    (sum, b) => sum + b.total_minutes,
    0
  );
  const weekendMinutes = shiftBreakdowns.reduce(
    (sum, b) => sum + b.weekend_minutes,
    0
  );

  const overtimeMinutes = combinedOvertimeMinutes(
    completedShifts,
    settings.daily_overtime_threshold_minutes,
    settings.weekly_overtime_threshold_minutes
  );

  const regularMinutes = Math.max(0, totalMinutes - overtimeMinutes);

  return {
    year,
    month,
    total_minutes: totalMinutes,
    regular_minutes: regularMinutes,
    overtime_minutes: overtimeMinutes,
    weekend_minutes: weekendMinutes,
    shift_count: completedShifts.length,
    shifts: shiftBreakdowns,
  };
}

/**
 * Filter shifts to those that started in a given year/month (UTC).
 */
export function filterShiftsByMonth(
  shifts: Shift[],
  year: number,
  month: number // 1-12
): Shift[] {
  return shifts.filter((s) => {
    const d = new Date(s.start_time);
    return d.getUTCFullYear() === year && d.getUTCMonth() + 1 === month;
  });
}

/**
 * Returns the current month's total net minutes from a list of completed shifts.
 */
export function currentMonthTotals(shifts: Shift[]): {
  total_minutes: number;
  shift_count: number;
} {
  const now = new Date();
  const monthly = filterShiftsByMonth(
    shifts,
    now.getUTCFullYear(),
    now.getUTCMonth() + 1
  ).filter((s) => s.end_time !== null);

  return {
    total_minutes: monthly.reduce((sum, s) => sum + (netMinutes(s) ?? 0), 0),
    shift_count: monthly.length,
  };
}
