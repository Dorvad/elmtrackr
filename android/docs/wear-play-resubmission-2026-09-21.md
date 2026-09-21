# Wear OS resubmission after the 10055 rejection — review, fixes, and the manual steps

**Written:** 21 September 2026, against commit `8691411` on `Main`.
**Rejected artifact:** wear **10055** (17 September 2026), two findings.
**Next artifacts:** phone **56** / wear **10056**, versionName **1.3.3** — already
set in both `build.gradle.kts` files. Confirm in Play Console that neither code
has ever been uploaded before building (§B step 2); if one has, bump both.

> **Issue found: Wear app functionality not working as described** … *Your app
> crashed when testing.*
>
> **Issue found: Play listing description** … *Your play listing description
> doesn't mention tile or complication although it's included in your app.*

The second finding is not fixable in this repository. It has failed every Wear
review that left the listing untouched, and it fails on its own even when the
watch is perfect. Section B is the manual work, in order. Section A is what this
round changed in code and why.

---

## A. Code review — what was found and what changed

### A.1 What was verified rather than assumed

- **`:wear-sync` and `:wear` unit tests** run green with the real toolchain
  (Gradle 9.1, AGP 9.0, Kotlin 2.3.21, JDK 21), including the Robolectric
  launch-path suite: `Application`, `WearMainActivity` create→resume→destroy
  at default, 1.5× (API 30) and 2.0× font scale and on a round canvas, the data
  layer listener, the complication provider, and the tile trampoline.
- **`:wear:assembleRelease`** (R8 full mode + resource shrinking) builds, and the
  merged release manifest carries `standalone=true`, the exported trampoline,
  the tile and complication services, and the `androidx.startup` removals for
  WorkManager and ProfileInstaller.
- **Both CI workflows are green** on the branches that were merged into `Main`
  on 21 September; the merge commit's own run was still in progress at the time
  of writing — check it before building (§B step 3).

### A.2 Defects fixed in this round

1. **A punch took 10 seconds when the paired phone did not have ElmTrackr.**
   The watch treated every connected node as an ElmTrackr phone and waited the
   full acknowledgement budget (10 s) before recording the punch locally. A
   reviewer testing a standalone watch app almost always has it paired to a phone
   *without* the app. Their experience of 10055: tap the tile, nothing visible for
   ten seconds; tap PUNCH IN in the app, a 3-second countdown, then a ten-second
   spinner. That reads as "functionality not working as described" even when
   nothing crashes.

   The phone app now advertises a Wear capability (`elmtrackr_phone_app`, in
   `app/src/main/res/values/wear.xml`, pinned against the resource shrinker by
   `res/raw/keep.xml`), and the watch asks for it before sending. A node that
   advertises it is a confirmed ElmTrackr phone and keeps the 10 s budget. Any
   other connected node — a phone without the app, or one on a build older than
   this one — is still tried, so nothing regresses during the update window, but
   with a 2.5 s budget and a 1 s refresh poll instead of 3 s. Selection logic is
   pure (`WearPhoneReach` in `:wear-sync`) and unit-tested.

2. **The complication provider could take the process down.** `onComplicationRequest`
   caught exceptions and returned *preview* data — a fake "clocked in 1:00" — as
   if it were live, and if building the preview threw too, the exception left a
   `SuspendingComplicationDataSourceService` coroutine unhandled, which is a
   process death while a reviewer is adding the complication. `getPreviewData`,
   which the watch-face editor calls on the main thread with no framework guard,
   had no guard of its own either. Both now degrade in order: live data → the
   signed-out face → an empty slot (`NoDataComplicationData`), and a failed build
   is reported to crash reporting. A new launch-path test builds the preview for
   all three supported types.

3. **The phone could crash on a watch message.** `WearMessageListenerService`
   served the watch's REFRESH by calling `WearSyncPublisher.refresh()` on a scope
   with no exception handler. Only the publish half of `refresh()` guards itself;
   the database read before it did not. A Room failure there was an uncaught
   exception on a background coroutine — a phone crash triggered by the watch
   coming into range. The scope now reports and swallows, and the refresh call is
   wrapped.

