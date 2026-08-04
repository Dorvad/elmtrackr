# What changed — August 2026, in plain terms

A record of everything altered in this round of work, written for someone who has
not read the code. Each entry says what was wrong, what a user would have noticed,
and what happens now.

Nothing here changes stored data. No shift, project, invoice or payment is rewritten
— only how figures are calculated for display and how the app behaves.

---

## 1. Pay and hours figures that disagreed with each other

These are the most important changes. In each case the app showed two numbers that
described the same thing and did not match.

### Overtime hours were counted from the wrong time

**Was:** the monthly report counted overtime from 8 hours a day, while the pay
figures counted it from 8 hours 36 minutes — the Israeli standard. So a report could
say "2 hours overtime" beside a pay figure that had treated most of those minutes as
normal hours. This affected the report card, the CSV and the PDF, for every Israeli
user on the default setup.

**Now:** both read the same threshold, taken from the user's own job settings.

### A short shift could be paid nothing

**Was:** if the workplace rounds time to the nearest 15 minutes and also guarantees a
minimum shift, a 7-minute call-out rounded down to zero and then skipped the
minimum — so it paid nothing at all. The minimum exists precisely for that case.

**Now:** the minimum is applied to the time actually worked, so the guarantee holds.
A shift with no worked time still pays nothing.

### Premium rates were ignored on some screens

**Was:** if a user assigned a premium rate to a shift, most screens showed it, but
anything fed by one particular data source silently fell back to the 150% holiday
rate instead.

**Now:** every screen reads the same rates.

### Overnight weekend shifts were priced as if they were one day

**Was:** the app decided whether a shift was a weekend shift from its start date
only. A Friday 23:00 to Saturday 07:00 shift was paid entirely at the weekday rate;
a Saturday 23:00 to Sunday 07:00 shift entirely at the weekend rate. The hours report
split both correctly, so the hours and the money described different halves of the
same shift.

**Now:** weekend minutes are counted day by day, matching the report.

### Overtime restarted every month

**Was:** weekly overtime is worked out over a week. When a week straddled the 1st of
the month, the app forgot the hours worked in the previous month, so the first
partial week of every month under-counted overtime. Worst where overtime is weekly
rather than daily — Israel's 42-hour week, and US federal, which has no daily
threshold at all.

**Now:** the calculation looks back to the start of the week, while still reporting
only the month you are viewing.

### Two further mismatches found while fixing the above

Neither was in any audit; both were caught by a new test failing.

- **Overtime hours were invented for some job types.** The US federal setup has no
  daily overtime at all, but the report still counted every minute past 8 hours as
  overtime — hours that no pay figure ever paid.
- **Weekend hours were counted for jobs with no weekend premium.** Those minutes are
  excluded from the base that overtime is measured against, so the report *understated*
  overtime for hours the pay engine had counted.

---

## 2. Project money that could not be reconciled

### The effective hourly rate was several times too high

**Was:** a project's fee covers the whole job, but the monthly report divided it by
one month's hours. A ₪10,000 project worked evenly over three months showed roughly
three times its real hourly rate, on screen and in the CSV.

**Now:** divided by all the hours tracked against the project, and labelled
"all-time" so a monthly report cannot imply otherwise.

### The CSV did not add up

**Was:** each project's row showed its billing over its whole life, while the TOTAL
row at the bottom showed only the selected month. A project invoiced in June and
viewed in July showed its amount on its own row and zero in the total. Anyone
checking the export against an accountant's ledger could not make it balance.

**Now:** every money and hours column states its basis — "(period)" or "(all-time)" —
and the period columns add up to the period total. A test enforces that.

### Invoices were marked overdue before they were due

**Was:** viewing the current month on the 5th, an invoice due on the 25th was already
counted in the overdue total — while the project's own badge correctly said
"Billed".

**Now:** overdue is measured against today. Past months are unaffected.

---

## 3. Things that lost your work or gave no way out

### Project, billing and payment forms threw away what you typed

**Was:** fill in a project's name, client, fee, currency, tax and dates — or a
payment amount — then swipe back, and all of it was gone with no warning. The shift
form and the settings screens already asked first.

**Now:** all three ask before discarding, from both the back arrow and the back
gesture.

### The Projects tab could get permanently stuck

**Was:** if the Projects tab hit an error it showed a "Try again" button that did
nothing. The only way to recover was to force-close the app.

**Now:** the button works, like the equivalent on every other screen.

### "Not now" on the refund reminder did not last

**Was:** dismissing the end-of-month travel-refund reminder only held until the app
was closed. Next launch, the same reminder for the same month came back.

**Now:** the dismissal is remembered for that month, and the reminder returns next
month as intended.

### "Review refunds" opened the wrong tab

**Was:** the reminder's button opened Reports on the Hours tab, leaving the user to
find the Travel Refunds tab themselves — the work the reminder existed to save.

**Now:** it opens the refunds tab.

### The clock-out notification could go missing

**Was:** if a user clocked in from the widget or watch and then granted notification
permission inside the app, no notification appeared and there was no Clock Out button
outside the app until something else happened to refresh it.

**Now:** granting the permission posts the notification immediately.

### The "you're in overtime" alert could arrive hours late

**Was:** all reminders used a scheduler the system is allowed to delay while the
phone is idle. For the alert that tells a user they have entered overtime, a delay of
hours defeats the purpose.

