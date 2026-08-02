package com.elmtrackr.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.elmtrackr.app.ui.auth.AuthScreen
import com.elmtrackr.app.ui.components.states.LoadingState
import com.elmtrackr.app.ui.auth.AuthViewModel
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraMotion
import com.elmtrackr.app.ui.design.auroraMotionEnabled
import com.elmtrackr.app.ui.onboarding.OnboardingScreen
import com.elmtrackr.app.ui.shell.AppNavState
import com.elmtrackr.app.ui.shell.AppShellViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.android.awaitFrame

@Composable
fun AppNavGraph() {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        awaitFrame()
        ready = true
    }
    if (!ready) {
        LoadingState()
        return
    }

    val shellViewModel: AppShellViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val navController = rememberNavController()
    val navState by shellViewModel.navState.collectAsState()
    val paidProjectsEnabled by shellViewModel.paidProjectsEnabled.collectAsState()
    val pendingDeepLink by shellViewModel.pendingDeepLink.collectAsState()
    // Read once here: the transition factories below are plain functions that
    // run outside composition, so they cannot see LocalReduceMotion themselves.
    val motionEnabled = auroraMotionEnabled()

    LaunchedEffect(navState) {
        val route = when (navState) {
            AppNavState.Loading -> return@LaunchedEffect
            AppNavState.Auth -> AppRoute.AUTH
            AppNavState.Onboarding -> AppRoute.ONBOARDING
            AppNavState.Main -> AppRoute.MAIN
        }
        navController.navigate(route) {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.LOADING,
        enterTransition = { rootEnterTransition(motionEnabled) },
        exitTransition = { rootExitTransition(motionEnabled) },
        popEnterTransition = { rootPopEnterTransition(motionEnabled) },
        popExitTransition = { rootPopExitTransition(motionEnabled) },
    ) {
        composable(AppRoute.LOADING) { LoadingState() }
        composable(AppRoute.AUTH) {
            AuthScreen(viewModel = authViewModel)
        }
        composable(AppRoute.ONBOARDING) {
            OnboardingScreen()
        }
        composable(AppRoute.MAIN) {
            MainScaffold(
                authViewModel = authViewModel,
                paidProjectsEnabled = paidProjectsEnabled,
                pendingDeepLink = pendingDeepLink,
                onDeepLinkConsumed = shellViewModel::consumeDeepLink,
            )
        }
    }
}

private fun rootEnterTransition(motionEnabled: Boolean) =
    if (!motionEnabled) {
        fadeIn(tween(0))
    } else {
        fadeIn(tween(AuroraMotion.ContentCrossfadeMillis, easing = AuroraEaseOut))
    }

private fun rootExitTransition(motionEnabled: Boolean) =
    if (!motionEnabled) {
        fadeOut(tween(0))
    } else {
        fadeOut(tween(AuroraMotion.ContentCrossfadeMillis))
    }

private fun rootPopEnterTransition(motionEnabled: Boolean) =
    if (!motionEnabled) {
        fadeIn(tween(0))
    } else {
        slideInHorizontally(tween(250, easing = AuroraEaseOut)) { it / 5 } +
            fadeIn(tween(AuroraMotion.FadeMillis, easing = AuroraEaseOut))
    }

private fun rootPopExitTransition(motionEnabled: Boolean) =
    if (!motionEnabled) {
        fadeOut(tween(0))
    } else {
        slideOutHorizontally(tween(180, easing = AuroraEaseOut)) { -it / 5 } +
            fadeOut(tween(AuroraMotion.PressMillis))
    }

