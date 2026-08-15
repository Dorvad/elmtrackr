# Russian localization

Russian (`res/values-ru`) is the fourth UI language, alongside English
(`res/values`), Hebrew (`res/values-iw`) and Arabic (`res/values-ar`). This
document is the terminology contract: it exists so the same concept does not
arrive in the UI under three different Russian words, which is the usual failure
mode once more than one person edits the strings.

Its Arabic counterpart is [arabic-localization.md](./arabic-localization.md).
The mechanical rules below (placeholders, `translatable="false"`, product names)
are deliberately the same in both.

## Status

The Russian strings are a **complete first pass, pending native-speaker
review.** Every translatable key in `res/values` has a Russian counterpart —
1,719 in `:app` and 34 in `:wear` — so lint's `MissingTranslation` has nothing
to report and the language is fully usable. What has *not* happened is a native
Russian speaker reading the wording.

Review these first, in this order:

1. **Payroll, tax and leave vocabulary** — `strings_domain.xml`,
   `strings_settings.xml`, `strings_leave.xml`. These carry regulatory terms
   (premium stacking, statutory sick-pay tiers, deduction bases) where a loose
   synonym changes the meaning rather than just the register. Note that the
   presets describe US, UK, EU and Israeli law — the Russian must describe
   *those* systems, not map them onto ТК РФ equivalents, which would be wrong.
2. **The four `<plurals>`** — the `one` / `few` / `many` split is the single
   most error-prone thing in this folder. See the table below.
3. **Legal text** — `strings_legal.xml` and the disclaimers at the bottom of
   `strings_leave.xml`. The English is deliberately hedged ("estimates only",
   never "legally owed"); the Russian must stay equally hedged.
4. **`strings_reports.xml` insight cards** — these are jokes, and jokes are
   where a faithful translation still lands wrong. Two separate questions for
   the product owner, not the translator: whether the wordplay survives
   (`insight_binge_text` leans on a Breaking Bad / Better Call Saul pun that
   does not), and whether the Israeli national references — Tel Aviv–Eilat,
   El Al, HaTikvah, army-service hours — are the right content for a
   Russian-reading audience. They were translated faithfully rather than
   substituted, because changing which content a locale shows is a product
   decision, not a translation one. The same cards also quote prices in ₪,
   which reads oddly outside Israel but is what the English does.

## Variant

Standard Russian, resource folder `values-ru`, no regional qualifier. Russian
has no meaningful UI-level regional split (unlike, say, pt-BR/pt-PT), so a
single folder serves every Russian-reading audience.

## Conventions

**Infinitives for actions.** Buttons and menu items use the infinitive —
`Сохранить`, not `Сохрани` or `Сохранение`. This is the Russian software
standard and what Android itself uses. Section headers and labels are nouns
(`Настройки`, `Перерыв`).

**Formal «вы», lowercase.** The app addresses the user as `вы`, not `ты`, and
not capitalised `Вы` — matching modern Russian UI practice. This also solves a
grammatical problem for free: the plural verb form that `вы` takes is
gender-neutral (`вы отработали`), so no string has to guess the reader's
gender. Never introduce a gendered past-tense singular (`отработал`) — it would
be wrong for half the users.

**Guillemets for quotes.** `«Создать уведомление»`, not `"…"` or `“…”`. The
few strings that quote a user-supplied name use `«%1$s»`.

**Decimal comma.** Literal numbers written into strings use the Russian
decimal comma: `1,5 = оплата 150%`. Numbers the app formats at runtime are
already localised by the platform — do not touch those.

**Units.** Hours `ч`, minutes `мин`, seconds `с`, kilometres `км`. Abbreviated
without a full stop, as Russian typography does for these.

**No bidi marks.** Russian is left-to-right, so unlike `values-iw` and
`values-ar` there are no RIGHT-TO-LEFT MARK characters in this folder. If you
copy a string across from Arabic, strip the `‏`.

