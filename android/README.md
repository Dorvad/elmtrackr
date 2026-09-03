# ElmTrackr — Native Android App

> **Android-first (June 2026):** This directory is the **primary product**. The Next.js web app (`app/`) is frozen — see [ANDROID_FIRST.md](../ANDROID_FIRST.md). Supabase wire formats are owned here: [docs/supabase-contract.md](docs/supabase-contract.md).

> CI debug APKs pick up `SUPABASE_URL` and `SUPABASE_ANON_KEY` from GitHub Actions secrets at build time.

> **Implementation status (2026-07-06):** Authenticated user identity, one-time
> legacy-data adoption, account-isolated offline sync, current Supabase wire
> formats, auth callbacks, live theme selection, shift month navigation, insights
> (feature-gated), refund claim editing with CameraX receipt capture and PDF
> export, compensation profiles on shift create/edit, weekly/month-over-month
> report comparisons, staged multi-step onboarding, native rendering for all
> clock styles, and debug/release verification are implemented.
> Hebrew localization is complete (full string parity between `values/` and
> `values-iw/`). Remaining work tracked as separate deliverables: advanced
> motion polish and broader emulator/device instrumentation coverage.

Native Kotlin + Jetpack Compose app. Shares a Supabase backend with the legacy
web app but has a **separate codebase**. **No WebView, no Capacitor.** New
features and schema decisions are made in Android first.

```
elmtrackr/
├── android/          ← you are here (active product)
├── supabase/         ← shared schema (Android-owned contract)
├── app/              ← frozen Next.js web (see app/ARCHIVED.md)
└── .github/workflows/android.yml
```

---

## Supabase authentication

### Local setup

1. Copy the config template:
   ```bash
   cp android/local.properties.example android/local.properties
   ```
2. Fill in your values in `local.properties`:
   ```properties
   supabase.url=https://xxxx.supabase.co
   supabase.anon.key=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   ```
3. Rebuild the project — `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_ANON_KEY`
   are injected at compile time from these values.

> `local.properties` is git-ignored. **Never commit real keys.**

### GitHub Actions / CI builds (tablet downloads)

APKs downloaded from the **Actions → ElmTrackr-debug** artifact will connect
to Supabase only if the repo has secrets configured. Set them once in
**GitHub → repo → Settings → Secrets and variables → Actions**:

| Secret name | Value |
|---|---|
| `SUPABASE_URL` | Your project URL, e.g. `https://xxxx.supabase.co` |
| `SUPABASE_ANON_KEY` | Your **anon / public** key (`eyJ…`) |

The workflow writes these into `android/local.properties` at build time (the
file is never committed). If the secrets are absent — fork pull requests,
fresh forks, or repos without secrets — the build still succeeds and the APK
shows "Auth not configured" on the Account tab.

> **Security:** Only use the **anon / public** key in the Android app. Never
> add the `service_role` key or any other privileged key — it would be
> compiled into the APK, which is trivially extractable. Row-level security
> (RLS) in Supabase ensures the anon key is safe to embed.

### Supabase dashboard configuration

In your Supabase project → **Authentication → URL Configuration**, add:

| Field | Value |
|---|---|
| Redirect URLs | `elmtrackr://auth/callback`, `elmtrackr://auth/reset-password` |

`elmtrackr://auth/callback` handles email confirmation and OAuth. Password-reset
emails use `elmtrackr://auth/reset-password` so the app can show the new-password
screen after the link is opened.

### Behavior when credentials are missing

When `local.properties` is absent (or the keys are blank) — the common case on CI
and fresh checkouts — the app builds and runs, and the auth screen shows an
"Auth not configured" message instead of a sign-in form.

**It does not, however, track shifts.** An earlier version of this section claimed
"all local shift tracking continues to work without authentication"; that is not
true and never was. `CurrentUserProvider` resolves the user from
`lastActiveUserId`, which is written only by `SupabaseAuthRepository` on a
successful sign-in. With nobody signed in there is no user id, so `clockIn`
returns null, the Wear punch answers `not_signed_in`, and `AppShellViewModel`
routes to the auth screen rather than the dashboard.

An unconfigured build is therefore good for compiling, running unit tests and
inspecting the auth screen — not for exercising the app. Anything that records a
shift needs Supabase credentials and a signed-in account. Giving the app a
device-local identity so it can work without one is a real product change, not a
configuration flag; see `docs/wear-play-submission-runbook.md` §2.

---

## Offline-first sync architecture

ElmTrackr uses Room as the **single source of truth** and Supabase PostgREST as the
**remote backend**. All writes go to Room first; sync to Supabase happens in the
background via WorkManager.

### Data flow

