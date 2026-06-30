# Android-first product development

**Effective:** June 2026  
**Status:** Active policy for the ElmTrackr monorepo

The native Android app (`android/`) is the **primary product**. The Next.js web app (`app/`, `components/`, `lib/`, `types/`) is **frozen** — kept for reference and historical sync compatibility only.

## Why

- Play Store distribution and offline-first UX are the product goals.
- Web and Android share Supabase but have separate codebases. When web led schema and enum choices, Android crashed on real user data (e.g. strict `enum.valueOf()` on web-shaped values).
- CI, releases, and feature work now target `android/` and `supabase/` only.

## Default branch

**`Main`** is the canonical default branch on GitHub for active Android and Supabase work.

Set it under **GitHub → Settings → General → Default branch → `Main`**.

All new feature branches should branch from **`Main`**, not from legacy web-focused branches. Older references to `elmtrackr-android` describe the same product line before the branch was renamed.

## What is in active development

| Area | Path | Status |
|------|------|--------|
| Native Android app | `android/` | **Active** — features, UI, releases |
| Supabase schema & migrations | `supabase/` | **Active** — owned by Android team |
| Shared contract docs | `android/docs/supabase-contract.md` | **Source of truth** for persisted values |
| CI | `.github/workflows/android.yml` | **Active** — builds and tests Android |
| Next.js web app | `app/`, `components/`, `lib/`, `types/` | **Frozen** — no new features |

## What is frozen (web)

Do **not** add features, screens, or product behavior to the web app unless explicitly approved with the `android-approved` label (see below).

Allowed web changes without approval:

- Security patches for dependencies
- Removing or archiving dead code
- Documentation that states the freeze

## Supabase contract ownership

Schema changes and persisted enum/string formats are defined in:

1. `supabase/migrations/` — apply migrations in order
2. `android/docs/supabase-contract.md` — canonical list of tables, columns, and enum wire formats
3. `android/app/src/main/java/com/elmtrackr/app/domain/model/` — Kotlin enums and `fromPersisted()` parsers

**Rule:** Any new column or enum value must be added to Android first (or in the same PR as the migration), with tolerant parsing (`fromPersisted`, never bare `valueOf` on synced data).

Web `types/index.ts` is **not** authoritative. Update it only when mirroring an already-approved Android contract.

## Pull request rules

### Android / Supabase PRs (normal path)

- Target base branch: **`Main`**
- Touch `android/` and/or `supabase/` as needed
- Fill out the schema checklist in the PR template when migrations or wire formats change
- Android CI must pass

### Web PRs (exception path)

Web changes require:

1. A written reason in the PR description (bugfix, security, deprecation)
2. The **`android-approved`** label on the PR
3. Confirmation that no new enum values or column shapes are introduced without updating `android/docs/supabase-contract.md` and Android parsers

CI workflow **Android-first guard** fails PRs that modify frozen web paths without the `android-approved` label.

## Repository layout

```
elmtrackr/
├── ANDROID_FIRST.md          ← this policy
├── android/                  ← primary product (Kotlin + Compose)
│   └── docs/supabase-contract.md
├── supabase/                 ← shared backend schema
├── app/                      ← frozen Next.js app (see app/ARCHIVED.md)
├── components/               ← frozen
├── lib/                      ← frozen (except supabase client stubs if needed for types)
└── types/                    ← frozen — mirror Android contract only
```

## Onboarding checklist for contributors

1. Clone the repo and work from the **`Main`** branch.
2. Read `android/README.md` for build and sync architecture.
3. Read `android/docs/supabase-contract.md` before changing Supabase or persisted fields.
4. Open Android Studio on the `android/` folder (not the repo root).
5. Do not implement product features in `app/` unless explicitly scoped and labeled `android-approved`.

## Related work

- Tablet adaptive layouts and enum crash hardening: see open PRs targeting **`Main`**.
- Future option: archive web to a branch `archive/web-2026` and trim the default clone to `android/` + `supabase/` only.