**Placeholders are never reordered casually.** `%1$s` / `%1$d` may move within a
sentence — and in Russian they often must, because the case ending follows the
word order — but positional indices must keep pointing at the same argument.
Strings with a bare `%s` / `%d` have exactly one argument and must keep exactly
one. Strings carrying `formatted="false"` in `res/values` keep that attribute
here too.

**Plurals need four categories.** Russian's CLDR set is `one, few, many,
other`. There is no `zero` — nought takes `many` — and no `two`. All four
`<plurals>` in the app supply all four:

| Category | Applies to | Noun form after the number |
|----------|-----------|---------------------------|
| `one`    | 1, 21, 31, 101 … (n%10=1, n%100≠11) | nominative singular (`1 смена`) |
| `few`    | 2–4, 22–24 … (n%10=2–4, n%100≠12–14) | genitive singular (`2 смены`) |
| `many`   | 0, 5–20, 25–30 … | genitive plural (`5 смен`) |
| `other`  | fractions (1,5) | genitive singular (`1,5 смены`) |

Two traps machine translation reliably falls into. First, `one` is **not** just
the literal number 1 — it covers 21, 31, 101 — so its string must keep the
`%d` placeholder even where the English `one` case spelled the digit out.
Second, 11–14 are `many`, not `few`, despite ending in 1–4.

**Not translated.** The six `translatable="false"` widget-preview strings in
`strings_widgets.xml` are absent from `values-ru` by design — they are Android
Studio preview scaffolding, not UI. `ElmTrackr` is a product name and stays in
Latin script everywhere, as do `Supabase`, `Google Play`, `Wear OS`, `PayPal`,
`Netflix`, `Spotify` and `SQLCipher`. Currency codes (`ILS`, `USD`) stay Latin;
only the currency *names* are translated.

## Glossary

The left column is the English string as it appears in `res/values`.

### Core objects

| English | Russian | Note |
|---------|---------|------|
| Shift | смена | 1 смена / 2 смены / 5 смен |
| Home | Главная | nav tab |
| Reports | Отчёты | |
| Projects | Проекты | |
| Settings | Настройки | |
| Task | задача | |
| Project | проект | |
| Client | клиент | |
| Leave | отпуск | paid time off; sick leave is больничный |
| Break | перерыв | |
| Receipt | чек | |
| Ride | поездка | travel-refund context |
| Refund claim | заявка на компенсацию | |
| Workplace | место работы | |

### Time tracking

| English | Russian | Note |
|---------|---------|------|
| Punch in / Clock in | отметить приход | |
| Punch out / Clock out | отметить уход | |
| On shift | на смене | |
| Clocked out | смена завершена | |
| Duration | продолжительность | |
| Overnight shift | ночная смена через полночь | one crossing midnight |
| Timezone | часовой пояс | |

### Pay

| English | Russian | Note |
|---------|---------|------|
| Pay | оплата | never зарплата — this app pays by the hour, not a salary |
| Hourly rate | почасовая ставка | |
| Rate | ставка | |
| Base rate | базовая ставка | |
| Gross pay | оплата до вычетов | |
| Overtime | сверхурочные | |
| Premium | надбавка | the extra above the base rate |
| Multiplier | коэффициент | |
| Stacking | суммирование надбавок | how premiums combine |
| Threshold | порог | |
| Tier | ступень | |
| Deduction | удержание | |
| Tax | налог | |
| VAT | НДС | |
| Fee | гонорар | what a freelancer charges |
| Compensation profile | профиль оплаты | |
| Payroll | расчёт зарплаты | the employer's payroll function |
| Estimate | оценка | pay figures are always estimates — keep the hedge |
| Payslip | расчётный листок | |

### App mechanics

| English | Russian | Note |
|---------|---------|------|
| Sync | синхронизация | |
| Backup | резервная копия | |
| Widget | виджет | home-screen widget |
| Tile | плитка | Wear OS tile |
| Complication | дополнение циферблата | Wear OS; review — no settled Russian term |
| App lock | блокировка приложения | |
| Device language | язык устройства | |
| Discard | отменить изменения | |
| Retry | повторить | |
