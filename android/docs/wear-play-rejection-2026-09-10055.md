# Wear OS rejection, 17 September 2026 — version code 10055

Play rejected wear artifact **10055** on two independent counts. One is a Play
Console paste. The other is a process death on a review watch. They are not
the same bug, and fixing only the code will fail again on the listing.

> **Issue found: Wear app functionality not working as described**
> The functionality of your app doesn't work as described. … Your app crashed
> when testing.
> Version code 10055

> **Issue found: Play listing description**
> Your play listing description doesn't mention tile or complication although
> it's included in your app.
> Version code 10055

---

## 1. Listing (WO-G2) — do this first, it is not in this repository

This is the July 2026 finding, returned because the store listing was never
updated. The watch app still ships a **tile** and a **complication**, so the
listing has to name both, in every language it is offered in. The copy to
paste is [play-listing-wear-copy.md](play-listing-wear-copy.md).

Until that paste is live, every new watch artifact fails this heading, crash
or no crash.

---

## 2. Crash — what 10055 actually was

This repository was at wear **10053** / phone **53** when 10055 was rejected.
The extra two codes were a local hand-bump at build time that was never
committed — the same drift that left rejected 10041 on the listing for two
months. Treat 10055 as this tree plus that bump: standalone punches, the
Theme.NoDisplay trampoline fix, and crash reporting.

Those still did not hold. Two remaining launch-path holes this round closes:

1. **WorkManager auto-init.** `:app` already removes
   `androidx.work.WorkManagerInitializer` from `androidx.startup` and
   implements `Configuration.Provider`. `:wear` did not. That initializer is a
   ContentProvider, so it runs *before* `Application.onCreate` — before
   `WearCrashReporting`, before any `runCatching`. A JobScheduler failure on a
   Wear review device is then a process death with no stack. The watch now
   matches the phone: initializer removed, on-demand init via
   `ElmTrackrWearApp`.
2. **Tile request scopes.** Adding the tile is a standard review step. The
   tile service's coroutine scope had a `SupervisorJob` and no
   `CoroutineExceptionHandler`. If `buildTile()` *and* the idle fallback both
   threw, the default handler killed the process. The data-layer listener and
   the repository apply scope had the same shape. All three now report and
   swallow; the tile's last-resort layout is an empty box.

Also: black `Theme.ElmTrackrWear` (and API 31+ splash attributes) so the first
frame is not a light DeviceDefault window, which is both WO-V13/WO-V15 and a
crash vector when the platform injects a splash.

The next artifact is **phone 56 / wear 10056**, versionName **1.3.3**. 10055 is
burned. Move it to Not included.

The crash is still not reproduced on hardware. Same caveat as
[wear-play-rejection-2026-09.md](wear-play-rejection-2026-09.md) §3.

---

## 3. Resubmission order

1. Paste [play-listing-wear-copy.md](play-listing-wear-copy.md) into **every**
   listing language. Grep for `tile` and `complication`. Stop if either is
   missing.
2. Build both artifacts from **this same commit**:
   `./gradlew :app:bundleRelease :wear:bundleRelease`
3. New release. Include 56 and 10056. Set 10055 (and any older rejected wear
   code) to **Not included**.
4. Walk the hardware list in [wear-play-resubmission-2026-08.md §3](wear-play-resubmission-2026-08.md)
   if a watch is available. Launch, add the tile, tap punch, add the
   complication, largest font.