### A.3 Reviewed and left alone, with the reason

- **Standalone punches with no user are dropped when a phone appears.** A punch
  made on a watch that has never synced carries an empty `userId`, and
  `syncPendingWithPhone` discards such events rather than attributing them to
  whoever is signed in on the phone. That is a deliberate product decision
  recorded in the cursor commits of 15–21 September (a wrong attribution is worse
  than a lost minute), and changing it is the anonymous-identity work described in
  [wear-play-submission-runbook.md](wear-play-submission-runbook.md) §2. It is
  invisible to a reviewer and out of scope for a resubmission.
- **`Theme.Translucent.NoTitleBar` on the trampoline, black `Theme.ElmTrackrWear`,
  WorkManager on-demand init, `standalone=true`, exported trampoline with a
  per-install token** — all present, all asserted by `WearManifestContractTest`.
- **The crash Play saw on 10055 is still not reproduced.** No stack trace was
  ever received. What exists is (a) every launch-path component guarded and
  exercised on the JVM, (b) crash reporting on the watch since 10052, so the next
  failure produces a trace, and (c) two places in Play Console that may already
  hold the 10055 trace — §B step 1.

---

## B. Manual steps — Play Console and hardware, in this order

Console section names are as of September 2026 and move occasionally; if a name
below is not found, use the search box at the top of Play Console.

### Step 1 — Look for the 10055 crash before building anything (10 minutes)

Two places may already contain the stack trace nobody has:

1. **Monitor and improve → Android vitals → Crashes and ANRs.** Filter by
   version code **10055**. If a crash is listed, open it, copy the full stack
   trace into a new issue, and send it to whoever builds 10056 *before* the
   build. If the frames are obfuscated (single letters), upload
   `wear/build/outputs/mapping/release/mapping.txt` from the 10055 build machine
   as the deobfuscation file for 10055 (Step 6 has the path) and re-open the
   crash a few hours later.
2. **Test and release → Pre-launch report.** Open the report for the release that
   carried 10055. Look at the **Stability** tab for the watch devices. A crash
   there comes with a device model, an OS version and a stack trace.

If either shows a crash in ElmTrackr code, do not build 10056 until it is fixed.
If both are empty, proceed; Play's reviewer is then the only witness and §A.3
applies.

### Step 2 — Confirm the version codes are free (2 minutes)

**Test and release → App bundle explorer.** Search for **56** and **10056**. Both
must be absent. If either is present, bump `versionCode` in
`android/app/build.gradle.kts` and `android/wear/build.gradle.kts` together
(wear is always `10000 + phone`) and commit before building. Never bump by hand
on the build machine without committing — that is how 10055 came to be reviewed
while the repository said 10053.

### Step 3 — Confirm CI is green on the commit you will build (2 minutes)

GitHub → **Actions → Android CI**, the run for the `Main` commit you are about to
build. Both the *Build & test* and *Build release AAB* jobs must be green. The
wear lint step is advisory and may show warnings; a red job is a stop.

### Step 4 — Fix the store listing (15 minutes, fails on its own if skipped)

**Grow users → Store presence → Main store listing.**

