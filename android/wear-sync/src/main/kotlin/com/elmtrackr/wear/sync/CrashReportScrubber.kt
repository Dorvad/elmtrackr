package com.elmtrackr.wear.sync

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Request

/**
 * The last gate a crash report passes through before it leaves the device.
 *
 * [SensitiveTextScrubber] answers "what does an identifying *string* look like".
 * This answers "which parts of a Sentry event are strings a user could appear in",
 * which is a different and larger question than the one the phone and the watch were
 * each answering on their own: both modules scrubbed the event message, the exception
 * values and breadcrumb *messages*, and nothing else. Everything else an event carries
 * travelled untouched.
 *
 * That gap matters for the Play data safety declaration, not only for privacy. The
 * declaration has to name every category of user data the app transmits, and a
 * category that can arrive through an unscrubbed field is a category that has to be
 * declared whether or not it happens to be populated today. Narrowing what can leave
 * is what keeps the declaration short enough to be true.
 *
 * What this removes, and why each one is reachable:
 *
 * - **Breadcrumb data.** Breadcrumbs carry a `data` map that no one was scrubbing.
 *   `UpdateDiagnostics` and `ReviewDiagnostics` write to it deliberately, and Sentry's
 *   own OkHttp integration — which the Gradle plugin auto-installs because okhttp is
 *   on the classpath under Ktor — puts the full request URL and query string of an
 *   HTTP call in there. A PostgREST URL is `…/rest/v1/shifts?user_id=eq.<uuid>`: the
 *   user id and the filter are in the query string.
 * - **The request context.** Same source, structured rather than free text: url,
 *   query string, headers (which is where an `apikey` or a bearer token would be),
 *   cookies and body.
 * - **User email and username.** Sentry attaches an installation id as the user id,
 *   which is a pseudonymous per-install identifier and is declared as such. It never
 *   attaches an email while `isSendDefaultPii` is off, and nothing in this app calls
 *   `Sentry.setUser`. Clearing them anyway costs nothing and means a future call that
 *   does set a user cannot quietly widen what the declaration has to say.
 * - **Extras.** Free-form by definition.
 *
 * Stack frames are deliberately left alone — class, method, file and line carry no
 * user data and are the entire value of the report.
 *
 * ### Why it lives here
 *
 * `:wear-sync` is the only module the phone and the watch share, and this is the same
 * reasoning [SensitiveTextScrubber] records: a redaction ruleset in two copies is a
 * ruleset that drifts, and the copy that drifts is the one that leaks. The Sentry
 * types are `compileOnly` — both consumers already ship the SDK, and this module has
 * no business putting it on anyone's classpath.
 */
object CrashReportScrubber {

    private const val REDACTED = "[redacted]"

    /** Data keys whose value is a URL, and so keeps its path but loses its query. */
    private val URL_KEYS = setOf("url", "http.url", "request_url")

    /**
     * Data keys whose value is dropped whole.
     *
     * A query string, a fragment or a body has no diagnostic shape worth preserving —
     * unlike a URL, where the path names the endpoint that failed.
     */
    private val DROPPED_KEYS = setOf(
        "http.query",
        "query",
        "query_string",
        "http.fragment",
        "fragment",
        "body",
        "http.request.body",
        "http.response.body",
        "authorization",
        "cookie",
        "cookies",
    )

    /** Rewrites [event] in place. Safe to call on an event with nothing to scrub. */
    fun scrub(event: SentryEvent) {
        event.message?.let { message ->
            message.formatted = SensitiveTextScrubber.scrub(message.formatted)
            message.message = SensitiveTextScrubber.scrub(message.message)
            message.params = message.params?.map { SensitiveTextScrubber.scrub(it) ?: it }
        }
        event.exceptions?.forEach { it.value = SensitiveTextScrubber.scrub(it.value) }
        event.breadcrumbs?.forEach { scrub(it) }
        event.request?.let { scrub(it) }
        event.user?.let { user ->
            user.email = null
            user.username = null
            user.ipAddress = null
        }
        scrubExtras(event)
    }

    /**
     * Rewrites [breadcrumb] in place.
     *
     * Public and called from `beforeBreadcrumb` as well as from [scrub], so that a
     * breadcrumb is clean from the moment it is recorded. That matters for the paths
     * `beforeSend` never sees: a native crash is written to the outbox by the NDK
     * handler with whatever the scope held at the time, and uploaded on the next
     * launch without passing back through the send callback.
     */
    fun scrub(breadcrumb: Breadcrumb) {
        breadcrumb.message = SensitiveTextScrubber.scrub(breadcrumb.message)
        // Collected first and applied after: `getData()` is documented by neither the
        // SDK nor its type as a live view or a copy, and writing through `setData`
        // while iterating whichever it is would be a bet on the answer.
        val replacements = scrubbedValues(breadcrumb.data)
        replacements.forEach { (key, value) -> breadcrumb.setData(key, value) }
    }

    private fun scrub(request: Request) {
        request.url = request.url?.let { scrubUrl(it) }
        request.queryString = request.queryString?.let { REDACTED }
        request.fragment = null
        request.cookies = null
        request.headers = null
        request.data = null
    }

    private fun scrubExtras(event: SentryEvent) {
        val extras = event.extras ?: return
        scrubbedValues(extras).forEach { (key, value) -> event.setExtra(key, value) }
    }

    /** The subset of [values] that changed, already scrubbed. Non-strings are left as they are. */
    private fun scrubbedValues(values: Map<String, Any?>): Map<String, Any> {
        val scrubbed = LinkedHashMap<String, Any>()
        for ((key, value) in values) {
            if (value !is String) continue
            val cleaned = scrubValue(key, value) ?: continue
            if (cleaned != value) scrubbed[key] = cleaned
        }
        return scrubbed
    }

    private fun scrubValue(key: String, value: String): String? {
        val lowered = key.lowercase()
        return when {
            lowered in DROPPED_KEYS -> REDACTED
            lowered in URL_KEYS -> scrubUrl(value)
            else -> SensitiveTextScrubber.scrub(value)
        }
    }

    /**
     * Keeps the scheme, host and path of [url] — which is what says *what* failed —
     * and drops the query and fragment, which is where the values are.
     *
     * The remaining path still goes through the text rules, because a Supabase storage
     * object path is `receipts/<user id>/<shift id>.jpg`: identifiers in a path, with
     * no query string in sight.
     */
    private fun scrubUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val marked = if (path.length == url.length) path else "$path?$REDACTED"
        return SensitiveTextScrubber.scrub(marked)
    }
}
