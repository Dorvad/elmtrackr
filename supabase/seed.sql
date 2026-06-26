-- ============================================================
-- Optional seed data — only useful for local Supabase dev
-- Do NOT run this in production
-- ============================================================

-- Demo user must already exist in auth.users (sign up via the app first).
-- Replace this UUID with your actual user ID from auth.users.

-- Example: update your settings after first login
-- update public.user_settings
--   set timezone = 'Asia/Riyadh',
--       daily_overtime_threshold_minutes = 480,
--       weekly_overtime_threshold_minutes = 2400,
--       weekend_days = '{5,6}'   -- Friday=5, Saturday=6
--   where user_id = '<your-user-id>';

-- Example shifts for testing (replace user_id):
-- insert into public.shifts (user_id, start_time, end_time, break_minutes, notes) values
--   ('<your-user-id>', now() - interval '3 days' + interval '8 hours',  now() - interval '3 days' + interval '17 hours', 30, 'Normal day'),
--   ('<your-user-id>', now() - interval '2 days' + interval '8 hours',  now() - interval '2 days' + interval '20 hours', 60, 'Long day with overtime'),
--   ('<your-user-id>', now() - interval '1 day'  + interval '22 hours', now() - interval '1 day'  + interval '32 hours', 0,  'Overnight shift');
