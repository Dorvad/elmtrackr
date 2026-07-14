# ElmTrackr — Release Checklist (first public release)

Step-by-step guide for taking the Android app from this repository to the Play
Store. Items marked **[manual]** must be done by a human outside this repo;
everything else is verified by code or CI.

---

## 0. Pre-flight decision: application ID

`app/build.gradle.kts` now sets:

```kotlin
applicationId = "com.elmlaunch.myapp"
```

**This identifier becomes permanent the moment the app is first published** —
it can never be changed afterwards, and it is visible to users in the Play
Store URL (`play.google.com/store/apps/details?id=...`). The previous
placeholder package has been replaced with the existing ElmLaunch ID before
first upload.

> Changing the ID before the store release is safe, but any previously
> sideloaded debug/CI builds will appear as a separate app on testers' devices.

---

## 1. Upload keystore **[manual — do this on your own computer, not a shared machine]**

The keystore is the key that proves future updates come from you.

### 1.1 Generate it

With a JDK installed (Android Studio ships one), run:

```bash
keytool -genkeypair -v \
  -keystore elmtrackr-release.jks \
  -alias elmtrackr \
  -keyalg RSA -keysize 4096 \
  -validity 9125 \
  -dname "CN=ElmTrackr, O=<your company>, C=IL"
```

It will prompt for a keystore password — use a long, generated password.
`-validity 9125` is 25 years; Play requires at least until 2033.

### 1.2 Store it safely (in this order of importance)

1. Save the `.jks` file and both passwords in a **password manager** entry.
2. Keep a second copy of the `.jks` on an **offline medium** (encrypted USB
   drive or company vault) — not only on one laptop.
3. **Never commit it to git.** This repo's `.gitignore` already excludes
   `keystore/*.jks`, and CI builds fall back to debug signing when the file is
   absent (debug-signed builds are rejected by Play, so nothing wrong can ship).

### 1.3 Point the build at it

On the machine that produces release builds, add to `android/local.properties`
(this file is git-ignored):

```properties
KEYSTORE_PATH=keystore/elmtrackr-release.jks
KEYSTORE_PASSWORD=<store password>
KEY_ALIAS=elmtrackr
KEY_PASSWORD=<key password>
```

and place the `.jks` in `android/keystore/`.

### 1.4 Enroll in Play App Signing **[manual]**

When you create the app in Play Console (step 6), accept **Play App Signing**
(it is the default for new apps). Google then holds the *app signing key* and
your `.jks` is only the *upload key* — if the upload key is ever lost or
leaked, Google can issue a new one. This is your safety net; do not opt out.

---

## 2. Release-build QA (R8) — run before every upload

The release build is minified by R8; test **it**, not the debug build.

```bash
cd android
./gradlew :app:bundleRelease        # phone .aab for Play upload
./gradlew :wear:bundleRelease       # watch .aab for Play upload (same listing)
./gradlew :app:assembleRelease      # .apk for local device testing
adb install app/build/outputs/apk/release/app-release.apk
```

> **Watch app delivery.** The watch app is a separate artifact under the same
> application id (`com.elmlaunch.myapp`), not an APK embedded in the phone build.
> Upload **both** `.aab`s to the same Play Console app: the phone bundle and
> `wear/build/outputs/bundle/release/wear-release.aab`. Play then delivers the
> watch app to a paired watch automatically. Both bundles must be signed with
> the same upload keystore (§1) — the `:wear` module reads it from
> `local.properties` exactly like `:app`.

Walk the entire flow on a real device, in this order (these are the areas R8
most commonly breaks — serialization, reflection, deep links):

- [ ] Fresh install → onboarding completes and settings persist
- [ ] Sign-up → confirmation email → `elmtrackr://auth/callback` opens the app
- [ ] Password reset → `elmtrackr://auth/reset-password` opens the app
- [ ] Clock in / clock out (dashboard, widget, notification action, watch)
- [ ] Background sync completes; Sync Details shows no failed rows
- [ ] Monthly report renders; CSV and PDF export & share
- [ ] Refund flow: camera capture, ML Kit scan, amount extraction, PDF packet
- [ ] Compensation rules edit + per-shift pay breakdown matches expectations
- [ ] Biometric app lock (enable, background the app, reopen)
- [ ] Account deletion completes and returns to auth screen

---

## 3. Hebrew / RTL walkthrough **[manual — device in Hebrew]**

