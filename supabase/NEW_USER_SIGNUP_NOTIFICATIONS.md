# New user signup notifications (Supabase → Slack / Discord)

Get a chat message every time someone creates an ElmTrackr account.

ElmTrackr already creates a `public.profiles` row on signup via the `on_auth_user_created` trigger in `schema.sql`. This guide hooks **that** insert — no app changes required.

> **Timing:** This fires when the account is **created** (`signUp()`), not when email is confirmed. If you only want verified users, see [Confirmed users only](#optional-confirmed-users-only) at the end.

---

## Before you start

- [ ] Supabase project owner or admin access
- [ ] A private Slack channel **or** Discord channel you control
- [ ] ~10 minutes

**Why not paste a Slack/Discord webhook URL directly into Supabase?**

Supabase Database Webhooks POST a fixed JSON payload (`type`, `table`, `record`, …). Slack and Discord expect their own JSON shape (`text` / `content`). A direct URL usually returns `invalid_payload` and you see nothing in chat.

Use one of the no-code adapters below, then point Supabase at **that** URL.

---

## Option A — Slack (recommended, no code)

Uses **Slack Workflow Builder** as a tiny adapter: receives Supabase JSON → posts a formatted message.

### Step 1 — Create the Slack workflow

1. Open Slack (desktop or web).
2. Click your workspace name → **Tools** → **Workflow Builder**  
   (Or go to `https://slack.com/launch/workflows` and pick your workspace.)
3. Click **New Workflow**.
4. Name it e.g. `ElmTrackr new signups`.
5. **Choose how to start this workflow** → **Webhook**.
6. Slack shows a **Webhook URL** — copy it somewhere safe (you’ll paste it into Supabase in Step 3).
7. Under **Variables**, Slack may ask you to add sample data. Paste this test payload (matches what Supabase sends):

   ```json
   {
     "type": "INSERT",
     "table": "profiles",
     "schema": "public",
     "record": {
       "id": "00000000-0000-0000-0000-000000000001",
       "email": "test@example.com",
       "full_name": "Test User",
       "created_at": "2026-06-25T12:00:00+00:00",
       "updated_at": "2026-06-25T12:00:00+00:00"
     },
     "old_record": null
   }
   ```

8. Click **Next**.

### Step 2 — Add the message step

1. **Add step** → **Send a message** → pick a **private** channel (e.g. `#elmtrackr-signups` — create it first if needed).
2. Message body — use Workflow variables from the webhook (wording varies slightly by Slack version; look for **Insert record** / **record** fields):

   ```
   🆕 New ElmTrackr signup
   Email: {{record.email}}
   Name: {{record.full_name}}
   User ID: {{record.id}}
   ```

   If `full_name` is empty, that’s normal — users often haven’t set a display name yet.

3. **Publish** the workflow.

### Step 3 — Create the Supabase Database Webhook

1. [Supabase Dashboard](https://supabase.com/dashboard) → your ElmTrackr project.
2. **Database** → **Webhooks** (under Integrations, or **Database** → **Webhooks** depending on UI).
3. **Create a new webhook** / **Enable Webhooks** if prompted.
4. Fill in:

   | Field | Value |
   |---|---|
   | **Name** | `notify-new-profile` |
   | **Table** | `profiles` |
   | **Schema** | `public` |
   | **Events** | ✅ **Insert** only (leave Update/Delete unchecked) |
   | **Method** | `POST` |
   | **URL** | The Slack Workflow webhook URL from Step 1 |
   | **HTTP headers** | Leave default (`Content-Type: application/json`) |

5. **Save** / **Create webhook**.

### Step 4 — Test

1. Sign up a **new** test account in the web app or Android app (use an email you haven’t used before).
2. Within a few seconds, check `#elmtrackr-signups` in Slack.

**If nothing appears:**

- Supabase → **Database** → **Webhooks** → open your hook → check delivery logs / errors.
- Slack → Workflow Builder → open the workflow → **Activity** tab for failed runs.
- Confirm the webhook listens on **`profiles` INSERT**, not `auth.users` (you can’t webhook `auth.users` from the dashboard easily; `profiles` is the right table for ElmTrackr).

---

## Option B — Discord (no code, via Make.com)

Discord incoming webhooks also need a `content` field. [Make.com](https://www.make.com) (free tier) is a simple adapter.

### Step 1 — Discord webhook

1. Discord → your server → channel (private) → **Edit Channel** → **Integrations** → **Webhooks**.
2. **New Webhook** → name it `ElmTrackr signups` → **Copy Webhook URL**.

### Step 2 — Make.com scenario

1. Sign in at [make.com](https://www.make.com) → **Create a new scenario**.
2. **Trigger:** search **Webhooks** → **Custom webhook** → **Add**.
3. Click the webhook module → **Create a webhook** → copy the **address** URL (this goes into Supabase).
4. Click **Run once** — Make waits for a sample request.
5. In another tab, send a test POST (or complete Step 4 below first and sign up a test user).

   Optional manual test with curl:

   ```bash
   curl -X POST "YOUR_MAKE_WEBHOOK_URL" \
     -H "Content-Type: application/json" \
     -d '{"type":"INSERT","table":"profiles","schema":"public","record":{"id":"00000000-0000-0000-0000-000000000001","email":"test@example.com","full_name":"Test","created_at":"2026-06-25T12:00:00Z","updated_at":"2026-06-25T12:00:00Z"},"old_record":null}'
   ```

6. Make should capture the request. Click **OK**.
7. **Add module** → **Discord** → **Create a Message** (or **Send a Webhook Message**).
8. Connect your Discord account / paste the webhook URL from Step 1.
9. **Message content** (map from the webhook module):

   ```
   🆕 New ElmTrackr signup
   Email: {{record.email}}
   Name: {{record.full_name}}
   ID: {{record.id}}
   ```

   Use Make’s field picker on the Custom webhook module → `record` → `email`, etc.

10. **Save** the scenario → turn scheduling **ON**.

### Step 3 — Supabase Database Webhook

Same as Slack **Step 3**, but paste the **Make.com custom webhook URL** instead of Slack’s.

| Field | Value |
|---|---|
| **Name** | `notify-new-profile` |
| **Table** | `profiles` |
| **Events** | Insert |
| **URL** | Make.com custom webhook URL |

### Step 4 — Test

Sign up with a fresh test email → message should land in Discord.

---

## What Supabase sends (reference)

Every `profiles` insert triggers a POST like:

```json
{
  "type": "INSERT",
  "table": "profiles",
  "schema": "public",
  "record": {
    "id": "uuid",
    "email": "user@example.com",
    "full_name": null,
    "created_at": "2026-06-25T12:00:00+00:00",
    "updated_at": "2026-06-25T12:00:00+00:00"
  },
  "old_record": null
}
```

Your adapter only needs `record.email`, `record.full_name`, and `record.id`.

---

## Security & privacy

- Use a **private** channel — messages contain user emails (PII).
- Don’t commit webhook URLs to git; they’re secrets (anyone with the URL can post to your channel).
- If a URL leaks: rotate it in Slack/Discord/Make and update the Supabase webhook URL.
- Optional: in Supabase webhook settings, add a custom header (e.g. `X-Webhook-Secret`) and configure your adapter to reject requests without it.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| No message after signup | Webhook on wrong table | Must be `public.profiles`, event **Insert** |
| Slack `invalid_payload` | Direct Slack incoming URL in Supabase | Use Workflow Builder (Option A), not a raw incoming webhook |
| Message for every profile fix/migration | Re-ran seed/migration inserting profiles | Webhook fires on all inserts; filter in adapter or disable during migrations |
| Duplicate messages | Multiple webhooks on same table | Delete duplicate hooks in Supabase |
| Signup works but no profile row | `handle_new_user` trigger missing | Re-run `supabase/schema.sql` or check SQL Editor for errors |

**Verify the trigger exists** (Supabase → SQL Editor):

```sql
select tgname
from pg_trigger
where tgname = 'on_auth_user_created';
```

Should return one row.

---

## Optional: confirmed users only

Signup notifications on `profiles` INSERT include users who haven’t confirmed email yet.

To notify only after confirmation you’ll need a different hook (e.g. Supabase Auth hook or Edge Function on `user.updated` when `email_confirmed_at` is set). That’s Option 2 from the architecture discussion — ask if you want that wired up in the repo.

---

## Quick checklist

- [ ] Private `#elmtrackr-signups` (or Discord equivalent) created
- [ ] Adapter URL ready (Slack Workflow or Make.com)
- [ ] Supabase webhook: `public.profiles` → **Insert** → adapter URL
- [ ] Test signup with a new email
- [ ] Message received with correct email and user id
