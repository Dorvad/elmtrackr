# Sign in with Google — configuration

The client half is built and tested. It cannot work until the three things below
exist, and none of them can be created from inside this repository: they live in
the Google Cloud console, the Supabase dashboard, and `local.properties`.

Until `google.web.client.id` is set the button does not appear at all. That is
deliberate — a sign-in button that cannot work is worse than no button, because
a user has no way to tell that the failure is ours.

---

## 1. Google Cloud — two OAuth client IDs

Both are required, and they do different jobs. This is the step most often got
wrong: creating only the Android client produces a button that opens, shows the
account picker, and then fails on the exchange.

**a. Web application client**
Google Cloud console → *APIs & Services* → *Credentials* → *Create credentials*
→ *OAuth client ID* → *Web application*.

This client's ID is the **audience** of every ID token the app receives, and the
value Supabase checks. It is what goes into `local.properties` below. It is not
a secret: it ships inside the APK either way. Its client *secret* is a secret and
belongs only in the Supabase dashboard.

**b. Android client**
Same screen → *Android*.

| Field | Value |
| --- | --- |
| Package name | `com.elmlaunch.myapp` (the `applicationId`, **not** the namespace `com.elmtrackr.app`) |
| SHA-1 | the signing certificate fingerprint — see below |

Which SHA-1 depends on how the build reaches the device, and a build signed with
a key Google has not seen fails with "Developer console is not set up correctly":

- **Debug builds on a developer machine** — the debug keystore's SHA-1.
  `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
- **Internal testing and production through Play** — the **Play App Signing**
  certificate, from Play Console → *Release* → *Setup* → *App signing*. Not the
  upload key. Uploading to Play re-signs the artifact, so the upload key's
  fingerprint is not what the device carries.

Register every fingerprint that will be used, each as its own Android client.

## 2. Supabase — enable the Google provider

Dashboard → *Authentication* → *Providers* → *Google*.

| Field | Value |
| --- | --- |
| Enabled | on |
| Client ID | the **Web** client ID from 1a |
| Client secret | the Web client's secret |
| Authorized Client IDs | the **Web** client ID from 1a |

*Authorized Client IDs* is the field that matters for the native flow: it is the
list of audiences Supabase will accept an ID token for. The token the app
receives is issued to the Web client, so that is the value to add here.

Leave *Skip nonce checks* **off**. The app sends a per-attempt nonce and relies
on it being verified; turning the check off removes a replay defence for no gain.

## 3. This repository — `android/local.properties`

```properties
google.web.client.id=<the Web client ID from 1a>.apps.googleusercontent.com
```

Untracked, like the Supabase keys beside it. CI and release builds need the same
line written into `local.properties` before the build runs, or they produce an
app with no Google button.

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
Google copies the hash into the token's `nonce` claim; Supabase hashes what it
was given and compares. Sending the same string to both fails every time, which
is why `SignInNonce` produces both forms and `SignInNonceTest` pins the encoding
against published vectors.

**One button, both directions.** Supabase creates the account on first use, so
signing up and signing in are the same call. The label follows the form the user
is looking at — "Sign up with Google" on the create-account form, "Sign in with
Google" otherwise. Both phrasings are on Google's approved list.

**The browser is the fallback, not the primary.** When the device has no Google
account, or no Credential Manager provider at all, the app falls back to the
hosted OAuth flow, which returns through `elmtrackr://auth/callback` and is
exchanged by the existing deep-link handler under PKCE.

A **misconfigured client id is deliberately not** routed to that fallback. The
fallback would often work and would hide the misconfiguration for as long as it
kept working; instead the failure is shown and reported to Sentry.

## Worth confirming on the first test round

**An email account and a Google account with the same address.** Whether Supabase
treats these as one user or two depends on the project's identity-linking
settings, not on anything in this app. Sign up with email, sign out, then sign in
with Google using the same address, and check whether the shifts are still there.
If they are not, the setting to look at is identity linking in the Supabase
dashboard — decide it before real users create accounts, because it is far harder
to reconcile afterwards.

**Signing out actually forgets the account.** `signOut()` clears the Credential
Manager state, so the picker should reappear rather than silently returning the
same account. Worth confirming on a device with two Google accounts.

## Known limitation

The callback for the browser fallback is still the custom scheme
`elmtrackr://auth/*`, which any installed app may register. PKCE is already on,
so an intercepted callback carries a single-use code that is worthless without
the verifier — but the fix for the scheme itself is a verified HTTPS App Link,
which needs a hosted `.well-known/assetlinks.json` carrying the release signing
fingerprint. Tracked in `declined-findings.md`.

The native Credential Manager path does not touch a browser or a redirect URL at
all, so it is unaffected by this.
