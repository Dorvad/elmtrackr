# Location-based clock-in reminders — viability assessment

**Status:** Assessment only, not implemented
**Scope:** "Set a workplace location; when I enter its radius, remind me to clock in."

## Summary recommendation

Viable and worth building, with conditions. The right implementation is the
platform Geofencing API (one workplace geofence, ~150–200 m radius), strictly
opt-in, processed fully on-device, with the reminder suppressed when a shift
is already running. It is the single biggest data-quality and retention lever
identified in the product strategy (item C3), and it is the reminder users
actually need: the current overtime reminders help people clock *out*, but the
most common tracking failure is forgetting to clock *in*. Ship it after the
configurable reminder schedule, not alongside it, and treat the permission
flow as the main design problem rather than the geofence itself.

## Technical approach

Android's recommended mechanism is the Play Services Geofencing API
(`GeofencingClient`). The OS monitors registered regions and wakes the app
with a broadcast on entry/exit — no continuous location polling by the app.

- **Battery:** effectively negligible for a small number of fences. The
  system piggybacks on location signals other apps and the OS already
  produce. This fits the app's offline-first, battery-respecting posture.
- **Reliability:** entry events are not instant; with Wi-Fi scanning on,
  detection latency is typically tens of seconds to a few minutes, and the
  practical minimum radius is on the order of 100–200 m. A reminder tolerates
  this well (unlike automatic punching, see below). Fences are dropped on
  reboot and on some OEM "battery optimization" kills, so they must be
  re-registered on boot (`BOOT_COMPLETED`) and on app start.
- **Verification note:** exact latency/radius behavior varies by device and
  should be validated on real hardware during a spike; the figures above are
  typical ranges, not guarantees.

## Permissions and Play Store policy — the real cost

Geofencing while the app is closed requires `ACCESS_FINE_LOCATION` plus
`ACCESS_BACKGROUND_LOCATION`. Two consequences:

1. **UX friction.** On Android 11+, background location cannot be granted in
   a single dialog: the user must first grant foreground location, then be
   sent to system settings to select "Allow all the time." Expect meaningful
   drop-off; the feature must degrade gracefully when only foreground
   permission is granted (i.e., stay off with a clear explanation).
2. **Play review.** Background location triggers Play's location-policy
   review: a declaration form, an in-app prominent disclosure shown *before*
   the runtime prompt, and a demonstration video. This is process overhead,
   not a blocker — a clock-in reminder at a user-chosen workplace is exactly
   the kind of core, user-visible benefit the policy is designed to allow.
   Budget the extra release-cycle time for the first submission with it.

## Privacy posture

This can and should be built with zero location data leaving the device:

- The workplace coordinate and radius are stored locally (encrypted store,
  consistent with the SQLCipher posture); nothing location-related syncs to
  Supabase.
- The app never records a location history — the OS calls in only at fence
  transitions, and the app keeps no trail.
- Worker-side trust is the product's stated differentiator. A monitoring-shy
  audience will accept "your phone reminds you at work" only if the marketing
  and settings copy state plainly that location never leaves the device.

## Product-fit assessment

- **Solves the right problem.** Forgotten clock-ins corrupt every downstream
  number (hours, overtime, pay). A nudge at arrival fixes the root cause in a
  way no amount of clock-out reminders can.
- **Reminder, not auto-punch.** Automatic clock-in on entry is tempting but
  wrong for v1: geofence latency and false entries (bus passing the office,
  living near work) would create wrong shifts silently. A notification with a
  one-tap "Clock in" action keeps the user in control and makes false
  triggers harmless. The action can reuse the existing headless clock-in path
  (widgets/Wear already use it).
- **Needed guardrails (v1):**
  - Suppress when a shift is already active.
  - Cooldown (e.g., don't re-fire within a few hours of dismissal or exit —
    lunch runs shouldn't re-nudge).
  - Optional day/time window ("weekdays 06:00–12:00") — the configurable
    reminder-schedule model just added is a natural place for this.
  - One saved place in v1; multi-place (multi-job) later.
- **Effort estimate:** roughly comparable to the reminder-schedule feature —
  a place-picker screen (map or address search adds a Maps dependency; a
  "use current location" + radius slider avoids it), a geofence registration
  service, boot/kill re-registration, the disclosure/permission flow, and the
  Play declaration. The permission and policy work is the long pole, not the
  code.

## Recommendation

Build it, as a reminder (not an auto-punch), on-device only, opt-in, one
place, after validating geofence latency on real devices in a short spike.
If the Play declaration overhead needs deferring, an interim step with most
of the value and none of the location cost is a schedule-based clock-in
reminder ("weekdays at 08:50 if not clocked in") built on the new reminder
rules — but it should be a stepping stone, not the destination.
