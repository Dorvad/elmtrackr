import type { Shift, DaySegment } from "@/types";
import { netMinutes } from "./duration";

/**
 * Returns true if the shift crosses midnight (spans more than one calendar day).
 */
export function isOvernightShift(shift: Shift): boolean {
  if (!shift.end_time) return false;
  const start = new Date(shift.start_time);
  const end = new Date(shift.end_time);
  return (
    start.getUTCFullYear() !== end.getUTCFullYear() ||
    start.getUTCMonth() !== end.getUTCMonth() ||
    start.getUTCDate() !== end.getUTCDate()
  );
}

/**
 * Split a completed shift into per-calendar-day segments (UTC dates).
 *
 * Break time is distributed proportionally across segments.
 * Each segment carries the ISO date (YYYY-MM-DD) and net minutes for that day.
 *
 * Example: 22:00 Mon → 02:00 Tue, 30 min break
 *   → Mon: proportional net minutes, Tue: remaining net minutes
 */
export function splitShiftByDay(shift: Shift): DaySegment[] {
  if (!shift.end_time) return [];

  const totalNet = netMinutes(shift);
  if (totalNet === null || totalNet <= 0) return [];

  const start = new Date(shift.start_time);
  const end = new Date(shift.end_time);
  const totalGross =
    (end.getTime() - start.getTime()) / 60_000;

  const segments: DaySegment[] = [];
  let cursor = new Date(start);

  while (cursor < end) {
    // Midnight of the next day (UTC)
    const nextMidnight = new Date(
      Date.UTC(
        cursor.getUTCFullYear(),
        cursor.getUTCMonth(),
        cursor.getUTCDate() + 1
      )
    );

    const segmentEnd = nextMidnight < end ? nextMidnight : end;
    const grossMins =
      (segmentEnd.getTime() - cursor.getTime()) / 60_000;

    // Net minutes for this segment, proportional to gross share
    const proportion = totalGross > 0 ? grossMins / totalGross : 0;
    const netMins = Math.round(totalNet * proportion);

    const dateStr = cursor.toISOString().slice(0, 10); // YYYY-MM-DD
    segments.push({ date: dateStr, minutes: netMins, is_weekend: false });

    cursor = segmentEnd;
  }

  return segments;
}
