# ElmTrackr — UX/UI Review (August 2026)

**Scope:** the native Android app (`android/`), the primary product per [ANDROID_FIRST.md](../../ANDROID_FIRST.md).
**Method:** source audit of the 123 Kotlin files under `ui/` plus `navigation/`, read against the 31
committed Paparazzi goldens in `app/src/test/snapshots/images/`. Every count in this document was
measured, not estimated; the commands that produce them are encoded in `DesignSystemBudgetTest`.

The app is in good shape. This is not a rescue review — the navigation layer, the pay engine, the
bidi handling and the typography are all better than category norm. What follows is what a year of
fast feature delivery has left behind, and what it costs the user.

---

## 1. The core problem: accumulation without consolidation

Paid Projects, the v1.2 update wizard, the setup checklist, the clock faces, travel refunds with
OCR, Wear and the widgets each landed as a self-contained, well-built feature. None of them removed
anything, and few of them reused what the previous one had built. Three consequences:

- **The dashboard grew by accretion.** It reaches 13 top-level blocks and roughly 19 card surfaces in
  a realistic worst case, four of which are promotional rather than informational.
- **The design system is bypassed more often than it is used.** Not from carelessness — the token
  scale was missing the values screens actually needed, so literals were the only option.
- **The same UI was built twice.** The month summary, the distribution bar, the pay cell, the empty
  state and the loading skeleton each exist in more than one implementation.

---

## 2. Dashboard density and duplication

`ui/dashboard/DashboardScreen.kt` is 2,260 lines and renders inside a non-lazy scrolling column.
Worst realistic state — clocked out, Paid Projects on, tasks defined, end of month with unresolved
refunds, checklist not dismissed, just after ending a project shift:

> header → refund banner → time-source selector → task bar → project-shift summary → clock card →
> project section + card → setup checklist (6 rows) → month header → distribution card → 4 stat
> cards → gross-pay card → recent-shifts header → recent shifts (5 rows)

Four of those compete for the same visual weight using the same card treatment, and nothing arbitrates
between them.

| Finding | Evidence |
|---|---|
| Refund-banner dismissal is session-only | `DashboardScreen.kt:323` uses `rememberSaveable`; the banner returns on next launch |
| The two pre-clock-in controls stack | The code prevents both *chip lists* showing at once, but in hourly mode the source selector (`ProjectTimeDashboardUi.kt:83-112`) and the task bar (`DashboardScreen.kt:456-470`) still render as two separate blocks above the clock |
| The update wizard can open over the checklist | Both are gated independently; a mid-setup user upgrading sees a full-screen dialog over a dashboard already carrying a welcome card and a 6-step checklist |
| The "Review refunds" CTA lands on the wrong tab | It navigates to Reports, but `ReportsScreen` takes no initial tab and `activeTab` always initialises to `HOURS` |

**Duplication with Reports.** Confirmed against the goldens: the dashboard's month summary block is
near-identical to the top of Reports → Hours, built from a separate implementation.

| Component | Dashboard | Reports |
|---|---|---|
| Distribution bar + legend | `DashboardScreen.kt:2017-2073` — 12dp bar, 50% rounding, 1dp gaps | `ReportsScreen.kt:1822-1872` — 10dp bar, 6dp per-segment rounding, 2dp gaps, percentages |
| Pay cell | `PaySummaryCell:1995` | `PayCell:1926` |

`formatHoursDecimal` existed in four copies. Three pinned `Locale.US`; the dashboard's did not.
**Caveat:** both shipped app languages use a period as the decimal separator, so this was a latent
inconsistency rather than a live defect — it surfaces only when the *device* locale uses a comma
while the app language does not.

---

## 3. Design-system drift

| Metric | Measured |
|---|---|
| Raw `dp` literals outside `theme/` and `design/` | **1,126** (excluding `0.dp` and illustration geometry) vs 265 `Spacing.*` references |
| Elevation token | **Did not exist** — every shadow hand-tuned per component |
| Semantic colour roles | **Did not exist** — three different greens for "positive": `#10B981`, `#22C55E`, `#1E9E63` |
| Raw `Color(0x…)` literals outside `theme/` | **145** |
| Raw `OutlinedTextField` call sites | **41** (plus 18 already behind a `ProjectTextField` wrapper) |
| Raw Material cards | **39**, vs 31 `ElmCard` uses — five different ways to draw a rounded surface |
| Raw Material chips / segmented buttons | **28**, vs 11 uses of the Aurora equivalents |
| Empty-state implementations | **2**, visibly different, split across screens |
| Section-header conventions | **3**; `ElmSectionHeader` used in 6 files |
| Screen-title type scales | **4** for the same role |
| Loading skeletons | **4** hand-rolled, no shared primitive |

