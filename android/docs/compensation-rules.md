# Compensation rules

ElmTrackr estimates pay on the device. Figures are estimates, not payroll, tax, or legal advice. The Android app in `android/` is the calculation engine. The frozen Next.js app is not authoritative and its presets have drifted.

Retrieval date for the sources below: **24 September 2026**.

## Architecture

```
Shift times + breaks + flags
        ↓
CompensationResolver
  snapshot on the shift, else the profile, else legacy user settings
        ↓
RegionCode.IL → IsraeliCompensationEngine (minute segments)
other regions → PayrollCalculator.calculateGenericShiftPay (tier segments)
        ↓
ShiftPayBreakdown (gross by category)
        ↓
shift screen, month summary, reports, CSV, PDF, statistics
```

Wear OS shows hours and status. It does not recompute pay.

Money for wages is `Double` (`hourlyRate / 60 * minutes * multiplier`). Project fees use `BigDecimal` (`domain/money/Money.kt`). Wage totals can drift by fractions of a minor unit across a long month. Display formatting must not be reused as the calculation.

### Important files

| Role | Path |
| --- | --- |
| Presets | `android/app/src/main/java/com/elmtrackr/app/domain/compensation/RegionPresets.kt` |
| Rule model | `android/app/src/main/java/com/elmtrackr/app/domain/model/CompensationModels.kt` |
| JSON codec | `android/app/src/main/java/com/elmtrackr/app/domain/compensation/CompensationRulesCodec.kt` |
| Israel engine | `android/app/src/main/java/com/elmtrackr/app/domain/compensation/IsraeliCompensationEngine.kt` |
| Other regions | `android/app/src/main/java/com/elmtrackr/app/domain/PayrollCalculator.kt` |
| Settings | `android/app/src/main/java/com/elmtrackr/app/ui/settings/CompensationSettingsScreen.kt` |
| Sick / vacation defaults | `android/app/src/main/java/com/elmtrackr/app/domain/leave/LeavePresets.kt` |

Profiles are stored locally and synced. A completed shift keeps a compensation snapshot, so later edits to the profile do not rewrite that shift until the user recalculates it.

Changing region replaces the rule set only after a confirm when the current rules differ from the outgoing preset. Saved profiles are not rewritten by an app update.

## How a minute is paid (Israel)

1. Payable minutes: gross minus unpaid breaks, then rounding, then a minimum-shift floor.
2. Each payable minute is placed on the local clock (Asia/Jerusalem for the Israel preset). DST transitions use `java.time` instead of a fixed offset.
3. The daily standard is chosen once from the first payable minute: night work (at least 2 hours between 22:00 and 06:00) uses 7 hours; otherwise the day weekly rest begins uses the pre-rest standard before the rest-start time; otherwise an employer short day uses its own standard; otherwise the ordinary daily standard.
4. Weekly rest begins on the first configured weekend day that follows a working day. With Fri+Sat that is Friday. With Sat+Sun that is Saturday. Minutes before `weeklyRestStartTime` on that day are not rest.
5. Daily and weekly overtime ladders are combined with the profile stacking policy. The Israel preset uses highest-only.
6. On a rest minute the rate is the rest base (weekend or holiday multiplier, whichever is higher when both apply) plus any overtime above 1.0. That yields 150% / 175% / 200% on the shipped preset.

## Regional defaults

These are starting points for a new profile. They are not a determination that a particular employer must pay them. Users can edit each field. Values already saved on a profile are left alone.

### Israel (`IL`)

| Rule | Default | Basis | Status |
| --- | --- | --- | --- |
| Ordinary day | 8 h 36 min (516 min) | 2018 extension order: 42-hour week, commonly 8.6 h on four days | Correct as the ordinary day. The fifth 8.6 h day crosses the weekly 42 h threshold. |
| Week | 42 h (2520 min), Sunday start | Extension order to 42 hours. Statute s.3 still says 45 hours; the extension order is what workplaces apply. | Correct for the private sector. Public sector is often 40 h from 1 Sep 2024 and is not this preset. |
| Pre-rest day | 7 h (420 min) before 17:00 on the day rest starts | Law s.2(b): the day before weekly rest is at most 7 hours | 7 hours is the statutory cap. 17:00 is a practical default, not candle-lighting time. |
| Employer short day | Unset | Extension order: one employer-chosen day, commonly 7 h 36 min (456 min) | Not invented. Set it in Work week → Shortened workday. |
| Overtime | 125% for the first 2 hours, then 150%, daily and weekly | Law s.16 | Correct as a minimum. Agreements may pay more. |
| Weekly rest | 150%, and 175% / 200% when that time is also overtime | Law s.17 is 150% for rest hours. The app adds the overtime premium above 1.0. | 150% matches s.17. Stacking with overtime is the usual payslip practice; confirm against the contract if it differs. |
| Night | 7 h day when ≥2 h fall between 22:00 and 06:00. Multiplier 1.0 | Law definition of night work and s.2(b) | Correct. There is no universal night percentage in that law. |
| Holiday | Manual flag, 150%, tiered with overtime the same way as rest | Not a single statutory percentage for every holiday hour | Ambiguous. The flag is per shift. |
| 6-day week | Not the preset | Private sector: ordinary days up to 8 h, Friday up to 7 h. Public sector often Sun–Thu 7 h and Friday 5 h. | Choose weekend days, daily standard, and the pre-rest standard. Do not assume 8.6 h. |

