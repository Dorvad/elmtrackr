/**
 * Compensation estimation based on user-configured profiles.
 *
 * ElmTrackr does not provide legally compliant payroll calculations.
 * Results are gross estimates based on the selected compensation profile.
 */

import type {
  PayBracket,
  PaySegment,
  Shift,
  ShiftPayBreakdown,
  UserSettings,
  CompensationProfile,
} from "@/types";
import { netMinutes } from "@/lib/shifts/duration";
import { isWeekendDate } from "@/lib/shifts/weekend";
import {
  resolveShiftCompensation,
  type ResolvedCompensation,
} from "@/lib/compensation/profile";
import {
  COMPENSATION_ESTIMATE_NOTE,
} from "@/lib/compensation/constants";
import { formatCurrency } from "@/lib/compensation/currency";

export { formatCurrency } from "@/lib/compensation/currency";

interface Tier {
  label: string;
  capMinutes: number;
  rate: number;
  category: PaySegment["category"];
}

function buildTiersFromRules(
  resolved: ResolvedCompensation,
  isSpecial: boolean,
  net: number
): Tier[] {
  const rules = resolved.rules_json;

  if (isSpecial) {
    const holidayTiers = rules.holiday.tiers;
    if (holidayTiers && holidayTiers.length > 0) {
      return tiersFromAfterMinutes(holidayTiers, "holiday");
    }
    if (rules.holiday.enabled) {
      return [{ label: `${Math.round(rules.holiday.multiplier * 100)}% — Holiday`, capMinutes: Infinity, rate: rules.holiday.multiplier, category: "holiday" }];
    }
    if (rules.weekend.enabled) {
      return [{ label: `${Math.round(rules.weekend.multiplier * 100)}% — Weekend`, capMinutes: Infinity, rate: rules.weekend.multiplier, category: "weekend" }];
    }
  }

  const tiers: Tier[] = [
    {
      label: "100% — Regular",
      capMinutes: rules.regular.dailyStandardMinutes,
      rate: 1.0,
      category: "regular",
    },
  ];

  if (rules.overtime.enabled) {
    const sorted = [...rules.overtime.dailyTiers].sort(
      (a, b) => a.afterMinutes - b.afterMinutes
    );
    for (let i = 0; i < sorted.length; i++) {
      const tier = sorted[i];
      const nextAfter = sorted[i + 1]?.afterMinutes ?? Infinity;
      const cap = nextAfter === Infinity ? Infinity : nextAfter - tier.afterMinutes;
      tiers.push({
        label: `${Math.round(tier.multiplier * 100)}% — Overtime`,
        capMinutes: cap,
        rate: tier.multiplier,
        category: "overtime",
      });
    }
  }

  if (
    tiers.length === 1 &&
    net > rules.regular.dailyStandardMinutes &&
    rules.overtime.dailyTiers.length > 0
  ) {
    tiers.push({
      label: "150% — Overtime",
      capMinutes: Infinity,
      rate: 1.5,
      category: "overtime",
    });
  }

  return tiers;
}

function tiersFromAfterMinutes(
  tierDefs: Array<{ afterMinutes: number; multiplier: number }>,
  category: PaySegment["category"]
): Tier[] {
  const sorted = [...tierDefs].sort((a, b) => a.afterMinutes - b.afterMinutes);
  const tiers: Tier[] = [];
  for (let i = 0; i < sorted.length; i++) {
    const tier = sorted[i];
    const nextAfter = sorted[i + 1]?.afterMinutes ?? Infinity;
    const cap =
      nextAfter === Infinity ? Infinity : nextAfter - tier.afterMinutes;
    tiers.push({
      label: `${Math.round(tier.multiplier * 100)}% — ${category === "holiday" ? "Holiday" : "Premium"}`,
      capMinutes: cap,
      rate: tier.multiplier,
      category,
    });
  }
  return tiers;
}