For **every** language the listing is offered in (the listing languages, not the
app's — check the language selector at the top of the page), open the **Full
description** and add the paragraph for that language from
[play-listing-wear-copy.md](play-listing-wear-copy.md). English, Hebrew, Arabic
and Russian are prepared there. If the listing has a language that file does not
cover, translate the English paragraph and keep the two English words **tile**
and **complication** literally in it — the reviewer greps for those words in
every language.

Before saving each language:

1. Use the browser's find (Ctrl/Cmd+F) on the description for `tile`.
2. Find `complication`.
3. Confirm the description does not say "Android Wear" anywhere ("Wear OS" is
   fine).

Save, then **Publishing overview → Send changes for review** if the console asks
for it. The listing change can be reviewed together with the release; it does not
have to go first, but it must be *in* the review.

### Step 5 — Re-check the items Play re-verifies on every review (10 minutes)

1. **App access** — *Monitor and improve → Policy → App content → App access*.
   The reviewer account must be a real, email-confirmed account that signs in on
   a clean install of the phone app today. Test it before submitting. The
   instructions field should read, in substance: *"No sign-in is required to
   track a shift on the watch. To see cloud sync, sign in on the phone app with
   the credentials provided; the tile and complication reflect the phone's shift
   once the paired phone is signed in."*
2. **Wear screenshots** — *Main store listing → Wear OS screenshots*. Each must
   be the app's own pixels only: no watch frame, no shadow, no caption, no
   background, square, 384–3840 px, PNG or JPEG. Capture from a watch or Wear
   emulator with `android/tools/capture-wear-screenshots.sh` and validate with
   `android/tools/check-wear-screenshots.py`. Include one of the **tile** and one
   of a watch face with the **complication** — the description now promises both.
3. **Data safety** — *App content → Data safety*. Must still declare crash logs,
   diagnostics and the per-install identifier per
   [play-data-safety.md](play-data-safety.md) §3. This was a separate rejection in
   September; it is re-checked every time.
4. **Form factor** — *Test and release → Setup → Advanced settings → Form
   factors*. Wear OS must be listed. If it is not, add it here before uploading;
   otherwise the watch bundle is accepted but never reviewed as a Wear app.

### Step 6 — Build both artifacts from the same commit (20 minutes)

On the release machine, with `android/local.properties` holding the upload
keystore properties and the Sentry DSN:

```bash
cd android
git checkout Main && git pull
git log -1 --oneline            # note the commit; it goes in the release notes
./gradlew clean :app:bundleRelease :wear:bundleRelease
```

Outputs:

| Artifact | Path |
|---|---|
| Phone bundle (56) | `app/build/outputs/bundle/release/app-release.aab` |
| Watch bundle (10056) | `wear/build/outputs/bundle/release/wear-release.aab` |
| Phone mapping | `app/build/outputs/mapping/release/mapping.txt` |
| Watch mapping | `wear/build/outputs/mapping/release/mapping.txt` |

Keep the two mapping files with the bundles. Verify both bundles are signed with
the upload key, not the debug key:

```bash
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab | grep -i "owner\|sha256"
keytool -printcert -jarfile wear/build/outputs/bundle/release/wear-release.aab | grep -i "owner\|sha256"
```

Both must print the same certificate, and it must be the one shown under
*Test and release → Setup → App signing → Upload key certificate*. A debug
certificate ("CN=Android Debug") means `local.properties` is missing the
keystore lines; fix and rebuild.

### Step 7 — Test the watch release build on hardware or an emulator (45 minutes)

This has never been done for any rejected build, and it is the only way to see
what the reviewer sees. Build an installable watch APK from the same commit:

```bash
./gradlew :wear:assembleRelease
adb -s <watch-serial> install -r wear/build/outputs/apk/release/wear-release.apk
adb -s <watch-serial> logcat -c
adb -s <watch-serial> shell am start -n com.elmlaunch.myapp/com.elmtrackr.wear.WearMainActivity
adb -s <watch-serial> logcat -b crash -d      # must print nothing
```

Then, on the watch itself, in this order, and after each step run
`adb logcat -b crash -d` again:

1. Launch from the launcher; see the black face with the bolt and PUNCH IN.
2. Tap; let the 3-2-1 countdown finish; see "Clocked in", then the running ring.
3. Tap again; see "Clocked out", then the idle face with "Last out • Today".
4. Long-press the watch face → add the **ElmTrackr Punch** tile; swipe to it.
   Tap the tile's bolt: the watch vibrates within ~3 s and the tile switches to
   the running face. Tap again to stop.
5. Watch face editor → add the **ElmTrackr Status** complication to a slot; it
   shows OUT (or the elapsed time while running); tap it to open the app.
6. Settings → Display → Font size → largest. Repeat 1–3.
7. Force-stop the app (Settings → Apps → ElmTrackr → Force stop) and repeat 1.
8. If a phone is paired: with the phone app **not** installed, repeat 2 and 4
   and confirm the punch completes within about 3 seconds. Then install the phone
   app, sign in, and confirm a watch punch appears on the phone's dashboard.

Cover a round watch and, if available, a square one; Wear OS 3 (API 30) and the
newest available. A Wear OS emulator from Android Studio (Device Manager → Wear
OS → Pixel Watch, API 33 or 34) is acceptable for everything except the haptic in
step 4.

Any crash: `adb logcat -b crash -d > wear-crash.txt` and stop. The release build
keeps `SourceFile` and `LineNumberTable`, so the trace has file names and line
numbers; class names are renamed and are resolved with the watch `mapping.txt`
from step 6 (`retrace` ships in the Android SDK's `cmdline-tools`).

### Step 8 — Create the release (15 minutes)

**Test and release → Production** (or the testing track being used) →
**Create new release**.

1. Upload **both** bundles: `app-release.aab` and `wear-release.aab`. The
   artifact list must show **56** and **10056** and nothing else.
2. If the console offers to keep older artifacts (10055, 10052, 10041 or the
   previous phone code), set every one of them to **Not included**. A rejected
   watch code left in the release is what gets reviewed.
3. Release notes: one line per language; mention the watch fix in plain words,
   e.g. "Faster wrist punches when the phone is out of reach; stability fixes for
   the watch tile and complication."
4. After the upload finishes, open **App bundle explorer** → **10056** →
   **Downloads** tab → **Upload** next to *ReTrace mapping file*, and upload
   `wear/build/outputs/mapping/release/mapping.txt`. Repeat for **56** with the
   phone mapping. Without this, any crash Play records for these codes is
   obfuscated.
5. **Save → Review release → Start rollout**. If the console reports that
   changes are pending review (the listing from Step 4), send everything for
   review together.

### Step 9 — After submitting (ongoing)

- **Pre-launch report** for the new release, usually within a few hours: open the
  Stability tab and the Wear devices' screenshots. A crash there arrives before
  the human review does and is the earliest possible signal.
- **Sentry**: filter by release `com.elmtrackr.wear@1.3.3+10056`. A crash on the
  reviewer's watch lands here with a readable trace, provided the build machine's
  `local.properties` had `sentry.dsn` set at build time (`WearCrashReporting`
  compiles it in; without it the reporter is inert).
- If Play rejects again, copy the finding text and the version code into a new
  file in this folder *before* changing anything, and start from Step 1.

---

## C. Files changed in this round

| File | Change |
|---|---|
| `wear-sync/.../WearCapabilities.kt` | New: the two capability names, shared by both sides |
| `wear-sync/.../WearPhoneReach.kt` | New: pure phone selection and time budgets |
| `wear-sync/src/test/.../WearPhoneReachTest.kt` | New: tests for the above |
| `wear/.../sync/WearActionClient.kt` | Capability-first phone lookup; budget by confirmation |
| `wear/.../complication/ElmTrackrComplicationService.kt` | Degrade to signed-out face, then empty slot; guarded preview |
| `wear/src/test/.../WearLaunchPathTest.kt` | New test: complication preview for every type |
| `app/src/main/res/values/wear.xml` | New: phone advertises `elmtrackr_phone_app` |
| `app/src/main/res/raw/keep.xml` | New: pins the capability array against resource shrinking |
| `app/.../wear/WearSyncPublisher.kt` | Watch capability name comes from `WearCapabilities` |
| `app/.../wear/WearMessageListenerService.kt` | Exception handler on the scope; REFRESH wrapped |
| `docs/wear-play-submission-runbook.md` | §0 points here |
