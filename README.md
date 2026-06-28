# ElmTrackr

Shift tracking and payroll insights for hourly workers — **native Android** with offline-first sync to Supabase.

> **Product direction (June 2026):** Android leads development. The Next.js web app is frozen. See [ANDROID_FIRST.md](./ANDROID_FIRST.md).

## Quick start (Android)

```bash
cd android
cp local.properties.example local.properties   # add Supabase URL + anon key
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
```

Install: `adb install app/build/outputs/apk/debug/app-debug.apk`

Full build, CI, Play Store, and architecture docs: **[android/README.md](./android/README.md)**

## Repository layout

| Path | Role |
|------|------|
| [`android/`](./android/) | **Active** — Kotlin + Jetpack Compose app |
| [`supabase/`](./supabase/) | **Active** — Postgres schema and migrations |
| [`android/docs/supabase-contract.md`](./android/docs/supabase-contract.md) | Canonical Supabase wire formats (owned by Android) |
| [`app/`](./app/) | **Frozen** — legacy Next.js web UI ([ARCHIVED.md](./app/ARCHIVED.md)) |
| [`.github/workflows/android.yml`](./.github/workflows/android.yml) | CI — debug APK + unit tests on every Android PR |

## Default branch

Use **`elmtrackr-android`** as the GitHub default branch. Feature work branches from there.

## Contributing

1. Read [ANDROID_FIRST.md](./ANDROID_FIRST.md) before opening a PR.
2. Target `elmtrackr-android` unless you have a specific reason not to.
3. Schema or enum changes: update `supabase/migrations/`, `android/docs/supabase-contract.md`, and Android `fromPersisted()` parsers in the same PR.
4. Web changes require the `android-approved` label — see the PR template.

## Supabase

Both clients share one Supabase project. Android is the source of truth for new schema and persisted value formats going forward.

Local schema reference: [`supabase/schema.sql`](./supabase/schema.sql)

## Web app (frozen)

The web app under `app/` is not actively developed. It remains in the repo for reference. Do not add product features there without Android team approval.

To run locally (reference only):

```bash
npm install
npm run dev
```
