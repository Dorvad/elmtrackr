# One-time products: clock face packs

**Status:** live — packs are sold
**Flag:** `PAID_CLOCK_FACE_PACKS` (BuildConfig, default `true`; set
`paid.clock.face.packs=false` in `local.properties` only for Play-less local builds)
**Scope:** `android/` only — no Supabase schema change, no web change

This document covers what is in the code, what still has to be done in Play
Console, and the order to do it in. It is the checklist for turning packs from
free to paid.

---

## 1. What ships today

Nothing changes for users. With the flag off, `FreeClockFacePackEntitlements`
and `FreeClockFacePackStore` are bound, every pack reports as owned, no billing
client is constructed, and the gallery renders exactly as it did in 1.2.4.

The paid path is compiled, tested and reviewed alongside it. Flipping the flag is
the only code change needed once Play Console is ready.

## 2. The product structure

| Play product id | Grants | Notes |
|---|---|---|
| `clock_faces_progress` | Progress pack (4 faces) | |
| `clock_faces_atmosphere` | Atmosphere pack (4 faces) | |
| `clock_faces_nature` | Nature pack (4 faces) | |
| `clock_faces_journeys` | Journeys pack (4 faces) | |
| `clock_faces_payday` | Payday pack (4 faces) | Added in 1.3.0 |
| `clock_faces_all_packs` | **Every** pack, including packs added later | Sold alongside the packs, not instead of them |

All six are **one-time products**, non-consumable — bought once, owned forever,
never consumed. Essentials stays bundled with the app and is never sold.

Ids are generated from the `ClockFaceGroup` enum in `ClockFacePackProducts`, so a
new pack gets an id automatically. **Play never lets a product id be reused after
deletion, and a renamed id is a different product existing buyers do not own** —
so renaming a `ClockFaceGroup` constant breaks every purchase made against it.
Add a new group instead.

## 3. Architecture

```
ui/settings/ClockFaceGalleryScreen   ← renders owned / buy / unavailable
        │
ui/settings/SettingsViewModel        ← purchase entry points, snackbar feedback
        │
billing/ClockFacePackEntitlements    ← "may this pack be added?"  (one question)
billing/ClockFacePackStore           ← prices, purchase, restore  (the storefront)
        │
billing/PlayClockFacePackStore       ← implements both; the only place grants happen
        │
billing/PlayBillingConnection        ← the only file that imports Play Billing
        │
   Google Play Billing 9.1.0
```

Supporting pieces:

| File | Role |
|---|---|
| `ClockFacePackProducts` | Product ids and what each grants. Pure Kotlin. |
| `ClockFacePackOwnership` | Purchases ∪ free-era grant → owned packs. Pure Kotlin. |
| `ClockFacePackGrandfathering` | One-time seed of the free-era grant. |
| `ClockFacePackBillingCoordinator` | Foreground refresh; installs a pack after purchase. |
| `PurchasePreferences` | Device-local cache of Play's answer. Never the record. |
| `di/BillingModule` | Binds the Play or the free implementation off the flag. |

Two rules the design turns on:

1. **Play is the record.** Nothing in the app decides a purchase happened. Every
   grant traces to a row Play returned or to the free-era seed. The DataStore
   copy exists so the answer survives being offline, and is replaced wholesale on
   every successful refresh — which is what makes refunds take effect.
2. **Ownership gates *adding* a pack, never *having* one.**
   `ClockFacePacks.available()` still adds the group holding the selected face
   regardless of ownership, so nobody's dashboard stops drawing. There is a test
   that fails if that ever gets wired to ownership.

### Existing users keep their packs

Whatever is installed on a device the first time a build with the flag on runs
becomes permanently owned (`ClockFacePackGrandfathering`). Packs the user had not
taken are the ones that cost money. This matches the standing rule in
`product-strategy.md` — *never gate what was previously free* — and avoids the
one-star reviews that removing a shipped feature earns.

Note the limit, and be ready to answer it in support: packs have always been
device-local, so there is no account-scoped record of the free era to consult. A
user who had Nature on a tablet and installs on a new phone grandfathers nothing
on the phone. A face they had *selected* still syncs and stays usable.

## 4. Play Console setup

> Console menu labels move between releases. The steps below are what has to
> exist, not a guaranteed click path — verify the current wording in the console
> and in Google's own Play Billing documentation before following it literally.

1. **Merchant account — already in place.** Confirm only that the payments
   profile is still active and that tax treatment for the countries being sold to
   is settled: for digital goods Google collects and remits VAT in many
   jurisdictions but not all, and which applies depends on the registered country.
   Verify this in the payments profile rather than assuming.
2. **Upload a build containing the Billing Library first.** Play only exposes
   in-app product creation after it has seen an uploaded artifact that declares
   billing. Upload a build of this branch (flag off is fine — the library and the
   `com.android.vending.BILLING` permission are present either way) to an internal
   testing track. `versionCode` 42 / wear 10042 are already set and unused, so the
   first upload needs no bump; every upload after that does, both together
   (`release-checklist.md`).
