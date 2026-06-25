export interface Profile {
  id: string;
  email: string;
  full_name: string | null;
  created_at: string;
  updated_at: string;
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
  is_special_day: boolean; // holiday / Shabbat override — triggers 150/175/200% pay
  refund_action: RefundAction;
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
}

// ── Payroll ──────────────────────────────────────────────────────────────────

export interface PayBracket {
  label: string;
  minutes: number;
  rate: number; // pay multiplier, e.g. 1.0, 1.25, 1.5
  amount: number; // gross pay for this bracket (in currency units)
}

export interface ShiftPayBreakdown {
  brackets: PayBracket[];
  total_gross: number;
  is_special: boolean; // true if weekend day or is_special_day flag set
}