```
User action
  │
  ▼
LocalXxxRepository          ← writes Room immediately, syncStatus = PENDING_*
  │                           then calls SyncTrigger.schedule()
  ▼
SyncScheduler               ← enqueues a one-time SyncWorker (KEEP policy)
  │
  ▼
SyncWorker (CoroutineWorker)
  │
  ▼
SyncRepositoryImpl
  ├── Push phase (in order: shifts → refund claims → settings → profiles)
  │     PENDING_CREATE  → upsert to Supabase, set remoteId, mark SYNCED
  │     PENDING_UPDATE  → upsert using existing remoteId, mark SYNCED
  │     PENDING_DELETE  → delete from Supabase, mark SYNCED
  │     FAILED          → retry (same as CREATE / UPDATE logic)
  │     error           → mark FAILED, record lastSyncError
  └── Pull phase (same order)
        new remote record       → insert locally (SYNCED)
        remote newer + SYNCED   → update local (SYNCED)
        local has PENDING_*     → skip (local wins, remote overwritten on next push)
        remote deleted + SYNCED → soft-delete locally
```

### Conflict strategy

| Local state | Remote newer | Result |
|---|---|---|
| SYNCED | yes | Remote wins (updatedAt comparison) |
| PENDING_* | any | **Local wins** — never discarded |
| Both active shifts | pull active shift | Skip remote (duplicate guard) |

### Key classes

| Class | Responsibility |
|---|---|
| `SyncRepositoryImpl` | Orchestrates push + pull for all four entity types |
| `SyncWorker` | `CoroutineWorker` that reads the current userId and calls `syncAll` |
| `SyncScheduler` | `SyncTrigger` implementation; enqueues one-time and periodic work |
| `SyncTrigger` | `fun interface` injected into local repos; `NoOpSyncTrigger` used in tests |
| `RemoteXxxDataSource` | Interface over PostgREST table, Supabase implementations + fake for tests |
| `RemoteMapper.kt` | Extension functions converting `ShiftEntity ↔ JsonObject` (no compiler plugin) |

### Sync triggers

- After every `clockIn`, `clockOut`, `updateShift`, `deleteShift` call
- After every refund-claim or settings write
- On app launch (periodic WorkManager task, 15-min interval, requires network)

### CI / credential safety

`SupabaseClientProvider.get()` returns `null` when `SUPABASE_URL` / `SUPABASE_ANON_KEY`
are blank (the default on CI). `SyncRepositoryImpl` checks this at the top of `syncAll`
and returns `SyncResult.NotConfigured` immediately — no network calls, no crashes.

### Adding a new entity type to sync

1. Add `PENDING_CREATE/UPDATE/DELETE`, `remoteId`, `lastSyncedAt`, `lastSyncError`, `syncStatus` fields to the Room entity (already done for all current entities).
2. Add `getPendingSyncXxx()`, `updateSyncState(...)`, `getXxxByRemoteId(...)` to the DAO.
3. Add to/from JSON mapping in `RemoteMapper.kt`.
4. Create `RemoteXxxDataSource` interface + `SupabaseXxxDataSource` implementation.
5. Wire push and pull in `SyncRepositoryImpl` (follow the existing pattern).
6. Add the data source to `ElmTrackrApp`.

---

## Building locally

### Prerequisites

| Tool | Version | Where to get it |
|---|---|---|
| Android Studio | Hedgehog 2023.1+ (or any recent) | developer.android.com/studio |
| OR Android command-line tools | latest | developer.android.com/tools |
| Android SDK platform | API 36 | via SDK Manager |
| Build Tools | 36.0.0 | via SDK Manager |
| JDK | 17 | bundled with Android Studio, or Temurin |

### Step-by-step (command line)

```bash
# 1. From the repo root — change into the android module
cd android

# 2. Build a debug APK
./gradlew :app:assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

```bash
# Run unit tests (no emulator needed)
./gradlew testDebugUnitTest
```

```bash
# Install directly to a connected device or running emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Opening in Android Studio

1. Open Android Studio → **Open** → select the `android/` folder (not the repo root).
2. Let Gradle sync finish.
3. Hit **Run** (▶) or **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

---

## GitHub Actions — source of truth for builds

Because cloud AI sessions (including the one that built this app) run in
environments where `dl.google.com` is blocked and the Android SDK is not
installed, **GitHub Actions is the authoritative build system**.

Every push or pull request that touches `android/**` automatically:

1. Spins up an `ubuntu-latest` runner with Android SDK pre-installed.
2. Installs Android platform 35 + build tools 35.0.0.
3. Writes `android/local.properties` from `SUPABASE_URL` / `SUPABASE_ANON_KEY` repo
   secrets (skipped silently if the secrets are absent — fork PRs, unconfigured repos).
