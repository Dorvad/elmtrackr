# ElmTrackr — Native Android App

Native Kotlin + Jetpack Compose app that lives in this directory alongside the
Next.js web app. The two share the same Supabase backend but have completely
separate codebases. **No WebView, no Capacitor.**

```
elmtrackr/
├── android/          ← you are here (native Android)
├── app/              ← Next.js web app (untouched)
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

### Supabase dashboard configuration

In your Supabase project → **Authentication → URL Configuration**, add:

| Field | Value |
|---|---|
| Redirect URL | `elmtrackr://auth/callback` |

This deep-link scheme is used for email confirmation, password reset, and
any OAuth callbacks added later.

### Behavior when credentials are missing

When `local.properties` is absent (or the keys are blank) — the common case on CI
and fresh checkouts — the app builds and runs normally. The **Account** tab shows
a "Auth not configured" message instead of a sign-in form. All local shift
tracking continues to work without authentication.

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
| Android SDK platform | API 35 (Android 15) | via SDK Manager |
| Build Tools | 35.0.0 | via SDK Manager |
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
3. Runs `./gradlew :app:assembleDebug`.
4. Runs `./gradlew testDebugUnitTest`.
5. Uploads `app-debug.apk` as a downloadable artifact — **no secrets needed**.

CI builds without `local.properties` — Supabase keys default to empty strings,
`SupabaseClientProvider.isConfigured()` returns `false`, and the app shows the
"not configured" state on the Account tab. Auth unit tests use `FakeAuthRepository`
and never touch the network.

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
            ├── ElmTrackrApp.kt         # Application — manual DI container
            ├── MainActivity.kt         # Single activity; handles auth deep links
            ├── navigation/
            │   ├── AppNavGraph.kt      # Scaffold + NavHost (5 tabs incl. Account)
            │   └── BottomNavItem.kt    # Dashboard/Shifts/Reports/Settings/Account
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
        ├── shifts     ← ShiftsScreen     (placeholder — not yet polished)
        ├── reports    ← ReportsScreen    (monthly report + month navigator)
        └── settings   ← SettingsScreen  (preferences + auth/sign-out section)
```

### Routing logic (`AppShellViewModel`)

| Auth configured | Profile | Onboarding done | Route |
|---|---|---|---|
| any | — | — | `loading` (initial) |
| no | — | yes | `main` |
| no | — | no | `onboarding` |
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
| Settings | `SettingsViewModel` — user settings; auth section from `AuthViewModel` |
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

### Placeholder screens

- **Shifts** — scaffold only; list/detail UI will be added in the next phase.
- **Dashboard** — clock in/out and monthly stats are functional. Charts and payroll detail are not yet implemented.
- **Reports** — monthly summary and month navigator are functional. Weekly chart is not yet implemented.

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
| 9 — Core screens | Shifts list/detail, new shift form, full payroll view |
| 10 — Reports | Weekly chart, overtime breakdown, pay summary |
| 11 — Refunds | Travel refund claims, CameraX receipt capture |
| 12 — Notifications | WorkManager "forgot to clock out" reminder, Glance widget |
