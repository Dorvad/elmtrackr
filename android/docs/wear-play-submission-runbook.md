# Wear OS submission runbook — September 2026

Written after a third rejection. The first two rounds were fixed in code; this
one is about making sure the fixed code is what Play actually looks at, and then
removing the reviewer's dependency on your phone entirely.

**Verified 3 September 2026** against the live
[Wear OS app quality guidelines](https://developer.android.com/docs/quality-guidelines/wear-app-quality)
and [Package and distribute Wear OS apps](https://developer.android.com/training/wearables/packaging).
Requirement IDs below are quoted from that checklist. Play changes these without
notice — re-read both pages before a submission rather than trusting this file.

---

## 0. Check this first. It may be the entire problem.

The rejection names **version code 10041**.

**This repository has never produced a 10041.** The wear `versionCode` history is:

| Commit | Date | wear versionCode |
|---|---|---|
| `f53d2dc` | 2026-08-08 | 10011 |
| `e4d0a04` | 2026-08-10 | 10012 |
| **`41b835e`** | **2026-08-23** | **10042** — *"Fix the Wear OS launch crashes"* |
| `5183178` | 2026-08-30 | 10043 |

It went 10012 → 10042, skipping 10041 entirely. **Every launch-crash fix lives in
10042 and later.** So a rejection naming 10041 is a rejection of a build that
contains none of them.

Three things could produce that, and they need different responses:

1. **The rejected 10041 is still in the release.** Play re-reviews what is in the
   track. If 10041 was never moved out, each "resubmit" re-reviews the same
   broken artifact and you wait a week to be told so again.
2. **10042/10043 was never successfully rolled out** — uploaded to a draft that
   was never sent for review, or the release was left unsubmitted.
3. **You are re-reading the August rejection email.** In which case the fixed
   build genuinely has not been reviewed yet.

### What to check, in the console, before touching any code

1. **Release → Production (and every other track) → Releases.** Look at the
   artifact list for the release under review. If **10041** appears anywhere,
   that is what is being reviewed.
2. **App bundle explorer.** Search for 10041, 10042 and 10043. This tells you
   which artifacts Play has actually received, and their status.
3. If 10041 is present: create a new release, include **only** the newest wear
   artifact, set 10041 to **Not included**, and roll out.

Until an artifact ≥ 10042 has been through review, the code fixes are untested by
Google and nothing below will change the outcome.

> I cannot see your Play Console, so I cannot tell you which of the three it is.
> This is the one item on the list that costs two minutes to check and could
> explain every week you have lost.

---

## 1. Why this keeps happening — the structural cause

The watch app declares itself phone-dependent:

```xml
<meta-data android:name="com.google.android.wearable.standalone" android:value="false" />
```

That single line puts a five-step setup between a reviewer and any working watch
screen:

1. pair a phone with the test watch,
2. install the ElmTrackr phone app on it,
3. sign in on the phone with your App Access credentials,
4. wait for the data layer to push a snapshot,
5. only then does the watch show anything but *"Sign in on your phone."*

Every rejection so far has been a different link in that chain — screenshots,
then a crash, now functionality. Each fix addressed one link and left the chain
intact, and each round costs a week.

**The relevant requirement is WO-P5:** *"For non-standalone apps, ensure that the
companion app can connect with the Wear app and allows the user to use the Wear
app as expected."* You are being judged on a setup you do not control and cannot
observe.

---

## 2. The fix you chose: make the watch standalone

The strongest version is not just flipping the flag, and there is a prerequisite
that has to be stated plainly because it is bigger than the watch.

### The app cannot currently be used without an account — including on the phone

`android/README.md` says "all local shift tracking continues to work without
authentication". **The code does not support that claim.** `CurrentUserProvider`
resolves the user from `lastActiveUserId`, and that preference is written in
exactly one place — `SupabaseAuthRepository` on a successful sign-in — and cleared
on sign-out. There is no anonymous or device-local identity. So with nobody signed
in:

- `ClockInActions.clockInHeadless` returns null,
- `WearActions.clockIn` answers `not_signed_in`,
- and the shell routes to the auth screen rather than the dashboard.

Two consequences, both important:

1. **The README is wrong and should be corrected**, because it is the sentence
   that makes this look easier than it is.
2. **A Play reviewer must sign in.** There is no build configuration or offline
   mode that avoids it. Your App Access credentials are therefore load-bearing on
   every single review — phone *and* watch — which is why the July credential
   rejection was so damaging.

### What "standalone" therefore requires

A watch that works with no phone still needs an identity to attach shifts to.
There are two honest routes:

**Route A — anonymous local identity (recommended).** Give the app a device-local
user id when nobody has signed in, so shifts can be recorded and later claimed by
an account. This makes the README's claim true, lets a reviewer install and punch
in with no setup at all, and is the only route that removes credentials from the
critical path. It is a product change affecting the phone as well as the watch,
and it needs a defined migration for "sign in later and adopt these shifts" —
`LegacyDataAdopter` already does adoption of exactly this shape and is the model
to follow.

**Route B — sign in on the watch.** Keep accounts mandatory and add Google
Sign-In on the watch via Credential Manager (the phone gained Google Sign-In in
August, `f95db2e`, so Supabase already accepts the ID token). This satisfies
**WO-P6**, which forbids a password field on the watch. It removes the *phone*
dependency but not the *account* dependency, so a reviewer still has to sign in —
just on the watch instead of the phone.

Route A is the one that ends the rejection cycle. Route B is smaller but leaves
you dependent on the reviewer completing a sign-in.

Either way it is the better product: an hourly worker who leaves their phone in a
locker is precisely the person a wrist punch-clock is for.

### What that requires

| Piece | Why | Size |
|---|---|---|
| An identity that exists without sign-in (Route A) | Nothing can be recorded without one — see above | L |
| Local shift state on the watch (DataStore or Room) | A punch must survive with no phone in range | M |
| Local punch path that does not call the phone | Today `punchIn()` returns `phone_unreachable` and shows a failure | M |
| Reconciliation when a phone reappears | Two devices can now both hold an open shift; `RunningShiftResolver` on the phone already implements exactly this rule (earliest open shift wins, merge, tombstone the rest) and is a pure function, so the watch can reuse it | M |
| Flip `standalone` to `true` | **Only after** the three above; declaring it early is a false claim a reviewer will catch | S |

### Authentication — the constraint that shapes this

**WO-P6:** *"Your app must not ask the user to input a username or password
directly on the Wear OS device."* So the watch must never show a password field.
Three legitimate paths:

- **No account needed for the core loop** (recommended, and what the phone
  already does) — tracking works locally; signing in is optional and only adds
  cloud sync.
- **Google Sign-In on the watch** via Credential Manager. The phone app gained
  Google Sign-In in August (`f95db2e`), so the Supabase side already accepts a
  Google ID token.
- **Token handoff from the phone** over the data layer — but that reintroduces
  the phone dependency for anyone who has not set it up, which is the thing we
  are removing.

### My professional opinion on sequencing

**Standalone will not fix a crash.** The rejection has two halves — *"doesn't
work as described"* and *"does not install or launch without crashing"* — and
only the first is about pairing. If there is a genuine crash, standalone code
will crash too, and you will have spent the work and still failed. So:

1. §0 — establish which artifact is under review. **Do this first.**
2. §3 — prove on hardware that the current build does not crash.
3. §2 — then do the standalone work.

Doing 3 before 1 and 2 risks another wasted week.

---

## 3. Proving the crash is gone — never yet done

`wear-play-resubmission-2026-08.md` §3 is explicit that the crash Play saw was
**never reproduced**. The fixes removed the crash paths a static reading could
find; that is not the same as a confirmed fix, and Play gave no stack trace.

The release build is R8-minified with `SourceFile`/`LineNumberTable` kept, so a
crash now yields a readable trace — the previous release could not.

```bash
cd android
./gradlew :wear:assembleRelease -PallowDebugSignedRelease=true
adb install -r wear/build/outputs/apk/release/wear-release.apk

adb logcat -c
adb shell am start -n com.elmlaunch.myapp/com.elmtrackr.wear.WearMainActivity
adb logcat -b crash -d          # readable stack trace if it dies
```

Cover, at minimum:

- [ ] a **round** watch and a **square** one
- [ ] **Wear OS 3 (API 30)** — the module's `minSdk` — and the newest API you can get
- [ ] **a watch with no paired phone at all** — the reviewer's harness may well be
      in this state, and it is the path that reaches the most error handling
- [ ] the **tile**: add it to the carousel and *tap the punch button*
      (this was broken by `exported="false"` and is now covered by
      `WearManifestContractTest`)
- [ ] the **complication**: add it to a watch face
- [ ] Settings → Display → Font size at **largest**, through idle → countdown →
      running → confirmation
- [ ] cold launch after force-stop, and launch straight after install

---

## 4. Compliance audit — current state

Audited against the live checklist on 3 September 2026.

| Requirement | State | Evidence |
|---|---|---|
| **WO-P2** install/launch without crashing | **Hardened, unproven** | Application scope has a `CoroutineExceptionHandler`; DataStore has a corruption handler; every system entry point goes through `ElmTrackrWearApp.from()` instead of an unchecked cast; `WearStateRepository` construction is lazy and cannot throw. Not yet verified on hardware — §3. |
| **WO-P5** companion connects and works | **At risk by design** | This is the structural problem in §1. Removed only by going standalone. |
| **WO-P6** no password entry on the watch | **Pass** | The watch never shows a credential field; it directs the user to the phone. |
| **WO-V9** tile signed-out prompts sign-in | **Pass** | `tile_sign_in` / `tile_on_phone` strings render a sign-in prompt on the tile. |
| **WO-V10** tile preview | **Pass** | `androidx.wear.tiles.PREVIEW` → `@drawable/tile_preview` in the manifest. |
| Black background | **Pass** | Fixed July; `tile_bg_gradient.xml` and the app background are solid black. |
| Large-font text clipping | **Pass** | Fixed July; verify again on hardware per §3. |
| targetSdk ≥ 34 | **Pass** | `targetSdk = 36`. |
| 64-bit support (**enforced 15 Sep 2026**) | **Pass** | The `:wear` module has no native code — no `.so`, no `abiFilters`, no NDK, and no native dependency. Nothing to do, but note the date: it is 12 days away. |
| Unique versionCode across form factors | **Pass** | `wear == 10000 + phone`, currently 10043 / 43. |
| Wear screenshots, listing, credentials | **You confirmed done** | Re-checked on *every* review — see §5. |

---

## 5. Play Console steps, in order

Do these in this order. 1 and 2 are the ones that have bitten before.

1. **Confirm the artifact under review** — §0. Bundle explorer, then the release's
   artifact list. Nothing else matters until an artifact ≥ 10042 is in review.
2. **App access.** The reviewer account must be a real, **email-confirmed**
   account that you have signed in with on a clean install. Instructions must say
   plainly: *"Sign in on the phone app first; the watch app, tile and
   complication activate once the paired phone is signed in."*
   → If §2's standalone work ships, replace this with *"no sign-in is required to
   track a shift"*, which is far more robust.
3. **Store listing** must contain the words **tile** and **complication**
   literally, in every listed language. The reviewer greps for them.
4. **Wear screenshots**: the app's own pixels only — no device frame, no border,
   no caption, no background. Capture with `tools/capture-wear-screenshots.sh`,
   validate with `tools/check-wear-screenshots.py`. Confirm the current format,
   aspect-ratio and size limits on
   [Play's preview-asset page](https://support.google.com/googleplay/android-developer/answer/9866151)
   at submission time — these change.
5. **Form factor.** Confirm Wear OS is declared for the app and that the watch
   artifact is attached to the same listing as the phone one.
6. **Roll out**, with the rejected artifact set to *Not included*.

---

## 6. What would end the cycle for good

Two things, neither of which is a code fix:

- **A pre-submission checklist that includes reading the artifact list**, so a
  build carrying the fixes is never confused with the build being reviewed. §0
  exists because that check was missing.
- **Removing the reviewer from the critical path** by making the watch work with
  no phone and no account (§2). A reviewer who can install and immediately punch
  in cannot file "doesn't function as described".
