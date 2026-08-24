# Sign in with Google — setup, step by step

The client is built, tested and merged. It cannot work until three things exist,
and none of them can be created from inside this repository: two OAuth clients in
Google Cloud, the Google provider in Supabase, and one line in
`android/local.properties`.

Written for someone who has never done this before. Follow it in order — step 3
needs a value produced in step 1, and step 4 needs values produced in step 3.

Allow about 30 minutes for the first time.

> **A note on console screenshots and menu names.** Google renamed the OAuth
> settings to *Google Auth Platform* and the layout has moved more than once. The
> menu names below were checked against Google's own documentation, but if a label
> does not match what you see, look for the section that does the same job rather
> than assuming the step is gone.

---

## Before you start

| You need | Where it comes from |
| --- | --- |
| A Google account | Any account. It becomes the owner of the Cloud project. |
| Access to the ElmTrackr Supabase project | The same project whose URL and anon key are already in `local.properties`. |
| This repository checked out on your own machine | Step 1 reads a file that only exists there. |
| A JDK on your PATH | Ships with Android Studio. Step 1 uses `keytool` from it. |

Two names you will need repeatedly. They are **not** the same, and using the
wrong one is the single most common failure:

| | Value |
| --- | --- |
| **Application ID** (this is the one Google wants) | `com.elmlaunch.myapp` |
| Namespace (internal to the code — never enter this) | `com.elmtrackr.app` |

---

## Step 1 — Get the SHA-1 fingerprint of your signing key

Google will only accept a sign-in request from an app signed with a key it has
been told about. That key is identified by a **SHA-1 fingerprint**: a 40-character
string of hex digits separated by colons.

You need a different fingerprint depending on how the app reaches the phone, and
**you will end up registering more than one**. Do the debug one now; come back for
the Play one when you first upload to Play.

### 1a. The debug fingerprint — for builds you install from Android Studio

Every Android install has an automatically generated debug key at a fixed path,
with a fixed password. Run this in a terminal on your own machine.

**macOS / Linux**

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

**Windows (PowerShell)**

```powershell
keytool -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey `
  -storepass android `
  -keypass android
```

The output is long. Find the block that looks like this and copy the **SHA1**
line — not SHA-256, not MD5:

```
Certificate fingerprints:
         SHA1: A1:B2:C3:D4:E5:F6:...:99
         SHA256: ...
```

> `keytool: command not found`? It lives inside the JDK that Android Studio
> installs. On macOS that is usually
> `/Applications/Android\ Studio.app/Contents/jbr/Contents/Home/bin/keytool`; on
> Windows, `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`. Use the
> full path instead of `keytool`.
>
> `debug.keystore` does not exist? Build and run the app once from Android Studio.
> It is created on the first build.

### 1b. The Play App Signing fingerprint — for anything distributed through Play

**Do this when you first upload to Play, not now.** Play re-signs every upload
with a key Google holds, so the fingerprint the phone sees is not your upload
key's. Registering the upload key instead of this one produces an app that works
on your desk and fails for every tester.

Play Console → your app → **Test and release** → **Setup** → **App signing** →
copy the SHA-1 under **App signing key certificate**.

### 1c. The release keystore fingerprint — only if you sideload a signed APK

Only relevant if you hand someone an APK built with the release keystore directly
rather than through Play (see §1 of `release-checklist.md`):

```bash
keytool -list -v -keystore <path to your release .jks> -alias <your alias>
```

---

## Step 2 — Create the Google Cloud project and configure the consent screen

### 2a. Create the project

1. Go to <https://console.cloud.google.com>.
2. Click the project dropdown in the top bar → **New Project**.
3. Name it something you will recognise, e.g. `ElmTrackr`. Leave the organisation
   as-is. Click **Create**.
4. Wait for the notification, then make sure the project dropdown now shows it.
   Everything below happens inside this project — if the dropdown shows a
   different one, you will configure the wrong project and nothing will work.

### 2b. Configure the Google Auth Platform

This is the screen users see when they pick an account: your app's name and logo,
and who is allowed to sign in at all. Google will not let you create OAuth clients
until it is configured.

In the left menu, find **Google Auth Platform** (in some layouts it sits under
*APIs & Services* and is called *OAuth consent screen*). If it offers a **Get
started** flow, take it — it walks the same sections.

The sections are:

| Section | What to do |
| --- | --- |
| **Branding** | App name (what users see — use `ElmTrackr`), user support email, and your developer contact email. A logo is optional now. |
| **Audience** | Choose **External** unless the Google account is part of a Google Workspace organisation and only that organisation will ever sign in. External is almost certainly right. |
| **Clients** | Leave for step 3. |
| **Data Access** | Add the scopes `openid`, `email` and `profile` if they are not already listed. These three are non-sensitive: they do not trigger Google's app-verification review. Do not add anything else — every extra scope is one more thing to justify later. |