**The structural cause matters more than the numbers.** The spacing scale was 4/8/16/24/32 — a clean
ladder that omitted 6, 10, 12, 14 and 18 dp, which are among the most-used values in the codebase.
Developers were not ignoring the system; the system had no answer for them. Nothing (no detekt,
ktlint, `lint.xml` or `.editorconfig`) prevented new drift.

`ReportsScreen.kt:136-153` additionally defined a **second full palette** — `InsightColor`, six
Tailwind hues — with no dark variant, competing with the Aurora indigo→aqua identity.

---

## 4. Accessibility and contrast

- **Zero** `stateDescription`, **zero** `heading()`, **zero** `onClickLabel` in the entire app.
  Expandable cards do not report open/closed to TalkBack; 2,000-line screens cannot be navigated by
  heading.
- **Touch targets below 48dp** at six sites: the break ± steppers (36dp), the task colour swatch
  (28dp), the insight pagination dots (6dp), `ElmSelectors` (40dp min), the weekend-day chip, and the
  Reports tab pill.
- **Reduce motion was ignored where it matters most.** 22 call sites used the correct gate; 9 checked
  `ValueAnimator.areAnimatorsEnabled()` directly — which reads only the *system* animator scale, not
  the app's own setting — and two more had no check at all. Among them: the animated app background
  and every loading skeleton.
- **Contrast.** `#10B981` is used as *text* for positive deltas on white (`ReportsScreen.kt:741,775,1332`)
  at roughly 2.5:1. `InsightColor` has no dark arm at all. Separately, and pre-existing:
  `AuroraPeachDeep` as the overtime stat value on `AuroraOvertimeBg` measures about 2.7:1, below even
  the 3:1 large-text threshold — see the backlog.
- **Untranslated strings reaching Hebrew users:** the app-lock confirmation dialog
  (`SettingsScreen.kt:456-461`), the backup share sheet (`SyncBackupShare.kt:16,19`), `AppLogo.kt:18`,
  `WidgetLayouts.kt:207,521`, and `"Synced with warnings: …"` (`SyncRepositoryImpl.kt:284`), which
  falls through to `UiText.Raw`.
  **Correction to an earlier reading:** `SyncStatusText.kt:55 SYNCED_PREFIX` is a *parsing* token for
  a persisted value, not display text. Translating it would break parsing of existing rows.
- **RTL defect:** `ReminderRulesEditor.kt:173-178` renders `ChevronRight` without `.mirrorInRtl()`
  while every sibling row in `SettingsListUi.kt` mirrors correctly. In Hebrew the arrow points the
  wrong way.
- **No `SnackbarHost`** on Dashboard, Reports or Projects, so a transient failure on those screens can
  only surface as a full-screen error takeover.

---

## 5. Onboarding

Ten or eleven steps stand between install and first value. The dashboard setup checklist
(`domain/setup/SetupChecklist.kt`) already models exactly this kind of deferred setup, so the
infrastructure for a shorter wizard was already built.

**A pay-affecting default was found while reviewing this.** `regionCode` initialises to
`RegionCode.IL`, but `dailyOtText`/`weeklyOtText` initialise to hardcoded `"8"`/`"40"`
(`OnboardingScreen.kt:116-123`) while the Israeli preset is 8.6 h / 42 h (516/2520 minutes,
`RegionPresets.kt:35-36`). The preset is applied only inside `onSelectRegion`, so a user who accepts
the pre-selected region without tapping it gets the wrong overtime thresholds. This becomes more
serious if the work-week step is deferred, and must be fixed in the same change.

---

## 6. What is already good

Worth recording so it is not "cleaned up" by a future pass:

- **Navigation.** A single source of truth for destinations; `PaidProjectsNavGuard`'s tri-state
  `Boolean?` prevents a cold start bouncing the user off the Projects tab; the bottom bar drops labels
  above font scale 1.3/1.5 while preserving `Role.Tab` and the selected state in semantics.
- **Bidi.** `domain/text/BidiText.kt` uses isolate characters (U+2068/U+2069) at 20 call sites and
  strips them on the CSV path — the correct approach, not a workaround.
