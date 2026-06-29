# Web app — frozen

**Status:** Archived / not in active product development (June 2026)

This Next.js app (`app/`, `components/`, `lib/`, `types/`) is **no longer the lead client** for ElmTrackr. The native Android app in `android/` owns new features, UX, and Supabase contract decisions.

## What this means

- **No new product features** — screens, flows, or behavior should be built in Android, not here.
- **No schema leadership** — do not introduce new Supabase columns, enum values, or wire formats from web-only PRs.
- **Bugfixes and security** — allowed when necessary; PRs need the `android-approved` label if they touch persisted data shapes.
- **Reference only** — useful for comparing historical UI or TypeScript types; not deployed as the primary product.

## Where to work instead

| Task | Location |
|------|----------|
| New features & UI | `android/` |
| Database migrations | `supabase/migrations/` |
| Persisted enum / column contract | `android/docs/supabase-contract.md` |
| Policy | [ANDROID_FIRST.md](../ANDROID_FIRST.md) |

## Running locally (optional)

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). Requires Supabase env vars as documented in the web codebase.
