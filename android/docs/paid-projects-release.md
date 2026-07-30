# Paid Projects — MVP release readiness

Companion to `paid-projects-plan.md`, which is the implementation plan this was
built against. This document is the release record: what shipped, what did not,
what was verified and how, and what is known to be wrong.

Branch: `claude/paid-projects-android-bq9i2q`. Database version 18.

---

## 1. Feature summary

Paid Projects is an **optional** module for people who are paid a fixed price for
a piece of work rather than an hourly wage. It is off by default and adds nothing
to the interface until a user turns it on.

**Projects.** A project carries a name, a client name, a currency, an agreed fee,
an optional tax label and rate, an optional hour budget, an optional target
hourly rate, optional start and deadline dates, and notes. Work status moves
between draft, active, paused, completed, cancelled and archived. Work status and
billing status are independent: finishing the work does not mark it paid, and
being paid does not mark it finished.

**The three amounts.** Every project shows the fee before tax, the tax, and the
client total. A user may enter whichever figure they know — quote the fee and
have tax added, or quote the client total and have the fee extracted. Both routes
produce the same three figures, and they always add up exactly.

**Time.** A shift can be tracked against a project instead of against an hourly
wage. The existing shift engine is reused unchanged; a single field on the shift
records which of the two pays for it. Project time earns no wage and contributes
to no overtime, premium or payroll figure.

**Billing.** A user records that they billed a client: the amount, the date, an
optional due date, and a reference from an invoice they issued somewhere else.
The record snapshots the amounts at the moment of billing and never changes when
the project is later edited.

**Payments.** Payments are recorded against a billing record. Partial payments
are supported; the outstanding balance is derived, never stored, and never goes
negative. Billing status — not billed, billed, partially paid, paid, overdue,
cancelled — is derived from the records and today's date. There is no manual
"mark as paid" toggle.

**Insights and reports.** Effective hourly rate, hour budget usage, days to
deadline, days to payment due, days overdue. A projects tab in Reports with
filters, and CSV export. Hourly earnings and project payments are always
presented separately, because they are measured on different bases.

**Reminders.** Optional local notifications for a payment due soon, due today, or
overdue.

---

## 2. Explicit MVP exclusions

None of the following is in this release. Each was designed to be addable later
without a destructive migration.

| Excluded | Why it is safe to add later |
| --- | --- |
| Official invoice PDF generation | Billing records already snapshot every figure an invoice needs |
| Country-specific invoice compliance | Tax label and rate are free-form per project, not a fixed national model |
| Accounting integrations | Rows carry `remoteId` and `syncStatus`, the existing sync envelope |
| Online payments | Payments are a separate table keyed to a billing record |
| Automatic exchange rates | No rate is stored anywhere, so none has to be unlearned |
| Currency conversion | Cross-currency arithmetic is refused by the type system |
| Clients as a full CRM | `Project.clientId` exists and is unused; client names are text today |
| Milestones | Billing records are already many-per-project in the schema |
| Project expenses | Nothing claims to be profit, so adding costs does not contradict it |
| Recurring invoices | Billing records are independent rows with their own dates |
| Quotes | A project already holds an agreed fee before anything is billed |
| Credit notes | Payments and cancellations are separate, additive facts |
| Government tax reporting | Tax is recorded per record, separately from the base |
| Peppol | No document format is generated to be incompatible with |
| Automatic tax advice | The app records the user's own rate and says so |

The app states in its own copy that it does not create official invoices, does
not give tax advice, does not convert currencies, and does not calculate net
profit.

---

## 3. Database migration summary

Three new tables and a small number of added columns. No table was dropped, no
column was removed or retyped, and there is **no destructive fallback** anywhere
in the database configuration.

| Version | Change |
| --- | --- |
| 14 → 15 | `user_settings.featuresPaidProjects` and the Paid Projects defaults |
| 15 → 16 | `projects`, `project_billing_records`, `project_payments`; `shifts.projectId`, `shifts.projectNameSnapshot` |
| 16 → 17 | `shifts.compensationSource`, nullable with no backfill |
| 17 → 18 | `project_billing_records.notes` |

