package com.elmtrackr.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.elmtrackr.app.R

/**
 * The app's primary destinations, in the order they appear in the bottom bar
 * and the tablet rail. This is the single destination configuration: both
 * navigation surfaces render [visibleItems], so they cannot disagree about
 * which tabs exist.
 *
 * PROJECTS sits fourth so Settings stays last, which keeps the relative order
 * of the original four destinations — and their slide direction, which is
 * derived from this ordinal — unchanged whether or not Paid Projects is on.
 */
enum class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    /** True for destinations gated behind the Paid Projects feature flag. */
    val requiresPaidProjects: Boolean = false,
) {
    DASHBOARD("dashboard", R.string.nav_home, Icons.Outlined.Home),
    SHIFTS("shifts", R.string.nav_shifts, Icons.AutoMirrored.Outlined.Assignment),
    REPORTS("reports", R.string.nav_reports, Icons.Outlined.BarChart),
    PROJECTS("projects", R.string.nav_projects, Icons.Outlined.WorkOutline, requiresPaidProjects = true),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
    ;

    companion object {
        /**
         * Material's guidance, and the product constraint: a bottom bar never
         * carries more than five primary destinations. Asserted in
         * BottomNavItemTest so a sixth entry fails the build's test run.
         */
        const val MAX_PRIMARY_DESTINATIONS = 5

        /** Destinations to show for the given feature state. */
        fun visibleItems(paidProjectsEnabled: Boolean): List<BottomNavItem> =
            entries.filter { paidProjectsEnabled || !it.requiresPaidProjects }

        fun fromRoute(route: String?): BottomNavItem? =
            entries.firstOrNull { it.route == route }
    }
}
