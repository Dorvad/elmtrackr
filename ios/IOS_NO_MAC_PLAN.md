# iOS no-Mac implementation plan

**Status:** Planning document  
**Audience:** Dor + AI coding tools + a non-technical Mac helper  
**Goal:** Build a native iOS version of ElmTrackr while Dor does not personally own a Mac.

---

## 1. Why this plan exists

Dor can write and review most of the iOS source code from Cursor, GitHub, and non-Mac tools, but iOS apps still need Apple tooling for the final project shell, simulator runs, signing, archiving, and TestFlight/App Store upload.

The practical workflow is therefore:

```text
Dor + Cursor: create almost all Swift source files in a local Swift Package.
Dad's Mac: create/open the tiny Xcode app shell, connect the package, run builds, and send screenshots/errors.
GitHub: pass changes back and forth.
```

Dad should not need to write code, understand Swift, use Terminal, or debug anything. He should only follow exact click-by-click Xcode instructions and send screenshots when something fails.

---

## 2. Repository facts this plan must respect

ElmTrackr is currently Android-first:

- `android/` is the active product.
- `supabase/` is active and shared.
- `android/docs/supabase-contract.md` is the canonical source for backend tables, columns, persisted enum values, and wire formats.
- `app/`, `components/`, `lib/`, and `types/` are frozen legacy web areas.
- The iOS app must not revive the frozen web app.
- The iOS app must not be a WebView wrapper.
- The iOS app must not use Capacitor.
- New backend fields, schema changes, or persisted enum changes must not be invented from iOS.

The iOS app should be a **native Swift + SwiftUI app** that shares the same Supabase project and gradually reaches feature parity with Android.

---

## 3. Recommended structure

Create the iOS work under `ios/`:

```text
elmtrackr/
├── android/                         # Existing active Android app
├── supabase/                        # Existing shared backend schema/migrations
├── app/                             # Frozen legacy web app, do not revive
└── ios/
    ├── IOS_NO_MAC_PLAN.md           # This document
    ├── README.md                    # iOS developer notes
    ├── DAD_XCODE_SETUP.md           # Click-by-click Mac helper instructions
    ├── TESTING_CHECKLIST.md         # Manual QA checklist for Dad
    ├── TESTFLIGHT_PREP.md           # Later, when ready
    ├── Config/
    │   ├── ElmTrackr.example.xcconfig
    │   └── ElmTrackr.local.xcconfig # Local only, gitignored, never committed
    ├── ElmTrackr.xcodeproj          # Created later on Dad's Mac
    └── ElmTrackrCore/
        ├── Package.swift
        ├── Sources/
        │   └── ElmTrackrCore/
        │       ├── RootView.swift
        │       ├── App/
        │       ├── Domain/
        │       ├── Data/
        │       ├── Features/
        │       └── Shared/
        └── Tests/
            └── ElmTrackrCoreTests/
```

### Why a local Swift Package?

Most Xcode project files are hard to edit safely without Xcode. A local Swift Package is much easier: Cursor can create and edit Swift files inside `ios/ElmTrackrCore/Sources/ElmTrackrCore/`, and Xcode will usually pick them up without Dad manually adding each file to a target.

The tiny Xcode app shell should do little more than:

```swift
import SwiftUI
import ElmTrackrCore

struct ContentView: View {
    var body: some View {
        RootView()
    }
}
```

That keeps Dad's role minimal.

---

## 4. Non-goals

Do not do these unless explicitly requested later:

- Do not create an iOS WebView around the frozen Next.js app.
- Do not use Capacitor.
- Do not implement product features in `app/`.
- Do not change Supabase migrations from the iOS port unless the Android contract is updated in the same work.
- Do not invent new remote columns.
- Do not hardcode Supabase keys.
- Do not commit local Supabase config.
- Do not require Dad to write code.
- Do not ask Dad to fix Xcode errors.
- Do not attempt TestFlight before the app builds and runs locally on Dad's Mac.

---

## 5. Source-of-truth files