**Money on disk.** Amounts are stored as canonical plain-decimal `TEXT`, not as
floating point and not as integer minor units. Dates that are calendar dates —
billed on, due on, paid on — are stored as epoch day, so no time zone can move
them to a different day.

**`compensationSource` is nullable on purpose.** A shift written by an older
build has no value, and absent is read as employee-paid. Backfilling would have
required deciding what pre-existing shifts meant; treating unknown data as
project time would have silently removed hours from someone's pay.

**Verification.** All 16 exported production schema versions — 1–8 and 10–17 —
were replayed forward to 18 against real SQLite and reached the expected schema.
Version 16 shift data survives 16 → 17 byte-for-byte with `compensationSource`
arriving `NULL`. Room's own migration tests require an instrumented device and
were not run; this replay is a substitute for them and is called out again under
known limitations.

---

## 4. Navigation behaviour summary

There is **one** destination list. Both the phone bottom bar and the tablet rail
render it, so the two surfaces cannot disagree about which destinations exist.

| Feature state | Destinations |
| --- | --- |
| Off | Home, Shifts, Reports, Settings — **four** |
| On | Home, Shifts, Reports, **Projects**, Settings — **five** |

Projects sits fourth so Settings stays last. The original four keep their order
and their slide direction, which is derived from that order, whether or not the
module is on. Five is the hard ceiling, asserted in a test that fails the build
if a sixth primary destination is ever added.

Turning the module off while a project screen is open does not remove the
destination from the graph — that would crash. The user is moved to the dashboard
through the ordinary tab-navigation path, which preserves the back stack and
per-tab saved state.

At large font scales five labels do not fit. The labels are dropped and the icons
carry the bar; the tab name, role and selected state stay in the accessibility
semantics, so a screen reader is unaffected. The bar never scrolls horizontally
and no destination is ever hidden behind a gesture.

Enabling the module announces the new destination to accessibility services,
because it changes navigation on a screen the user is not currently looking at.

---

## 5. Financial QA

**Repository search.** No `Float` or `Double` appears anywhere in the money
layer. Two non-money uses of `Float` remain and are correct: a budget-usage ratio
that feeds a progress indicator and a "near budget" heuristic. The over-budget
decision itself is integer minute arithmetic, not the ratio.

One genuine defect was found and fixed during this pass: the hour budget was
parsed as `Double` and rendered back as `minutes / 60.0`, which put
`1.6666666666666667` in the edit form for a 100-minute budget. It is now
`BigDecimal` throughout, rendered at two decimal places, which always round-trips
to the same whole minute.

**No manual percentage arithmetic.** Tax is computed once, in `ProjectFee`, and
the tax amount is always the residual — client total minus base — never an
independently rounded percentage. That is what makes `base + tax == clientTotal`
hold exactly rather than usually.

**No manual currency-symbol concatenation.** All rendering goes through
`NumberFormat.getCurrencyInstance`, which places the symbol and picks the
separators for the locale. Where a currency code has to appear beside an amount,
it is joined by a translatable format string, so the order is the translator's.

**Locale-sensitive formatting.** Number input accepts both `.` and `,` and
normalises. Output is formatted for the interface locale, independently of the
project's currency.

**Tested.** Very large amounts (10¹⁵), one-cent and zero fees, zero tax,
exclusive tax, inclusive tax, repeating decimals across six rates and five
amounts, partial payments, 118 sequential payments settling to exactly zero,
final payment, overpayment refusal, wrong-currency refusal, three simultaneous
currencies, zero tracked hours, one minute of tracked time, and 500 project
shifts.

---

## 6. Performance QA

Indexes exist on every column the module queries by: `userId`,
`userId + workStatus`, `userId + syncStatus`, `remoteId` on projects, and
additionally `projectLocalId` and `billingRecordLocalId` on billing records and
payments, so a project's billing history and a record's payments are index
lookups rather than table scans.

