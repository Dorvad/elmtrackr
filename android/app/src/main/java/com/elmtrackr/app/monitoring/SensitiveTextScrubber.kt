package com.elmtrackr.app.monitoring

/**
 * Removes identifying values from text on its way into a crash report.
 *
 * Crash reports leave the device. Most of what this app throws is harmless, but three
 * sources routinely carry identifiers into an exception message:
 *
 * - **PostgREST/Postgres errors.** A constraint violation is reported with the offending
 *   row inlined — `Key (user_id, start_time)=(3f2b…, 2026-07-11 06:00:00+00) already
 *   exists` — so a duplicate-shift bug ships a user id and the exact minute they
 *   clocked in.
 * - **Auth failures**, which can quote a JWT or an email address.
 * - **URLs** built with an `apikey` or `access_token` query parameter.
 *
 * The scrubber replaces the *values* and keeps the shape, because the shape is the
 * diagnostic: `Key (…)=(…) already exists` still says which constraint failed.
 *
 * It runs over exception messages, the event message and breadcrumb messages. It
 * deliberately does **not** touch stack frames — class, method, file and line carry no
 * user data and are the whole value of the report.
 *
 * This is a second line of defence, not the first. `isSendDefaultPii` is off, no user
 * is attached to events, and nothing here logs record contents on purpose.
 */
object SensitiveTextScrubber {

    private const val REDACTED = "[redacted]"

    /**
     * Order matters: the Postgres detail rule runs first so it can blank a whole
     * key/value pair, and the narrower rules then catch anything it did not frame.
     */
    private val RULES: List<Pair<Regex, String>> = listOf(
        // Key (user_id, start_time)=(uuid, 2026-07-11 06:00:00+00)
        Regex("""Key\s*\(([^)]*)\)\s*=\s*\([^)]*\)""") to "Key ($1)=($REDACTED)",
        // Detail: Failing row contains (…)
        Regex("""(?i)(failing row contains)\s*\([^)]*\)""") to "$1 ($REDACTED)",
        // JWTs — three base64url segments. Checked before the generic token rule so a
        // bearer JWT is redacted as a whole.
        Regex("""eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]+""") to REDACTED,
        Regex("""(?i)\b(bearer)\s+[A-Za-z0-9._~+/=-]{8,}""") to "$1 $REDACTED",
        // apikey=…, access_token=…, refresh_token=… in a URL or header dump.
        Regex("""(?i)\b(apikey|api_key|access_token|refresh_token|token|password)\b\s*[=:]\s*[^\s&"',;)]+""")
            to "$1=$REDACTED",
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""") to REDACTED,
        // Bare UUIDs: user ids, row ids, workplace ids.
        Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""") to REDACTED,
    )

    fun scrub(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        var out: String = text
        for ((pattern, replacement) in RULES) {
            out = pattern.replace(out, replacement)
        }
        return out
    }
}
