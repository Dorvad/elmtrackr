/**
 * Israeli payroll calculation utility.
 *
 * Weekday tiers (relative to the shift's own start):
 *   • Regular hours (up to daily threshold): 100%
 *   • First 2 overtime hours:               125%
 *   • Beyond 2 overtime hours:              150%
 *
 * Shabbat / Holiday tiers (triggered when is_special_day is true, OR the
 * shift start falls on a configured weekend day):
 *   • First 2 hours:  150%
 *   • Next 2 hours:   175%
 *   • Beyond 4 hours: 200%
 */

import type { Shift, UserSettings, PayBracket, ShiftPayBreakdown } from "@/types";
import { netMinutes } from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";

interface Tier {
  label: string;
  /** How many minutes this tier covers (Infinity = no cap). */
  minutes: number;
  rate: number;
}

const WEEKDAY_TIERS = (dailyThresholdMinutes: number): Tier[] => [
  { label: "100% — Regular",          minutes: dailyThresholdMinutes, rate: 1.0  },
  { label: "125% — Overtime (2h)",    minutes: 120,                   rate: 1.25 },
  { label: "150% — Extended OT",      minutes: Infinity,              rate: 1.5  },
];

const SPECIAL_TIERS: Tier[] = [
  { label: "150% — Shabbat / Holiday (2h)",    minutes: 120,      rate: 1.5  },
  { label: "175% — Shabbat / Holiday OT (2h)", minutes: 120,      rate: 1.75 },
  { label: "200% — Shabbat / Holiday Extended",minutes: Infinity, rate: 2.0  },
];

/**
 * Returns a pay breakdown for a single completed shift, or null if:
 * - the shift is still active (no end_time)
 * - the hourly_rate has not been configured
 */
export function calculateShiftPay(
  shift: Shift,
  settings: UserSettings
): ShiftPayBreakdown | null {
  if (shift.end_time === null) return null;
  if (!settings.hourly_rate || settings.hourly_rate <= 0) return null;

  const net = netMinutes(shift);
  if (net === null || net <= 0) return null;

  const ratePerMinute = settings.hourly_rate / 60;
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const isWeekend = isWeekendDate(dateStr, settings.weekend_days);
  const isSpecial = shift.is_special_day || isWeekend;

  const tiers = isSpecial
    ? SPECIAL_TIERS
    : WEEKDAY_TIERS(settings.daily_overtime_threshold_minutes);

  const brackets: PayBracket[] = [];
  let remaining = net;

  for (const tier of tiers) {
    if (remaining <= 0) break;
    const mins = tier.minutes === Infinity ? remaining : Math.min(remaining, tier.minutes);
    brackets.push({
      label: tier.label,
      minutes: mins,
      rate: tier.rate,
      amount: mins * ratePerMinute * tier.rate,
    });
    remaining -= mins;
  }

  const total_gross = brackets.reduce((sum, b) => sum + b.amount, 0);
  return { brackets, total_gross, is_special: isSpecial };
}

/**
 * Sums pay breakdowns across multiple shifts.
 * Shifts without a breakdown (active / no hourly_rate) are skipped.
 */
export function sumMonthlyPay(
  shifts: Shift[],
  settings: UserSettings
): { total_gross: number; regular_gross: number; overtime_gross: number; special_gross: number } {
  let total_gross = 0;
  let regular_gross = 0;
  let overtime_gross = 0;
  let special_gross = 0;

  for (const shift of shifts) {
    const breakdown = calculateShiftPay(shift, settings);
    if (!breakdown) continue;
    total_gross += breakdown.total_gross;
    if (breakdown.is_special) {
      special_gross += breakdown.total_gross;
    } else {
      for (const b of breakdown.brackets) {
        if (b.rate === 1.0) regular_gross += b.amount;
        else overtime_gross += b.amount;
      }
    }
  }

  return { total_gross, regular_gross, overtime_gross, special_gross };
}

/** Format a currency amount as "₪1,234.56". */
export function formatCurrency(amount: number): string {
  return "₪" + amount.toLocaleString("he-IL", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