Aggregation over 500 project shifts is integer minute arithmetic and was
exercised in tests. The project report is combined last in the Reports view model
so that it cannot delay the hourly report a user already had.

**Not measured on a device.** No profiling run, no frame-timing capture, no
measurement with a genuinely large database. See known limitations.

---

## 7. Privacy and security

| Area | Finding |
| --- | --- |
| Encryption | Projects, billing records and payments are in the same SQLCipher database as every other table, under the same Keystore-backed passphrase |
| Logging | No project, billing, payment or money value is written to any log |
| Backups | `allowBackup="false"`, so no Android cloud auto-backup. Project data travels only through the app's own explicit local backup export, and round-trips |
| Widgets | No widget code reads project data at all |
| Shortcuts | No shortcut exposes project data |
| Lock screen | Fixed during this pass. Billing reminders are the first notification in the app to carry a monetary amount; they now declare `VISIBILITY_PRIVATE` and supply a public version that names the project but omits the figure |
| Feature off | Reminders are cancelled by their own notification ids, so the active-shift notification is untouched. Data is preserved, screens are closed |

---

## 8. Product copy review

Every mention of an invoice, of tax, of profit or of currency conversion in the
Paid Projects copy is either a denial or a reference to a document the user
produced elsewhere. Verified in both English and Hebrew:

- "ElmTrackr does not create official invoices."
- "ElmTrackr does not give tax advice and does not issue official invoices."
- "ElmTrackr does not convert currencies."
- "ElmTrackr does not decide which tax applies and gives no tax advice."
- "Expenses are not tracked, so this is not net profit."

No string claims legal compliance, certification, or that the app replaces
accounting software. The billing reference field refers to "the number from your
invoice, receipt book or accounting software" — the user's system, not this one.

---

## 9. Automated test results

**1,335 tests, 0 failures.** `assembleDebug`, `lintDebug` (0 findings),
`compileDebugAndroidTestKotlin` and `testDebugUnitTest` all pass.

The eight long-standing `ProjectsRenderTest` failures are resolved — see item 2
under known limitations for what they actually were.

Suites added specifically for release QA:

| Suite | Covers |
| --- | --- |
| `PaidProjectsJourneyTest` | The release journeys end to end through the real view model |
| `ProjectFinancialEdgeCaseTest` | Magnitude, tax modes, repeating decimals, payments, currencies, hours |
| `ProjectDateSemanticsTest` | Date-only storage, midnight, cross-zone disagreement |
| `BidiTextTest` | Which token shapes reorder in Hebrew and which never did |
| `CurrencyGlobalizationTest` | Zero- and three-decimal currencies, ISO 4217, symbol ambiguity |
| `ProjectLocalizationRenderTest` | Both languages against both currencies |
| `NavigationAccessibilityRenderTest` | Four and five destinations, rail, text scaling, touch targets |
| `ProjectStatusAnnouncementRenderTest` | Status never conveyed by colour alone |
| `PaidProjectsToggleAccessibilityTest` | The feature selector as a screen reader meets it |
| `PayrollProjectIsolationTest` | Source-level guard that project money cannot reach payroll |
| `ProjectTimePayrollIsolationTest` | Identical payroll output with and without project shifts |

---

## 10. Manual QA results

**Not performed.** No build was installed on a phone, a tablet or an emulator
during this work. Everything in this document was verified by automated test, by
source inspection, or by offline replay against real SQLite.

The following are therefore **unverified by manual QA** and are listed so nobody
mistakes an automated pass for a device pass:

- Process death and state restoration during a project shift
- Deep links resolving to a project while the module is being toggled
- Rapid repeated toggling of the feature flag on a real device
- Theme changes mid-session
- Actual tablet rendering and the rail at real breakpoints
- Notification appearance on a real lock screen, including the redacted version
- Widget behaviour after the module is enabled and disabled
- Foldable and landscape layouts

A device pass on those points is the remaining gate before shipping.

---

## 11. Known limitations

