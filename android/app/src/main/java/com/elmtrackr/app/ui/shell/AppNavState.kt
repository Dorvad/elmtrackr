package com.elmtrackr.app.ui.shell

sealed interface AppNavState {
    data object Loading : AppNavState
    data object Auth : AppNavState
    data object Onboarding : AppNavState
    data object Main : AppNavState
}
