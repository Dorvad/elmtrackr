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

The 17 September 2026 rejection names **version code 10055**, twice: a crash,
and a listing that does not mention tile or complication.

**The listing half cannot be fixed in this repository.** Paste
[play-listing-wear-copy.md](play-listing-wear-copy.md) into every listing
language and grep for `tile` and `complication` before you upload. That finding
has come back on every Wear review that left the listing untouched, including
10055.

**This repository was at 10053 when 10055 was rejected.** The extra codes were
a local hand-bump that was never committed. The next pair is **56 / 10056**.
Move 10055 to Not included.

### What to check, in the console, before touching any more code

1. **Store listing** — `tile` and `complication` in every listed language.
2. **App bundle explorer** — confirm 10055 is the artifact under review, then
   that 10056 is the one you are about to attach.
3. **The release under review** — if 10055 (or 10052, or 10041) still appears
   in the artifact list, that is what is being reviewed.
4. If a rejected code is present: new release, newest artifacts only, rejected
   ones set to **Not included**, roll out.

Until an artifact ≥ 10056 is in review *and* the listing mentions tile and
complication, the code in this tree is not what Google is judging.

> I cannot see your Play Console. The listing paste is the one item that costs
> two minutes and has independently failed every Wear submission so far.

---

## 1. Why this kept happening — the structural cause (historical)

The watch **now** declares `standalone=true` and can punch with no phone.
Section 0 of this file is the live checklist. What follows is why 10041–10052
failed, so it is not undone.

The watch used to declare itself phone-dependent:

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
| Unique versionCode across form factors | **Pass** | `wear == 10000 + phone`, currently 10056 / 56. |
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