4. Runs `./gradlew :app:assembleDebug`.
5. Runs `./gradlew testDebugUnitTest`.
6. Uploads `app-debug.apk` as a downloadable artifact.

When Supabase secrets are configured, the artifact APK connects to your Supabase
project. When they are absent, `SupabaseClientProvider.isConfigured()` returns
`false` and the app shows "Auth not configured" on the Account tab — all local
shift tracking still works. Auth unit tests use `FakeAuthRepository` and never
touch the network.

Workflow file: [`.github/workflows/android.yml`](../.github/workflows/android.yml)

---

## Downloading the APK from GitHub Actions (for your tablet)

**Step 1 — Find the run**

1. Go to the repo on GitHub.
2. Click the **Actions** tab.
3. In the left sidebar, click **Android CI**.
4. Click the most recent green run.

**Step 2 — Download the artifact**

5. Scroll to the bottom of the run summary page.
6. Under **Artifacts**, click **ElmTrackr-debug**.
7. A `ElmTrackr-debug.zip` downloads — unzip it to get `app-debug.apk`.

**Step 3 — Side-load onto your Android tablet**

8. Transfer the `.apk` to your tablet (email, Google Drive, USB, etc.).
9. On the tablet: **Settings → Apps → Special app access →
   Install unknown apps** → allow your file manager or browser.
10. Open the `.apk` and tap **Install**.

> Debug APKs are signed with a local debug keystore and are for sideloading only.

---

## Play Store upload (release AAB)

**Do not upload the `ElmTrackr-release` artifact from GitHub Actions to Play Console.**
CI generates a throwaway signing key on every run. Play Console will reject it with
“signed with the wrong key”.

Build and sign locally with the **same release keystore** used for your first Play upload.

### 1. Configure signing in `android/local.properties`

```properties
KEYSTORE_PATH=keystore/elmtrackr-release.jks
KEYSTORE_PASSWORD=your-store-password
KEY_ALIAS=your-key-alias
KEY_PASSWORD=your-key-password
```

### 2. Verify the keystore fingerprint matches Play Console

Play Console → **App integrity** → **App signing** shows the upload certificate SHA-1.
Your local keystore must match (expected: `A5:37:CC:E7:CA:2E:C9:42:D3:7C:DE:34:C3:9D:D6:11:FE:BD:F7:C3`).

```bash
keytool -list -v \
  -keystore android/keystore/elmtrackr-release.jks \
  -alias YOUR_KEY_ALIAS
```

Look for `SHA1:` in the output. If it does not match, you are using the wrong keystore file.

### 3. Build the release bundle

```bash
cd android
./gradlew :app:bundleRelease
```

Output: `android/app/build/outputs/bundle/release/app-release.aab`

### 4. Upload to Play Console

- Use a **new `versionCode`** each upload (currently **42** / `1.2.4` in `app/build.gradle.kts`,
  with `:wear` on **10042** — see the invariant in `docs/release-checklist.md`).
- Upload the locally built `app-release.aab`, not a CI artifact.

---

## Project structure

```
android/
├── local.properties.example  ← copy to local.properties and add Supabase keys
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml    # Version catalog (AGP, Kotlin, Supabase, Room…)
│   └── wrapper/
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml   # includes elmtrackr://auth deep-link intent filter
        └── java/com/elmtrackr/app/
            ├── ElmTrackrApp.kt         # Application — Hilt entry point (@HiltAndroidApp)
            ├── MainActivity.kt         # Single activity; handles auth deep links
            ├── navigation/
            │   ├── AppNavGraph.kt      # Auth-aware NavHost (loading/auth/onboarding/main)
            │   └── BottomNavItem.kt    # 4 tabs: Home/Shifts/Reports/Settings (Account lives in Settings)
            ├── ui/
            │   ├── auth/              # AuthScreen, AuthViewModel, AuthUiState
            │   ├── dashboard/
            │   ├── shifts/
            │   ├── reports/
            │   └── settings/
            ├── domain/
            │   ├── model/             # AuthResult, Profile, Shift, …
            │   └── repository/        # AuthRepository interface
            └── data/
                ├── auth/              # SupabaseClientProvider (reads BuildConfig)
                ├── local/             # Room entities, DAOs, mappers, DataStore
                └── repository/        # SupabaseAuthRepository, LocalAuthRepository, …
```

---

## Navigation structure

