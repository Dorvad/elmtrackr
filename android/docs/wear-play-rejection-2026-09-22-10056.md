# Wear OS rejection, 22 September 2026 — version code 10056

Play rejected wear **10056** on two findings. Neither is the crash of earlier
rounds; both are Wear quality requirements a reviewer checks by hand, and both
are fixed in this repository. The next artifacts are phone **57** / wear
**10057**, versionName **1.3.4**. 10056 is burned; set it to *Not included*.

> **Wear app functionality not working as described** — *texts are overlapping
> when a large font size is selected in your app.*
>
> **Missing ongoing activity** — *When a user has an ongoing activity, your app
> did not … show the ongoing activity indicator on the watch face; update recent
> apps with the appropriate app launcher chip; reference the ongoing activity
> from the tile.*

The listing finding from 10055 (tile and complication not named in the
description) did **not** return, so the paste from
[play-listing-wear-copy.md](play-listing-wear-copy.md) took. Leave it in place.

---

## 1. Text overlap at large font sizes — what it was

Every watch face was built from two independent layers: the ELMTRACKR wordmark
pinned to the top of the screen, and the content column centred on the whole
screen. Nothing related the two. The idle column (bolt, PUNCH IN, status, a
two-line detail) is about 144dp tall at the default font size on a 192dp face,
so it already reached up to 24dp from the top while the wordmark sat at 28–42dp
— a near miss that the launch screenshots, taken with no detail line, never
showed. At the largest accessibility size the column is taller than the screen
and runs straight through the wordmark and the time.

The July fix for the *previous* large-font finding ("texts are cut off") capped
the numerals and wrapped the detail line, which stopped clipping. It did not
touch the composition, so the two layers still met.

### The fix

`WearFace` now lays the wordmark and the content out in one measure pass
(`WearFaceLayout` in `ui/WearScreens.kt`):

- a top reserve clears the scaffold's curved TimeText, and grows with the font
  scale because TimeText does;
- content is measured against the height left under that reserve, so it has a
  finite bound and can scroll;
- if wordmark + gap + content fit, the content is screen-centred as before, or
  pushed down just enough to clear the wordmark;
- if they do not fit, the wordmark is not placed and the content takes the space.

Overlap is impossible by construction, at any scale, on any screen size. In
addition: the status label and the PUNCH IN label wrap to two lines instead of
running off a round edge (Russian and Arabic labels are long), the punch
confirmation message is capped at 1.3× and three lines so a failure message fits
under the mark, and the setup screen lost its own scroll (a nested vertical scroll
measured against the outer one's infinite height throws during composition).

The **tile** had the same two-layer composition and the same failure. It is now a
single column — wordmark, then a weighted box that centres the content in what is
left — with ellipsised, centred text. Tile text has no per-element cap, so the
count-up picks a smaller typography role as the system font scale rises
(`countUpTypography`), keeping the rendered digits near the app's own cap instead
of 80sp through the ring.

### Still to verify on a device (Step 6 in the runbook)

Settings → Display → Font size → largest, then: idle face, punch in, running
face, punch out, the tile in both states, the countdown, and the confirmation.
Robolectric composes these at 2.0× without throwing, but it cannot judge pixels.

---

## 2. Missing ongoing activity — what it was

Wear OS treats anything the wearer is in the middle of — a workout, a timer, a
shift — as an *ongoing activity*, and the quality guidelines require an app that
has one to surface it in three places: an indicator on the watch face, a chip in
the recents list, and the tile. The watch app had a running shift and none of the
first two. The tile already showed the running face, which covers the third.

### The fix

`ongoing/WearOngoingShift.kt` posts an ongoing notification carrying an
`androidx.wear.ongoing.OngoingActivity` while a shift is active and cancels it
when the shift ends. The status is a `StopwatchPart` anchored at the shift's
start, so the system counts up on its own; the chip's tap opens the watch app.
It is driven from `WearStateRepository.applySnapshot`, the same choke point that
already refreshes the tile, the complication and the refresh worker, so every
route into a running shift — the app, the tile, a phone snapshot, a restored
cache after reboot — produces the indicator.

Two deliberate choices, recorded here so they are not undone by accident:

- **No foreground service.** Google's sample keeps the notification alive from a
  foreground service. A shift runs for hours, and on API 34+ a foreground service
  needs a declared type — for a timer that is `specialUse`, which is its own Play
  Console declaration and review. The notification is held by the system once
  posted and the stopwatch is rendered from the start time, so the indicator
  survives the process dying. The cost is that the notification is
  user-dismissible on newer Android; it comes back at the next snapshot.
- **`POST_NOTIFICATIONS` is requested on first open**, not at the first punch, so
  the 3-2-1 countdown is never interrupted by a system dialog. Without the grant
  the indicator is simply absent — `WearOngoingShift` checks and logs, never
  throws. **A reviewer who denies the prompt will not see the indicator**; the
  App access instructions in Play Console should say to allow it (Step 5 in the
  runbook).

Tests: `WearLaunchPathTest` posts an active snapshot and asserts the notification
exists, is ongoing and carries the ongoing-activity extras, then clears it on an
idle snapshot; and asserts the class is silent without the permission.
`WearManifestContractTest` asserts the permission is declared.

---

## 3. Resubmission — what changes from the 21 September guide

Follow [wear-play-resubmission-2026-09-21.md](wear-play-resubmission-2026-09-21.md)
Section B with these amendments:

1. **Version codes are 57 / 10057.** Confirm both are unused in App bundle
   explorer; set 10056 to *Not included* in the new release.
2. **App access instructions** — add one sentence: *"When the watch app asks to
   send notifications on first launch, allow it; this enables the on-shift
   indicator on the watch face."*
3. **Hardware test, add:** with the largest font size, walk idle → punch in →
   running → punch out, then open the tile in both states. Then, at the default
   size, punch in and confirm (a) the watch face shows the ElmTrackr ongoing
   indicator, (b) the recents list (swipe from the watch face / press the button
   per device) shows the ElmTrackr chip with the count-up, (c) tapping either
   opens the app, and (d) punching out removes both.
4. **Release notes** — "Watch: fixes text layout at large font sizes and shows
   the running shift as an ongoing activity on the watch face."

Everything else in that guide — the pre-launch report check, keystore
verification, mapping uploads, Sentry release `com.elmtrackr.wear@1.3.4+10057` —
stands.
