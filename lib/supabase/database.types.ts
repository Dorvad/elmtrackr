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
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          timezone?: string;
          daily_overtime_threshold_minutes?: number;
          weekly_overtime_threshold_minutes?: number;
          weekend_days?: number[];
          hourly_rate?: number | null;
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
          created_at?: string;
          updated_at?: string;
        };
        Update: {
          start_time?: string;
          end_time?: string | null;
          break_minutes?: number;
          notes?: string | null;
          is_special_day?: boolean;
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