### 2c. Add yourself as a test user — read this one carefully

A new External project starts in **Testing** status. In Testing, **only Google
accounts explicitly listed as test users can sign in.** Anyone else gets an
"access blocked" error that looks like a bug in the app.

On the **Audience** page, under **Test users**, click **Add users** and add:

- your own Google account,
- every account that will take part in the first test round.

Testing status caps the number of test users. Google publishes the current limit
on that page — check it there rather than assuming, and if your test group is
larger than the cap you will need to publish the app first.

When you are ready for real users, come back to **Audience** and click **Publish
app** to move to *In production*. With only the three scopes above, that does not
require Google's verification review — but the app name only appears on the
consent screen after brand verification, so before that users see your project ID
instead. Fine for testing; worth fixing before launch.

---

## Step 3 — Create the two OAuth clients

You need **both**. This is where most setups go wrong: creating only the Android
client produces a button that opens, shows the account picker, and then fails at
the exchange with an unhelpful message.

Their jobs are different:

- The **Android** client tells Google "this package, signed with this key, is
  allowed to ask for tokens."
- The **Web** client is the *audience* written into every token that comes back.
  It is what Supabase checks. Its ID is the value the app compiles in.

Go to **Google Auth Platform** → **Clients** → **Create client**.

### 3a. The Web client — create this one first

| Field | Value |
| --- | --- |
| Application type | **Web application** |
| Name | `ElmTrackr Web` (internal label only — users never see it) |
| Authorised JavaScript origins | leave empty |
| Authorised redirect URIs | the Supabase callback URL — see below |

**The Supabase callback URL.** In the Supabase dashboard, go to
*Authentication* → *Providers* → *Google*. The page displays a callback URL for
your project, of the form:

```
https://<your-project-ref>.supabase.co/auth/v1/callback
```

Copy it from that page rather than typing it — the project ref is a long random
string and one wrong character fails silently.

This URI is only used by the browser fallback (devices with no Google account or
no Play Services). The native path never touches it. Add it anyway: without it,
those devices hit a Google error page instead of signing in.

Click **Create**. A dialog shows a **Client ID** and a **Client secret**.

**Copy both now and keep them somewhere you can get at in step 4.** The ID looks
like `123456789012-abc...xyz.apps.googleusercontent.com`. You can reopen the
client later to see the ID; the secret can be re-copied from the same page.

The **client ID** is not a secret — it ships inside the APK either way. The
**client secret** is one: it goes into the Supabase dashboard and nowhere else.
Never into `local.properties`, never into a commit.

### 3b. The Android client

**Create client** again.

| Field | Value |
| --- | --- |
| Application type | **Android** |
| Name | `ElmTrackr Android (debug)` — include which key it is for; you will have several |
| Package name | `com.elmlaunch.myapp` |
| SHA-1 certificate fingerprint | the fingerprint from step 1a |

Click **Create**. This client produces an ID too — **you do not need it.** It is
registered with Google, and that is its whole job. The app never references it.

**Repeat 3b for every fingerprint**, each as its own Android client with the same
package name: one for the debug key now, one for the Play App Signing key when you
upload, one for the release keystore if you ever sideload a signed build.

> Changes here can take a few minutes to take effect. If a fingerprint you just
> added still fails, wait five minutes before assuming it is wrong.

---

## Step 4 — Enable the Google provider in Supabase

Supabase dashboard → your project → **Authentication** → **Providers** →
**Google**.

| Field | Value |
| --- | --- |
| **Enable Sign in with Google** | on |
| **Client ID (for OAuth)** | the **Web** client ID from step 3a |
| **Client Secret (for OAuth)** | the **Web** client secret from step 3a |
| **Authorized Client IDs** | the **Web** client ID from step 3a, again |
| **Skip nonce check** | **leave off** |

Two of those deserve an explanation.

**Authorized Client IDs** is the list of audiences Supabase will accept an ID
token for, and it is the field the native flow actually checks. The token the app
receives is issued to the Web client, so the Web client ID goes here. If you ever
add another platform, this becomes a comma-separated list with the web ID first.

**Skip nonce check stays off.** Supabase's documentation tells iOS projects to
enable it; the Android section does not. This app sends a per-attempt nonce and
relies on it being verified. Turning the check off removes a replay defence and
buys nothing.

Click **Save**.

---

## Step 5 — Put the client ID into the build

Open `android/local.properties` — the same file that already holds `supabase.url`
and `supabase.anon.key`. It is untracked by git, which is why the value is not in
the repository.

Add one line:

```properties
google.web.client.id=123456789012-abc...xyz.apps.googleusercontent.com
```

Use the **Web** client ID from step 3a, complete with the
`.apps.googleusercontent.com` suffix.