- **Localisation parity.** All 17 string files match between `values/` and `values-iw/`; the only
  gaps are six `translatable="false"` widget-preview placeholders.
- **Typography.** One stray `.sp` literal in the entire app.
- **Review prompt policy.** Eight blockers, a usage-day requirement and a 90-day cooldown — restrained
  and well-built.
- Earlier audit items verified fixed: `premiumProfiles` now threads through dashboard calculations,
  and onboarding replay preserves settings via `preserveExisting`.

---

## 7. Changed in this pass

| Area | Change |
|---|---|
| Formatting | One `HoursFormatter`, locale-independent, replacing four copies |
| Tokens | Spacing scale extended to the values in use, plus a `Layout` role layer; `Elevation`/`AuroraShadow`; semantic success/warning/info roles with a fill-vs-ink split; `isAuroraDarkTheme()` reads a real flag with the luminance heuristic kept as fallback |
| Guard | `DesignSystemBudgetTest` ratchets five drift metrics; `.editorconfig` records existing conventions |
| CI | `verifyPaparazziDebug` added — the 31 goldens were previously committed but never checked |
| Motion | Every reduce-motion bypass closed, including the app background and all four skeletons |

---

## 8. Backlog

Ordered by value relative to risk. Nothing here is blocked; each is deliberately scoped out of the
current pass.

### High

1. **Finish the spacing migration.** ~1,100 literals remain. Do it directory by directory, one commit
   each, lowering the budget in the same commit. Do **not** round 14dp to 16dp in bulk — that turns a
   mechanical refactor into an unreviewable visual regression.
2. **Fix the overtime text contrast.** `AuroraPeachDeep` doubles as a graphic accent (clock rings,
   progress arcs) and as the overtime stat value. Introduce the ink split at the text sites only, so
   the clock faces keep their accent. `AuroraWarningInk` already exists for this.
3. **Add goldens where there are none.** Projects screens, the Reports refunds/projects tabs, the
   insight carousel, and dark variants of anything except Dashboard and Compensation. These are
   exactly the areas where a mechanical change would be invisible to CI.
4. **Make the Paparazzi CI step blocking** once one run is confirmed green against unmodified goldens
   on a GitHub runner.

### Medium

5. **Unify the Settings card chrome onto `ElmCard`.** Settings uses `cardElevation(1.dp)` — a grey
   Material shadow — against `ElmCard`'s indigo-tinted lift. Unifying changes the look of the entire
   Settings surface and deserves its own review, not a drive-by.
6. **`ElmDropdownField`.** Four copies of the same `ExposedDropdownMenuBox` recipe exist, none with
   `Role.DropdownList` or an expanded/collapsed `stateDescription`.
7. **Reports → `LazyColumn`.** Reports renders unbounded shift rows, week rows and refund cards
   eagerly. It is the screen that actually needs laziness; the dashboard, after this pass, does not.
   Use the existing `AuroraListScreen` pattern.
8. **`liveRegion` decision for the running timer.** `LiveClockTimer` re-emits every second with no
   live-region policy. Announcing every second would be hostile, so the current behaviour is probably
   right — but it should be a recorded decision rather than an omission.

### Low

9. **Landscape and foldables.** There is no `WindowSizeClass`, no fold awareness, and no orientation
   branch. A phone in landscape stays a 448dp centred column with wide empty margins.
10. **`"Main job"` default profile name.** Resolving it from resources means a profile created under a
    Hebrew locale keeps a Hebrew name permanently, because the value is persisted. That is a product
    decision, not a bug fix.
11. **detekt/ktlint**, only after verifying compatibility with the current AGP/Kotlin pair, and as a
    replacement for the budget test rather than an addition to it.

---

## 9. Verification notes

```bash
cd android
./gradlew :app:testDebugUnitTest      # unit, Robolectric render, budget, contrast
./gradlew :app:verifyPaparazziDebug   # 31 goldens — must be unchanged unless a visual change is intended
./gradlew :app:recordPaparazziDebug   # deliberate re-record only
./gradlew :app:lintDebug
```

`recordPaparazziDebug` rewrites all 31 images. After recording, `git status` must list only the
subset a change was expected to touch — that check is the real review artefact. An unexpected file in
the diff means a shared primitive moved when it should not have.

Not covered by any of the above: every `isTabletLayout()` branch (instrumented tests only, which CI
does not run) and RTL beyond the three existing RTL goldens.
