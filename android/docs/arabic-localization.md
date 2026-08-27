# Arabic localization

Arabic (`res/values-ar`) is one of the app's UI languages, alongside English
(`res/values`), Hebrew (`res/values-iw`) and Russian (`res/values-ru`). This
document is the terminology contract: it exists so the same concept does not
arrive in the UI under three different Arabic words, which is the usual failure
mode once more than one person edits the strings.

Its Russian counterpart is [russian-localization.md](./russian-localization.md).
The mechanical rules below (placeholders, `translatable="false"`, product names)
are deliberately the same in both.

## Status

The Arabic strings are a **complete first pass, pending native-speaker review.**
Every translatable key in `res/values` has an Arabic counterpart — 1,719 in
`:app` and 34 in `:wear` — so lint's `MissingTranslation` has nothing to report
and the language is fully usable. What has *not* happened is a native Arabic
speaker reading the wording.

Review these first, in this order:

1. **Payroll, tax and leave vocabulary** — `strings_domain.xml`,
   `strings_settings.xml`, `strings_leave.xml`. These carry regulatory terms
   (premium stacking, statutory sick-pay tiers, deduction bases) where a loose
   synonym changes the meaning rather than just the register.
2. **The four `<plurals>`** — the `few` / `many` split is the single most
   error-prone thing in this folder. See the table below.
3. **Legal text** — `strings_legal.xml` and the disclaimers at the bottom of
   `strings_leave.xml`. The English is deliberately hedged ("estimates only",
   never "legally owed"); the Arabic must stay equally hedged.
4. **`strings_reports.xml` insight cards** — these are jokes, and jokes are
   where a faithful translation still lands wrong. Two separate questions for
   the product owner, not the translator: whether the wordplay survives
   (`insight_binge_text` leans on a Breaking Bad / Better Call Saul pun that
   does not), and whether the Israeli national references — Tel Aviv–Eilat,
   El Al, HaTikvah, army-service hours — are the right content for an
   Arabic-reading audience. They were translated faithfully rather than
   substituted, because changing which content a locale shows is a product
   decision, not a translation one.

Two smaller things worth a look:

- `weekday_short_labels` uses CLDR's short Arabic day names (`ثلاثاء`,
  `أربعاء`), which are longer than the Hebrew single letters. Both call sites
  wrap (`FlowRow`, and a comma-joined review row), so nothing overflows — but
  the weekend picker is taller in Arabic than in the other two languages.
- `complication_label` renders "complication" as `إضافة الساعة`. There is no
  settled Arabic term for the Wear OS concept; if Google's own wording differs,
  match that instead.

## Variant

Modern Standard Arabic, resource folder `values-ar`, no regional qualifier. This
is what Android, Google and Microsoft ship for Arabic UI, and it reads neutrally
for every Arabic-speaking audience. If wording ever needs to read more locally,
add a regional overlay (`values-ar-rPS`, `values-ar-rEG`, …) holding only the
keys that differ, rather than forking the whole folder.

## Conventions

**Verbal nouns for actions.** Buttons and menu items use the verbal noun
(masdar) rather than the imperative — `حفظ` not `احفظ`, `إضافة مناوبة` not
`أضف مناوبة`. This matches the register Android itself uses in Arabic and it is
what the Hebrew strings do with their infinitives. The imperative is reserved for
onboarding copy that deliberately addresses the reader.

**Bidi marks.** A Latin word or a digit that starts a segment inside Arabic text
gets a RIGHT-TO-LEFT MARK (`‏`) in front of it, exactly as the Hebrew
strings do — for example `‏ElmTrackr يقدّر الأجر…` and
`‏1.5 = أجر بنسبة 150%`. Without it the bidi algorithm pushes the Latin run
or the number to the wrong end of the line. Percentages and multipliers written
at the start of a clause are the common case.

