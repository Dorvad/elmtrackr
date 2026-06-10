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
4. Runs `./gradlew testDebugUnitTest` (non-blocking until tests are written).
5. Uploads `app-debug.apk` as a downloadable artifact — **no secrets needed**.

Workflow file: [`.github/workflows/android.yml`](../.github/workflows/android.yml)

---

## Downloading the APK from GitHub Actions (for your tablet)

This is the fastest way to get the latest build onto a device without a USB
cable.

**Step 1 — Find the run**

1. Go to the repo on GitHub.
2. Click the **Actions** tab.
3. In the left sidebar, click **Android CI**.
4. Click the most recent green run (or any run you want).

**Step 2 — Download the artifact**

5. Scroll to the bottom of the run summary page.
6. Under **Artifacts**, click **ElmTrackr-debug**.
7. A `ElmTrackr-debug.zip` downloads — unzip it to get `app-debug.apk`.

**Step 3 — Side-load onto your Android tablet**

8. Transfer the `.apk` to your tablet (email, Google Drive, AirDrop-equivalent,
   USB, etc.).
9. On the tablet: **Settings → Apps → Special app access →
   Install unknown apps** → allow your file manager or browser.
10. Open the `.apk` and tap **Install**.

> **Note:** Debug APKs are signed with a local debug keystore and are meant for
> development / sideloading only. They are not suitable for distribution via the
> Play Store.

---

## Why CI and not a local build?

| Scenario | Can build? | Reason |
|---|---|---|
| Your machine with Android Studio | ✅ Yes | Full SDK installed |
| GitHub Actions | ✅ Yes | ubuntu-latest has Android SDK; platform-35 is fetched from Google Maven |
| Claude Code / cloud AI session | ❌ No | `dl.google.com` returns HTTP 403; Android SDK absent |

The workflow is intentionally **secret-free** — it produces an unsigned debug APK
using only the default `GITHUB_TOKEN` (read-only).

---

## Project structure

```
android/
├── settings.gradle.kts       # Gradle project root
├── build.gradle.kts          # Root build — plugin declarations
├── gradle.properties         # Daemon off in CI, AndroidX enabled
├── gradle/
│   ├── libs.versions.toml    # Version catalog (AGP, Kotlin, Compose BOM…)
│   └── wrapper/              # Gradle 8.14.3 wrapper
└── app/
    ├── build.gradle.kts      # compileSdk 35, minSdk 26, Compose enabled
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/elmtrackr/app/
            ├── ElmTrackrApp.kt        # Application subclass
            ├── MainActivity.kt        # Single activity, edge-to-edge
            ├── navigation/
            │   ├── AppNavGraph.kt     # Scaffold + NavHost
            │   └── BottomNavItem.kt   # Dashboard / Shifts / Reports / Settings
            ├── ui/
            │   ├── theme/             # Aurora color palette, M3 typography
            │   ├── dashboard/         # Clock In/Out widget + month stats
            │   ├── shifts/            # Placeholder (Phase 2)
            │   ├── reports/           # Placeholder (Phase 2)
            │   └── settings/          # Placeholder (Phase 2)
            ├── domain/                # Business logic (Phase 2 — overtime, payroll)
            ├── data/                  # Repositories + Supabase client (Phase 2)
            └── sync/                  # WorkManager sync jobs (Phase 2)
```

---

## Roadmap

| Phase | What |
|---|---|
| ✅ 1 — Scaffold | Compose app, bottom nav, Aurora theme, CI pipeline |
| 2 — Auth + data | Supabase-kt, login screen, shift repository, Settings screen |
| 3 — Core screens | Shifts list/detail, new shift form, payroll calculation |
| 4 — Reports | Weekly chart, overtime breakdown, pay summary |
| 5 — Refunds | Travel refund claims, CameraX receipt capture |
| 6 — Notifications | WorkManager "forgot to clock out" reminder, Glance home widget |
