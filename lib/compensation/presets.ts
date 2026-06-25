import type { CompensationRules, RegionCode } from "@/types";
import { DEFAULT_WEEKEND_DAYS } from "@/lib/shifts/weekend";

export interface RegionPreset {
  regionCode: RegionCode;
  label: string;
  description: string;
  currencyCode: string;
  timezone: string;
  profileName: string;
  rules: CompensationRules;
  stackingPolicy: "highest_only" | "additive";
}

const DEFAULT_ROUNDING = {
  enabled: false,
  incrementMinutes: 15,
  direction: "nearest" as const,
};

/** Israeli-style suggested setup — mirrors legacy ElmTrackr tiers. */
const IL_RULES: CompensationRules = {
  regular: {
    dailyStandardMinutes: 480,
    weeklyStandardMinutes: 2400,
    weekendDays: [5, 6],
    paidBreaks: false,
    autoDeductBreakMinutes: null,
    minimumShiftMinutes: null,
    rounding: DEFAULT_ROUNDING,
  },
  overtime: {
    enabled: true,
    dailyTiers: [
      { afterMinutes: 480, multiplier: 1.25 },
      { afterMinutes: 600, multiplier: 1.5 },
    ],
    weeklyTiers: [],
  },
  weekend: {
    enabled: true,
    days: [5, 6],
    multiplier: 1.5,
    stacking: "highest_only",
  },
  holiday: {
    enabled: true,
    manualSpecialDayEnabled: true,
    multiplier: 1.5,
    stacking: "highest_only",
    /** Tiered holiday rates matching legacy 150/175/200% model. */
    tiers: [
      { afterMinutes: 0, multiplier: 1.5 },
      { afterMinutes: 120, multiplier: 1.75 },
      { afterMinutes: 240, multiplier: 2.0 },
    ],
  },
  night: {
    enabled: false,
    startTime: "22:00",
    endTime: "06:00",
    multiplier: 1.25,
    applyTo: "minutes_inside_window",
    stacking: "highest_only",
  },
  deductions: {
    enabled: false,
    mode: "none",
    percentage: 0,
    fixedAmount: 0,
  },
};

const US_RULES: CompensationRules = {
  regular: {
    dailyStandardMinutes: 480,
    weeklyStandardMinutes: 2400,
    weekendDays: [0, 6],
    paidBreaks: false,
    autoDeductBreakMinutes: null,
    minimumShiftMinutes: null,
    rounding: DEFAULT_ROUNDING,
  },
  overtime: {
    enabled: true,
    dailyTiers: [{ afterMinutes: 480, multiplier: 1.5 }],
    weeklyTiers: [{ afterMinutes: 2400, multiplier: 1.5 }],
  },
  weekend: {
    enabled: false,
    days: [0, 6],
    multiplier: 1.0,
    stacking: "highest_only",
  },
  holiday: {
    enabled: true,
    manualSpecialDayEnabled: true,
    multiplier: 1.5,
    stacking: "highest_only",
  },
  night: {
    enabled: false,
    startTime: "22:00",
    endTime: "06:00",
    multiplier: 1.1,
    applyTo: "minutes_inside_window",
    stacking: "highest_only",
  },
  deductions: {
    enabled: false,
    mode: "none",
    percentage: 0,
    fixedAmount: 0,
  },
};

const GB_RULES: CompensationRules = {
  regular: {
    dailyStandardMinutes: 480,
    weeklyStandardMinutes: 2880,
    weekendDays: [0, 6],
    paidBreaks: false,
    autoDeductBreakMinutes: null,
    minimumShiftMinutes: null,
    rounding: DEFAULT_ROUNDING,
  },
  overtime: {
    enabled: true,
    dailyTiers: [],
    weeklyTiers: [{ afterMinutes: 2880, multiplier: 1.5 }],
  },
  weekend: {
    enabled: false,
    days: [0, 6],
    multiplier: 1.0,
    stacking: "highest_only",
  },
  holiday: {
    enabled: true,
    manualSpecialDayEnabled: true,
    multiplier: 1.5,
    stacking: "highest_only",
  },
  night: {
    enabled: false,
    startTime: "22:00",
    endTime: "06:00",
    multiplier: 1.1,
    applyTo: "minutes_inside_window",
    stacking: "highest_only",
  },
  deductions: {
    enabled: false,
    mode: "none",
    percentage: 0,
    fixedAmount: 0,
  },
};