function applyNightPremium(
  baseRate: number,
  rules: ResolvedCompensation["rules_json"],
  shift: Shift,
  stackingPolicy: ResolvedCompensation["stacking_policy"]
): { effectiveRate: number; nightMinutes: number } {
  if (!rules.night.enabled) {
    return { effectiveRate: baseRate, nightMinutes: 0 };
  }

  const start = new Date(shift.start_time);
  const end = shift.end_time ? new Date(shift.end_time) : null;
  if (!end) return { effectiveRate: baseRate, nightMinutes: 0 };

  const nightDelta = rules.night.multiplier - 1.0;
  const overlapsNight = isNightShift(start, end, rules.night.startTime, rules.night.endTime);

  if (!overlapsNight) {
    return { effectiveRate: baseRate, nightMinutes: 0 };
  }

  const net = netMinutes(shift) ?? 0;
  const nightMinutes =
    rules.night.applyTo === "entire_shift"
      ? net
      : countNightMinutes(start, end, rules.night.startTime, rules.night.endTime, shift.break_minutes);

  if (nightMinutes <= 0) {
    return { effectiveRate: baseRate, nightMinutes: 0 };
  }

  let nightRate: number;
  if (stackingPolicy === "additive") {
    nightRate = baseRate + nightDelta;
  } else {
    nightRate = Math.max(baseRate, rules.night.multiplier);
  }

  return { effectiveRate: nightRate, nightMinutes };
}

function parseTimeToMinutes(time: string): number {
  const [h, m] = time.split(":").map(Number);
  return h * 60 + (m || 0);
}

function isNightShift(
  start: Date,
  end: Date,
  nightStart: string,
  nightEnd: string
): boolean {
  return countNightMinutes(start, end, nightStart, nightEnd, 0) > 0;
}

function countNightMinutes(
  start: Date,
  end: Date,
  nightStart: string,
  nightEnd: string,
  breakMinutes: number
): number {
  const ns = parseTimeToMinutes(nightStart);
  const ne = parseTimeToMinutes(nightEnd);
  const totalMs = end.getTime() - start.getTime();
  if (totalMs <= 0) return 0;

  let nightMs = 0;
  const step = 60_000;
  for (let t = start.getTime(); t < end.getTime(); t += step) {
    const d = new Date(t);
    const mins = d.getHours() * 60 + d.getMinutes();
    const inNight =
      ns > ne
        ? mins >= ns || mins < ne
        : mins >= ns && mins < ne;
    if (inNight) nightMs += step;
  }

  const grossMinutes = totalMs / 60_000;
  const netRatio = grossMinutes > 0 ? Math.max(0, grossMinutes - breakMinutes) / grossMinutes : 1;
  return Math.round((nightMs / 60_000) * netRatio);
}

export interface PayCalculationContext {
  settings: UserSettings;
  profiles?: CompensationProfile[];
}

function getProfilesMap(profiles?: CompensationProfile[]): Map<string, CompensationProfile> {
  return new Map((profiles ?? []).map((p) => [p.id, p]));
}

/**
 * Returns an estimated pay breakdown for a single completed shift, or null if:
 * - the shift is still active (no end_time)
 * - base hourly rate has not been configured
 */
