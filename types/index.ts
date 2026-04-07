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
  created_at: string;
  updated_at: string;
}

export interface Shift {
  id: string;
  user_id: string;
  start_time: string; // ISO 8601
  end_time: string | null; // null = active shift
  break_minutes: number;
  notes: string | null;
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
}
