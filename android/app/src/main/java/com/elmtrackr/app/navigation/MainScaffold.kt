package com.elmtrackr.app.navigation

import android.animation.ValueAnimator
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.auth.AuthViewModel
import com.elmtrackr.app.ui.dashboard.DashboardScreen
import com.elmtrackr.app.ui.reports.ReportsScreen
import com.elmtrackr.app.ui.settings.SettingsScreen
import com.elmtrackr.app.ui.shifts.ShiftsScreen
import com.elmtrackr.app.ui.onboarding.OnboardingScreen
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraHair
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.AuroraPlum
import com.elmtrackr.app.ui.theme.auroraNavBarBackground
import com.elmtrackr.app.ui.theme.auroraNavSelectedLabel
import com.elmtrackr.app.ui.theme.auroraNavUnselectedIcon
import com.elmtrackr.app.ui.theme.auroraNavUnselectedLabel
import com.elmtrackr.app.ui.design.AuroraEaseOut
import com.elmtrackr.app.ui.design.AuroraMeshBackground
import com.elmtrackr.app.ui.design.AuroraMotion
import com.elmtrackr.app.ui.design.auroraPressScale
import com.elmtrackr.app.ui.layout.PhoneContentMaxWidth
import com.elmtrackr.app.ui.layout.isTabletLayout
import com.elmtrackr.app.ui.navigation.ElmSideNavigation

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
    var hideNavChrome     by rememberSaveable { mutableStateOf(false) }
    val isTablet          = isTabletLayout()

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(BottomNavItem.DASHBOARD.route) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    if (isTablet) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (!hideNavChrome) {
                ElmSideNavigation(
                    currentRoute = currentRoute,
                    onNavigate = navigateToTab,
                )
            }
            NavHost(
                navController    = navController,
                startDestination = BottomNavItem.DASHBOARD.route,
                modifier         = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                mainNavGraph(
                    navController = navController,
                    authState = authState,
                    authViewModel = authViewModel,
                    pendingShiftEditId = pendingShiftEditId,
                    onPendingEditConsumed = { pendingShiftEditId = null },
                    onPendingEditSet = { pendingShiftEditId = it },
                    onFormVisibilityChanged = { hideNavChrome = it },
                    onReplayOnboarding = { replayOnboarding = true },
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideNavChrome) {
            ElmBottomNav(
                currentRoute = currentRoute,
                onNavigate   = navigateToTab,
            )
            }
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
            mainNavGraph(
                navController = navController,
                authState = authState,
                authViewModel = authViewModel,
                pendingShiftEditId = pendingShiftEditId,
                onPendingEditConsumed = { pendingShiftEditId = null },
                onPendingEditSet = { pendingShiftEditId = it },
                onFormVisibilityChanged = { hideNavChrome = it },
                onReplayOnboarding = { replayOnboarding = true },
            )
        }
    }
}

private fun NavGraphBuilder.mainNavGraph(
    navController: NavHostController,
    authState: AuthUiState,
    authViewModel: AuthViewModel,
    pendingShiftEditId: String?,
    onPendingEditConsumed: () -> Unit,
    onPendingEditSet: (String) -> Unit,
    onFormVisibilityChanged: (Boolean) -> Unit,
    onReplayOnboarding: () -> Unit,
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
            onPendingEditConsumed = onPendingEditConsumed,
            onFormVisibilityChanged = onFormVisibilityChanged,
        )
    }
    composable(BottomNavItem.REPORTS.route) {
        ReportsScreen(
            onNavigateToShift = { shiftId ->
                onPendingEditSet(shiftId)
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
            onReplayOnboarding = onReplayOnboarding,
        )
    }
}

@Composable
private fun ElmBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
  val navShape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding(),
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .blur(24.dp),
      ) {
        AuroraMeshBackground(modifier = Modifier.matchParentSize())
      }
    }
    Surface(
      color = auroraNavBarBackground(),
      shadowElevation = 0.dp,
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 16.dp,
          shape = navShape,
          clip = false,
          ambientColor = AuroraIndigo.copy(alpha = 0.06f),
          spotColor = AuroraIndigo.copy(alpha = 0.08f),
        ),
    ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      HorizontalDivider(
        color = AuroraHair,
        thickness = 1.dp,
      )
      Row(
        modifier = Modifier
          .widthIn(max = PhoneContentMaxWidth)
          .fillMaxWidth()
          .align(Alignment.CenterHorizontally)
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        BottomNavItem.entries.forEach { item ->
          val isSelected = currentRoute == item.route
          val interactionSource = remember { MutableInteractionSource() }
          val pillAlpha by animateFloatAsState(
            targetValue = if (isSelected) 1f else 0f,
            animationSpec = tween(
              durationMillis = if (isSelected) 250 else 150,
              easing = AuroraEaseOut,
            ),
            label = "bottom-nav-pill-alpha",
          )
          val pillScale by animateFloatAsState(
            targetValue = if (isSelected) 1f else 0.9f,
            animationSpec = tween(
              durationMillis = if (isSelected) 250 else 150,
              easing = AuroraEaseOut,
            ),
            label = "bottom-nav-pill-scale",
          )
          Column(
            modifier = Modifier
              .weight(1f)
              .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                contentDescription = "${item.label}${if (isSelected) ", selected" else ""}"
              }
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
                    shape = RoundedCornerShape(CornerRadius.NavPill),
                    ambientColor = AuroraIndigo.copy(alpha = 0.2f),
                    spotColor = AuroraIndigo.copy(alpha = 0.6f),
                  )
                  .background(navGradient, RoundedCornerShape(CornerRadius.NavPill)),
              )
              Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else auroraNavUnselectedIcon(),
                modifier = Modifier.size(20.dp),
              )
            }
            Text(
              text = item.label,
              fontSize = 10.5.sp,
              lineHeight = 14.sp,
              color = if (isSelected) auroraNavSelectedLabel() else auroraNavUnselectedLabel(),
              fontWeight = FontWeight.SemiBold,
            )
          }
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

