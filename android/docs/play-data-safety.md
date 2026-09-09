# Play data safety — what leaves the device, and what to declare

**Written:** September 2026, in response to the Data safety rejection below.
**Covers:** the whole listing — phone `:app` and watch `:wear`, both `com.elmlaunch.myapp`.
**Owner:** whoever submits the release. The form is filled in Play Console; this
document is the evidence behind every answer, so the next person does not have to
re-derive it from the source.

> **Data safety section in Google Play User Data policy: Invalid Data safety form**
>
> The way that your app collects and shares user data does not match your
> declaration in the data safety form. We detected user data transmitted off
> devices that you have not disclosed in your app's data safety form as user data
> collected.

## What Play is actually saying

This is a **mismatch** finding, not a "you collect too much" finding. Google
observed traffic leaving the device that the form does not account for. There are
only two ways to close it: declare what leaves, or stop it leaving. Both were used
here — [§5](#5-what-changed-in-code-with-this-document) narrows what can leave,
[§3](#3-the-declaration-to-enter-in-play-console) declares everything that still
does.

Google's definition is the one to hold in mind throughout, because it is broader
than most people's:

> **Collection:** "Transmitting data from your app off a user's device." This
> includes data transmitted by libraries, SDKs, and webviews under app control.

Off the device is the test. Not "sent to us", not "kept", not "personal" —
**off the device**. Under it, a crash report is collection, a session ping is
collection, and an SDK the app never calls directly but ships is still the app's
responsibility.

---

## 1. The gap this repository can see

The only record here of what was declared is
[release-checklist.md](release-checklist.md) §6 step 3:

> data safety form (accounts: email; user content: shifts, receipts; encrypted in
> transit; deletable in-app **and via a web deletion URL** …)

Nothing in that list is diagnostics, crash logs or an identifier. Meanwhile the
phone app has shipped a **Sentry** client since before the first release, and the
watch app since `104394e` (8 September 2026). A crash report carries a stack trace,
the device model and OS, the app version, a breadcrumb trail, and — this is the
part that reads as a tracking identifier to an automated scan — **a persistent
per-install id**, which Sentry writes to `getFilesDir()/INSTALLATION` and attaches
as the event's user id.

Sessions make it continuous rather than occasional: with
`isEnableAutoSessionTracking = true` the SDK sends an envelope on every foreground
and background, crash or no crash. An app that phones a third-party host on every
launch, from a listing whose form says only "email, shifts, receipts", is exactly
the shape of this rejection.

One thing to confirm rather than assume, because it decides whether Sentry is the
cause or merely a gap: **does the uploaded build carry a DSN?** Nothing is
transmitted without one — `CrashReporting.isAvailable()` is false and the SDK never
starts — and the DSN comes from `sentry.dsn` in `local.properties`, which is not in
this repository. Check the machine that built the release, or open the Sentry
project and look for events from `com.elmtrackr.app@1.3.1+52`. The September watch
work was done so that the next rejection would arrive with a stack trace, which
presumes release builds do carry one. If it turns out none ever has, Sentry is not
what Play saw, [§2.1](#21-supabase--the-apps-own-backend) and
[§2.3](#23-google-play-services) are where to look instead — and the declaration in
§3 is still the one to file, because a DSN is one line of `local.properties` away
from being true.

**First thing to do — compare against the live form.** This repository cannot see
Play Console, so confirm which of these are ticked today under
*App content → Data safety*, and treat any unticked row as a cause:

| Data type | Should be ticked | Why |
|---|---|---|
| App info and performance → **Crash logs** | Yes | Sentry, both artifacts |
| App info and performance → **Diagnostics** | Yes | Sentry sessions, breadcrumbs, device/app context |
| **Device or other IDs** | Yes | Sentry's per-install id |
| Personal info → **Email address** | Yes | Supabase account |
| Personal info → **User IDs** | Yes | Supabase user id on every synced row |
| Personal info → **Name** | Yes | optional display name in `profiles` |
| Financial info → **Other financial info** | Yes | pay rates and pay estimates sync |
| Photos and videos → **Photos** | Yes | receipt images in Supabase Storage |
| App activity → **Other user-generated content** | Yes | shifts, notes, jobs, projects, leave |

The three at the top are the ones the wording of the rejection points at. Two
others are easy to miss on a second pass, and both are worth stating with Google's
own definitions next to them:

- **Other financial info** — *"Any other financial information. For example, a
  user's salary or debts."* An hourly rate, a premium multiplier and a pay estimate
  are salary data, and they sync.
- **Device or other IDs** — the examples Google gives are *"an IMEI number, MAC
  address, Widevine Device ID, Firebase installation ID, or advertising
  identifier."* Sentry's per-install id is the same kind of thing as a Firebase
  installation id, and is declared on that basis.

---

## 2. Everything that leaves the device

Four destinations, and one list of things that only look like a fifth.

### 2.1 Supabase — the app's own backend

`https://<project>.supabase.co`, configured at build time from `local.properties`
(`android/app/build.gradle.kts`). PostgREST, Auth and Storage; no other Supabase
products are installed (`data/auth/SupabaseClientProvider.kt`).

| What | Where it comes from |
|---|---|
| Email address, password or Google ID token, display name | sign-up and sign-in; `profiles` |
| Supabase user id (uuid) | every synced row |
| Shift times, breaks, notes, overtime and weekend rules | `shifts`, `user_settings` |
| Pay rules and pay estimates — hourly rates, premiums, payroll settings | `compensation_profiles`, `premium_profiles`, `workplaces` |
| Jobs, projects, tasks, billing records and payments | `projects`, `project_billing_records`, `project_payments`, `tasks` |
| Leave: absence events (sick / vacation), allocations, balances | `absence_events`, `absence_allocations`, `leave_*` |
| Travel refund claims and **receipt photographs** | `refund_claims`, Storage bucket |

The table list is `docs/supabase-contract.md`. Transport is HTTPS. Row-level
security scopes every row to its owner, and account deletion removes all of it
(*Settings → Account → Delete account*).

**Sharing?** No, in Play's sense. Supabase hosts data on the developer's
instructions, which is Google's "service provider" exception to *sharing*. It is
still **collection**, and is declared as such.

### 2.2 Sentry — crash reporting

`sentry.io`, DSN compiled in from `local.properties`. Sentry Java/Android
**8.50.1** in both modules: `:wear` declares `sentry-android-core` by hand, and
`:app` gets the SDK from the Sentry Gradle plugin's auto-installation, which
resolves from what it finds on the classpath rather than from anything written in
`build.gradle.kts`. Measured on `:app`'s `releaseRuntimeClasspath` (§6 has the
command), it resolves to:

```
io.sentry:sentry-android:8.50.1          → core + ndk (+ sentry-native-ndk 0.15.4) + replay
io.sentry:sentry-android-fragment:8.50.1
io.sentry:sentry-android-navigation:8.50.1
io.sentry:sentry-android-sqlite:8.50.1
io.sentry:sentry-compose-android:8.50.1
io.sentry:sentry-kotlin-extensions:8.50.1
io.sentry:sentry-okhttp:8.50.1
```

The watch carries `sentry-android-core` and nothing else — deliberately, and
`wear/build.gradle.kts` records the 3.1 MB reason.

What travels, per event:

| What | Note |
|---|---|
| Stack trace, exception type and message | scrubbed — `SensitiveTextScrubber` |
| Breadcrumb trail | screen and lifecycle transitions, plus this app's own update/review funnel counters (`UpdateDiagnostics`, `ReviewDiagnostics`) |
| App version, build, release, environment | `com.elmtrackr.app@1.3.1+52` / `com.elmtrackr.wear@…` |
| Device model, manufacturer, OS version, locale, memory, storage, orientation, battery | the SDK's device context |
| **A per-install identifier** | `io.sentry.android.core.Installation` — a uuid in `getFilesDir()/INSTALLATION`, sent as the event's user id. Survives restarts and updates; cleared by uninstall or "clear data" |
| Session start/end | one envelope per foreground and background |

What does **not** travel, and why it is worth writing down: `isSendDefaultPii` is
off, so no email, username, device name, file paths or IP field; no screenshots,
no view hierarchy, no session replay ([§5](#5-what-changed-in-code-with-this-document)
pins all three off explicitly); `tracesSampleRate` is `0.0`, so no performance
spans and no SQL; and nothing in this app ever calls `Sentry.setUser`.

**A caveat that needs a live check.** Sentry's own documentation says the full
request URL and query string of outgoing HTTP calls are *always* sent when its HTTP
integration is active — not gated on `isSendDefaultPii`. `sentry-okhttp` **is** on
the phone's classpath, as the list above shows, because okhttp arrives under Ktor.
What is not established is whether it is wired in: the interceptor is added by
bytecode instrumentation of code that *builds* an `OkHttpClient`, this app never
builds one, Ktor's engine builds it inside a dependency, and the plugin's
`forceInstrumentDependencies` is off by default. So Supabase URLs — which carry
`user_id=eq.<uuid>` and the filters of every query — are *believed* not to reach
Sentry today.

That is a build-configuration argument, not a measurement, and it is one dependency
bump away from changing. Treat it as a standing risk, verify it the way §6 says, and
note that [§5](#5-what-changed-in-code-with-this-document) makes the answer
irrelevant: URLs lose their query string, and headers, cookies and bodies are
dropped, before any event leaves the device.

**Sharing?** No, on the same service-provider basis as Supabase — **provided a data
processing agreement with Sentry is actually in place**. See §7; if it is not, the
honest answer flips to "shared", and that changes the form.

### 2.3 Google Play services

Five Google libraries transmit on the app's behalf. Google Play services' own
collection is Google's to declare, but the integration is the app's to describe:

| Library | What it does | Declarable |
|---|---|---|
| Play Billing (`billing`, `billing-ktx`) | clock-face pack purchases, acknowledgement, ownership query | No — the payment service collects transaction data directly from the user, and this app never sends purchase data to its own backend (ownership lives in DataStore; nothing writes a purchase token to Supabase). See §7 if server-side receipt validation is ever added |
| Credential Manager + Google ID (`googleid`) | Google sign-in; the ID token goes to Supabase, which verifies it | Covered by the Personal info rows |
| In-app update (`app-update`) | asks Play whether a newer version exists | No user data |
| In-app review (`review`) | hands the review flow to Play | No user data |
| Wearable Data Layer (`play-services-wearable`) | the phone pushes `WearShiftSnapshot` to the paired watch and the watch sends punch actions back | See below |

The snapshot carries current-shift state — active flag, shift id, start time,
today's minutes, daily goal, and the crash-reporting consent flag — between two
installs of the same app, on the same person's own paired devices, over a Google
transport the app does not configure. That is not collection by the developer and
not sharing with a third party, and it is not declared. It is written down here so
the next reader does not have to wonder whether it was considered.

### 2.4 On device, and staying there

Not collection, per Google's own exception — *"User data accessed by your app that
is only processed locally on the user's device and not sent off device does not
need to be disclosed"*:

- **Camera** (`CameraX`) — receipt capture. The image goes to app-private storage;
  only an attached receipt is uploaded, and only to Supabase.
- **ML Kit document scanner** (`play-services-mlkit-document-scanner`) — Google
  documents the whole scanner flow as on-device, with the UI and models delivered
  through Google Play services.
- **ML Kit text recognition** and **Tesseract4Android** — receipt OCR, both
  on-device. Tesseract's Hebrew data ships in the APK.
- **Room + SQLCipher**, **DataStore**, `EncryptedSharedPreferences`, biometric
  unlock — local storage only.
- **CSV / PDF export** — written to app storage and handed to a share sheet the
  user drives. User-initiated, and not the app transmitting.

---

## 3. The declaration to enter in Play Console

*App content → Data safety → Manage → Data types.* Every row below is "Collected:
Yes, Shared: No, Processed ephemerally: No" unless stated.

| Category → Data type | Collected | Required or optional | Purposes | Evidence |
|---|---|---|---|---|
| Personal info → **Name** | Yes | Optional — display name may be left blank | App functionality; Account management | `profiles` |
| Personal info → **Email address** | Yes | **Required** — an account is required to use the app | App functionality; Account management | Supabase Auth |
| Personal info → **User IDs** | Yes | Required | App functionality; Account management | Supabase user id on every row |
| Financial info → **Other financial info** | Yes | Required | App functionality | pay rules, rates and pay estimates in `compensation_profiles`, `premium_profiles`, `workplaces`, `user_settings` |
| Photos and videos → **Photos** | Yes | Optional — only if a receipt is attached | App functionality | Storage receipts bucket |
| App activity → **Other user-generated content** | Yes | Required | App functionality | shifts, notes, jobs, projects, tasks, leave records |
| App info and performance → **Crash logs** | Yes | **Optional** — *Settings → Help & About → Share crash reports*, on by default, off stops it | Analytics; Fraud prevention, security and compliance ¹ | Sentry, both artifacts |
| App info and performance → **Diagnostics** | Yes | Optional — same toggle | Analytics | Sentry sessions, breadcrumbs, device and app context |
| Device or other IDs | Yes | Optional — same toggle | Analytics | Sentry per-install id |

¹ Tick *Fraud prevention, security and compliance* only if crash data genuinely
feeds a security or abuse process. If it is only used to fix bugs, **Analytics**
alone is the accurate answer, and one purpose that is true beats two that are
half-true.

**Not collected**, and each for a reason that survives being asked about:

| Not declared | Why |
|---|---|
| Location (approximate or precise) | no location permission in either manifest; nothing reads location. See §7 for the one thing to check in Sentry |
| Financial info → User payment info, Purchase history | Play Billing collects transaction data directly from the user; the app never transmits purchase data to its own backend |
| Messages, Contacts, Calendar, Audio, Web browsing | no permission, no API, no code |
| Files and docs | exports are local and user-shared; receipts are declared as Photos |
| Health and fitness | see §7 — a judgment call, made deliberately |
| Installed apps | the `<queries>` entry resolves a Custom Tabs browser for sign-in; nothing is enumerated or transmitted |

### The rest of the form

| Question | Answer | Basis |
|---|---|---|
| Is all data encrypted in transit? | **Yes** | HTTPS to Supabase and to Sentry; the Wear transport is Google Play services' own |
| Can users request data deletion? | **Yes** | in-app *Settings → Account → Delete account*, plus the web deletion URL Play requires for apps with accounts — confirm that URL still resolves before submitting |
| Committed to Play Families Policy? | No | not a children's app |
| Independent security review | No | none has been done; do not claim one |

---

## 4. Statements outside the form that must match it

A form that disagrees with the privacy policy is the same finding in a different
place. Two texts are wrong today.

### 4.1 The privacy policy — `https://elmtrackr.site/privacy`

The published policy (mirrored in this repository at `lib/legal/content.ts`) says:

> We do not sell your personal data. **We do not use third-party advertising or
> analytics SDKs.** Data is shared only with infrastructure providers needed to run
> the service (e.g. Supabase as our database host).

The middle sentence has not been true since Sentry shipped. `lib/legal/content.ts`
has been corrected to the text below; **publish the same text on the site**, which
is the copy Play reads. The repository copy is not deployed, so editing it alone
changes nothing Google can see.

Replace the *Sharing* section with:

> We do not sell your personal data, and we use no advertising SDKs and no
> advertising or marketing trackers. Data reaches two processors, both acting on our
> instructions: **Supabase**, which hosts the database and receipt storage, and
> **Sentry**, which receives crash reports and app-health diagnostics when crash
> reporting is left on. You can turn crash reporting off at any time in
> Settings → Help & About → Share crash reports.

…and add a section after *Data we collect*:

> **Crash reports and diagnostics.** When crash reporting is on — it is on by
> default and you can turn it off in Settings → Help & About → Share crash reports —
> a failure sends a technical report to Sentry, our crash-reporting processor. It
> contains the error and its stack trace, your device model and Android version, the
> app version, a short trail of the screens and actions leading up to the failure,
> and a random identifier for the installation, which lets us tell one device's
> crashes from another's. It does not contain your name, your email address, your
> shifts, your pay or your receipts, and identifying values are stripped from error
> text before the report is sent. The Wear OS app does the same, following the
> setting you choose on the phone.

`PRIVACY_POLICY_LAST_UPDATED` is already moved to 9 September 2026; make the site
say the same date on the day it is republished. Leave
`LegalDocuments.LAST_UPDATED` alone — despite the name it dates the *terms of
service* screen, and the terms have not changed.

### 4.2 The settings toggle

`settings_crash_reports_desc` said "anonymous". A report carrying a per-install
identifier is pseudonymous, not anonymous, and the difference is precisely what the
Device-or-other-IDs row of the form is about. The string now says what is sent
instead of asserting a property of it — see §5.

---

## 5. What changed in code with this document

Narrowing what can leave is what keeps the declaration in §3 short enough to be
true, and short enough to stay true.

1. **`CrashReportScrubber` (`:wear-sync`)** — event scrubbing moved out of the two
   app modules, which each scrubbed only the event message, the exception values and
   breadcrumb *messages*. It now also covers **breadcrumb data** (where an HTTP
   integration puts request URLs and query strings, and where this app's own
   diagnostics write), the **request context** (url, query string, headers, cookies,
   body), the **user's email, username and IP**, and **extras**. URLs keep scheme,
   host and path — the endpoint is the diagnostic — and lose the query.
   Stack frames are untouched: they carry no user data and are the reason the report
   is worth anything.
2. **Scrubbing at record time as well as send time** — `beforeBreadcrumb` alongside
   `beforeSend`, because an NDK crash is written to the outbox by the native handler
   and uploaded on the next launch without passing back through `beforeSend`.
3. **Screen contents pinned off in both modules** — `isAttachScreenshot`,
   `isAttachViewHierarchy`, and both session-replay sample rates. All are off by
   default in 8.50.1; they are now written down, because a default is a decision
   someone else gets to change, and any of the three would change what §3 has to say
   without a line of this app's code moving.
4. **The consent string** now describes what is sent rather than calling it
   anonymous (four locales).

One consequence worth knowing before CI reports it: the Paparazzi golden
`19-settings-help` renders the row whose description changed, so it will differ.
That check is advisory in CI and its goldens are already host-specific — see the
comment on the step in `.github/workflows/android.yml` — so it was **not**
re-recorded here. Re-record it on the machine whose goldens are committed, with the
rest of the set, or not at all.

Untouched on purpose: `isEnableAutoSessionTracking` stays on. It is what
crash-free-rate is computed from, it is the reason Device-or-other-IDs is declared,
and turning it off is a product decision rather than a compliance one — see §7.

---

## 6. Re-verify before every release

The form has to be re-checked whenever a dependency changes, not only when a
feature does. Four checks, none of which takes long:

1. **What SDKs actually ship.** The phone's Sentry artifacts are auto-installed by
   the Gradle plugin from what it finds on the classpath, so the list is not visible
   by reading `build.gradle.kts`:

   ```bash
   ./gradlew :app:dependencies --configuration releaseRuntimeClasspath > /tmp/deps.txt
   grep -E "io\.sentry|com\.google\.(android|mlkit)|supabase" /tmp/deps.txt | sort -u
   ```

   Diff that against the last release. A new `io.sentry:*` line, or any new SDK, is
   a form question until proven otherwise.
2. **What a new SDK collects.** Check the
   [Google Play SDK Index](https://play.google.com/sdks) entry — the rejection
   letter points at it — and the SDK's own data-collection page. Record the answer
   here rather than in a pull request comment.
3. **What actually goes over the wire.** The only real evidence. Run a release
   build through a proxy on a test device, exercise sign-in, a punch, a receipt
   upload and a forced crash, and read the hosts and payloads. Anything that is not
   `*.supabase.co`, `*.sentry.io` or a Google endpoint is a finding.
4. **What Sentry actually stores.** Open the newest event in the Sentry project and
   read the USER, CONTEXTS and BREADCRUMBS panels with §2.2 next to you. This is
   also the check for the IP question in §7.

---

## 7. Open items — decisions the form depends on

These need an owner's answer, not a developer's guess.

1. **A data processing agreement with Sentry.** The whole "Shared: No" column rests
   on Supabase and Sentry being processors acting on instructions. Supabase and
   Sentry both offer a DPA; confirm both are signed and filed. Without one for
   Sentry, its rows become *Shared: Yes*, which also drags in the third-party
   disclosure in the privacy policy.
2. **Does Sentry record an IP or a country?** With `isSendDefaultPii = false` the
   SDK does not attach the device IP, and a Sentry event should therefore carry no
   IP and no geo. This was not measured. Open a recent event: if the USER panel
   shows an IP address or a country, **Approximate location** has to be declared —
   or stopped at source: Sentry's project security and privacy settings include an
   option to stop storing IP addresses.
3. **Sick leave and Health info.** `absence_events.type` distinguishes `sick` from
   `vacation`, and it syncs. The reading taken here is that a sick day recorded for
   pay and entitlement purposes is an employment record rather than health
   information — the contract in `supabase-contract.md` explicitly says the notes
   field is "never medical detail" — so **Health info is not declared**. That is a
   judgment call and it is worth an owner's sign-off, because the field is free text
   and a user can type a diagnosis into it. If it is ever surfaced as anything more
   clinical than "sick", revisit both the declaration and Play's separate health
   policies.
4. **Whether session tracking stays on.** It is the reason Device-or-other-IDs is
   declared. Turning off `isEnableAutoSessionTracking` in both modules would stop the
   per-launch envelope but not the id on crash events, so it narrows the traffic
   without removing the row. Release-health metrics are the cost. Product's call.
5. **Server-side receipt validation.** If purchases are ever verified on a backend,
   the purchase token leaves the device to a non-Google destination and
   **Purchase history** becomes declarable.
6. **What deletion reaches.** *Delete account* removes everything in Supabase. It
   does not reach Sentry, which keeps events for its project retention period.
   Nothing in a Sentry event is linked to the account — the identifier there is a
   per-install uuid the backend has never seen — so the position is defensible, but
   it is better written down now than improvised when a data-subject request
   arrives. Sentry's retention setting is where the exposure is bounded.

---

## Sources

Everything above that is not from this repository comes from one of these:

- Google, *Provide information for Google Play's Data safety section* (Play Console
  Help) — the form's structure, the collection and sharing definitions, the
  service-provider and on-device-processing exceptions, and the payment-service
  exception.
- Google, *Declare your app's data use* (Android Developers) — the per-data-type
  definitions quoted in §1 and §3.
- Sentry, *Data Collected* (Android platform docs) — what the SDK sends by default,
  including the statement that the full request URL and query string of HTTP calls
  are always sent when its HTTP integration is active.
- Google, *Document Scanner* (ML Kit docs) — the on-device processing statement in
  §2.4.
- The published artifacts themselves: `io.sentry:sentry:8.50.1`,
  `io.sentry:sentry-android-core:8.50.1` and
  `io.sentry:sentry-android-gradle-plugin:6.16.0`, read directly for the
  installation-id behaviour, the option defaults and the auto-installation set.

## What this document could not check

No access to Play Console from here: the *live* form contents, the App access
credentials, and whether the web deletion URL still resolves are all unverified.
The Sentry project's own settings — IP storage, data scrubbing, retention — were
likewise not read. Everything else above is from this repository, the SDK artifacts
themselves, and the vendors' published documentation.
