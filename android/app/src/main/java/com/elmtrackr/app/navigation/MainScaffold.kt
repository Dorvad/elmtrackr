package com.elmtrackr.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elmtrackr.app.ui.auth.AuthViewModel
import com.elmtrackr.app.ui.dashboard.DashboardScreen
import com.elmtrackr.app.ui.reports.ReportsScreen
import com.elmtrackr.app.ui.settings.SettingsScreen
import com.elmtrackr.app.ui.shifts.ShiftsScreen
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraInk2

@Composable
fun MainScaffold(authViewModel: AuthViewModel) {
    val navController    = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute     = navBackStackEntry?.destination?.route
    val authState        by authViewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                BottomNavItem.entries.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        icon    = { Icon(item.icon, contentDescription = item.label) },
                        label   = { Text(item.label) },
                        selected = selected,
                        colors  = NavigationBarItemDefaults.colors(
                            selectedIconColor   = Color.White,
                            selectedTextColor   = AuroraIndigo,
                            indicatorColor      = AuroraIndigo,
                            unselectedIconColor = AuroraInk2,
                            unselectedTextColor = AuroraInk2,
                        ),
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(BottomNavItem.DASHBOARD.route) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = BottomNavItem.DASHBOARD.route,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(BottomNavItem.DASHBOARD.route) { DashboardScreen() }
            composable(BottomNavItem.SHIFTS.route)    { ShiftsScreen() }
            composable(BottomNavItem.REPORTS.route)   { ReportsScreen() }
            composable(BottomNavItem.SETTINGS.route) {
                SettingsScreen(
                    authState = authState,
                    onSignOut = { authViewModel.signOut() },
                )
            }
        }
    }
}