**Every machine that builds the app needs this line**, including CI. A build
without it compiles and runs perfectly and simply has no Google button — which is
deliberate, but is also easy to mistake for a bug.

---

## Step 6 — Build and check it

```bash
cd android
./gradlew :app:assembleDebug
```

Install on a device or emulator that has Play Services and at least one Google
account added in the system settings, and check, in this order:

1. **The button is there.** Sign-in screen, above the email field, with the
   Google mark and an "or" divider below it. Not there → step 5 did not take
   effect; confirm the line is in `android/local.properties`, then rebuild.
2. **It opens the account picker.** Tap it. A system sheet listing the device's
   Google accounts should appear within a second.
3. **It signs you in.** Pick an account. The app should land on the dashboard.
4. **Switching accounts works.** Sign out from Settings, tap the button again.
   The picker must reappear rather than silently reusing the same account. Best
   checked on a device with two Google accounts.
5. **The sign-up label is right.** On the sign-in screen tap "Don't have an
   account? Sign up". The button should now read "Sign up with Google". It makes
   the same call either way; only the promise on the label changes.

---

## When it fails

Errors from Google are famously unhelpful. This maps the ones you are likely to
hit to their actual cause.

| What you see | What it actually means |
| --- | --- |
| No Google button at all | `google.web.client.id` missing from `local.properties`, or the build predates adding it. |
| "Developer console is not set up correctly", or error `[28444]` | The signing key is not registered. The most common case by far: the build was signed with a key whose SHA-1 has no Android client — a Play build against a debug fingerprint, or a teammate's machine with a different debug key. Register that fingerprint (step 3b). |
| Picker opens, then "Unable to continue with Google" | The token was rejected at the exchange. Check the **Authorized Client IDs** field in Supabase holds the **Web** client ID (step 4) — not the Android one, not empty. |
| Something about the nonce claim | The hashed and raw forms were swapped, or *Skip nonce check* was toggled while the app sends a nonce. The app's side is pinned by `SignInNonceTest`; check the Supabase toggle first. |
| "Access blocked: app has not completed verification" | The project is in **Testing** and this Google account is not on the test-user list (step 2c). |
| Browser opens instead of the in-app sheet | Expected on a device with no Google account or no Play Services. Sign-in still completes. |
| Browser opens and shows a Google `redirect_uri_mismatch` | The Supabase callback URL is missing from the Web client's **Authorized redirect URIs** (step 3a). |

The app reports the technical detail of any real failure to Sentry rather than
showing it, so if crash reporting is configured the exact exception is there.

---

## Worth confirming during the first test round

**An email account and a Google account with the same address.** Whether Supabase
treats these as one user or two depends on the project's identity-linking
settings, not on anything in this app. Sign up with email, sign out, then sign in
with Google using the same address, and check whether the shifts are still there.
Decide this before real users create accounts — it is far harder to reconcile
afterwards.

**Signing out actually forgets the account**, as in check 4 above.

---

## How the flow works, and why it is shaped this way

**Credential Manager, not the old Google Sign-In SDK.** The token is obtained
through `androidx.credentials` with `GetSignInWithGoogleOption`, which shows the
full account picker including "use another account". The One Tap style
`GetGoogleIdOption` filters to accounts already authorized for this app, which is
empty for every first-time user — the wrong shape for a button whose main job is
the first sign-up.

**The nonce travels in two forms.** The app generates a random 256-bit value,
gives Google the **SHA-256 hash** of it, and gives Supabase the **raw** value.
Google copies the hash into the token's `nonce` claim; Supabase hashes what it was
given and compares. Sending the same string to both fails every time, which is why
`SignInNonce` produces both forms and `SignInNonceTest` pins the encoding against
published vectors.

**One button, both directions.** Supabase creates the account on first use, so
signing up and signing in are the same call. The label follows the form the user
is looking at; both phrasings are on Google's approved list.

**The browser is the fallback, not the primary.** With no Google account on the
device, or no credential provider at all, the app falls back to the hosted OAuth
flow, which returns through `elmtrackr://auth/callback` and is exchanged by the
existing deep-link handler under PKCE.

A **misconfigured client id is deliberately not** routed to that fallback. The
fallback would often work and would hide the misconfiguration for as long as it
kept working; instead the failure is shown and reported.

## Known limitation

The callback for the browser fallback is still the custom scheme
`elmtrackr://auth/*`, which any installed app may register. PKCE is on, so an
intercepted callback carries a single-use code that is worthless without the
verifier — but the fix for the scheme itself is a verified HTTPS App Link, which
needs a hosted `.well-known/assetlinks.json` carrying the release signing
fingerprint. Tracked in `declined-findings.md`.

The native Credential Manager path never touches a browser or a redirect URL, so
it is unaffected.
