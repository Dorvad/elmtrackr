package com.elmtrackr.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Filled.Home),
    SHIFTS("shifts", "Shifts", Icons.Filled.AccessTime),
    REPORTS("reports", "Reports", Icons.Filled.Analytics),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
    ACCOUNT("account", "Account", Icons.Filled.AccountCircle),
}
