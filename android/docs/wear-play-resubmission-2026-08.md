# Wear OS rejection, August 2026 — what changed and what is left

Play rejected watch artifact **versionCode 10041** on two counts:

1. **Wear screenshots** — "must not be positioned within the device frames, or
   include additional text, graphics, or backgrounds that are not part of the
   interface of the app." Four listing assets were flagged
   (`IN_APP_EXPERIENCE-2649`, `-1878`, `-2361`, `-1973`).
2. **Wear app functionality** — "Your app does not install or launch without
   crashing."

The first is a listing-asset problem and is fixed in Play Console, not in code.
The second is addressed by the code changes below, with an important caveat in
[§3](#3-what-is-not-yet-proven).

---

## 1. Screenshots — Play Console, manual

### The rule

A Wear screenshot has to be the app's own screen and nothing else. No watch
render around it, no drop shadow, no caption, no gradient behind it, no logo in
the corner. If the pixels were not drawn by ElmTrackr on the watch, they do not
belong in the file.

Play's Wear screenshot spec at the time of writing:

| Requirement | Value |
|---|---|
| Format | PNG or JPEG |
| Aspect ratio | 1:1 (square) |
| Side length | 384–3840 px |
| File size | up to 8 MB |
| Count | 1–8 |

Confirm those numbers against
[Play's preview-asset help page](https://support.google.com/googleplay/android-developer/answer/9866151)
before uploading — Google changes them without notice.

### Producing compliant assets

The only reliable way to satisfy "nothing that is not part of the interface" is
to read the framebuffer off the device, which is what the capture script does:

```bash
cd android
# start a Wear OS emulator (or connect a watch over adb), then:
tools/capture-wear-screenshots.sh
```

It prompts for each screen, captures with `adb exec-out screencap -p`, and runs
the validator over the results. The validator can also be run on its own
against assets from any source:

```bash
tools/check-wear-screenshots.py path/to/screenshots
```

It hard-fails on format, aspect ratio, dimensions and file size, and warns when
the border pixels look like a frame or a mat. The frame check is a heuristic —
a clean run is not a substitute for looking at the images.

### Which screens to submit

Five screens carry the listing and all five are the app's own interface:

1. Watch app, clocked out — bolt button and PUNCH IN.
2. Watch app, clocked in — goal ring around the live count-up.
3. Watch app — the 3-2-1 pre-punch countdown.
4. The ElmTrackr **tile** in the tile carousel.
5. A watch face carrying the ElmTrackr **complication**.

4 and 5 are worth including: the listing description has to mention the tile and
the complication (see `play-review-compliance.md` §1), and a reviewer who reads
that description looks for them.

Do not re-upload anything from the previous set. Replace all of them.

---

## 2. Crash and functionality — fixed in code

Every item below is in this repository. None of them was reachable in a debug
build, which is why they survived to a store review.

### 2.1 The tile's punch button could not launch anything

`WearPunchTrampolineActivity` — the invisible activity a tile tap targets — was
declared `android:exported="false"`. A tile's `LaunchAction` is carried out by
the Wear OS tile host, a separate app under a separate uid, so the activity
manager refused the start: tapping the tile's punch button did nothing at all.
The activity is now exported, ignores any action value other than the two punch
actions, and cannot throw on a malformed intent.

A JUnit test (`WearManifestContractTest`) now asserts this, so the same
regression cannot land silently again.

### 2.2 Failures on the launch path took the process down

`Application.onCreate` starts a coroutine that reads the cached snapshot from
DataStore and then pushes it to three system surfaces — WorkManager, the
complication registry, and the tile updater. None of that was guarded, and none
of it is guaranteed to succeed on an arbitrary watch. An exception in any of
them reached the default handler, which on Android means the process dies before
the first frame. That is exactly the shape of "does not launch without
crashing".

Now:

- The DataStore has a `ReplaceFileCorruptionHandler`, so one bad write no longer
  turns every subsequent launch into a crash.
- The cache read, each of the three surface refreshes, and the view model's
  bootstrap are individually guarded and logged.
- The application scope carries a `CoroutineExceptionHandler`; background upkeep
  can fail without killing the app.

### 2.3 Unchecked `applicationContext` casts in every system entry point

The tile service, the complication provider, the data-layer listener, the
refresh worker and the trampoline all reached the repository through
`applicationContext as ElmTrackrWearApp`. These components are started by the
system, and a `ClassCastException` there is an immediate crash with no useful
message. They now go through `ElmTrackrWearApp.from(context)` and degrade
instead — the tile renders its signed-out face rather than leaving a blank slot
in the carousel.

### 2.4 Release builds were unreadable and unverified

- `:wear` minifies with R8 full mode and shrinks resources, and carried two keep
  rules and no `SourceFile`/`LineNumberTable` attributes. A crash on a
  reviewer's watch arrived as fully obfuscated frames. The module now has a keep
  file modelled on the phone module's: stack-trace attributes, the
  kotlinx.serialization keeps for the wire model, the WorkManager name keeps,
  the coroutines volatile-field keeps, and the trampoline.
- `res/values/wear.xml`'s `android_wear_capabilities` array is read by the
  platform by name and referenced from nowhere in code or manifest, so the
  release resource shrinker was free to strip it — which stops the watch
  advertising itself to the phone. `res/raw/keep.xml` now pins it.
- **CI never built `:wear` at all.** It ran `:app:assembleDebug` and
  `:app:lintDebug`. The workflow now runs `:wear:assembleRelease` (R8 and
  resource shrinking, debug-signed for verification) and `:wear:lintRelease`,
  and uploads the APK as a build artifact for emulator testing.

### 2.5 Smaller quality items

- The launcher icon was a 1024×1024 `nodpi` PNG. A `nodpi` bitmap is decoded at
  its authored size wherever it is drawn, so the watch launcher was allocating
  about 4 MB for a 48dp slot, and the setup screen another for a 34dp badge.
  Both now use the density-bucketed `ic_launcher` / `ic_launcher_round` mipmaps,
  and the watch APK's resources shrank from 616 KB to 292 KB.
- `:wear-sync` had no JVM target pinned, so the Kotlin JVM plugin targeted
  whatever JVM Gradle ran on — 21 on CI, commonly 17 locally. Pinned to 17,
  matching `:app` and `:wear`.
- A test now asserts that format placeholders match across `values`,
  `values-iw` and `values-ar`. A translation carrying a placeholder the default
  string does not throws `MissingFormatArgumentException` at draw time — a crash
  that hits one language and nobody else.

---

## 3. What is not yet proven

**The specific crash Play saw has not been reproduced.** The rejection gives no
stack trace, no device model, and no Android version, and the artifact it
describes — versionCode 10041 — is higher than anything recorded in this
repository, so the exact build that failed is not reconstructable from source
here.

What the changes above do is remove the crash paths that a static review of the
module can identify, and make the next failure diagnosable rather than opaque.
That is not the same as a confirmed fix. Before resubmitting, reproduce the
install-and-launch on real hardware:

```bash
cd android
./gradlew :wear:assembleRelease -PallowDebugSignedRelease=true
adb install -r wear/build/outputs/apk/release/wear-release.apk

# clear the log, launch cold, and keep everything the app says
adb logcat -c
adb shell am start -n com.elmlaunch.myapp/com.elmtrackr.wear.WearMainActivity
adb logcat -d > wear-launch.log
```

Cover at least:

- a **round** watch and a **square** one;
- **Wear OS 3 (API 30)**, the module's `minSdk`, and the newest API available;
- a watch with **no paired phone** — the reviewer's harness may well be in this
  state, and it is the path that reaches the most error handling;
- the tile: add it to the carousel and **tap the punch button** (this is 2.1);
- the complication: add it to a watch face;
- Settings → Display → Font size at its largest, through idle → countdown →
  running → confirmation.

If it still crashes, `adb logcat -b crash -d` now yields a readable stack trace
with file names and line numbers, which the previous release could not produce.

---

## 4. Resubmission checklist

1. **Confirm the version codes against Play Console → Release overview.** This
   repository was at phone 12 / wear 10012 while Play had already seen wear
   10041, so the two are out of sync and the repository is not the authority.
   They are now set to phone **42** / wear **10042**, preserving the documented
   `wear == 10000 + phone` rule and clearing the rejected code. If Play shows a
   higher number than 42 for the phone, raise both before building.
2. Build both artifacts: `./gradlew :app:bundleRelease :wear:bundleRelease`.
3. Install the wear release on an emulator or watch and walk §3's list.
4. Replace **all** Wear screenshots in the listing with captures from
   `tools/capture-wear-screenshots.sh`. Delete the old ones.
5. Re-check the two items from the July rejection that are re-verified on every
   review — the listing description must name the **tile** and the
   **complication**, and the App access reviewer credentials must still sign in.
   See `play-review-compliance.md` §1 and §4.
6. Move the rejected 10041 artifact to "Not included" and roll out.