const EU_RULES: CompensationRules = {
  regular: {
    dailyStandardMinutes: 480,
    weeklyStandardMinutes: 2400,
    weekendDays: [0, 6],
    paidBreaks: false,
    autoDeductBreakMinutes: null,
    minimumShiftMinutes: null,
    rounding: DEFAULT_ROUNDING,
  },
  overtime: {
    enabled: true,
    dailyTiers: [{ afterMinutes: 480, multiplier: 1.25 }],
    weeklyTiers: [{ afterMinutes: 2400, multiplier: 1.25 }],
  },
  weekend: {
    enabled: false,
    days: [0, 6],
    multiplier: 1.0,
    stacking: "highest_only",
  },
  holiday: {
    enabled: true,
    manualSpecialDayEnabled: true,
    multiplier: 1.5,
    stacking: "highest_only",
  },
  night: {
    enabled: false,
    startTime: "22:00",
    endTime: "06:00",
    multiplier: 1.25,
    applyTo: "minutes_inside_window",
    stacking: "highest_only",
  },
  deductions: {
    enabled: false,
    mode: "none",
    percentage: 0,
    fixedAmount: 0,
  },
};

const CUSTOM_RULES: CompensationRules = {
  regular: {
    dailyStandardMinutes: 480,
    weeklyStandardMinutes: 2400,
    weekendDays: DEFAULT_WEEKEND_DAYS,
    paidBreaks: false,
    autoDeductBreakMinutes: null,
    minimumShiftMinutes: null,
    rounding: DEFAULT_ROUNDING,
  },
  overtime: {
    enabled: true,
    dailyTiers: [{ afterMinutes: 480, multiplier: 1.5 }],
    weeklyTiers: [],
  },
  weekend: {
    enabled: false,
    days: DEFAULT_WEEKEND_DAYS,
    multiplier: 1.0,
    stacking: "highest_only",
  },
  holiday: {
    enabled: true,
    manualSpecialDayEnabled: true,
    multiplier: 1.5,
    stacking: "highest_only",
  },
  night: {
    enabled: false,
    startTime: "22:00",
    endTime: "06:00",
    multiplier: 1.25,
    applyTo: "minutes_inside_window",
    stacking: "highest_only",
  },
  deductions: {
    enabled: false,
    mode: "none",
    percentage: 0,
    fixedAmount: 0,
  },
};

export const REGION_PRESETS: RegionPreset[] = [
  {
    regionCode: "IL",
    label: "Israel",
    description: "Suggested starting point for Israeli-style overtime and weekend/holiday tiers. You can edit the rules to match your workplace.",
    currencyCode: "ILS",
    timezone: "Asia/Jerusalem",
    profileName: "Main job",
    rules: IL_RULES,
    stackingPolicy: "highest_only",
  },
  {
    regionCode: "US",
    label: "United States",
    description: "Suggested starting point for US-style daily and weekly overtime. You can edit the rules to match your workplace.",
    currencyCode: "USD",
    timezone: "America/New_York",
    profileName: "Main job",
    rules: US_RULES,
    stackingPolicy: "highest_only",
  },
  {
    regionCode: "GB",
    label: "United Kingdom",
    description: "Suggested starting point for UK-style weekly overtime. You can edit the rules to match your workplace.",
    currencyCode: "GBP",
    timezone: "Europe/London",
    profileName: "Main job",
    rules: GB_RULES,
    stackingPolicy: "highest_only",
  },
  {
    regionCode: "EU",
    label: "European Union",
    description: "Suggested starting point for EU-style overtime defaults. You can edit the rules to match your workplace.",
    currencyCode: "EUR",
    timezone: "Europe/Berlin",
    profileName: "Main job",
    rules: EU_RULES,
    stackingPolicy: "highest_only",
  },
  {
    regionCode: "CUSTOM",
    label: "Custom / Manual",
    description: "Start with a blank slate and configure all compensation rules yourself.",
    currencyCode: "USD",
    timezone: "UTC",
    profileName: "Main job",
    rules: CUSTOM_RULES,
    stackingPolicy: "highest_only",
  },
];

export function getPresetByRegion(regionCode: RegionCode): RegionPreset {
  return (
    REGION_PRESETS.find((p) => p.regionCode === regionCode) ??
    REGION_PRESETS.find((p) => p.regionCode === "CUSTOM")!
  );
}

export const CURRENCY_OPTIONS = [
  { code: "ILS", label: "ILS — Israeli Shekel" },
  { code: "USD", label: "USD — US Dollar" },
  { code: "GBP", label: "GBP — British Pound" },
  { code: "EUR", label: "EUR — Euro" },
  { code: "CAD", label: "CAD — Canadian Dollar" },
  { code: "AUD", label: "AUD — Australian Dollar" },
  { code: "CHF", label: "CHF — Swiss Franc" },
  { code: "JPY", label: "JPY — Japanese Yen" },
];

export const TIMEZONE_OPTIONS = [
  { value: "Asia/Jerusalem", label: "Asia/Jerusalem" },
  { value: "America/New_York", label: "America/New_York" },
  { value: "America/Chicago", label: "America/Chicago" },
  { value: "America/Denver", label: "America/Denver" },
  { value: "America/Los_Angeles", label: "America/Los_Angeles" },
  { value: "Europe/London", label: "Europe/London" },
  { value: "Europe/Berlin", label: "Europe/Berlin" },
  { value: "Europe/Paris", label: "Europe/Paris" },
  { value: "UTC", label: "UTC" },
];
