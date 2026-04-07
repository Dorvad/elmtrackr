import type { DaySegment } from "@/types";

// Default weekend: Friday (5) and Saturday (6) — ISO weekday: Sun=0, Mon=1, ..., Sat=6
export const DEFAULT_WEEKEND_DAYS: number[] = [5, 6];

/**
 * Returns true if the given date (YYYY-MM-DD, treated as UTC) falls on a weekend day.
 * `weekendDays` is an array of ISO weekday integers (0=Sun … 6=Sat).
 */
export function isWeekendDate(
  dateStr: string,
  weekendDays: number[] = DEFAULT_WEEKEND_DAYS
): boolean {
  const date = new Date(dateStr + "T00:00:00Z");
  return weekendDays.includes(date.getUTCDay());
}

/**
 * Annotate each DaySegment with whether its date is a weekend day.
 */
export function annotateWeekendSegments(
  segments: DaySegment[],
  weekendDays: number[] = DEFAULT_WEEKEND_DAYS
): DaySegment[] {
  return segments.map((seg) => ({
    ...seg,
    is_weekend: isWeekendDate(seg.date, weekendDays),
  }));
}

/**
 * Total weekend minutes across a list of segments.
 */
export function totalWeekendMinutes(segments: DaySegment[]): number {
  return segments
    .filter((s) => s.is_weekend)
    .reduce((sum, s) => sum + s.minutes, 0);
}