```
AppNavGraph (auth-aware, no bottom nav)
  ├── loading        ← CircularProgressIndicator while session resolves
  ├── auth           ← AuthScreen (sign-in / sign-up / reset; ElmTrackr logo + password toggle)
  ├── onboarding     ← OnboardingScreen (full setup form; all fields below)
  └── main           ← MainScaffold (4-tab bottom nav)
        ├── dashboard  ← DashboardScreen  (clock in/out, month stats, sync badge)
        ├── shifts     ← ShiftsScreen     (shift history, create/edit/delete)
        ├── reports    ← ReportsScreen    (monthly report + month navigator)
        └── settings   ← SettingsScreen  (preferences + auth/sign-out section)
```

### Routing logic (`AppShellViewModel`)

| Auth configured | Profile | Onboarding done | Route |
|---|---|---|---|
| any | — | — | `loading` (initial) |
| no | — | any | `auth` (shows "not configured" message) |
| yes | null | any | `auth` |
| yes | set | yes | `main` |
| yes | set | no | `onboarding` |

`AppShellViewModel` combines `AuthRepository.observeCurrentProfile()` and
`AppPreferencesRepository.preferences.map { it.onboardingCompleted }` into
`StateFlow<AppNavState>`. `LaunchedEffect(navState)` in `AppNavGraph` drives
the navController, always clearing the back stack with `popUpTo(0)`.

### Screens wired to real data

| Screen | Real ViewModel / data |
|---|---|
| Dashboard | `DashboardViewModel` — active shift, monthly report, settings, pending sync count |
| Reports | `ReportsViewModel` — monthly report + weekly totals, month navigator |
| Shifts | `ShiftsViewModel` — full shift list, create/edit/delete with offline-first writes |
| Settings | `SettingsViewModel` — full settings form, feature toggles, theme, sync, account |
| Auth | `AuthViewModel` — sign-in, sign-up, password reset, sign-out |
| Onboarding | `OnboardingViewModel` — full setup form, writes all settings, marks onboarding complete |

---

## Auth screen

The `AuthScreen` (`ui/auth/AuthScreen.kt`) handles three modes via `AuthMode` enum:

| Mode | Shows |
|---|---|
| `SIGN_IN` | Email + password + sign-in button + links to sign-up / forgot |
| `SIGN_UP` | Email + password (min 6 chars) + create account button |
| `FORGOT_PASSWORD` | Email only + send-reset-email button |

**Key behaviours:**
- Displays the ElmTrackr logo/title at the top.
- Password field has a show/hide toggle (Visibility icon).
- Client-side validation: requires `@` in email, min 6-char password for sign-up.
- `isError` + `supportingText` surfaces validation feedback under each field.
- IME action on email → moves focus to password; IME Done on password → submits.
- Button disabled while invalid or loading.
- Server-side error message shown below the password field.
- `AuthUiState.NotConfigured` → shows "Auth not configured" message (CI default).
- `AuthUiState.Loading` → full-screen spinner (session initialising).
- On successful sign-in/sign-up, `AppShellViewModel` drives navigation automatically.

---

## Onboarding screen

The `OnboardingScreen` (`ui/onboarding/OnboardingScreen.kt`) is a single scrollable form.

### Fields collected

| Field | Default | Notes |
|---|---|---|
| Display name | `""` | Saved to `Profile.fullName` if a profile exists |
| Timezone | Device timezone | Text field, editable |
| Daily OT threshold | 8 h/day | Stepper (−/+), min 1 |
| Weekly OT threshold | 40 h/week | Stepper (−/+), min 1 |
| Weekend days | Fri + Sat | FilterChip row, multi-select |
| Hourly rate | `null` | Optional decimal |
| Travel refunds | off | Switch |
| Paid projects | off | Switch |
| Insights | on | Switch |
| Clock styles | on | Switch |

### Validation (client-side, before any DB write)

- Daily OT hours > 0
- Weekly OT hours > 0
- Weekly OT hours ≥ daily OT hours
- Hourly rate, if provided, must be positive

Errors appear inline under the offending field. No network call is required.

### How onboarding completion is stored

1. `settingsRepository.saveSettings(...)` writes all fields to `UserSettings` in Room with `onboardingCompleted = true`.
2. `markOnboardingCompleted()` calls `AppPreferencesRepository.setOnboardingCompleted(true)`, which writes to DataStore.
3. `AppShellViewModel` observes the DataStore preference and emits `AppNavState.Main`, driving navigation.

### Dashboard

The `DashboardScreen` (`ui/dashboard/DashboardScreen.kt`) is fully functional:

| Feature | Status |
|---|---|
| Greeting (display name or "ElmTrackr") | ✅ |
| Clock In / Clock Out | ✅ offline-first |
| Live elapsed timer (ticks every second) | ✅ `produceState` |
| Edit active shift start time (TimePicker) | ✅ |
| This month: hours, overtime, shift count | ✅ |
| This month: gross pay (when hourly rate set) | ✅ |
| Recent shifts list (last 5 completed) | ✅ |
| Sync status indicator | ✅ |
| 3 native clock styles | ✅ Classic / Minimal / Aurora |

