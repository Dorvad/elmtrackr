import type { Shift, WeeklyTotals } from "@/types";
import { netMinutes } from "./duration";

/**
 * Daily overtime: minutes worked beyond the daily threshold in a single shift.
 * Overtime is calculated on net minutes (after break deduction).
 */
export function dailyOvertimeMinutes(
  shift: Shift,
  dailyThresholdMinutes: number
): number {
  const net = netMinutes(shift);
  if (net === null) return 0;
  return Math.max(0, net - dailyThresholdMinutes);
}

/**
 * Weekly overtime: minutes worked beyond the weekly threshold across shifts.
 * `shifts` must all belong to the same ISO week.
 * Weekly threshold is compared against total net minutes for the week.
 */
export function weeklyOvertimeMinutes(
  shifts: Shift[],
  weeklyThresholdMinutes: number
): number {
  const totalNet = shifts.reduce((sum, s) => {
    const n = netMinutes(s);
    return sum + (n ?? 0);
  }, 0);
  return Math.max(0, totalNet - weeklyThresholdMinutes);
}

/**
 * For a set of shifts in the same week, compute per-shift regular vs overtime
 * using both daily and weekly thresholds.
 *
 * Strategy:
 * 1. Compute daily overtime for each shift.
 * 2. Sum up total net minutes for the week.
 * 3. If total exceeds weekly threshold, additional weekly overtime is the excess
 *    beyond what's already counted as daily OT.
 *
 * Returns total overtime minutes for the week.
 */
export function combinedOvertimeMinutes(
  shifts: Shift[],
  dailyThresholdMinutes: number,
  weeklyThresholdMinutes: number
): number {
  const totalNet = shifts.reduce((sum, s) => sum + (netMinutes(s) ?? 0), 0);
  const totalDailyOt = shifts.reduce(
    (sum, s) => sum + dailyOvertimeMinutes(s, dailyThresholdMinutes),
    0
  );
  const weeklyOt = Math.max(0, totalNet - weeklyThresholdMinutes);
  // Take whichever gives more overtime (they overlap, so take the max)
  return Math.max(totalDailyOt, weeklyOt);
}

/**
 * Given a list of WeeklyTotals and the weekly threshold, return total overtime
 * minutes across all weeks.
 */
export function totalOvertimeFromWeeks(
  weeks: WeeklyTotals[],
  dailyThresholdMinutes: number,
  weeklyThresholdMinutes: number
): number {
  return weeks.reduce(
    (sum, week) =>
      sum +
      combinedOvertimeMinutes(
        week.shifts,
        dailyThresholdMinutes,
        weeklyThresholdMinutes
      ),
    0
  );
}
