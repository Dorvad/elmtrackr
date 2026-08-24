# Declined audit findings

Items raised by one of the four audits and **deliberately not done**, with the
reason. Recorded so they are not re-raised as oversights, and so the reasoning can
be challenged rather than guessed at.

A declined item is not a permanent no. Each entry says what would change the answer.

---

## Golden image testing

**Raised as:** re-record the 31 Paparazzi goldens and make `verifyPaparazziDebug`
blocking; add goldens for the Projects screens, the Reports refunds/projects tabs,
the insight carousel, and dark variants.

**Declined because the suite currently proves nothing.** All 31 goldens fail on a
machine other than the one that recorded them, against an *unmodified* checkout —
verified with a `git worktree` on a pre-change commit. Paparazzi renders through
layoutlib and text rasterisation varies with the host JDK and font stack; CI pins
JDK 17 while a current developer image is on 21. Re-recording locally only moves the
failure to everyone else, and adding more images to a suite that cannot distinguish
a real change from a host difference is negative value.

**What would change it:** record the images inside the CI container, so the same
environment that checks them produces them. Then the blocking step is worth having
and new coverage is worth adding.

**In the meantime** the visual work in this effort was verified through Robolectric
render tests, semantics assertions and computed contrast ratios — which caught real
defects the goldens would not have (a mispositioned link, four unreadable status
pills), because a golden only tells you a pixel changed, not whether it was wrong.

---

## Landscape and foldable layouts

**Raised as:** there is no `WindowSizeClass`, no fold awareness and no orientation
branch; a phone in landscape stays a 448 dp centred column with wide margins.

**Declined as not worth the work now.** It is accurate, and it is real work across
every screen. But nothing is broken — landscape is usable, just not optimised — and
a time tracker is used in portrait, held in one hand, for a few seconds at a time.

**What would change it:** tablet usage showing up in analytics, or a foldable
becoming a target device.

---

## Resolving the "Main job" default profile name from resources

**Declined because the fix creates a worse bug.** The name is persisted. Resolving
it from resources means a profile created while the app is in Hebrew keeps a Hebrew
name permanently, including for a user who then switches to English — and the app
cannot tell that name apart from one the user typed.

**What would change it:** storing a "this is the default name" marker separately
from the name itself, so display can be localised while the stored value stays
stable. That is a data-model change, not a string change.

---

## detekt / ktlint

**Declined as cost without coverage.** `DesignSystemBudgetTest` already ratchets
five drift metrics and has blocked real regressions during this effort — it caught a
raw `18.dp` that would otherwise have shipped. A second linter would need
configuring, tuning and silencing before it caught anything the budget test does
not.

**What would change it:** wanting rules the budget test cannot express — formatting,
complexity limits — and the appetite to tune them. If adopted it should *replace*
the budget test, not sit beside it.

---

## Rounding wage money to the minor unit before summing

**Raised as:** per-bracket amounts are rounded independently, so displayed brackets
can sum one minor unit away from the displayed total.

**Declined because the codebase already made this decision explicitly.**
`NoBinaryFloatingPointTest` accepts double-based arithmetic for wages, and the
boundary is documented there. Nobody has reported a one-agora discrepancy, and the
change touches every money path in the pay engine.

**What would change it:** bracket-total agreement mattering on the exported PDF —
i.e. someone reconciling a payslip line by line and finding it off by one unit.

---

## Payments against a cancelled billing record

**Raised as:** `ProjectPeriodTotals` filters to active records, so a payment whose
record was later cancelled vanishes from `received`.

**Declined because the route into that state is blocked.**
`ProjectBillingCorrection.canCancel` refuses to cancel a record that has payments
against it. This is defence-in-depth for a sync race that cannot currently occur.

**What would change it:** multi-record billing UI, or a sync path that can cancel a
record on one device while a payment lands on another. At that point assert the
invariant rather than filter it away.

---

## Folding Settings → Pay into the Compensation rules screen

**Raised as:** Settings → Pay edits only the default profile, and two undisclosed
editors exist for the same hourly rate.

**Declined as an architecture change dressed as a bug fix.** The disclosure the
audit actually asked for shipped — `settings_overtime_thresholds_hint` says the
thresholds apply to the default job. Merging the two editors is a product decision
about which screen owns pay setup.

---

## A per-shift compensation profile picker at clock-in

**Declined because it is a feature request, not a defect.** Clock-in books to the
default profile; the per-shift picker exists in the edit form. A user with two jobs
can correct a punch afterwards. Belongs on the product list.

---

## `PayWeekMinutes.forEachWithPriorWeekMinutes` grouping by ISO Monday

**Fix declined; the rename is not.** The helper groups by ISO Monday while the
engines anchor weeks on `rules.weekStartDay`. Currently harmless because
`sumMonthlyPay` discards the accumulator — but the name promises a boundary it does
not honour, which is a trap for the next person to wire it in. Rename rather than
re-implement.

---

## The IL `breakRatio` classification edge case

**Split, and half declined.** `payableNetMinutes` can exceed gross under round-up or
a minimum-shift top-up, and `breakRatio = net / grossMinutes` then maps topped-up
minutes into a compressed slice of wall clock, so their rest/night classification
uses the wrong window. Narrow: it needs rounding *and* a top-up to bite.

The performance half of the same finding was **not** declined. Half of it is now closed:
the per-payable-minute `Instant.atZone` is gone (`domain/time/ZoneMinutes`), and
`flowOn(computationDispatcher)` reached the dashboard, which never had it. The
`weekStateBeforeShift` repetition — the O(shifts²) part — is still open, and
`optimization-debug-audit-2026-08.md` says why it was left: memoising a classification in
a payroll engine needs a complete cache key, and the Israeli engine has no dedicated test
file to catch an incomplete one. Characterisation suite first, memo second.

---

## Foreground service for a running shift

**Not declined — blocked on verification.** See `README.md` and
`code-review-2026-07.md`. From Android 12 the background-start restrictions apply to
exactly the punch paths that need it most (widget, Wear, shortcut), so which
exemption covers a widget tap must be confirmed against current platform docs and on
hardware. Shipping it unverified risks turning a widget punch into a
`ForegroundServiceStartNotAllowedException`, which is worse than the gap it closes.

## HTTPS App Links for the auth callback

**Not declined — blocked on infrastructure.** Needs a hosted
`.well-known/assetlinks.json` carrying the release signing fingerprint, and the
Supabase redirect URLs updated. Flipping `autoVerify` without the hosted file breaks
sign-in outright. The client-side half — PKCE, so an intercepted callback carries a
useless one-time code instead of live tokens — has shipped.

Narrower than it was. Google sign-in goes through Credential Manager, which returns
an ID token in-process and never touches a redirect URL, so the custom scheme is now
only on the paths that genuinely need a link: email confirmation, password recovery,
and the Google browser fallback for devices with no credential provider.