3. **Create the five products** listed in §2 as one-time products, with the exact
   ids above — see §4.1 for the store copy to paste. Choose **non-consumable** if
   the console asks: nothing in this app calls `consumeAsync`, so a consumable
   product would describe a repeat purchase that cannot happen.
4. **Set prices** per country and **activate** each product. An inactive product
   returns nothing from `queryProductDetails`, which the app renders as
   *Unavailable* — the same as no Play at all.
### 4.1 Store copy

The product id is the only field that cannot be changed after creation. Name,
description, price, tags and languages are all editable later, so none of them
is worth stalling on.

Face and pack names below are taken from the app's own translation files, so
Play's purchase sheet reads the same as the screen the user came from. The
sentences around them were written for the store and have not been reviewed by a
native speaker of Hebrew or Arabic.

| Product id | Name (en-US) | Description (en-US) |
|---|---|---|
| `clock_faces_progress` | Progress clock faces | Four faces that show how far through the day you are: Aurora, Dial, Strand and Blocks. A one-time purchase, yours for good. |
| `clock_faces_atmosphere` | Atmosphere clock faces | Four faces built on colour, glow and texture: Night, Retro, Pulse and Prism. A one-time purchase, yours for good. |
| `clock_faces_nature` | Nature clock faces | Four faces for a day that grows, flows or fills: Sand, Tide, Sprout and Luna. A one-time purchase, yours for good. |
| `clock_faces_journeys` | Journeys clock faces | Four faces for a day with a destination: Orbit, Metro, Vinyl and Summit. A one-time purchase, yours for good. |
| `clock_faces_payday` | Payday clock faces | Four faces that show the shift as what it earns: Meter, Stacks, Jar and Ticker. A one-time purchase, yours for good. |
| `clock_faces_all_packs` | All clock face packs | All five packs at once: Progress, Atmosphere, Nature, Journeys and Payday. 20 faces, plus any pack added in future updates. |

**Purchase option** — Play's newer one-time products hold one or more purchase
options, each carrying offers. Each pack needs exactly one, of type **Buy** (not
Rent). Its id is a console-side label: nothing in this app reads
`purchaseOptionId`, so `buy` is a fine answer for all six. Note the id rules
differ from the product id — hyphens are allowed, underscores are not — and treat
it as permanent like the product id unless the console says otherwise.

The app reads the price through `getOneTimePurchaseOfferDetails()` and falls back
to the offer list, so a pack that later gains a second offer keeps showing a
price. Adding a second offer is still a change worth testing rather than
assuming.

Tag every product `clock-faces`. Tags are for the console's own grouping and
reporting; nothing in the app reads them.

Hebrew (`iw`) and Arabic (`ar`) are worth adding — the app ships both, and the
primary audience is Israeli hourly workers:

| Product id | Name (he) | Description (he) |
|---|---|---|
| `clock_faces_progress` | עיצובי שעון: התקדמות | ארבעה עיצובים שמראים איפה אתם ביום העבודה: אורורה, חוגה, רצועה ובלוקים. רכישה חד־פעמית, נשארת אצלכם. |
| `clock_faces_atmosphere` | עיצובי שעון: אווירה | ארבעה עיצובים של צבע, זוהר וטקסטורה: לילה, רטרו, פולס ופריזמה. רכישה חד־פעמית, נשארת אצלכם. |
| `clock_faces_nature` | עיצובי שעון: טבע | ארבעה עיצובים ליום שגדל, זורם או מתמלא: חול, גאות, נבט ולונה. רכישה חד־פעמית, נשארת אצלכם. |
| `clock_faces_journeys` | עיצובי שעון: מסעות | ארבעה עיצובים ליום עם יעד: אורביט, מטרו, ויניל ופסגה. רכישה חד־פעמית, נשארת אצלכם. |
| `clock_faces_payday` | עיצובי שעון: משכורת | ארבעה עיצובים שמראים את המשמרת דרך מה שהיא מכניסה: מונה, ערימות, צנצנת ומדד. רכישה חד־פעמית, נשארת אצלכם. |
| `clock_faces_all_packs` | כל חבילות עיצובי השעון | כל חמש החבילות יחד: התקדמות, אווירה, טבע, מסעות ומשכורת. 20 עיצובים, וגם כל חבילה שתתווסף בעדכונים הבאים. |