export function calculateShiftPay(
  shift: Shift,
  settingsOrContext: UserSettings | PayCalculationContext,
  profilesArg?: CompensationProfile[]
): ShiftPayBreakdown | null {
  const settings =
    "settings" in settingsOrContext ? settingsOrContext.settings : settingsOrContext;
  const profiles =
    "settings" in settingsOrContext
      ? settingsOrContext.profiles
      : profilesArg;

  if (shift.end_time === null) return null;

  const resolved = resolveShiftCompensation(
    shift,
    settings,
    getProfilesMap(profiles)
  );

  if (!resolved.base_hourly_rate || resolved.base_hourly_rate <= 0) return null;

  const net = netMinutes(shift);
  if (net === null || net <= 0) return null;

  const ratePerMinute = resolved.base_hourly_rate / 60;
  const dateStr = new Date(shift.start_time).toISOString().slice(0, 10);
  const weekendDays = resolved.rules_json.regular.weekendDays;
  const isWeekend =
    resolved.rules_json.weekend.enabled &&
    isWeekendDate(dateStr, weekendDays);
  const isHoliday =
    resolved.rules_json.holiday.enabled &&
    resolved.rules_json.holiday.manualSpecialDayEnabled &&
    shift.is_special_day;
  const isSpecial = isHoliday || isWeekend;

  const tiers = buildTiersFromRules(resolved, isSpecial, net);
  const brackets: PayBracket[] = [];
  const segments: PaySegment[] = [];
  let remaining = net;

  let regularGross = 0;
  let overtimeGross = 0;
  let weekendGross = 0;
  let holidayGross = 0;
  let nightGross = 0;
  let regularMinutes = 0;
  let overtimeMinutes = 0;
  let weekendMinutes = 0;
  let holidayMinutes = 0;
  let nightMinutes = 0;

  for (const tier of tiers) {
    if (remaining <= 0) break;
    const mins =
      tier.capMinutes === Infinity
        ? remaining
        : Math.min(remaining, tier.capMinutes);

    const { effectiveRate, nightMinutes: nm } = applyNightPremium(
      tier.rate,
      resolved.rules_json,
      shift,
      resolved.stacking_policy
    );

    const amount = mins * ratePerMinute * effectiveRate;
    const nightPremiumAmount =
      nm > 0 && effectiveRate > tier.rate
        ? mins * ratePerMinute * (effectiveRate - tier.rate)
        : 0;

    brackets.push({
      label: tier.label,
      minutes: mins,
      rate: effectiveRate,
      amount,
    });

    segments.push({
      label: tier.label,
      minutes: mins,
      rate: effectiveRate,
      amount,
      category: tier.category,
    });

    if (tier.category === "regular") {
      regularGross += amount - nightPremiumAmount;
      regularMinutes += mins;
    } else if (tier.category === "overtime") {
      overtimeGross += amount - nightPremiumAmount;
      overtimeMinutes += mins;
    } else if (tier.category === "weekend") {
      weekendGross += amount - nightPremiumAmount;
      weekendMinutes += mins;
    } else if (tier.category === "holiday") {
      holidayGross += amount - nightPremiumAmount;
      holidayMinutes += mins;
    }

    if (nightPremiumAmount > 0) {
      nightGross += nightPremiumAmount;
      nightMinutes += nm;
    }

    remaining -= mins;
  }

  const totalGross = brackets.reduce((sum, b) => sum + b.amount, 0);

  let deductionsGross = 0;
  const ded = resolved.rules_json.deductions;
  if (ded.enabled) {
    if (ded.mode === "percentage") {
      deductionsGross = totalGross * (ded.percentage / 100);
    } else if (ded.mode === "fixed") {
      deductionsGross = ded.fixedAmount;
    }
  }

  const netGross = Math.max(0, totalGross - deductionsGross);

  return {
    brackets,
    segments,
    total_gross: totalGross,
    regular_gross: regularGross,
    overtime_gross: overtimeGross,
    weekend_gross: weekendGross,
    holiday_gross: holidayGross,
    night_gross: nightGross,
    deductions_gross: deductionsGross,
    net_gross: netGross,
    regular_minutes: regularMinutes,
    overtime_minutes: overtimeMinutes,
    weekend_minutes: weekendMinutes,
    holiday_minutes: holidayMinutes,
    night_minutes: nightMinutes,
    total_minutes: net,
    is_special: isSpecial,
    profile_id: resolved.profile_id,
    profile_name: resolved.profile_name,
    currency_code: resolved.currency_code,
    disclaimer: COMPENSATION_ESTIMATE_NOTE,
  };
}

export function sumMonthlyPay(
  shifts: Shift[],
  settingsOrContext: UserSettings | PayCalculationContext,
  profilesArg?: CompensationProfile[]
): {
  total_gross: number;
  regular_gross: number;
  overtime_gross: number;
  special_gross: number;
  weekend_gross: number;
  holiday_gross: number;
  night_gross: number;
  deductions_gross: number;
  net_gross: number;
  currency_code: string;
} {
  let totalGross = 0;
  let regularGross = 0;
  let overtimeGross = 0;
  let specialGross = 0;
  let weekendGross = 0;
  let holidayGross = 0;
  let nightGross = 0;
  let deductionsGross = 0;
  let netGross = 0;
  let currencyCode = "USD";

  for (const shift of shifts) {
    const breakdown = calculateShiftPay(shift, settingsOrContext, profilesArg);
    if (!breakdown) continue;
    totalGross += breakdown.total_gross;
    regularGross += breakdown.regular_gross;
    overtimeGross += breakdown.overtime_gross;
    weekendGross += breakdown.weekend_gross;
    holidayGross += breakdown.holiday_gross;
    nightGross += breakdown.night_gross;
    deductionsGross += breakdown.deductions_gross;
    netGross += breakdown.net_gross;
    specialGross += breakdown.weekend_gross + breakdown.holiday_gross;
    currencyCode = breakdown.currency_code;
  }

  return {
    total_gross: totalGross,
    regular_gross: regularGross,
    overtime_gross: overtimeGross,
    special_gross: specialGross,
    weekend_gross: weekendGross,
    holiday_gross: holidayGross,
    night_gross: nightGross,
    deductions_gross: deductionsGross,
    net_gross: netGross,
    currency_code: currencyCode,
  };
}
