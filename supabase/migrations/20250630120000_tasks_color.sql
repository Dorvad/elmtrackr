-- Add optional task color (Android task flagship feature)
alter table public.tasks add column if not exists color text;

notify pgrst, 'reload schema';