1. **Room migration tests are not run.** They require an instrumented device.
   Forward replay of all 16 production schemas was done offline against SQLite
   instead. This is a substitute, not an equivalent.

2. **Unit tests fork a JVM per class.** This is a resolved defect recorded for
   the next person who wonders why the build does it.

   Eight `ProjectsRenderTest` detail-screen assertions failed in a full-suite run
   with `AppNotIdleException` while passing 22 of 22 when the class ran alone. The
   failure arrived with the commit that introduced those screens, so it was never
   a regression, and it was never a defect in the screens either — it was state
   surviving between test classes in one JVM. Bisecting narrowed it to the Compose
   classes (the failure reproduces with only those eleven) and cleared
   `FullAppScreenshotJvmTest` and `PaidProjectsNavigationRenderTest`
   individually. Raising the idling budget to 240 s changed nothing, which is
   consistent with a wedged clock rather than a slow one;
   `LinearProgressIndicator`, clock reads during composition, and infinite
   animations inside the tree under test were all eliminated as causes.

   `forkEvery = 1` fixes it, and the suite got **faster** rather than slower —
   about 5.5 minutes against 34 — because per-class forks parallelise where a
   single JVM did not. The remaining suspicion, not needed for the fix and so not
   chased to a conclusion, is the per-minute `while (true) { delay(...) }` ticker
   in the active-project-shift card leaving a scheduled task on Robolectric's
   shared looper. That ticker is legitimate in production and matches what the
   shifts list and dashboard already do.

3. **One active billing record per project in the UI.** The schema supports many
   and the queries already return a list; only the interface assumes one.

4. **Hebrew is the only non-English locale.** Other RTL languages would work
   mechanically but have no translations.

5. **`ShiftDurationCalculator` hardcodes English `h`/`m`** across roughly thirty
   hourly-feature call sites — reports, widget, dashboard, PDF export. This is a
   pre-existing app-wide localisation gap, untouched because changing it would
   alter the hourly experience. Project durations are localised.

6. **No expenses, so no profit.** Every rate the module shows is revenue per
   hour. The copy says so; a user who reads "effective hourly rate" as take-home
   pay would be wrong, and income tax and costs still come out of it.

7. **The settings toggle row is now a single toggleable node.** This fixed a real
   defect — the switch had no accessible name — but it changes every settings
   switch, not only Paid Projects: tapping the row now toggles it.

---

## 12. Release notes — English

**Paid Projects (optional)**

ElmTrackr now supports fixed-price work alongside hourly shifts. The feature is
off by default; nothing changes until you turn it on in Settings.

- Track projects with an agreed fee, a client, a currency and an optional
  deadline.
- Enter your fee before tax and have tax added, or enter the client total and
  have your fee worked out from it. The three amounts always add up.
- Track time against a project instead of against your hourly rate. Project time
  never affects your wages, overtime or premium calculations.
- See your effective hourly rate — the fee before tax divided by the hours you
  actually worked.
- Record that you billed a client, with a reference from the invoice you issued
  elsewhere. Record part payments and final payments; ElmTrackr keeps the
  outstanding balance.
- Optional reminders for payments due soon and payments overdue.
- Reports and CSV export for projects, kept separate from your hourly earnings.

ElmTrackr records what you tell it about your projects, billing and payments. It
does not create official invoices, give tax advice, convert currencies or replace
your accounting software.

---

## 13. Release notes — Hebrew

**פרויקטים בתשלום (אופציונלי)**

‏ElmTrackr תומך כעת בעבודה במחיר קבוע לצד משמרות שעתיות. התכונה כבויה כברירת
מחדל, ודבר אינו משתנה עד שמפעילים אותה בהגדרות.

- ניהול פרויקטים עם תשלום מוסכם, לקוח, מטבע ותאריך יעד אופציונלי.
- אפשר להזין את התשלום לפני מס ולתת למס להתווסף, או להזין את סה״כ לתשלום על ידי
  הלקוח ולחלץ ממנו את התשלום. שלושת הסכומים תמיד מסתכמים במדויק.
