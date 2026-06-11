package com.elmtrackr.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elmtrackr.app.ui.auth.AuthViewModel
import com.elmtrackr.app.ui.dashboard.DashboardScreen
import com.elmtrackr.app.ui.reports.ReportsScreen
import com.elmtrackr.app.ui.settings.SettingsScreen
import com.elmtrackr.app.ui.shifts.ShiftsScreen
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum

private val navGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
)

@Composable
fun MainScaffold(authViewModel: AuthViewModel) {
    val navController     = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route
    val authState         by authViewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            ElmBottomNav(
                currentRoute = currentRoute,
                onNavigate   = { route ->
                    navController.navigate(route) {
                        popUpTo(BottomNavItem.DASHBOARD.route) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
            )
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

@Composable
private fun ElmBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            color     = AuroraIndigo.copy(alpha = 0.10f),
            thickness = 1.dp,
        )
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomNavItem.entries.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    modifier            = Modifier
                        .weight(1f)
                        .clickable { onNavigate(item.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = if (selected)
                            Modifier
                                .size(width = 52.dp, height = 32.dp)
                                .background(navGradient, RoundedCornerShape(13.dp))
                        else
                            Modifier.size(width = 52.dp, height = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = item.icon,
                            contentDescription = item.label,
                            tint               = if (selected) Color.White else AuroraFaint,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text       = item.label,
                        style      = MaterialTheme.typography.labelSmall,
                        color      = if (selected) AuroraIndigo else AuroraFaint,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