Status: string resources have full parity (1,027 strings in both `values/` and
`values-iw/`, verified July 2026). What still needs human eyes:

Set the in-app language to עברית (or device language to Hebrew) and check
every screen for: leftover English, truncated text, wrong alignment, and
directional icons that failed to mirror.

- [ ] Onboarding (all 9 steps)
- [ ] Dashboard incl. getting-started checklist, all 15 clock faces
- [ ] Shifts list + create/edit form + pickers
- [ ] Reports incl. insights carousel, exports (check the PDF renders Hebrew)
- [ ] Settings: every hub destination, dialogs, snackbars
- [ ] Refund flow incl. camera overlays
- [ ] Home-screen widgets (all five), persistent notification, overtime
      reminders (background surfaces localize via `withAppLocale()` on ≤ 12 —
      test on an old device if available)
- [ ] Wear OS app, tile, and complication
- [ ] Known intentional Latin text: "English" (language endonym), "CSV"/"PDF"

---

## 4. Crash reporting (Sentry) **[manual: one-time setup]**

The app initializes Sentry only when (a) the build has a DSN and (b) the user
has not turned off **Settings → Help & About → Share crash reports** (default
on, PII off, no tracing). Without a DSN the feature is compiled out entirely.

1. Create a free account at sentry.io → create an **Android** project.
2. Copy the DSN (looks like `https://<key>@o<org>.ingest.sentry.io/<id>`).
3. Local release builds: add `sentry.dsn=<DSN>` to `android/local.properties`.
4. CI builds: add a `SENTRY_DSN` repository secret and write it into
   `local.properties` in the workflow (same pattern as `SUPABASE_URL`).
5. Update the privacy policy to disclose crash diagnostics collection and the
   in-app opt-out.
6. After the first internal build: force a test crash on a device and confirm
   the event arrives in Sentry.

---

## 5. Device / API matrix **[manual]**

Minimum coverage before production rollout:

| Device class | Why |
|---|---|
| API 26–28 phone (e.g. an old test device or emulator) | `withAppLocale()` fallback paths, legacy widget behavior |
| Current mid-range phone (API 34+) | The majority of real users |
| Small-screen / low-RAM device | Dashboard + checklist layout, performance |
| Tablet (or large-screen emulator) | Side-rail navigation layout |
| Wear OS watch paired to the phone | Punch sync, tile, complication |

For each: light + dark theme, Hebrew + English, and the Play Console
**pre-launch report** (runs automatically on internal-track uploads — treat
every crash it finds as a blocker).

---

## 6. Play Console — first-time publisher path **[manual]**

1. **Create a developer account** at play.google.com/console ($25 one-time,
   requires identity verification — allow a few days).
2. **Create app** → name "ElmTrackr", default language Hebrew, type App, Free.
3. Complete the **app content** declarations: privacy policy URL, data safety
   form (accounts: email; user content: shifts, receipts; encrypted in
   transit; deletable in-app **and via a web deletion URL** — Play requires
   the web link for apps with account creation), ads declaration (none),
   content rating questionnaire, target audience.
4. **Store listing**: Hebrew + English title/descriptions, screenshots
   (lead with the pay breakdown and clock faces), 1024×500 feature graphic,
   512×512 icon.
5. **Internal testing track**: upload the `.aab`, add tester emails, verify
   install via the opt-in link, watch the pre-launch report.
6. **Closed testing**: 10–20 real users for at least two weeks.
   (Note: personal accounts created after Nov 2023 are *required* to run a
   closed test with 12+ testers for 14 days before production access —
   organization accounts are exempt. Check which rule applies to yours.)
7. **Production**: staged rollout at 10–20%, watch Android Vitals + Sentry for
   a few days, then raise to 100%.

For each new upload: bump `versionCode` (and `versionName` when user-facing)
in `app/build.gradle.kts` — Play rejects reused version codes.

---

## Quick status summary

| Blocker | State |
|---|---|
| Application ID decision | Done — app ID is `com.elmlaunch.myapp` (§0) |
| Upload keystore | Guide ready (§1); generate + store manually |
| Release signing config | Done — safe debug fallback when keystore absent |
| R8 release build verified | Done in CI-equivalent build; re-verify on device (§2) |
| Hebrew string parity | Done (1,027/1,027); device walkthrough pending (§3) |
| Crash reporting | Done — opt-out toggle shipped; DSN setup pending (§4) |
| Device matrix | Pending (§5) |
