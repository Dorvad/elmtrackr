import type { Shift } from "@/types";

/**
 * Gross minutes = wall-clock duration of the shift.
 * Returns null for active shifts (no end_time).
 */
export function grossMinutes(shift: Shift): number | null {
  if (!shift.end_time) return null;
  const start = new Date(shift.start_time).getTime();
  const end = new Date(shift.end_time).getTime();
  return Math.max(0, Math.round((end - start) / 60_000));
}

/**
 * Net minutes = gross minus break time.
 * Returns null for active shifts.
 * Clamps to 0 (break cannot exceed shift duration).
 */
export function netMinutes(shift: Shift): number | null {
  const gross = grossMinutes(shift);
  if (gross === null) return null;
  return Math.max(0, gross - shift.break_minutes);
}

/**
 * Elapsed minutes for an active shift (no end_time), relative to `now`.
 * Returns null if shift is already completed.
 */
export function elapsedMinutes(
  shift: Shift,
  now: Date = new Date()
): number | null {
  if (shift.end_time) return null;
  const start = new Date(shift.start_time).getTime();
  return Math.max(0, Math.round((now.getTime() - start) / 60_000));
}

/**
 * Format minutes as "Xh Ym" (e.g. "8h 30m", "45m", "0m").
 */
export function formatMinutes(minutes: number): string {
  if (minutes <= 0) return "0m";
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

/**
 * Format minutes as decimal hours, e.g. 90 → "1.5".
 */
export function formatHoursDecimal(minutes: number, decimals = 2): string {
  return (minutes / 60).toFixed(decimals);
}

/**
 * Validate shift time range. Returns an error string or null.
 */
export function validateShiftTimes(
  startIso: string,
  endIso: string | null,
  breakMinutes: number
): string | null {
  const start = new Date(startIso);
  if (isNaN(start.getTime())) return "Invalid start time.";

  if (endIso) {
    const end = new Date(endIso);
    if (isNaN(end.getTime())) return "Invalid end time.";
    if (end <= start) return "End time must be after start time.";
    const gross = Math.round((end.getTime() - start.getTime()) / 60_000);
    if (breakMinutes >= gross)
      return "Break time cannot be equal to or longer than the shift duration.";
  }

  if (breakMinutes < 0) return "Break minutes cannot be negative.";

  return null;
}
