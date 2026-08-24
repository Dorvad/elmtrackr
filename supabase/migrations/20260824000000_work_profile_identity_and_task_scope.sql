-- Work profiles get a visual identity, and tasks get scoped to one.
--
-- Background: a compensation profile and a workplace stayed two separate rows
-- (see 20260811000000_workplaces_and_leave.sql, which argues for the split). That
-- split is now *internal only*. To the user there is one thing — a "work
-- profile" — that carries the pay rules, the visual identity added here, and the
-- leave arrangement its workplace row holds. The two tables stay because
-- entitlement must survive a wage change; nothing about that is user-facing any
-- more.
--
-- `color` and `icon` mirror the pair `tasks` already carries. A job and a task
-- are two levels of the same "what am I clocking into" question, and they read as
-- one system on screen only if they are drawn from one visual language.
--
-- `tasks.compensation_profile_id` scopes a task to a job: the clock-in picker
-- offers only the tasks of the profile being clocked into, which is what stops
-- the picker growing without bound as jobs are added.

-- ── Work profile identity ───────────────────────────────────────────────────

alter table public.compensation_profiles
    add column if not exists color text,
    add column if not exists icon text;

comment on column public.compensation_profiles.color is
    'Hex colour identifying this profile on screen, e.g. #5B4DF2. Null means never set; the client falls back rather than inventing one.';
comment on column public.compensation_profiles.icon is
    'Emoji identifying this profile on screen. Null means never set.';

-- ── Tasks belong to a work profile ──────────────────────────────────────────

-- `on delete set null`, matching the workplace_id links: deleting a profile must
-- never delete the record of work done under it. A task left with no profile is
-- read as belonging to the default one, which is also how rows written before
-- this migration are treated.
alter table public.tasks
    add column if not exists compensation_profile_id uuid
        references public.compensation_profiles (id) on delete set null;

comment on column public.tasks.compensation_profile_id is
    'The work profile this task belongs to. Null for tasks created before tasks were scoped to a job; the client treats those as the default profile rather than hiding them.';

create index if not exists tasks_compensation_profile_id_idx
    on public.tasks (compensation_profile_id);

-- Deliberately not backfilled. Writing a profile id into every existing task
-- here would rewrite the user's data during a deploy, before they had seen the
-- feature, and would pick the wrong profile for anyone whose default is not the
-- job the task belongs to. Null already means "the default profile", so the
-- behaviour is correct without touching a row; the client stamps the link the
-- first time a task is edited or used. Same reasoning as the workplace_id
-- columns in 20260811000000.
