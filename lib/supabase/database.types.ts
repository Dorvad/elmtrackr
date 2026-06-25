// Auto-generate this file with: npx supabase gen types typescript --project-id <id>
// Until then this hand-written version keeps TypeScript happy.

export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[];

export interface Database {
  public: {
    Tables: {
      profiles: {
        Row: {
          id: string;
          email: string;
          full_name: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id: string;
          email: string;
          full_name?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          id?: string;
          email?: string;
          full_name?: string | null;
          updated_at?: string;
        };
        Relationships: [];
      };
      user_settings: {
        Row: {
          id: string;
          user_id: string;
          timezone: string;
          daily_overtime_threshold_minutes: number;
          weekly_overtime_threshold_minutes: number;
          weekend_days: number[];
          hourly_rate: number | null;
          currency: string;
          currency_code: string | null;
          region_code: string | null;
          default_compensation_profile_id: string | null;
          onboarding_completed: boolean;
          onboarding_completed_at: string | null;
          features_travel_refunds: boolean;
          features_paid_projects: boolean;
          features_insights: boolean;
          features_clock_styles: boolean;
          clock_style: string;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          user_id: string;
          timezone?: string;
          daily_overtime_threshold_minutes?: number;
          weekly_overtime_threshold_minutes?: number;
          weekend_days?: number[];
          hourly_rate?: number | null;
          currency?: string;
          currency_code?: string | null;
          region_code?: string | null;
          default_compensation_profile_id?: string | null;
          onboarding_completed?: boolean;
          onboarding_completed_at?: string | null;
          features_travel_refunds?: boolean;
          features_paid_projects?: boolean;
          features_insights?: boolean;
          features_clock_styles?: boolean;
          clock_style?: string;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          timezone?: string;
          daily_overtime_threshold_minutes?: number;
          weekly_overtime_threshold_minutes?: number;
          weekend_days?: number[];
          hourly_rate?: number | null;
          currency?: string;
          currency_code?: string | null;
          region_code?: string | null;
          default_compensation_profile_id?: string | null;
          onboarding_completed?: boolean;
          onboarding_completed_at?: string | null;
          features_travel_refunds?: boolean;
          features_paid_projects?: boolean;
          features_insights?: boolean;
          features_clock_styles?: boolean;
          clock_style?: string;
          updated_at?: string;
        };
        Relationships: [];
      };
      compensation_profiles: {
        Row: {
          id: string;
          user_id: string;
          name: string;
          region_code: string;
          currency_code: string;
          timezone: string;
          base_hourly_rate: number | null;
          rules_json: Json;
          stacking_policy: string;
          effective_from: string;
          effective_until: string | null;
          is_default: boolean;
          is_archived: boolean;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          user_id: string;
          name: string;
          region_code: string;
          currency_code: string;
          timezone: string;
          base_hourly_rate?: number | null;
          rules_json: Json;
          stacking_policy?: string;
          effective_from?: string;
          effective_until?: string | null;
          is_default?: boolean;
          is_archived?: boolean;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          name?: string;
          region_code?: string;
          currency_code?: string;
          timezone?: string;
          base_hourly_rate?: number | null;
          rules_json?: Json;
          stacking_policy?: string;
          effective_from?: string;
          effective_until?: string | null;
          is_default?: boolean;
          is_archived?: boolean;
          updated_at?: string;
        };
        Relationships: [];
      };
      shifts: {
        Row: {
          id: string;
          user_id: string;
          start_time: string;
          end_time: string | null;
          break_minutes: number;
          notes: string | null;
          is_special_day: boolean;
          refund_action: "no_ride_taken" | "remind_later" | "submitted" | null;
          compensation_profile_id: string | null;
          compensation_snapshot_json: Json | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          user_id: string;
          start_time: string;
          end_time?: string | null;
          break_minutes?: number;
          notes?: string | null;
          is_special_day?: boolean;
          refund_action?: "no_ride_taken" | "remind_later" | "submitted" | null;
          compensation_profile_id?: string | null;
          compensation_snapshot_json?: Json | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          start_time?: string;
          end_time?: string | null;
          break_minutes?: number;
          notes?: string | null;
          is_special_day?: boolean;
          refund_action?: "no_ride_taken" | "remind_later" | "submitted" | null;
          compensation_profile_id?: string | null;
          compensation_snapshot_json?: Json | null;
          updated_at?: string;
        };
        Relationships: [];
      };
      refund_claims: {
        Row: {
          id: string;
          shift_id: string;
          user_id: string;
          direction: "to_work" | "from_work";
          provider: string;
          amount: number;
          ride_at: string;
          notes: string | null;
          receipt_path: string | null;
          created_at: string;
          updated_at: string;
        };
        Insert: {
          id?: string;
          shift_id: string;
          user_id: string;
          direction?: "to_work" | "from_work";
          provider: string;
          amount: number;
          ride_at: string;
          notes?: string | null;
          receipt_path?: string | null;
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          direction?: "to_work" | "from_work";
          provider?: string;
          amount?: number;
          ride_at?: string;
          notes?: string | null;
          receipt_path?: string | null;
          updated_at?: string;
        };
        Relationships: [];
      };
    };
    Views: Record<string, never>;
    Functions: Record<string, never>;
    Enums: Record<string, never>;
  };
}
