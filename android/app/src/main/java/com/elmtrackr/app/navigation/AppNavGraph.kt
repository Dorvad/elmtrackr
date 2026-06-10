package com.elmtrackr.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elmtrackr.app.ui.auth.AuthScreen
import com.elmtrackr.app.ui.auth.AuthViewModel
import com.elmtrackr.app.ui.dashboard.DashboardScreen
import com.elmtrackr.app.ui.reports.ReportsScreen
import com.elmtrackr.app.ui.settings.SettingsScreen
import com.elmtrackr.app.ui.shifts.ShiftsScreen

@Composable
fun AppNavGraph() {
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(text = item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(BottomNavItem.DASHBOARD.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BottomNavItem.DASHBOARD.route) { DashboardScreen() }
            composable(BottomNavItem.SHIFTS.route) { ShiftsScreen() }
            composable(BottomNavItem.REPORTS.route) { ReportsScreen() }
            composable(BottomNavItem.SETTINGS.route) { SettingsScreen() }
            composable(BottomNavItem.ACCOUNT.route) { AuthScreen(viewModel = authViewModel) }
        }
    }
}
