-- Paid Projects in the cloud: projects, their billing records and their payments.
--
-- These three Room tables had no Supabase counterpart, so a project, everything
-- billed against it, and every payment received existed on one device and
-- nowhere else. Changing phones or reinstalling lost the record of money the
-- user was owed unless they had exported a local backup first, and nothing in
-- the app said so.
--
-- Column names, nullability and semantics mirror the Room entities exactly. The
-- shapes are already settled and the app is the only writer; a second, subtly
-- different model on the server would be a source of bugs rather than a
-- refinement.

-- ── Money is text, deliberately ─────────────────────────────────────────────
--
-- Room stores these as TEXT holding canonical decimal strings because binary
-- floating point cannot represent most decimal amounts exactly, and a stored fee
-- must round-trip byte for byte. `numeric` would preserve the value in Postgres,
-- but PostgREST serialises numeric as a JSON number, so the value would pass
-- through a double on its way to and from the client — which is exactly the
-- conversion the local schema exists to avoid.
--
-- The app does all project arithmetic client-side (ProjectFee, ProjectMetrics),
-- so nothing here needs to sum these columns in SQL. The check constraint keeps
-- non-numeric text out.

create table if not exists public.projects (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references auth.users(id) on delete cascade,
  name                text not null,
  client_name         text,
  client_id           text,
  description         text,
  work_status         text not null,
  currency_code       text not null,
  base_fee            text not null check (base_fee ~ '^-?[0-9]+(\.[0-9]+)?$'),
  tax_label           text,
  tax_rate_percent    text not null check (tax_rate_percent ~ '^-?[0-9]+(\.[0-9]+)?$'),
  tax_mode            text not null,
  tax_amount          text not null check (tax_amount ~ '^-?[0-9]+(\.[0-9]+)?$'),
  client_total        text not null check (client_total ~ '^-?[0-9]+(\.[0-9]+)?$'),
  hour_budget_minutes integer,
  target_hourly_rate  text,
  -- Epoch day, matching the Room columns. Kept as integers rather than dates so
  -- a project's deadline cannot shift by a day through a timezone conversion.
  start_date          integer,
  deadline            integer,
  completion_date     integer,
  notes               text,
  archived_at         timestamptz,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  deleted_at          timestamptz,
  client_updated_at   timestamptz not null default now()
);

create table if not exists public.project_billing_records (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references auth.users(id) on delete cascade,
  project_id          uuid not null references public.projects(id) on delete cascade,
  base_amount         text not null check (base_amount ~ '^-?[0-9]+(\.[0-9]+)?$'),
  tax_label           text,
  tax_rate_percent    text not null check (tax_rate_percent ~ '^-?[0-9]+(\.[0-9]+)?$'),
  tax_mode            text not null,
  tax_amount          text not null check (tax_amount ~ '^-?[0-9]+(\.[0-9]+)?$'),
  total_amount        text not null check (total_amount ~ '^-?[0-9]+(\.[0-9]+)?$'),
  currency_code       text not null,
  external_reference  text,
  notes               text,
  billed_on           integer not null,
  due_on              integer,
  cancelled_at        timestamptz,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  deleted_at          timestamptz,
  client_updated_at   timestamptz not null default now()
);

create table if not exists public.project_payments (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references auth.users(id) on delete cascade,
  project_id          uuid not null references public.projects(id) on delete cascade,
  billing_record_id   uuid not null references public.project_billing_records(id) on delete cascade,
  paid_on             integer not null,
  amount              text not null check (amount ~ '^-?[0-9]+(\.[0-9]+)?$'),
  currency_code       text not null,
  method              text,
  external_reference  text,
  notes               text,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now(),
  deleted_at          timestamptz,
  client_updated_at   timestamptz not null default now()
);

-- ── Indexes ─────────────────────────────────────────────────────────────────

create index if not exists projects_user_id_idx on public.projects (user_id);
create index if not exists projects_updated_at_id_idx on public.projects (updated_at, id);

create index if not exists project_billing_records_user_id_idx
  on public.project_billing_records (user_id);
create index if not exists project_billing_records_project_id_idx
  on public.project_billing_records (project_id);
create index if not exists project_billing_records_updated_at_id_idx
  on public.project_billing_records (updated_at, id);

create index if not exists project_payments_user_id_idx
  on public.project_payments (user_id);
create index if not exists project_payments_billing_record_id_idx
  on public.project_payments (billing_record_id);
create index if not exists project_payments_updated_at_id_idx
  on public.project_payments (updated_at, id);

-- ── Row level security ──────────────────────────────────────────────────────
--
-- None of the client's fetch queries filter by user — they rely entirely on
-- these policies to scope the result set — so a table added without them would
-- leak straight into every user's local database.

alter table public.projects                enable row level security;
alter table public.project_billing_records enable row level security;
alter table public.project_payments        enable row level security;

do $$
declare
  t text;
begin
  foreach t in array array['projects', 'project_billing_records', 'project_payments'] loop
    execute format('drop policy if exists %I on public.%I', t || '_select_own', t);
    execute format('drop policy if exists %I on public.%I', t || '_insert_own', t);
    execute format('drop policy if exists %I on public.%I', t || '_update_own', t);
    execute format('drop policy if exists %I on public.%I', t || '_delete_own', t);

    execute format(
      'create policy %I on public.%I for select using (auth.uid() = user_id)',
      t || '_select_own', t
    );
    execute format(
      'create policy %I on public.%I for insert with check (auth.uid() = user_id)',
      t || '_insert_own', t
    );
    execute format(
      'create policy %I on public.%I for update using (auth.uid() = user_id) ' ||
      'with check (auth.uid() = user_id)',
      t || '_update_own', t
    );
    execute format(
      'create policy %I on public.%I for delete using (auth.uid() = user_id)',
      t || '_delete_own', t
    );
  end loop;
end
$$;

-- ── Account deletion ────────────────────────────────────────────────────────
--
-- The cascade from auth.users already removes these rows, but delete_own_account
-- is what the app calls and what its promise in Settings is measured against, so
-- the three tables are named there explicitly rather than left to the cascade.

create or replace function public.delete_own_project_data()
returns void language plpgsql security definer set search_path = public as $$
begin
  delete from public.project_payments        where user_id = auth.uid();
  delete from public.project_billing_records where user_id = auth.uid();
  delete from public.projects                where user_id = auth.uid();
end;
$$;

revoke all on function public.delete_own_project_data() from public;
grant execute on function public.delete_own_project_data() to authenticated;