**Now:** it uses an alarm that fires on time, or as close as the phone permits, and
falls back safely if the user has not granted exact-alarm permission.

---

## 4. Readability

### Four of five project status labels were unreadable

**Was:** each status pill used one colour for both its tinted background and its
text, which leaves very little contrast. Measured against the accessibility
standard, "Active" reached 1.83 against a required 4.5, "Paused" 2.59 and
"Completed" 3.56.

**Now:** the tint keeps its colour and the text uses a darker (or in dark mode
lighter) shade that passes. Checked by tests in both light and dark themes.

### The overtime figure on the dashboard was hard to read

**Was:** the number was drawn in a colour meant for graphics — clock rings and
progress arcs — measuring 2.69 against a required 4.5. The label beside it already
used the readable shade.

**Now:** all overtime text uses the readable shade. The graphic colour is unchanged
where it belongs.

### Borders were too faint

**Was:** the light theme's border colour measured 2.43 against a required 3.0. The
dark theme had been checked for years; the light one never had.

**Now:** darkened to 3.48, with the missing check added.

### Hours were printed in English for Hebrew users

**Was:** "8h 30m" was assembled in code, so every hourly figure showed English units
regardless of app language — including in the exported PDF, the document a user hands
an employer. The Paid Projects module had solved this for itself, so the app used two
different conventions.

**Now:** one shared formatter reads the units from the translation files, with
numbers formatted for the language.

### Dropdown menus told screen readers nothing about being open

**Was:** the four settings dropdowns gave no indication of whether their list was
open. The only cue on screen is a small arrow, which a screen reader ignores.

**Now:** they announce "Expanded" or "Collapsed", using the same wording as every
other collapsible part of the app. Four near-identical copies of the control became
one.

---

## 5. Security and release safety

### An intercepted sign-in link handed over a live session

**Was:** the app's sign-in callback arrives on a custom link (`elmtrackr://auth`)
that any installed app is allowed to claim. The sign-in method in use put the actual
access keys into that link, so an app that claimed it got a working session.

**Now:** the link carries a single-use code that is worthless to anyone but this app.

> **Needs testing before release.** Sign-in, sign-up and password reset all use this
> link, and there were no test credentials available in this environment. The change
> is one line, but it changes how sign-in completes.

The full fix — moving the callback to a verified web address — needs a file hosted on
a domain and cannot be done from the codebase alone.

### The watch could be impersonated

**Was:** the component that receives clock-in and clock-out from the watch accepted
them from any sender without checking where they came from.

**Now:** punches are only accepted from a currently paired device.

### A release build could be signed with the wrong key

**Was:** if the signing key was missing, the build quietly signed with the
development key and carried on — and the build script then reported "signed APK
produced". The Play Store would reject it, but nothing stops such a file being sent
to a tester or installed directly.

**Now:** a release build fails without the real key. Verification builds can still
opt in to the development key, but must ask for it explicitly and are warned. Applied
to the watch app too.

---

## 6. Documentation corrected

Three statements in the README about the clocked-in notification were wrong: it does
show a live-ticking timer, it *can* be swiped away on Android 14 and later, and the
component that manages it had been renamed. All three are fixed, and the two real
consequences are now written down where someone would look for them.

The July audit documents now record which findings are closed, which were already
fixed before they were raised, and which are open by choice.

---

## What is still open

### Needs a device

- **Sign-in, sign-up and password reset** after the security change above.
- **The camera and large PDF export paths**, which cannot be exercised without
  hardware.

### Needs a decision

- **Backing a running shift with a background service.** Would stop a dismissed
  notification or an aggressive battery saver from leaving a shift running invisibly.
  Scoped and deliberately not built: on modern Android the rules restrict starting
  such a service from exactly the places that need it most — the widget, the watch,
  the home-screen shortcut. Which exception applies has to be confirmed on hardware
  first, because getting it wrong turns a widget tap into a crash.
- **Paid Projects does not sync.** It is the one feature whose data does not survive
  changing phones without a manual backup.
- **App lock has no way out.** A user who turns on app lock and then removes their
  phone's screen lock is locked out; the only recovery clears app data, which loses
  unsynced shifts. Low likelihood, high impact.
- **Onboarding is still 10–11 steps** before a user reaches any value.

### Known and accepted

`docs/declined-findings.md` lists every audit item deliberately not acted on, with
the reason and what would change the answer. The largest are golden-image testing
(the suite cannot currently tell a real change from a difference between machines)
and landscape/foldable layouts.

Remaining tidy-up, none of it user-visible: roughly 1,090 hard-coded spacing values
to migrate onto the design system, the Reports screen to make properly scrolling,
and a shared set of form and header components to replace the last hand-built ones.

---

## How this was checked

Every change was verified with the full test suite, the Android linter, and a
release build with code shrinking — the path where two past defects were found. 74
tests were added and 4 removed (they asserted the English wording of a function that
had no callers and was deleted), leaving 1,545 in total.

Screenshot comparison testing was deliberately not used: all 31 stored images fail
on any machine other than the one that recorded them, even with no changes at all,
so it cannot currently distinguish a real problem from a difference between
computers. Visual work was checked instead by rendering screens in tests, asserting
what a screen reader would receive, and calculating colour contrast directly.