Sources:

- Hours of Work and Rest Law, 5711-1951, ss.2, 3, 7, 16, 17: https://www.nevo.co.il/law_html/law00/5174.htm
- Kol Zchut, work day and work week (cites the 42-hour extension order and the public-sector 40-hour week): https://www.kolzchut.org.il/he/יום_עבודה_ושבוע_עבודה
- Ministry of Labor guidance on the statute: https://www.gov.il/BlobFolder/legalinfo/rest-flexibility/he/workers-rights_rest-flexibility.pdf

### United States, federal (`US`)

| Rule | Default | Basis | Status |
| --- | --- | --- | --- |
| Weekly overtime | 1.5× after 40 h | FLSA, 29 U.S.C. §207 | Correct for non-exempt employees. No federal daily overtime. |
| Daily overtime | Off | FLSA has none | Correct. State law may differ; California is a separate preset. |
| Weekend | Off | No federal weekend premium | Correct |
| Holiday | 1.5× only if the shift is marked special | Not required by the FLSA | Contractual suggestion, not law. Set the multiplier to 1.0 if the contract pays straight time. |
| Workweek start | Sunday | Employer-defined 168-hour period | A default, not a rule. Editable. |

Source: https://www.law.cornell.edu/uscode/text/29/207

### California (`US_CA`)

| Rule | Default | Basis | Status |
| --- | --- | --- | --- |
| Daily | 1.5× after 8 h, 2× after 12 h | Labor Code §510(a) | Correct for employees covered by §510 |
| Weekly | 1.5× after 40 h, not stacked on top of daily | §510: the employer need not combine more than one overtime rate | Highest-only matches that sentence |
| 7th day | 1.5× for the first 8 h, 2× after, when all seven days of the workweek were worked | §510(a) | Correct for a 7-day workweek. Alternative workweeks (§511) and some wage-order exceptions are not modeled. |
| Holiday | 1.5× if marked special | Not required by §510 | Contractual suggestion |

Source: https://leginfo.legislature.ca.gov/faces/codes_displaySection.xhtml?lawCode=LAB&sectionNum=510

### United Kingdom (`GB`)

| Rule | Default | Basis | Status |
| --- | --- | --- | --- |
| Overtime premium | Off. A 1.5×-after-48 h ladder is stored but inactive | Working Time Regulations 1998 set a 48-hour average cap, not a pay rate | Correct to leave pay off. Enable and edit the ladder to match the contract. |
| Holiday premium | 1.5× if a shift is marked special | No statutory bank-holiday premium | Contractual suggestion |
| Week | 48 h stored as the weekly standard | Working Time Regulations 1998, reg. 4 | The number is the hours cap, not a pay trigger, which is why overtime ships disabled |

Source: https://www.legislation.gov.uk/uksi/1998/1833/regulation/4

### European Union (`EU`)

There is no single EU overtime percentage. Directive 2003/88/EC limits average hours and sets rest, not a pay multiplier. The preset (1.25× after 8 h and after 40 h) is explicitly illustrative. Edit it to the member state and the contract.

Source: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32003L0088

### Sick leave (Israel preset)

Day 1 unpaid, days 2–3 at 50%, day 4 onward at 100%. This matches the commonly applied Sick Pay Law sequence and is editable. Accrual is off. A workplace that pays better replaces the ladder; the app warns if a ladder pays less than the preset and does not block the save.

## Adding or updating a preset

1. Edit `RegionPresets.kt` only. Do not copy percentages into UI code.
2. Keep new fields optional with a null default so existing JSON still decodes.
3. Encode and decode the field in `CompensationRulesCodec.kt`.
4. If the field changes pay, teach both engines or document which region uses it.
5. Add a characterisation test that states the minutes and the multiplier, including one minute before a threshold, the threshold, and one minute after.
6. Update this file with the source URL and the retrieval date.
7. Do not migrate stored profiles to the new numbers. New profiles pick up the preset. Existing profiles keep `rules_json`. A shift snapshot keeps the rules from when it was calculated.

## Running the tests

From `android/`:

```bash
./gradlew :app:testDebugUnitTest --tests com.elmtrackr.app.domain.compensation.IsraeliCompensationEngineTest --tests com.elmtrackr.app.domain.compensation.RegionPresetsTest --tests com.elmtrackr.app.domain.PayrollCalculatorTest --tests com.elmtrackr.app.domain.compensation.CompensationRulesCodecTest
```

The broader pay suite is `:app:testDebugUnitTest`.

## Known limits

- Wage arithmetic is `Double`, not minor units.
- Public-sector Israel (40 h), a 6-day week, holiday eves, and sector extension orders are not separate presets.
- California alternative workweeks and the small-hours 7th-day exceptions in some wage orders are not applied.
- The generic engine does not split one shift at a mid-day rest boundary. That split exists in the Israel engine.
- The frozen web presets still use older US/IL numbers. Do not treat them as the product.