### Native clock styles

Only three clock styles render natively. All other `ClockStyle` values fall back to Classic.

| Style | Appearance |
|---|---|
| `CLASSIC` | Filled card with large timer; Clock In / Out buttons |
| `MINIMAL` | Large thin typography timer; circular IN / OUT button |
| `AURORA` | Linear gradient card (primary → tertiary); frosted-glass buttons |

### Shifts screen

The `ShiftsScreen` (`ui/shifts/ShiftsScreen.kt`) is fully functional and offline-first:

| Feature | Status |
|---|---|
| View all shifts (date, time range, duration, break, notes) | ✅ |
| Active shift badge | ✅ |
| Create a manual shift (date + time pickers) | ✅ offline-first (`PENDING_CREATE`) |
| Edit an existing shift | ✅ offline-first (`PENDING_UPDATE`) |
| Delete a shift | ✅ soft-delete (`PENDING_DELETE`) |
| Break minutes, notes, special-day flag | ✅ |
| Compensation profile picker (when profiles exist) | ✅ create + edit |
| Travel refund claims (CameraX + gallery, upload/sync) | ✅ feature-gated by `featuresTravelRefunds` |
| Travel refund action (conditional on `featuresTravelRefunds` setting) | ✅ |
| Validation: end time must be after start time; break ≥ 0 | ✅ |
| Empty-state prompt with FAB | ✅ |
| Back-press dismisses form | ✅ `BackHandler` |

**Offline-first behaviour:**
All writes go to Room immediately with the appropriate `PENDING_*` sync status.
`SyncScheduler.schedule()` is called after every create, update, and delete so
WorkManager picks up the change as soon as a network connection is available.

**Date/time picking:**
- `DatePickerWrapper` — Material 3 `DatePickerDialog` + `DatePicker`
- `TimePickerWrapper` — Material 3 `TimePicker` inside an `AlertDialog`
- Epoch-millis form state (`Long`) ensures `rememberSaveable` compatibility

**Known limitations:**
- Overnight shifts spanning midnight are supported, but the date column always
  shows the shift's start date.
- Reports integration (totals updated from the Shifts screen) is handled by the
  existing `ReportsViewModel`/Room observe flows — no extra wiring needed.

### Settings screen

The `SettingsScreen` (`ui/settings/SettingsScreen.kt`) is fully functional with offline-first writes:

| Section | Feature | Status |
|---|---|---|
| Profile | Display name (editable), email (read-only) | ✅ |
| Appearance | Theme dropdown (system/light/dark) | ✅ |
| Appearance | Clock style dropdown (Classic/Minimal/Aurora) | ✅ feature-gated by `featuresClockStyles` |
| Overtime Thresholds | Daily and weekly OT hours with inline validation | ✅ |
| Weekend Days | 7 FilterChips in 4+3 layout (Sun–Sat) | ✅ saves immediately |
| Payroll | Hourly rate (optional decimal) | ✅ |
| Location | Timezone text field | ✅ |
| Features | 4 toggle switches with descriptions | ✅ saves immediately |
| Save Settings | Single button saves all text-field changes | ✅ |
| Sync | Pending count, last sync status, Sync Now button | ✅ |
| Account | Reset password + Sign out (when auth configured) | ✅ |

**Save timing:**
- Feature toggles (Travel Refunds, Paid Projects, Insights, Clock Styles) and weekend days call `SettingsViewModel` immediately on change — no button tap required.
- All text-field sections (display name, OT thresholds, hourly rate, timezone, clock style) are batched and saved together when "Save Settings" is tapped.

**Theme storage:**
- Theme preference (system/light/dark) is stored in DataStore via `ThemePreferenceStore`, separate from `UserSettings` (which syncs to Supabase). Theme is local-only.

**Sign out:**
- The sign-out button is wired via `onSignOut` callback passed down from `MainScaffold`, which calls `AuthViewModel.signOut()`. The Settings screen itself has no auth ViewModel dependency.

**Sync section:**
- Shows live pending-change count from `SyncRepository.observePendingCount()`.
- "Last sync" shows the status string from `SyncRepository.observeLastSyncStatus()` or "Never".
- "Sync Now" triggers `SyncRepositoryImpl.syncAll()`. While syncing, the button shows "Syncing…" and is disabled.

**Validation:**
- Daily OT: must be > 0 and ≤ 24 h.
- Weekly OT: must be > 0, ≤ 168 h, and ≥ daily OT.
- Hourly rate: must be ≥ 0 when provided (null = not set).
- Timezone: must be a valid IANA zone ID (selected via searchable picker).
- Errors appear inline under the offending field via `supportingText`.