**Placeholders are never reordered casually.** `%1$s` / `%1$d` may move within a
sentence, but positional indices must keep pointing at the same argument. Strings
with a bare `%s` / `%d` have exactly one argument and must keep exactly one.
Strings carrying `formatted="false"` in `res/values` keep that attribute in
`values-ar` too — the Hebrew file does the same.

**Plurals need six categories.** Arabic's CLDR set is `zero, one, two, few,
many, other`, where English has `one, other` and Hebrew has `one, two, many,
other`. All four `<plurals>` in the app supply all six:

| Category | Applies to | Noun form after the number |
|----------|-----------|---------------------------|
| `zero`   | 0 | phrased as a negation (`لم …أي`) rather than "0 items" |
| `one`    | 1 | singular, number written as a word |
| `two`    | 2 | dual (`مناوبتان`), number written as a word |
| `few`    | n % 100 = 3–10 | plural (`%d مناوبات`) |
| `many`   | n % 100 = 11–99 | singular (`%d مناوبة`) |
| `other`  | 100, 101, fractions | singular (`%d مناوبة`) |

The `few` / `many` split is the one machine translation reliably gets wrong: 3–10
takes the broken plural, 11–99 takes the singular accusative. Do not collapse
them.

**Not translated.** The six `translatable="false"` widget-preview strings in
`strings_widgets.xml` are absent from `values-ar` by design — they are Android
Studio preview scaffolding, not UI. `ElmTrackr` is a product name and stays in
Latin script everywhere. Currency codes (`ILS`, `USD`) stay Latin; only the
currency *names* are translated.

## Glossary

The left column is the English string as it appears in `res/values`.

### Core objects

| English | Arabic | Note |
|---------|--------|------|
| Shift | مناوبة | plural مناوبات, dual مناوبتان |
| Home | الرئيسية | nav tab |
| Reports | التقارير | |
| Projects | المشاريع | |
| Settings | الإعدادات | |
| Task | مهمة | |
| Project | مشروع | |
| Client | عميل | |
| Leave | إجازة | time off, not "departure" |
| Break | استراحة | |
| Receipt | إيصال | |
| Ride | رحلة | travel-refund context |
| Refund claim | مطالبة استرداد | |

### Time tracking

| English | Arabic | Note |
|---------|--------|------|
| Punch in / Clock in | تسجيل الحضور | |
| Punch out / Clock out | تسجيل الانصراف | |
| On shift | في مناوبة | |
| Clocked out | خارج المناوبة | |
| Duration | المدة | |
| Overnight shift | مناوبة ليلية ممتدة | one crossing midnight |
| Timezone | المنطقة الزمنية | |

### Pay

| English | Arabic | Note |
|---------|--------|------|
| Pay | الأجر | never الراتب — this app pays by the hour, not a salary |
| Hourly rate | الأجر بالساعة | |
| Rate | معدل الأجر | shortened to المعدل where the sentence already says أجر |
| Base rate | معدل الأجر الأساسي | |
| Overtime | الساعات الإضافية | |
| Premium | علاوة | plural علاوات — the extra above the base rate |
| Multiplier | المضاعف | |
| Stacking | تراكم العلاوات | how premiums combine |
| Threshold | الحد | |
| Tier | شريحة | |
| Deduction | خصم | plural خصومات |
| Tax | ضريبة | |
| VAT | ضريبة القيمة المضافة | |
| Fee | الأتعاب | what a freelancer charges |
| Compensation profile | ملفّ أجر | |
| Payroll | الرواتب | the employer's payroll function |
| Estimate | تقدير | pay figures are always estimates — keep the hedge |

### App mechanics

| English | Arabic | Note |
|---------|--------|------|
| Sync | مزامنة | |
| Backup | نسخة احتياطية | |
| Widget | أداة | home-screen widget |
| Tile | لوحة | Wear OS tile |
| Complication | إضافة الساعة | Wear OS; review — no settled Arabic term |
| App lock | قفل التطبيق | |
| Device language | لغة الجهاز | |
| Discard | تجاهل | |
| Retry | إعادة المحاولة | |
