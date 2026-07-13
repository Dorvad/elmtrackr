# In-app updates — operator guide

This guide covers **manual steps in Google Play Console** and **release hygiene**
for ElmTrackr's in-app update system. Code lives under
`android/app/src/main/java/com/elmtrackr/app/update/`.

---

## What the app does now (after the reliability fix)

| Behavior | Detail |
|----------|--------|
| **Check timing** | Every time `MainActivity` resumes |
| **Flexible updates** | Play's system dialog; user can keep working |
| **Dismiss cooldown** | If user taps "No thanks", we wait **48 hours** before offering flexible again |
| **Immediate updates** | Blocking full-screen UI for high-priority or very stale releases |
| **Restart snackbar** | After a flexible download finishes (localized EN + HE) |
| **Play Store fallback** | If Play reports an update but in-app flows cannot start, a snackbar opens the listing (max once per **24 hours**) |
| **Diagnostics** | Sentry breadcrumbs when a DSN is configured and the user has not opted out |

**Important:** In-app updates only work for builds **installed from Google Play**.
Sideloaded APKs, CI artifacts, and emulators without Play silently skip checks.

---

## 1. Confirm which builds can ever show prompts

Users on APKs **without** the in-app update code (shipped before 29 June 2026, or
production `versionCode 9` uploaded before commit `638fe49`) **cannot** be
prompted in-app. They must update manually from the Play Store listing.

| versionCode | In-app updates in APK? |
|-------------|------------------------|
| 1–8 | No |
| 9 | Only if built from `638fe49` or later |
| 10+ (`1.1.0`) | Yes |

**Action:** In Play Console → **Release** → **App bundle explorer**, open each
production release and note the upload date. If `versionCode 9` went live before
the in-app-update merge, treat all `≤ 9` users as needing a **manual** Play
Store visit until they reach `10+`.

---

## 2. Set update priority for every production release

Play's `inAppUpdatePriority` is configured **per release in Play Console**, not
in this repo.

| Priority | App behavior |
|----------|--------------|
| **0** (default) | Flexible, dismissible dialog |
| **1–3** | Still flexible (unless stale — see below) |
| **4–5** | **Immediate** blocking update UI |

The app also escalates to immediate when the user is **≥ 30 days** behind
(`IMMEDIATE_STALENESS_DAYS` in `InAppUpdatePolicy.kt`).

### Steps (each new production upload)

1. Play Console → **Release** → **Production** (or testing track) → select the release.
2. Open **Release details** / **In-app update priority** (wording varies by Console version).
3. Set priority:
   - **0** — routine features, copy tweaks, non-urgent fixes.
   - **4 or 5** — crash fixes, data-loss fixes, security patches you need everyone on quickly.
4. Save and roll out.

> For the next upload after this fix, use **priority 4** so users still on old
> builds get the blocking immediate flow and pick up cooldown / fallback /
> crash fixes without dismissing a flexible dialog.

---

## 3. Staged rollout checklist

During a staged rollout, users **outside the cohort** get `UPDATE_NOT_AVAILABLE`
→ **no in-app prompt** (correct Play behavior).

| Step | Action |
|------|--------|
| Before rollout | Decide target % (e.g. 20% → 50% → 100%) |
| During partial rollout | Expect support messages from users not yet in cohort — normal |
| After incidents | Pause rollout in Console before shipping a hotfix |
| Full availability | Wait until rollout is **100%** before assuming all users can be prompted |

---

## 4. Version code hygiene (required every upload)

In `android/app/build.gradle.kts` (and `wear/build.gradle.kts`):

```kotlin
versionCode = 11   // must increase monotonically — Play rejects duplicates
versionName = "1.1.1"
```

**Action for your next release:**

1. Bump `versionCode` (currently **10** → use **11** or higher).
2. Bump `versionName` for user-facing release notes.
3. Build release AAB: `cd android && ./gradlew :app:bundleRelease`
4. Upload to Play Console.
5. Set **in-app update priority** (§2).
6. Roll out (staged or full).

---

## 5. Enable crash + update diagnostics (Sentry)

Update checks emit Sentry breadcrumbs (`category: in_app_update`) when:

- `sentry.dsn=...` is in `android/local.properties` (local) or `SENTRY_DSN` CI secret, **and**
- The user has not disabled **Settings → Help & About → Share crash reports**.

### One-time setup

1. Create a Sentry Android project at [sentry.io](https://sentry.io).
2. Add to `android/local.properties`:
   ```properties
   sentry.dsn=https://<key>@o<org>.ingest.sentry.io/<project>
   ```
3. For CI: add `SENTRY_DSN` repository secret and write it into `local.properties` in the workflow.
4. Ship an internal build, trigger an update check on a Play-installed device, and confirm breadcrumbs appear on events.

### Useful breadcrumb messages

| Message | Meaning |
|---------|---------|
| `update_check` | Play responded; includes `action`, `priority`, `version_code` |
| `flexible_update_dismissed` | User tapped "No thanks" on flexible dialog |
| `update_flow_failed` | Play flow failed (`result_code`) |
| `play_store_fallback` | Snackbar shown because in-app flow could not start |

---

## 6. Verify on a real Play-installed device

Do **not** test with a sideloaded CI APK — checks are intentionally skipped.

1. Install from **Internal testing** or **Closed testing** track (not `adb install` of a local APK).
2. Publish a **higher** `versionCode` to the same track.
3. Open the app on the older build.

| Scenario | Expected |
|----------|----------|
| Priority 0, flexible | Play bottom-sheet dialog once; dismiss → no re-prompt for 48h |
| Priority 4+, immediate | Full-screen blocking update |
| Accept flexible, wait for download | Localized "Restart" snackbar |
| In-app flow blocked | "Update" snackbar → opens Play Store listing |

Filter Logcat by tag `InAppUpdate` for local debugging.

---

## 7. Communicate with users on very old builds

Users on `versionCode ≤ 9` (pre-in-app-update production builds) will **never**
see an in-app prompt. Options:

1. **Play Console → Release notes** — ask users to update from the store.
2. **In-app** — only works after they reach a build that contains update code (`10+`).
3. **Future** — consider a remote `minSupportedVersionCode` gate (not implemented yet).

---

## Quick reference: why someone might still not see a prompt

| Cause | Fix |
|-------|-----|
| APK too old (no update code) | User must open Play Store manually |
| Not installed from Play | Expected; sideload/CI builds skip checks |
| Staged rollout | Wait for cohort or increase % |
| Dismissed flexible dialog | Wait 48h or kill app won't help until cooldown ends |
| Default priority 0 | Set priority ≥ 4 for critical releases |
| Play propagation delay | Wait a few hours after upload |
| Sentry not configured | §5 — optional but recommended for visibility |

---

## Files changed in the reliability fix

| File | Role |
|------|------|
| `InAppUpdateManager.kt` | Cooldown, fallback, diagnostics |
| `InAppUpdateCooldown.kt` | 48h / 24h windows (unit tested) |
| `InAppUpdatePromptStore.kt` | Persists dismiss / fallback timestamps |
| `InAppUpdateHost.kt` | Localized snackbars |
| `PlayStoreLinks.kt` | Opens `market://` or web listing |
| `UpdateDiagnostics.kt` | Sentry breadcrumbs |
| `strings_update.xml` / `values-iw` | English + Hebrew copy |