**Clock styles:**
The settings dropdown lists all supported `ClockStyle` values. Only **Classic**, **Minimal**, and **Aurora** render natively on the dashboard; any other persisted value (including legacy styles) falls back to Classic via `ClockStyle.fromPersisted()`.

**Known limitations:**
- Theme change takes effect immediately (DataStore + Compose recomposition).

---

### Reports screen

The `ReportsScreen` (`ui/reports/ReportsScreen.kt`) is fully functional and reads from local Room data:

| Feature | Status |
|---|---|
| Month navigation (prev/next) | ✅ next disabled at current month |
| Total hours, regular, overtime, weekend/special | ✅ |
| Completed shift count | ✅ |
| Payroll estimate (total, regular, OT, special) | ✅ shown when hourly rate set |
| "Set hourly rate in Settings" hint when no rate | ✅ |
| Per-week breakdown for selected month | ✅ ISO Monday-anchored weeks |
| Week-over-week delta vs prior month (when data exists) | ✅ |
| Month-over-month total hours comparison | ✅ |
| CSV export via Android share sheet | ✅ `elmtrackr-YYYY-MM.csv` |
| PDF export (hours report) | ✅ `ReportExporter.shareShiftPdf` |
| Daily insights carousel | ✅ feature-gated by `featuresInsights` |
| Shift insights stat grid | ✅ when insights enabled |
| Travel refund review (unresolved shifts) | ✅ feature-gated by `featuresTravelRefunds` |
| Refund reimbursement PDF export (per month + all months) | ✅ embeds receipt images when available |
| Empty state | ✅ |
| Loading / error state | ✅ |

**Monthly summary behaviour:**
`ReportsViewModel` combines `ReportsRepository.observeMonthlyReport` (pre-aggregated by `MonthlyReportBuilder`) with `ShiftsRepository.observeShiftsByMonth` (raw shifts for payroll + weekly grouping). All data comes from Room — no network required.

**Payroll estimate behaviour:**
Calls `PayrollCalculator.sumMonthlyPay(completedShifts, settings)` from the ViewModel.
Returns `null` when `UserSettings.hourlyRate` is null or zero.
Uses Israeli payroll tiers (100/125/150% weekday, 150/175/200% Shabbat/holiday).

**Weekly breakdown:**
Computed from the selected month's completed shifts via `WeeklyBreakdownBuilder.groupByWeek()`.
Shows ISO Monday-anchored week start date, total hours, shift count, and optional
delta vs the overlapping week in the previous month (`prevMonthMinutes`).

**PDF export behaviour:**
- **Hours tab:** `ReportExporter.shareShiftPdf` — monthly shift summary PDF via share sheet.
- **Refunds tab:** `ReportExporter.shareRefundPdf` — reimbursement packet with claim details and embedded receipt images (when signed URLs resolve).

**Insights gating:**
When `featuresInsights` is off, Reports shows a disabled-state card instead of daily insight cards and the shift insights grid.

**CSV export behaviour:**
- Columns: `Date, Start Time, End Time, Gross Min, Break Min, Net Min, Special Day, Notes[, Gross Pay]`
- `Gross Pay` column added only when hourly rate is set
- Filename: `elmtrackr-YYYY-MM.csv`
- Export fires `Intent.ACTION_SEND` with `text/plain` — share sheet handles file routing (email, Drive, etc.)
- Handles empty months safely (header-only CSV)

**Travel refund review:**
When `featuresTravelRefunds` is enabled in Settings, the Reports screen shows a section listing completed shifts with unresolved refund status (`null` or `REMIND_LATER`). Update the refund action from the Shifts tab.

---

## Native Android features

### Active-shift notification

When the user clocks in, a persistent notification appears immediately in the
status bar and notification shade.

| Property | Value |
|---|---|
| Channel | `active_shift` (importance: LOW — no sound or heads-up) |
| Title | "Clocked in" |
| Content | "Since HH:mm" plus a count-up chronometer (`setUsesChronometer(true)`) rendered by SystemUI |
| Style | Ongoing (`setOngoing(true)`) — **user-dismissible on Android 14+** |
| Tap | Opens the app to the Dashboard |
| Action button | "Clock Out" — clocks out without opening the app |

The notification is managed by `ActiveShiftNotificationManager` and driven by
`ActiveShiftSideEffectsCoordinator.startNotificationObserver()`, which collects the
Room `observeActiveShift` flow for the lifetime of the app process. It fires on
clock-in and cancels itself automatically on clock-out.

**Two known gaps, neither fixed.** There is no foreground service anywhere in the
app (no `startForeground` call), so a running shift is a Room row plus this
notification:

