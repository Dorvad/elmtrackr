package com.elmtrackr.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.elmtrackr.app.ui.auth.AuthScreen
import com.elmtrackr.app.ui.components.states.LoadingState
import com.elmtrackr.app.ui.auth.AuthViewModel
import com.elmtrackr.app.ui.onboarding.OnboardingScreen
import com.elmtrackr.app.ui.shell.AppNavState
import com.elmtrackr.app.ui.shell.AppShellViewModel

@Composable
fun AppNavGraph() {
    val shellViewModel: AppShellViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    val navController = rememberNavController()
    val navState by shellViewModel.navState.collectAsState()

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
    ) {
        composable(AppRoute.LOADING) { LoadingState() }
        composable(AppRoute.AUTH) {
            AuthScreen(viewModel = authViewModel)
        }
        composable(AppRoute.ONBOARDING) {
            OnboardingScreen()
        }
        composable(AppRoute.MAIN) {
            MainScaffold(authViewModel = authViewModel)
        }
    }
}

