# Wear OS rejection, September 2026 — what was ruled out, and what changed

Play rejected the watch artifact again, under a different heading from August's:

> **Wear App Quality Guidelines: Wear app functionality not working as described.**
> The functionality of your app doesn't work as described. … Your app crashed
> when testing.

August's heading was "your app does not install or launch without crashing"
([wear-play-resubmission-2026-08.md](wear-play-resubmission-2026-08.md)). That
document's §3 is the important precedent, and it is blunt: **the crash was never
reproduced.** The August work removed the crash paths a reading of the code could
find, which is not the same as fixing the crash, and §3 lists the hardware run
that would have confirmed it. The rejection came back.

So this round did not start by reading the code again. It started by trying to
break the module, and then by making the next failure produce evidence.

---

## 1. What was ruled out, with the check that ruled it out

Recorded so the next person does not spend the time again. None of these is the
crash.

| Hypothesis | Check | Result |
|---|---|---|
| The release build itself fails | `:wear:assembleRelease` with R8 full mode and resource shrinking | Builds clean |
| Compose / Wear-Compose version skew — the classic "compiles perfectly, `NoSuchMethodError` at launch" | Resolved the whole `releaseRuntimeClasspath` | No downgrades anywhere. wear-compose 1.5.0 against Compose 1.11.4 from the BOM, protolayout unified at 1.2.0 |
| R8 stripped something reached by name | Read `mapping/release/usage.txt` | Nothing that matters. The classes dropped whole (`WearMessages`, `WearPaths`, `WearAuroraColors`, the `R` classes) hold compile-time constants that are inlined at their use sites, exactly as the keep file's comment predicts |
| Something on the way to the first frame throws | Drove `WearMainActivity` create → start → resume → pause → stop → destroy under Robolectric, plus the view model with no cache and no Play Services, plus the data-layer listener | All pass |
| The signed-out state is a dead end | Read it | It is a real explained screen: "Open ElmTrackr on your paired phone to sign in" |

The activity was the notable gap. `WearLaunchPathTest` already covered the
Application, the complication provider and the trampoline — it had never created
the one component a reviewer tapping the launcher icon actually starts. It does
now, and it passes, which is a negative finding worth having: the crash is not an
unminified, no-Play-Services, JVM-reachable throw on the launch path.

What that leaves is the release binary on real hardware, the tile timeline (which
Robolectric cannot instantiate at all — `TileService` resolves a class that ships
with the Wear system), or something about the review device.

---

## 2. The reason this keeps happening: the watch reported nothing

Three rejections have mentioned a crash. Not one produced a stack trace, and the
reason is structural rather than unlucky: **`:wear` had no crash reporting.** The
phone module has had Sentry throughout. The watch had `Log.e`, and logcat on a
reviewer's watch is not somewhere anyone can read.

`WearCrashReporting` now closes that. It does not fix the crash — it makes the
next one answerable.

- Started **first** in `Application.onCreate`, so it is running before the path
  that keeps being rejected executes.
- **Every entry point is guarded.** A reporter that threw while starting, in the
  `onCreate` of an app being rejected for launch crashes, would be the worst
  possible way to make this worse. Init, shutdown and capture are each wrapped;
  a failure is logged and dropped.
- The application scope's `CoroutineExceptionHandler` now reports as well as
  logs, so survivable background failures stop being invisible.
- `release` is stamped `com.elmtrackr.wear@…`, not the phone's applicationId.
  The two artifacts share one id and report into one project, and a release
  string that did not name the form factor would file the watch's crashes with
  the phone's — the one question these reports exist to answer.

### Consent

The watch ships no settings screen — there is nowhere on that display for a
privacy toggle that belongs beside the rest of them — so the consent the user
gave or withheld in the phone app travels to the watch in
`WearShiftSnapshot.crashReportingEnabled` and is cached in `SharedPreferences`,
readable synchronously before anything can crash.

It defaults to on, which matches the phone's own opt-out default and is also what
makes an unpaired watch diagnosable — the state a review device is most likely to
be in. The phone stamps the flag in `WearSyncPublisher`, at the single choke point
every publish path passes through, so the signed-out payloads carry it too.

`SensitiveTextScrubber` moved from `:app` to `:wear-sync` so both artifacts redact
with one ruleset. A redaction ruleset is the last thing that should exist in two
copies. The watch does not talk to Postgres or hold a token, so most of its rules
are phone-only there — but `WearShiftSnapshot.shiftId` is a row id that travels to
the watch, so the UUID rule earns its place on the wrist.

### Only the core SDK

The Sentry Gradle plugin's auto-installation adds the whole `sentry-android`
bundle. Measured on this module's release APK:

| | Size |
|---|---|
| Before crash reporting | 3.58 MB |
| Auto-installed bundle | 7.33 MB |
| `sentry-android-core` alone | 4.20 MB |

The 3.1 MB in between is mostly `sentry-android-ndk`, whose `libsentry.so` is
785 KB in each of four ABIs, plus `-replay`. Neither earns its place on a wrist:
the crashes this module needs to see are JVM crashes on the launch path, and
session replay has no business recording a watch screen. Auto-installation is off
for `:wear` and the dependency is declared by hand.

---

## 3. Still unproven, and the two things to do before resubmitting

**The crash is still not reproduced.** Same caveat as August's §3, and it has to
be said plainly rather than buried: nothing in §1 or §2 is a confirmed fix. §2
means the *next* failure arrives as a stack trace instead of a guess.

Two things gate a resubmission.

### 3.1 Run it on hardware

[wear-play-resubmission-2026-08.md §3](wear-play-resubmission-2026-08.md) already
lists the run and it was never done. It is still the list: a round watch and a
square one, API 30 and the newest available, **a watch with no paired phone**,
the tile added to the carousel with its punch button tapped, the complication on
a watch face, and the largest font size through idle → countdown → running.

With a DSN compiled in, a crash there now also lands in Sentry — so the run is
worth doing even if it passes, to confirm reporting works before a reviewer is
the one relying on it.

### 3.2 The heading is about the description, not only the crash

This rejection led with *functionality not working as described*, which August's
did not. That is worth taking literally, because the watch app declares
`standalone=false`: until the phone app is installed and signed in, every Wear
surface this listing describes — the live timer, the tile, the complication —
shows its signed-out face. A reviewer who cannot sign in sees an app where
nothing described works, and reports exactly that.

`play-review-compliance.md` records that both halves of this have already been
rejected once: §1 for a listing that did not name the tile and complication, §4
for reviewer credentials that did not sign in. Both are re-verified on every
review and both are Play Console work, not code. Check them before uploading:

1. The **App access** reviewer account still signs in, on the phone app.
2. The listing description still names the **tile** and the **complication**
   literally, in every language.
3. The watch screenshots are still the app's own pixels — no frames, no mats
   (§1 of the August document, and `tools/check-wear-screenshots.py`).

Also confirm in Play Console **which versionCode was rejected**. This repository
is at phone 52 / wear 10052, and the August document records that production
drifted from what the tree said before. If the rejected artifact is not 10052,
what Google reviewed is not what is described here.

---

## 4. Mapping files

`autoUploadProguardMapping` is off, mirroring `:app`. The keep file preserves
`SourceFile` and `LineNumberTable`, so a wear crash has real line numbers, but
its class and method names are still renamed. `wear/build/outputs/mapping/release/mapping.txt`
has to be attached deliberately — to Sentry for the release above, or to Play as
the artifact's deobfuscation file — or the first trace this work buys will be
half-readable.
