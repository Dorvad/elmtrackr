package com.elmtrackr.app.navigation

import android.animation.ValueAnimator
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elmtrackr.app.ui.auth.AuthViewModel
import com.elmtrackr.app.ui.dashboard.DashboardScreen
import com.elmtrackr.app.ui.reports.ReportsScreen
import com.elmtrackr.app.ui.settings.SettingsScreen
import com.elmtrackr.app.ui.shifts.ShiftsScreen
import com.elmtrackr.app.ui.onboarding.OnboardingScreen
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraFaint
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraMotion
import com.elmtrackr.app.ui.design.auroraPressScale

private val navGradient = Brush.linearGradient(
    colorStops = arrayOf(0f to AuroraIndigo, 0.42f to AuroraPlum, 1f to AuroraAqua),
)

@Composable
fun MainScaffold(authViewModel: AuthViewModel) {
    var replayOnboarding by rememberSaveable { mutableStateOf(false) }
    var pendingShiftEditId by rememberSaveable { mutableStateOf<String?>(null) }
    if (replayOnboarding) {
        OnboardingScreen(replay = true, onCompleted = { replayOnboarding = false })
        return
    }
    val navController     = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute      = navBackStackEntry?.destination?.route
    val authState         by authViewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
            enterTransition = {
                val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                navEnterTransition(forward)
            },
            exitTransition = {
                val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                navExitTransition(forward)
            },
            popEnterTransition = {
                val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                navEnterTransition(forward)
            },
            popExitTransition = {
                val forward = routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)
                navExitTransition(forward)
            },
        ) {
            composable(BottomNavItem.DASHBOARD.route) {
                DashboardScreen(
                    onNavigateToReports = {
                        navController.navigate(BottomNavItem.REPORTS.route) {
                            popUpTo(BottomNavItem.DASHBOARD.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(BottomNavItem.SHIFTS.route) {
                ShiftsScreen(
                    pendingEditShiftId = pendingShiftEditId,
                    onPendingEditConsumed = { pendingShiftEditId = null },
                )
            }
            composable(BottomNavItem.REPORTS.route) {
                ReportsScreen(
                    onNavigateToShift = { shiftId ->
                        pendingShiftEditId = shiftId
                        navController.navigate(BottomNavItem.SHIFTS.route) {
                            popUpTo(BottomNavItem.DASHBOARD.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(BottomNavItem.SETTINGS.route) {
                SettingsScreen(
                    authState = authState,
                    onSignOut = { authViewModel.signOut() },
                    onReplayOnboarding = { replayOnboarding = true },
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
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color     = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
            )
            Row(
                modifier = Modifier
                    .widthIn(max = 448.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BottomNavItem.entries.forEach { item ->
                    val selected = currentRoute == item.route
                    val interactionSource = remember { MutableInteractionSource() }
                    val pillAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (selected) 250 else 150,
                            easing = AuroraEaseOut,
                        ),
                        label = "bottom-nav-pill-alpha",
                    )
                    val pillScale by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.9f,
                        animationSpec = tween(
                            durationMillis = if (selected) 250 else 150,
                            easing = AuroraEaseOut,
                        ),
                        label = "bottom-nav-pill-scale",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .auroraPressScale(interactionSource)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onNavigate(item.route) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(width = 48.dp, height = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 32.dp)
                                    .graphicsLayer {
                                        alpha = pillAlpha
                                        scaleX = pillScale
                                        scaleY = pillScale
                                    }
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = RoundedCornerShape(CornerRadius.Small),
                                        ambientColor = AuroraIndigo.copy(alpha = 0.2f),
                                        spotColor = AuroraIndigo.copy(alpha = 0.6f),
                                    )
                                    .background(navGradient, RoundedCornerShape(CornerRadius.Small)),
                            )
                            Icon(
                                imageVector        = item.icon,
                                contentDescription = item.label,
                                tint               = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text       = item.label,
                            fontSize   = 11.5.sp,
                            lineHeight = 14.sp,
                            color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun navEnterTransition(forward: Boolean) =
    if (!ValueAnimator.areAnimatorsEnabled()) {
        fadeIn(tween(0))
    } else {
        slideInHorizontally(tween(250, easing = AuroraEaseOut)) { width ->
            if (forward) width / 5 else -width / 5
        } + fadeIn(tween(AuroraMotion.FadeMillis, easing = AuroraEaseOut))
    }

private fun navExitTransition(forward: Boolean) =
    if (!ValueAnimator.areAnimatorsEnabled()) {
        fadeOut(tween(0))
    } else {
        slideOutHorizontally(tween(180, easing = AuroraEaseOut)) { width ->
            if (forward) -width / 7 else width / 7
        } + fadeOut(tween(AuroraMotion.PressMillis))
    }
private fun routeIndex(route: String?): Int = BottomNavItem.entries.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0

