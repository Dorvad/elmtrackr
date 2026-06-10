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

### Data sync note

Authentication is implemented in this phase; **data sync to Supabase is not yet
implemented**. Shifts, settings, and refund claims are stored locally in Room.
The `syncStatus` field on every entity tracks which records are pending upload
for when sync is added.

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

## Roadmap

| Phase | What |
|---|---|
| ✅ 1 — Scaffold | Compose app, bottom nav, Aurora theme, CI pipeline |
| ✅ 2 — Domain models | Overtime / payroll calculators, shift stats |
| ✅ 3 — Local persistence | Room DB, DataStore preferences, repository interfaces |
| ✅ 4 — MVVM | ViewModels, StateFlow UI state, screen wiring |
| ✅ 5 — Auth foundation | Supabase auth, deep links, Account tab, tests |
| 6 — Data sync | Upload local data to Supabase once authenticated |
| 7 — Core screens | Shifts list/detail, new shift form, full payroll view |
| 8 — Reports | Weekly chart, overtime breakdown, pay summary |
| 9 — Refunds | Travel refund claims, CameraX receipt capture |
| 10 — Notifications | WorkManager "forgot to clock out" reminder, Glance widget |