| Product id | Name (ar) | Description (ar) |
|---|---|---|
| `clock_faces_progress` | واجهات ساعة: التقدّم | أربع واجهات تُظهر إلى أي مدى بلغت في يومك: شفق، مينا، خيط، مكعبات. شراء لمرة واحدة يبقى لك. |
| `clock_faces_atmosphere` | واجهات ساعة: الأجواء | أربع واجهات من لون وتوهّج وملمس: ليلي، طراز قديم، نبض، منشور. شراء لمرة واحدة يبقى لك. |
| `clock_faces_nature` | واجهات ساعة: الطبيعة | أربع واجهات ليوم ينمو أو يجري أو يمتلئ: رمل، مدّ وجزر، برعم، قمر. شراء لمرة واحدة يبقى لك. |
| `clock_faces_journeys` | واجهات ساعة: الرحلات | أربع واجهات ليوم له وجهة: مدار، مترو، أسطوانة، قمة. شراء لمرة واحدة يبقى لك. |
| `clock_faces_payday` | واجهات ساعة: الأجر | أربع واجهات تُظهر المناوبة بما تكسبه: عدّاد، أكوام، جرة، مؤشر. شراء لمرة واحدة يبقى لك. |
| `clock_faces_all_packs` | كل حزم واجهات الساعة | كل الحزم الخمس معًا: التقدّم، الأجواء، الطبيعة، الرحلات، الأجر. 20 واجهة، بالإضافة إلى أي حزمة تُضاف في التحديثات القادمة. |

If a pack's faces are ever changed in `ClockFaceCatalog`, these descriptions name
the old ones until someone edits them. That is the cost of naming the faces, and
it is worth paying — the names are what let a buyer tell the packs apart.

5. **Add licence testers** (Setup → Licence testing) so test purchases are not
   charged. Testers must be signed into the Play account on the device with the
   app installed *through a Play track*: Play Billing does not answer a sideloaded
   APK, so a local `assembleDebug` build always shows packs as unavailable.
6. **Wait before concluding anything is broken.** A newly activated product does
   not reach `queryProductDetails` immediately — propagation takes hours, and the
   app renders the gap as *Unavailable*, which looks exactly like a bug. Give it
   time before debugging the code.

## 5. Pricing

Not set in code, deliberately — the app never prints a price it made up. Every
price the user sees comes from `ProductDetails.getOneTimePurchaseOfferDetails()`,
already formatted in their currency.

The price points themselves are a business decision and are **not** made here.
`product-strategy.md` flags its own figures as directional and unvalidated; the
same caution applies to these. Two structural notes that are safe to state:

- The bundle has to be worth more than the sum of its parts to the buyer and less
  than it to the seller — the usual anchor is pricing it below the cost of the
  four packs bought separately.
- Play takes a service fee on each transaction, and the rate depends on the
  account's annual revenue tier and programme enrolments. Read the current rate
  off the console rather than from memory before modelling net revenue.

## 6. Testing before release

Billing cannot be tested from a local build. The loop is: set
`paid.clock.face.packs=true` in `local.properties` → `bundleRelease` → upload to
internal testing → install from Play on a device signed in as a licence tester.
Every iteration costs an upload, so plan to work through the whole list per
build rather than one item at a time.

- [ ] Every unowned pack shows a Buy button with a real price from Play.
- [ ] Buying a pack completes, adds the pack automatically, and the button becomes
      Remove.
- [ ] Backing out of Play's sheet shows nothing — no error snackbar.
- [ ] Force-stopping mid-purchase and reopening still grants the pack (the
      foreground refresh in `MainActivity.onStart` picks it up).
- [ ] Uninstall and reinstall: packs come back with no restore button pressed.
- [ ] A device with the Play Store disabled shows *Unavailable*, not a Buy button
      that fails.
- [ ] A user who had packs installed before the flag flip keeps them, can remove
      one, and can re-add it for free.
- [ ] Test the pending-payment path with Play's test instruments if the target
      markets use slow payment methods.

Automated coverage lives in `ClockFacePackBillingTest` — the catalog, the
ownership union and the grandfathering seed. The Play client itself is not unit
tested; it is thin by design so that the parts worth testing are the pure ones.

## 7. Release sequence

1. Merge this branch with the flag **off**. Ships as a no-op.
2. Complete §4 in Play Console. Products active.
3. Internal testing build with the flag on. Work through §6.
4. Flip `PAID_CLOCK_FACE_PACKS` to `true` in the release build, bump
   `versionCode`/`versionName`, and note the change in the store listing's
   release notes — say plainly that existing packs stay free.
5. Staged rollout, per `release-checklist.md`. Watch Play Console's financial
   reports and Sentry for `Billing` tag noise together; a spike in
   `ITEM_UNAVAILABLE` means a product is misconfigured, not that users are
   declining.

## 8. Open items

- Prices per country are unset and are a business decision (§5).
- The store listing needs an in-app-purchase disclosure; Play requires the
  "Contains in-app purchases" label, which is derived from the products
  automatically, but the listing copy should not claim the packs are free.
- Nothing in the app offers a refund path. Play handles refunds itself, and the
  next foreground refresh revokes the pack — which is the intended behaviour, but
  worth confirming once against a real refund before rollout.
- If a future pack should be free rather than sold, mark it `isBundled` in
  `ClockFaceGroup`; the catalog stops generating a product id for it.