Before changing iOS implementation details, Cursor should read:

```text
README.md
ANDROID_FIRST.md
android/README.md
android/docs/supabase-contract.md
supabase/schema.sql
```

When implementing specific features, Cursor should also inspect the matching Android files under:

```text
android/app/src/main/java/com/elmtrackr/app/
```

Useful Android areas:

```text
navigation/
ui/auth/
ui/onboarding/
ui/dashboard/
ui/shifts/
ui/reports/
ui/settings/
domain/model/
domain/repository/
data/auth/
data/local/
data/repository/
```

The iOS implementation does not need to copy Kotlin code literally, but it should mirror the same product behavior, data contract, and safety rules.

---

## 6. Backend contract summary for iOS

The current Supabase contract includes these backend entities:

```text
profiles
user_settings
shifts
refund_claims
compensation_profiles
premium_profiles
tasks
```

Important contract rules:

- `android/docs/supabase-contract.md` is canonical.
- Web TypeScript types are not authoritative.
- Persisted enum parsing must be tolerant.
- Unknown remote enum strings must not crash the app.
- Do not use unsafe enum decoding.
- Dates sent to Supabase should use ISO-8601/timestamptz-compatible formats.
- Numeric values from Supabase may need flexible parsing.
- Local pending records should win over remote data until pushed.

### Tables to model in iOS

Minimum domain models:

```text
Profile
UserSettings
Shift
RefundClaim
CompensationProfile
PremiumProfile
Task
```

Minimum persisted enums:

```text
RefundAction
RefundDirection
RefundProvider
ClockStyle
RegionCode
StackingPolicy
PremiumType
SyncStatus
```

If Android adds another persisted enum, iOS must add a safe parser before consuming remote data.

---

## 7. Supabase and secret handling

The Android app builds even when Supabase credentials are missing, and the iOS app should do the same.

Recommended iOS behavior:

```text
No Supabase config -> app opens, shows Not Configured state for auth/sync.
Valid Supabase config -> auth and sync can work.
Bad Supabase config -> clear error, no crash.
```

Never commit real keys.

Use this committed template:

```text
ios/Config/ElmTrackr.example.xcconfig
```

Use this local-only file on the Mac:

```text
ios/Config/ElmTrackr.local.xcconfig
```

The local config file should be gitignored.

Expected values:

```text
SUPABASE_URL = https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY = YOUR_ANON_PUBLIC_KEY
```

Only the anon/public key belongs in the app. Never use a service role key in iOS.

Supabase auth redirect URLs should remain compatible with Android:

```text
elmtrackr://auth/callback
elmtrackr://auth/reset-password
```

Dad should not create or edit the real config unless Dor sends him exact values and exact instructions.

---

## 8. Offline-first iOS architecture

Android uses Room as local source of truth and syncs to Supabase in the background. iOS should preserve the same product principle:

```text
User action
  -> write locally first
  -> mark syncStatus as PENDING_CREATE / PENDING_UPDATE / PENDING_DELETE
  -> schedule/trigger sync
  -> push pending local changes
  -> pull remote changes
  -> update local source of truth
```

Recommended iOS layers:

```text
Domain/
  Models/
  Enums/
  Repositories/

Data/
  Local/
  Remote/
  Sync/

Features/
  Auth/
  Onboarding/
  Dashboard/
  Shifts/
  Reports/
  Settings/
  Tasks/
  Refunds/
  Compensation/
  Premiums/

Shared/
  UI/
  Utils/
  Formatting/
```

Local persistence options:

1. Start with in-memory repositories for UI prototyping.
2. Add simple durable local storage once the UI builds on Dad's Mac.
3. Use SwiftData/Core Data/SQLite/GRDB only when the Mac build loop is stable.
4. Keep repository protocols stable so storage can be swapped later.

For the no-Mac workflow, it is safer to start with simple code that builds, then add persistence gradually.

---

## 9. Sync order

Use the sync order from `android/docs/supabase-contract.md`:

```text
1. tasks
2. shifts
3. refund_claims
4. user_settings
5. profiles
6. compensation_profiles
```

Notes:

- Tasks must sync before shifts because shifts can reference `task_id` and store task snapshots.
- `premium_profiles` exists in the current contract. Do not ignore it when modeling the domain. If full sync support is not implemented in the MVP, document it clearly and do not invent an unsourced sync order without checking Android implementation.
- Pull can choose an order optimized for display, but push order must protect foreign-key dependencies.
- Local `PENDING_*` records must win over remote until pushed.
- Do not destructively delete local records unless the contract and Android behavior clearly support it.

---

## 10. Dad's role

Dad is the Mac helper. He should only do click-based tasks.

Dad can do:

```text
Install Xcode.
Download or clone the repo.
Open the iOS project.
Create the first tiny Xcode app shell if it does not exist.
Add the local ElmTrackrCore package.
Replace ContentView.swift with RootView once.
Choose an iPhone Simulator.
Press Run.
Take screenshots.
Send errors to Dor.
Compress and send the ios folder if needed.
```

Dad should not do:

```text
Write code.
Use Terminal unless Dor gives exact copy-paste commands.
Fix Swift errors.
Change random Xcode settings.
Edit Supabase config unless Dor sends exact values.
Touch Android files.
Touch Supabase migrations.
```

Create `ios/DAD_XCODE_SETUP.md` with extremely simple steps and screenshots/wording assumptions.

---

## 11. Dor's working loop

Use this loop for every phase:

```text
1. Ask Cursor for one small implementation step.
2. Review the files.
3. Commit and push.
4. Ask Dad to pull/download and run in Xcode.
5. Dad sends screenshot or error.
6. Paste the error back into Cursor.
7. Cursor makes the smallest safe fix.
8. Commit, push, retest.
```

Do not let Cursor implement many features before the Mac build is verified.

The first milestone is not real auth or sync. The first milestone is simply:

```text
Dad can run an iPhone Simulator and see RootView with ElmTrackr tabs.
```

---

## 12. Implementation phases

### Phase 0 — planning

Create:

```text
ios/IOS_NO_MAC_PLAN.md
```

No code yet.

Definition of done:

```text
Plan exists.
No Android changes.
No Supabase changes.
```

---

### Phase 1 — local Swift Package starter

Create:

```text
ios/ElmTrackrCore/Package.swift
ios/ElmTrackrCore/Sources/ElmTrackrCore/RootView.swift
ios/README.md
ios/DAD_XCODE_SETUP.md
```

RootView should show a simple SwiftUI TabView:

```text
Dashboard
Shifts
Reports
Settings
```

No Supabase, no auth, no persistence.

Definition of done:

```text
Package source exists.
Dad instructions exist.
Cursor has not touched Android or Supabase.
Dad can attempt Xcode setup.
```

---

### Phase 2 — Xcode shell on Dad's Mac

Dad creates or opens:

```text
ios/ElmTrackr.xcodeproj
```

The app target imports `ElmTrackrCore` and renders `RootView()`.

Definition of done:

```text
The app opens in iPhone Simulator and shows placeholder tabs.
```

Expected fixes:

- Package platform version mismatch.
- Files not visible to package.
- `public` access missing on RootView.
- Xcode target not linked to local package.

Cursor should fix source/package issues. Dad should only retry.

---

### Phase 3 — app shell and routing

Add:

```text
AppSessionState
AppRouter
AppEnvironment
AppShellView
LoadingView
Auth placeholder
Onboarding placeholder
MainTabView
```

Routing states:

```text
loading
notConfigured
signedOut
needsOnboarding
signedIn
```

Definition of done:

```text
App still opens in Simulator.
Placeholder auth/onboarding/main routes are controllable in code.
```

---

### Phase 4 — domain models and safe enums

Add domain models:

```text
Profile
UserSettings
Shift
RefundClaim
CompensationProfile
PremiumProfile
Task
```

Add safe persisted enums:

```text
RefundAction
RefundDirection
RefundProvider
ClockStyle
RegionCode
StackingPolicy
PremiumType
SyncStatus
```

Add helpers:

```text
DateCoding
FlexibleNumber
ElmTrackrError
```

Definition of done:

```text
Unknown remote enum values do not crash.
Contract parsing tests exist if possible.
Dad can still build the app.
```

---

### Phase 5 — repository protocols and in-memory prototype

Add repository protocols:

```text
AuthRepository
ProfileRepository
SettingsRepository
ShiftRepository
TaskRepository
RefundClaimRepository
CompensationProfileRepository
PremiumProfileRepository
```

Add in-memory implementations first.

Definition of done:

```text
UI can be built without Supabase.
Data does not need to survive restart yet.
Dad can test fake flows.
```

---

### Phase 6 — fake/local UI MVP

Implement with in-memory repositories:

```text
Auth UI
Onboarding UI
Dashboard clock in/out
Shifts list and edit
Reports
Settings
Tasks MVP
Refund claims scaffold
```

Definition of done:

```text
Dad can click through the prototype without real backend config.
No Supabase required.
No app crash on restart, though data may reset until persistence is added.
```

---

### Phase 7 — durable local persistence

Only after the fake/local UI builds reliably on Dad's Mac, add durable local persistence.

Recommended sequence:

1. Keep repository protocols unchanged.
2. Add a simple durable store first if SwiftData/Core Data adds build complexity.
3. Ensure data survives app restart.
4. Preserve sync fields on local entities.

Each synced local entity should support:

```text
localId
remoteId
userId where relevant
createdAt
updatedAt
deletedAt or soft-delete marker where relevant
syncStatus
lastSyncedAt
lastSyncError
```

Definition of done:

```text
Clocked shifts and settings survive app restart.
Dad can verify by closing and reopening the Simulator app.
```

---

### Phase 8 — Supabase dependency and config

Add Supabase Swift only after the app shell is proven.

Add:

```text
SupabaseConfig
SupabaseClientProvider
RemoteConfigurationState
```

Behavior:

```text
Missing config -> no crash.
Invalid config -> clear error.
Valid config -> client available.
```

Definition of done:

```text
App still opens without config.
Dad can later add local config if Dor sends exact values.
```

---

### Phase 9 — real Supabase auth

Implement:

```text
sign up
sign in
sign out
forgot password
session restore
not configured state
deep link handling
```

Deep links:

```text
elmtrackr://auth/callback
elmtrackr://auth/reset-password
```

Definition of done:

```text
App can sign in/out with Supabase when config exists.
App still works safely without config.
```

---

### Phase 10 — remote data sources

Add remote data sources for contract tables:

```text
profiles
user_settings
shifts
refund_claims
compensation_profiles
premium_profiles
tasks
```

Requirements:

```text
Table names match contract.
Column names match contract.
Wire formats match contract.
No UI reads directly from remote.
No invented backend fields.
Unknown remote values do not crash.
```

Definition of done:

```text
Remote data sources can be tested with fakes or controlled Supabase calls.
No UI direct network dependency.
```

---

### Phase 11 — offline sync engine

Add:

```text
SyncEngine
SyncScheduler
SyncResult
SyncCursorStore
SyncConflictPolicy
PendingSyncCounter
```

Behavior:

```text
Push pending local changes first.
Pull remote changes after push.
Local pending records win until pushed.
Synced local records can accept newer remote updates.
Failures mark records as failed and store lastSyncError.
Missing config returns notConfigured, not crash.
Manual sync from Settings.
Best-effort background sync only.
```

Definition of done:

```text
Create local shift offline.
Reconnect.
Tap Sync Now.
Shift appears in Supabase.
No duplicate active shifts.
```

---

### Phase 12 — sync status UI

Add:

```text
Dashboard sync badge
Settings sync section
Pending sync count
Last successful sync time
Last error summary
Manual Sync Now button
```

Definition of done:

```text
User can tell whether data is saved locally, pending sync, synced, failed, or not configured.
```

---

