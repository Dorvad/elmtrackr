# Play review compliance — Wear OS rejections (Jul 27)

> **17 September 2026:** Play rejected wear **10055** for the listing finding
> below *and* for "your app crashed when testing". The listing half is still
> this section. The crash half and the next version codes are
> [wear-play-rejection-2026-09-10055.md](./wear-play-rejection-2026-09-10055.md).
> Paste [play-listing-wear-copy.md](./play-listing-wear-copy.md) **before**
> uploading. §1 and §4 are re-checked on **every** review.

> **A second round of rejections arrived in August 2026** (screenshots inside
> device frames, and "does not install or launch without crashing"). See
> [wear-play-resubmission-2026-08.md](./wear-play-resubmission-2026-08.md).

Four rejections were received under the Wear App Quality Guidelines. Two are
fixed in code (this repo), two must be resolved in the Play Console. This
document covers all four so the next submission passes.

---

## 1. Listing description must mention the tile and complication **[Play Console, manual]**

**Rejection:** "Your play listing description doesn't mention tile or
complication although it's included in your app."

This is Wear quality requirement **WO-G2**. ElmTrackr ships a punch tile and a
shift complication, so the description must say so explicitly, in every
language the listing is offered in. The reviewer greps for the English words
**tile** and **complication**.

**Fix:** Play Console → Grow → Store presence → Main store listing. Paste the
paragraphs in [play-listing-wear-copy.md](./play-listing-wear-copy.md) into the
full description for English, Hebrew, Arabic, and Russian, then re-submit.

Do not mention "Android Wear". Search each translation for `tile` and
`complication` before you click submit. If either word is missing, this
heading fails on its own — it failed 10055 even after the watch was made
standalone.

## 2. Background not black **[fixed in code]**

**Rejection:** "Your app does not use a black background for all apps and tiles."

The watch app and tile used a full-bleed indigo→blue gradient. The Wear color
guidelines require a black background so UI blends with the bezel and saves
power on AMOLED. Fixed:

- `WearAuroraBackground` (app) — solid black.
- `tile_bg_gradient.xml` (tile + app) — solid black; tile `RESOURCES_VERSION`
  bumped to `3` so renderers drop the cached gradient.
- `tile_preview.xml` (tile picker) — black face.
- Theme `background` + countdown overlay scrim — black.

Brand color remains in foreground accents (ring, bolt button, status dots).

## 3. Text cut off at large font size **[fixed in code]**

**Rejection:** "texts are cut off when a large font size is selected in your app."

Fixed:

- Idle screen detail line: wraps to 2 lines with ellipsis (was clipped at 1).
- Setup screen: content scrolls when oversized fonts overflow the round bezel.
- Shift count-up + countdown numerals: font scale capped at 1.3× so digits
  never push past the ring (labels/body still scale fully).
- Countdown/confirmation overlays: horizontal padding so wrapped text clears
  the round edge.
- Tile detail line: `maxLines = 2`.

Before re-submitting, verify on an emulator/device: Settings → Display →
Font size → largest, then walk through idle → countdown → running →
confirmation.

## 4. Login credentials for review **[Play Console, manual]**

**Rejection:** "We could not access the in-app content with the login
credentials that you have provided."

The watch app is a companion (`standalone=false`): all content requires the
**phone** app to be signed in. Reviewer checklist:

1. Create a dedicated reviewer account in Supabase (e.g.
   `playreview@<yourdomain>`), and **confirm its email** — an unconfirmed
   account cannot sign in, which is the most common cause of this rejection.
2. Sign in with it once on a clean install to verify it reaches the dashboard.
3. Seed it with a few shifts/receipts so there is content to review.
4. In Play Console → App content → **App access** → "All or some functionality
   is restricted": provide the email + password, and add instructions:
   - "Sign in on the **phone** app first; the watch app, tile, and
     complication activate automatically once the paired phone is signed in."
5. Keep the credentials valid — Play re-reviews periodically and the account
   must keep working.

---

## Re-submission steps

1. Build new bundles (phone versionCode **11**, wear versionCode **10011**):
   `./gradlew :app:bundleRelease :wear:bundleRelease`. A watch artifact's
   versionCode must be unique across all form factors of the listing, so the
   `:wear` module keeps its own range: 10000 + the phone versionCode.
2. Upload both to their tracks, move the rejected versionCode 10 artifacts to
   "Not included", and roll out.
3. Update the store listing description (§1) and App access credentials (§4)
   **before** submitting for review — the listing and credential issues are
   checked again on every review.
