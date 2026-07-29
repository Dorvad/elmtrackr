package com.elmtrackr.app.navigation

/**
 * Keeps the Paid Projects destination reachable only while the feature is on,
 * without ever removing it from the navigation graph.
 *
 * The projects destination is registered unconditionally. Dropping a
 * destination from a NavHost's graph while it is the current one throws, and
 * rebuilding the graph rebuilds the back stack, losing per-tab state. Instead
 * the graph is fixed and this object decides three things: which route the bar
 * highlights, when to redirect away from a now-disabled destination, and where
 * an incoming deep link should land.
 *
 * Every function takes the flag as a nullable Boolean, where null means
 * "settings have not loaded yet". Treating an unloaded flag as false would
 * bounce a user off the projects tab on every cold start before Room answers.
 */
object PaidProjectsNavGuard {

    /** Scheme + host handled as a projects deep link (see AndroidManifest). */
    const val PROJECTS_DEEP_LINK_PREFIX = "elmtrackr://projects"

    /** The safest primary destination to fall back to. */
    val fallbackRoute: String get() = BottomNavItem.DASHBOARD.route

    /**
     * The route to redirect to, or null to stay put. Non-null only when the
     * feature is known to be off and a gated destination is selected.
     */
    fun redirectTarget(currentRoute: String?, paidProjectsEnabled: Boolean?): String? {
        if (paidProjectsEnabled != false) return null
        val current = BottomNavItem.fromRoute(currentRoute) ?: return null
        return if (current.requiresPaidProjects) fallbackRoute else null
    }

    /**
     * The route the bar and rail should mark as selected. While a redirect is
     * in flight the current route is still the gated one for a frame, and
     * Projects is no longer in the item list — without this the bar would
     * briefly show nothing selected.
     */
    fun highlightedRoute(currentRoute: String?, paidProjectsEnabled: Boolean?): String? =
        redirectTarget(currentRoute, paidProjectsEnabled) ?: currentRoute

    /** True for URIs this guard is responsible for. */
    fun isProjectsDeepLink(uri: String?): Boolean {
        val value = uri?.trim()?.lowercase() ?: return false
        return value == PROJECTS_DEEP_LINK_PREFIX ||
            value.startsWith("$PROJECTS_DEEP_LINK_PREFIX/") ||
            value.startsWith("$PROJECTS_DEEP_LINK_PREFIX?")
    }

    /**
     * Where a deep link should navigate: the projects tab when the feature is
     * on, the dashboard when it is off, and null while the flag is still
     * loading so the caller keeps the link pending rather than misrouting it.
     * Non-projects URIs return null — they are not this guard's business.
     */
    fun deepLinkTarget(uri: String?, paidProjectsEnabled: Boolean?): String? {
        if (!isProjectsDeepLink(uri)) return null
        return when (paidProjectsEnabled) {
            null -> null
            true -> BottomNavItem.PROJECTS.route
            false -> fallbackRoute
        }
    }
}
