# Workplaces and leave — what is built, and what is left

**Status:** Domain, persistence and the reporting flow are in. Cloud sync and the
remaining screens are not.

Follows the shape `paid-projects-plan.md` describes: the local model and the
Supabase schema land first, the cloud wiring follows in its own change.

## In

| Area | Where |
|------|-------|
| Supabase schema, RLS, account deletion | `supabase/migrations/20260811000000_workplaces_and_leave.sql` |
| Contract | `android/docs/supabase-contract.md` |
| Domain types | `domain/model/LeaveModels.kt` |
| Calculation engine | `domain/leave/` |
| Room v19 + `MIGRATION_18_19` | `data/local/ElmTrackrDatabase.kt` |
| Entities, DAOs, mappers | `data/local/{entity,dao,mapper}` |
| Repositories, DI, data cleaner, legacy adopter | `data/repository/Local{Workplaces,Leave}Repository.kt` |
| Reporting flow, reachable from the shifts add action | `ui/leave/`, `ui/shifts/ShiftsScreen.kt` |
| Strings, both locales | `res/values{,-iw}/strings_leave.xml` |
| Unit tests (82) | `app/src/test/.../domain/leave/` |
| Migration test | `app/src/androidTest/.../ElmTrackrDatabaseMigrationTest.kt` |

## Left, in the order that unblocks the most

### 1. Commit the exported schema — do this first

`app/schemas/com.elmtrackr.app.data.local.ElmTrackrDatabase/19.json` is a KSP
build artifact and is **not** in this change, because it cannot be written by
hand: the file carries an `identityHash` Room derives from the schema, and a
guessed one would either fail the integrity check at open time or, worse, pass
while describing a schema that does not match.

```bash
cd android && ./gradlew :app:assembleDebug   # writes app/schemas/.../19.json
git add app/schemas/com.elmtrackr.app.data.local.ElmTrackrDatabase/19.json
```

Until it is committed, `ElmTrackrDatabaseMigrationTest` cannot validate against
version 19 and the instrumented CI job fails. Do not create a gap here the way
`9.json` was left missing.

### 2. Balances and policy screens

`LocalLeaveRepository.addBalanceSnapshot` / `estimateBalance` /
`observeBalanceEstimates` and `LocalWorkplacesRepository.updatePolicyRules` are
in and tested; the screens are not. Strings for both already exist
(`leave_balance_*`, `leave_policy_*`, `leave_workplaces_*`).

Add them as `SettingsDestination` entries — the checklist is in
`ui/settings/SettingsScreen.kt`: enum constant, `backDestination()`,
`motionOrder()`, a branch, and a `SettingsHubNavRow` in `SettingsHub`.
`SettingsNavigationTest` fails if a destination has no way back.

Show all three numbers separately — official, used since, estimated — and keep
the negative case visible. `LeaveBalanceEstimate` already carries
`unconvertibleCount` for the hours/days mismatch the UI has to surface rather
than guess.

### 3. Reports and export

Do **not** add leave to `MonthlyReport`. Its documented invariant is
`regular + overtime + weekend == total` minutes, and the distribution bar, CSV
totals row and PDF header all rely on it.

Mirror the Paid Projects precedent instead: a separate nullable
`paidLeave: MonthlyLeaveEarnings?` on `ReportsUiState.Ready`, built in its own
flow in `ReportsViewModel` and combined last (above `.flowOn`) so it cannot
delay the hourly report. `LeaveEarningsBuilder.buildMonthly` already returns the
shape, keeping days and hours apart rather than converting between them.

CSV: `ReportsViewModel.buildCsvContent` has no money columns at all today, so a
separate leave block after the `TOTAL` row reads better than widening a
hours-only header. Everything user-typed must go through `csvEscape` — it is the
formula-injection guard, and the export goes to a payroll recipient. PDF:
`ReportExporter.shareShiftPdf` already takes the whole `Ready` state, so a new
field needs no signature change.

### 4. Cloud sync

The five tables exist server-side with RLS, and the entities carry the full
`remoteId` / `syncStatus` / `deletedAt` / `lastSyncedAt` block, but there is no
`data/remote/RemoteLeave*` and no push or pull step. Leave is device-local until
that lands, so a reinstall loses it — the same gap Paid Projects had between
Room v16 and `b5412d9`, and worth closing the same way.

Order in both directions: workplaces → leave policies and balance snapshots →
absence events → absence allocations, all before shifts and compensation
profiles, which carry a workplace link. `SyncIdMapper` needs resolvers for
workplaces and absence events. The full per-table checklist is the one Paid
Projects followed; `LocalBackupFormat`/`Exporter`/`Importer` need the tables too,
with a `BACKUP_FORMAT_VERSION` bump.

## Deliberately not built

Automatic accrual, seniority entitlement, medical document storage, payslip OCR,
HR synchronisation, employer approval, and family-member sick categories. The
schema has room for accrual — `accrualEnabled`, `accrualDaysPerMonth`,
`maxAccruedDays` are on the policy and the Israeli preset records 1.5 and 90 as
reference figures — but it stays off. Accruing correctly for a part-time or
irregular hourly worker depends on the actual work pattern, partial months,
seniority and sector agreements, and a confidently wrong balance is worse than no
balance.

## One thing to verify before trusting the Israeli defaults

The sick ladder (0% / 50% / 50% / 100% from day one) and the vacation
`÷ 90` basis are reproduced from the Kol Zchut references listed in the original
brief because they are the arrangement most users will recognise on a payslip.
They are presented as an editable preset and as estimates, never as legal advice,
and the app has no code path that asks whether a workplace is Israeli. The sick
basis is implemented as "average pay per day actually worked over the preceding
three months", which is the natural reading rather than a verified statutory
formula — worth a check against a real payslip before the preset is described to
users as matching the law.