- On Android 14+ an ongoing notification can be swiped away. Dismissing it leaves
  the shift running with no visible affordance and no Clock Out action until the
  app or a widget is opened. The row and the elapsed time are safe — everything
  derives from the stored `startTime` — but the user has no way to see or end the
  shift from outside the app.
- The observer above lives in the app process. An OEM task-killer takes it with the
  process, and nothing re-posts the notification until something calls
  `ActiveShiftRestorer.restore()`: boot (`BootCompletedReceiver`), a date/time
  change (`WidgetDateChangeReceiver`), or a notification-permission grant
  (`MainActivity`). Opening the app also re-establishes the observer.

Promoting the shift to a foreground service is the fix. It was scoped and
deliberately not done: from Android 12 the background-start restrictions apply to
exactly the punch paths that need it most (widget, Wear, shortcut), so which
exemption covers a widget tap has to be confirmed against current platform docs and
on hardware before the service is wired in. Shipping it unverified risks turning a
widget punch into a `ForegroundServiceStartNotAllowedException`, which is worse than
the gap it closes.

### Clock-out from notification

Tapping "Clock Out" in the notification fires `ClockOutReceiver` (a
`BroadcastReceiver`). The receiver:

1. Calls `LocalShiftsRepository.clockOut(shiftId)` — writes to Room, sets endTime.
2. `SyncTrigger.schedule()` is called inside `clockOut`, queuing a WorkManager sync job.
3. Cancels the active-shift notification immediately.
4. Cancels the long-shift reminder notification.
5. **Never calls Supabase directly.**

No app launch, no internet required. The shift is persisted offline and synced
when a connection is next available.

### Long-shift reminder

When the user clocks in, `ElmTrackrApp` schedules `LongShiftReminderWorker` (a
`CoroutineWorker`) with a delay equal to the user's daily overtime threshold
(default: 480 minutes / 8 hours if settings are unavailable).

When the worker fires, it checks:
- Is the user still clocked in? If not, exits silently.
- Has the shift exceeded the threshold? If not, exits silently.
- If yes → shows one dismissible reminder notification ("You're still clocked in").

The reminder uses a separate channel (`reminders`, importance: DEFAULT) so it
produces a sound/heads-up. It is auto-dismissible. It will not repeat unless the
user clocks out and back in again.

When the user clocks out (any path — UI or notification action), the WorkManager
job is cancelled via `cancelUniqueWork`.

**Known limitation:** If the app process is killed and restarted mid-shift, the
reminder is rescheduled from the current time rather than the original clock-in
time. The worker verifies elapsed time at run time (using the shift's actual
`startTime`), so it will still show correctly once it fires; only the delay
resets.

### Notification permission (Android 13+)

On Android 13+ (API 33), `POST_NOTIFICATIONS` is a runtime permission.
`MainActivity.onCreate()` requests it via the Activity Result API on first launch.

- If granted: all notifications work normally.
- If denied: the app continues to function. Clock-in/out work exactly the same.
  Only the status-bar notification and reminder are suppressed.
- The `POST_NOTIFICATIONS` permission is declared in `AndroidManifest.xml` for
  forward compatibility; on API < 33 it is silently ignored by the system.

### Home screen widgets

ElmTrackr includes **five** Jetpack Glance widget styles aligned with the product
mockups. Each uses a **single stateful Punch In / Out control** that performs the
action headlessly (no app launch required).

| Widget picker name | Size | Design |
|---|---|---|
| **ElmTrackr Single Toggle** | 4×1 | Logo + status + live `H:MM:SS` timer + white/outlined pill CTA |
| **ElmTrackr Progress** | 4×1 | Day-goal progress bar + `today / 8h` + round toggle |
| **ElmTrackr Tall** | 4×2 | Oversized clock + full-width action bar at base |
| **ElmTrackr Ring** | 1×1 | Open ring → filled ring when on shift; whole tile toggles |
| **ElmTrackr Big Action** | 1×1 | Large white circle punch-in; stop glyph + corner timer when active |

| Property | Value |
|---|---|
| Library | Jetpack Glance 1.1.0 (`androidx.glance:glance-appwidget`) |
| Day goal | `UserSettings.dailyOvertimeThresholdMinutes` (default 8h) |
| Live timer | `H:MM:SS` while clocked in; refreshes every 60s via `WidgetRefreshWorker` |
| Update trigger | `ElmTrackrApp.startActiveShiftObserver()` → `ElmTrackrWidgetUpdater.update()` |

**Widget states:**

| State | Display | Action button |
|---|---|---|
| Idle | Last punch time or today's logged total | **PUNCH IN** (white filled pill / round button) |
| Active | Live elapsed `H:MM:SS` + since time | **PUNCH OUT** (outlined pill with stop glyph) |

