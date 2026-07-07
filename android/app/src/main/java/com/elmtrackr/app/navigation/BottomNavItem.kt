package com.elmtrackr.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.elmtrackr.app.R

enum class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    DASHBOARD("dashboard", R.string.nav_home, Icons.Outlined.Home),
    SHIFTS("shifts", R.string.nav_shifts, Icons.AutoMirrored.Outlined.Assignment),
    REPORTS("reports", R.string.nav_reports, Icons.Outlined.BarChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}
