export interface Profile {
  id: string;
  email: string;
  full_name: string | null;
  created_at: string;
  updated_at: string;
}

export type RegionCode = "IL" | "US" | "GB" | "EU" | "CUSTOM";

export type StackingPolicy = "highest_only" | "additive";

export interface CompensationRules {
  regular: {
    dailyStandardMinutes: number;
    weeklyStandardMinutes: number;
    weekendDays: number[];
    paidBreaks: boolean;
    autoDeductBreakMinutes: number | null;
    minimumShiftMinutes: number | null;
    rounding: {
      enabled: boolean;
      incrementMinutes: number;
      direction: "nearest" | "up" | "down";
    };
  };
  overtime: {
    enabled: boolean;
    dailyTiers: Array<{
      afterMinutes: number;
      multiplier: number;
    }>;
    weeklyTiers: Array<{
      afterMinutes: number;
      multiplier: number;
    }>;
  };
  weekend: {
    enabled: boolean;
    days: number[];
    multiplier: number;
    stacking: StackingPolicy;
  };
  holiday: {
    enabled: boolean;
    manualSpecialDayEnabled: boolean;
    multiplier: number;
    stacking: StackingPolicy;
    /** Optional tiered holiday rates (e.g. Israeli 150/175/200%). */
    tiers?: Array<{
      afterMinutes: number;
      multiplier: number;
    }>;
  };
  night: {
    enabled: boolean;
    startTime: string;
    endTime: string;
    multiplier: number;
    applyTo: "minutes_inside_window" | "entire_shift";
    stacking: StackingPolicy;
  };
  deductions: {
    enabled: boolean;
    mode: "none" | "percentage" | "fixed";
    percentage: number;
    fixedAmount: number;
  };
}

export interface CompensationProfile {
  id: string;
  user_id: string;
  name: string;
  region_code: RegionCode;
  currency_code: string;
  timezone: string;
  base_hourly_rate: number | null;
  rules_json: CompensationRules;
  stacking_policy: StackingPolicy;
  effective_from: string;
  effective_until: string | null;
  is_default: boolean;
  is_archived: boolean;
  created_at: string;
  updated_at: string;
}

export interface CompensationSnapshot {
  profile_id: string;
  profile_name: string;
  region_code: RegionCode;
  currency_code: string;
  timezone: string;
  base_hourly_rate: number | null;
  rules_json: CompensationRules;
  stacking_policy: StackingPolicy;
  calculated_at: string;
}

export interface UserSettings {
  id: string;
  user_id: string;
  timezone: string;
  daily_overtime_threshold_minutes: number;
  weekly_overtime_threshold_minutes: number;
  // Weekend days as ISO day numbers: 0=Sun, 1=Mon, ..., 5=Fri, 6=Sat
  weekend_days: number[];
  hourly_rate: number | null;
  currency_code: string | null;
  region_code: RegionCode | null;
  default_compensation_profile_id: string | null;
  // Onboarding state
  onboarding_completed: boolean;
  onboarding_completed_at: string | null;
  // Feature flags
  features_travel_refunds: boolean;
  features_paid_projects: boolean;
  features_insights: boolean;
  features_clock_styles: boolean;
  clock_style: ClockStyle;
  created_at: string;
  updated_at: string;
}

export type RefundAction = "no_ride_taken" | "remind_later" | "submitted" | null;

export interface Shift {
  id: string;
  user_id: string;
  start_time: string; // ISO 8601
  end_time: string | null; // null = active shift
  break_minutes: number;
  notes: string | null;
  is_special_day: boolean; // holiday / Shabbat override — triggers premium pay tiers
  refund_action: RefundAction;
  compensation_profile_id: string | null;
  compensation_snapshot_json: CompensationSnapshot | null;
  created_at: string;
  updated_at: string;
}

export type RefundProvider = "Lime" | "Dott" | "Bird" | "Taxi" | "Other";

export type RefundDirection = "to_work" | "from_work";

export interface RefundClaim {
  id: string;
  shift_id: string;
  user_id: string;
  direction: RefundDirection;
  provider: RefundProvider;
  amount: number;
  ride_at: string; // ISO 8601 — when the ride was taken
  notes: string | null;
  receipt_path: string | null; // Supabase Storage path
  created_at: string;
  updated_at: string;
}

// Computed shift with derived fields
export interface ShiftWithStats extends Shift {
  gross_minutes: number;
  net_minutes: number;
  is_overnight: boolean;
  spans_weekend: boolean;
}

export interface DaySegment {
  date: string; // YYYY-MM-DD
  minutes: number;
  is_weekend: boolean;
}

export interface ShiftBreakdown {
  total_minutes: number;
  regular_minutes: number;
  overtime_minutes: number;
  weekend_minutes: number;
  segments: DaySegment[];
}

export interface MonthlyReport {
  year: number;
  month: number; // 1-12
  total_minutes: number;
  regular_minutes: number;
  overtime_minutes: number;
  weekend_minutes: number;
  shift_count: number;
  shifts: ShiftBreakdown[];
}

export interface WeeklyTotals {
  week_start: string; // ISO date YYYY-MM-DD
  total_minutes: number;
  shifts: Shift[];
}

export type ClockStyle = "classic" | "minimal" | "focus" | "bold" | "night" | "retro" | "aurora" | "pulse" | "dial" | "strand" | "prism";

export type ClockStatus = "clocked_in" | "clocked_out";

export interface ValidationError {
  field: string;
  message: string;
}

export interface ShiftFormData {
  start_time: string;
  end_time: string;
  break_minutes: number;
  notes: string;
  is_special_day: boolean;
  compensation_profile_id?: string | null;
}

// ── Payroll ──────────────────────────────────────────────────────────────────

export interface PayBracket {
  label: string;
  minutes: number;
  rate: number; // pay multiplier, e.g. 1.0, 1.25, 1.5
  amount: number; // gross pay for this bracket (in currency units)
}

export interface PaySegment {
  label: string;
  minutes: number;
  rate: number;
  amount: number;
  category: "regular" | "overtime" | "weekend" | "holiday" | "night";
}

export interface ShiftPayBreakdown {
  brackets: PayBracket[];
  segments: PaySegment[];
  total_gross: number;
  regular_gross: number;
  overtime_gross: number;
  weekend_gross: number;
  holiday_gross: number;
  night_gross: number;
  deductions_gross: number;
  net_gross: number;
  regular_minutes: number;
  overtime_minutes: number;
  weekend_minutes: number;
  holiday_minutes: number;
  night_minutes: number;
  total_minutes: number;
  is_special: boolean;
  profile_id: string | null;
  profile_name: string | null;
  currency_code: string;
  disclaimer: string;
}