Tapping the status/time/logo area opens the app. The CTA always matches state and
clocks in/out via `WidgetActions` without opening the UI.

**Key classes:** `WidgetLayouts`, `WidgetPreferences.DisplayState`, `WidgetStateMapper`,
`WidgetRefreshWorker`, `ElmTrackrWidgetUpdater`

### App shortcuts

**Static shortcut (always available):**

| Shortcut | Action |
|---|---|
| "New Shift" / "Log a Manual Shift" | Opens the app (navigate to Shifts tab) |

Defined in `res/xml/shortcuts.xml`, registered via `<meta-data>` on `MainActivity`.

**Dynamic shortcuts (updated when shift state changes):**

| State | Shortcut shown |
|---|---|
| Clocked out | "Clock In" / "Clock In to ElmTrackr" |
| Clocked in | "Clock Out" / "Clock Out of ElmTrackr" |

Both dynamic shortcuts open the app to the Dashboard where the action can be
completed. Managed by `ElmTrackrApp.updateDynamicShortcuts()`, called whenever
the active-shift observer fires.

**Clock Out dynamic shortcut:** Uses a headless trampoline activity that clocks out without opening the main UI, then shows a brief confirmation notification.

**Theme:** Changes apply immediately across the app (no restart required). System theme tracks device dark-mode changes while "System default" is selected.

**Timezone:** Searchable IANA timezone picker in Settings (Payroll → Location).

**Known limitations:**
- Bottom-nav blur uses a frosted mesh layer on Android 12+; older devices use translucent fill only.

---

## Roadmap

| Phase | What |
|---|---|
| ✅ 1 — Scaffold | Compose app, bottom nav, Aurora theme, CI pipeline |
| ✅ 2 — Domain models | Overtime / payroll calculators, shift stats |
| ✅ 3 — Local persistence | Room DB, DataStore preferences, repository interfaces |
| ✅ 4 — MVVM | ViewModels, StateFlow UI state, screen wiring |
| ✅ 5 — Auth foundation | Supabase auth, deep links, Account tab, tests |
| ✅ 6 — Data sync | Offline-first sync engine: Room + Supabase PostgREST + WorkManager |
| ✅ 7 — App shell | Auth-aware navigation, onboarding flow, 4-tab main shell, auth section in Settings |
| ✅ 8 — Auth & Onboarding UI | Usable auth screen (logo, password toggle, validation); full onboarding form |
| ✅ 9 — Dashboard | Live timer, 3 native clock styles (Classic/Minimal/Aurora), edit start time, month summary, recent shifts, sync status |
| ✅ 10 — Shifts | Full shift history, create/edit/delete (offline-first), date+time pickers, validation, compensation profile picker |
| ✅ 11 — Reports | Monthly summary, payroll estimate, weekly breakdown with prior-month deltas, CSV + PDF export, insights, travel refund review |
| ✅ 12 — Settings | Full settings form, feature toggles, theme, weekend days, sync section, account |
| ✅ 13 — Native features | Active-shift notification, clock-out action, long-shift reminder, app shortcuts |
| ✅ 14 — Home screen widgets | Five Glance styles (4×1 single-toggle, 4×1 progress, 4×2 tall, 1×1 ring, 1×1 big action) |
| ✅ 15 — Refunds | CameraX receipt capture, gallery import, Supabase receipt storage/sync, refund claim management, reimbursement PDF export |
| ✅ 16 — Polish | Visual redesign, animations, headless shortcut clock-out, aurora mesh backgrounds, bottom-nav blur (API 31+), theme hot-reload, IANA timezone picker |

---

## Testing

### Unit tests

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

Covers payroll/overtime calculators, sync mappers, ViewModels, timezone helpers, and report builders.

### Instrumentation (device / emulator)

```bash
cd android && ./gradlew :app:connectedDebugAndroidTest
```

| Suite | What it covers |
|---|---|
| `MainActivitySmokeTest` | Main activity launches without crashing |
| `ScreenshotRegressionTest` | Compose golden screenshots for auth, onboarding, reports, shifts, dashboard skeleton |
| `ShiftDaoTest`, `SettingsDaoTest`, `ElmTrackrDatabaseMigrationTest` | Room DAO + migration integrity |

**Recording screenshot goldens** (on a device/emulator):

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.recordScreenshots=true
```

PNG files are written to the app's external files dir under `screenshots/`. Copy them into `app/src/androidTest/assets/goldens/` before committing updated baselines.

> Screenshot tests require golden PNGs in `androidTest/assets/goldens/`. CI currently runs unit tests only; connected tests are run locally or on a device farm before release.