- מעקב זמן על פרויקט במקום לפי שכר שעתי. זמן פרויקט אינו משפיע על השכר, על שעות
  נוספות או על תוספות.
- שכר אפקטיבי לשעה — התשלום לפני מס חלקי השעות שעבדתם בפועל.
- תיעוד חיוב של לקוח, עם אסמכתת חיוב חיצונית מהחשבונית שהפקתם במקום אחר. תיעוד
  תשלומים חלקיים ותשלום סופי, כאשר ElmTrackr מחשב את היתרה לתשלום.
- תזכורות אופציונליות לתשלומים שמועדם מתקרב ולתשלומים באיחור.
- דוחות וייצוא CSV לפרויקטים, בנפרד מההכנסה השעתית.

‏ElmTrackr מתעד את מה שאתם מזינים על הפרויקטים, החיוב והתשלומים. הוא אינו מפיק
חשבוניות רשמיות, אינו מספק ייעוץ מס, אינו מבצע המרות מטבע ואינו מחליף תוכנת
הנהלת חשבונות.

---

## 14. Play Store — What's new (English)

> **Paid Projects — optional, off by default**
>
> Working for a fixed price, not an hourly rate? Turn on Paid Projects in
> Settings to track project fees, tax and client totals, log time against a
> project without it touching your wages, and see your real hourly rate.
>
> Record what you billed and the payments you receive, including part payments,
> and ElmTrackr keeps the outstanding balance. Optional reminders for payments
> due and overdue.
>
> Hourly tracking is unchanged. ElmTrackr records your own project and payment
> information — it does not issue official invoices or give tax advice.

*(Under Google Play's 500-character limit for release notes.)*

---

## 15. Play Store — מה חדש (Hebrew)

> **פרויקטים בתשלום — אופציונלי, כבוי כברירת מחדל**
>
> עובדים במחיר קבוע ולא לפי שעה? הפעילו פרויקטים בתשלום בהגדרות כדי לנהל תשלום על
> הפרויקט, מס וסה״כ לתשלום על ידי הלקוח, לתעד זמן על פרויקט בלי להשפיע על השכר,
> ולראות את השכר האפקטיבי לשעה.
>
> תעדו מה חייבתם ואת התשלומים שהתקבלו, כולל תשלומים חלקיים, ו-ElmTrackr יחשב את
> היתרה לתשלום. תזכורות אופציונליות לתשלומים שמועדם מתקרב ולתשלומים באיחור.
>
> המעקב השעתי לא השתנה. ElmTrackr מתעד את המידע שאתם מזינים — הוא אינו מפיק
> חשבוניות רשמיות ואינו מספק ייעוץ מס.

---

## 16. Recommended post-MVP backlog

Roughly in the order the schema is already prepared for.

1. **Reusable client profiles.** `Project.clientId` exists and is unused. The
   cheapest item on this list.
2. **Multiple billing records per project.** The schema and queries already
   support it; only the UI assumes one. Unlocks milestones and deposits.
3. **Deposits.** A billing record dated before the work, against the same project.
4. **Milestones.** Follows from multiple billing records.
5. **Project expenses.** The first item that changes what the numbers mean — with
   costs recorded, "revenue per hour" could become a genuine margin, and the copy
   would need revisiting alongside it.
6. **Project timesheet PDF.** Tracked shifts already carry the project link.
7. **Payment-request PDF.** Still not an invoice; a billing record has every
   figure it needs.
8. **Country-specific invoice modules.** The first item that requires real legal
   work rather than engineering.
9. **Accounting integrations.** Rows already carry `remoteId` and `syncStatus`.
10. **Exchange-rate snapshots.** Rates stored per record, never a live lookup, so
    a historical figure never changes retroactively.
11. **Recurring projects.** A template plus a schedule.
12. **Online payment links.** Largest surface area: a payment provider, webhooks,
    and reconciliation against records the user may also have edited by hand.
