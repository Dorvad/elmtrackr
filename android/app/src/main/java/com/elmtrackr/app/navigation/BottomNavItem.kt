package com.elmtrackr.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("dashboard", "Home", Icons.Outlined.Home),
    SHIFTS("shifts", "Shifts", Icons.AutoMirrored.Outlined.Assignment),
    REPORTS("reports", "Reports", Icons.Outlined.BarChart),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}