### Phase 13 — TestFlight preparation

Only after Dad can build and run reliably:

Create:

```text
ios/TESTFLIGHT_PREP.md
```

Include non-technical instructions for:

```text
Apple ID / Developer Team
Bundle identifier
Version and build number
Signing
Archive
Validate
Distribute App
App Store Connect upload
Screenshots/errors to send Dor
```

Definition of done:

```text
Dad has a click-by-click guide for upload attempts.
No TestFlight attempt happens before local app testing succeeds.
```

---

## 13. Cursor prompt guardrails

Every Cursor prompt for iOS should include some version of:

```text
Important constraints:
- iOS app code should live mostly in ios/ElmTrackrCore as a local Swift Package.
- Avoid editing .xcodeproj unless absolutely necessary.
- Dad does not know code; do not require manual code edits on the Mac.
- Do not modify Android files.
- Do not modify Supabase migrations.
- Do not revive the frozen web app.
- Do not use WebView.
- Do not use Capacitor.
- Do not hardcode secrets.
- Do not invent backend fields.
- Follow android/docs/supabase-contract.md exactly.
- Unknown remote values must not crash the app.
- Keep each step small and buildable.
```

When Dad sends an Xcode error, use this Cursor prompt shape:

```text
My dad tested the iOS app on his Mac. He does not know code. Fix the repo so the next attempt is easier.

Context:
- iOS code lives mostly in ios/ElmTrackrCore.
- Dad uses Xcode only to build and run.
- Avoid requiring him to edit code.
- Avoid touching .xcodeproj unless absolutely necessary.
- Do not modify Android files.
- Do not modify Supabase migrations.
- Keep the app buildable.

Xcode result:
PASTE ERROR OR SCREENSHOT DESCRIPTION HERE

Task:
1. Identify the likely cause.
2. Make the smallest safe fix.
3. Do not refactor unrelated code.
4. Update Dad-facing instructions only if needed.
5. Tell me exactly what to ask him to test next.
```

---

## 14. Manual testing checklist for Dad

Create `ios/TESTING_CHECKLIST.md` later with these items:

```text
App opens in Simulator
Four tabs show
Fake sign up works
Fake sign in works
Onboarding saves
Dashboard clock in works
Dashboard clock out works
No duplicate active shift
Manual shift create works
Manual shift edit works
Manual shift delete works
Reports show totals
Settings save
Sign out returns to auth
Task create works
Clock in with task works
Old shift keeps task snapshot
Refund claim create works
Offline save works
Reopen app keeps local data after persistence phase
Sync Now works after Supabase phase
Not Configured state is clear when keys are missing
```

Dad should send screenshots, not explanations.

---

## 15. Definition of success

### First success

```text
Dad can run an iPhone Simulator and see RootView with ElmTrackr placeholder tabs.
```

### Prototype success

```text
Dad can click through fake auth, onboarding, clock in/out, shifts, reports, settings, tasks, and refund scaffold with no backend config.
```

### Local app success

```text
Data survives app restart on iOS.
```

### Backend success

```text
Real Supabase auth works.
Local data syncs to Supabase.
Remote data pulls back safely.
No unknown enum or schema shape crashes the app.
```

### TestFlight-ready success

```text
Dad can archive and upload a build using click-by-click docs, or Dor gets access to a Mac/cloud build runner and handles upload directly.
```

---

## 16. Recommended immediate next step

Create the local Swift Package starter:

```text
ios/ElmTrackrCore/Package.swift
ios/ElmTrackrCore/Sources/ElmTrackrCore/RootView.swift
ios/README.md
ios/DAD_XCODE_SETUP.md
```

The first version should do only this:

```text
Show ElmTrackr title.
Show Dashboard, Shifts, Reports, and Settings tabs.
Build without Supabase.
Require no local secrets.
Require no database.
Require no real auth.
```

Then send Dad `DAD_XCODE_SETUP.md` and ask for one result:

```text
Can the placeholder app open in iPhone Simulator?
```

Do not proceed to real features until that works.
