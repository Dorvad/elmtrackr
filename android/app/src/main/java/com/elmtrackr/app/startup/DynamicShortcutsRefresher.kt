package com.elmtrackr.app.startup

import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.di.ApplicationScope
import com.elmtrackr.app.sideeffects.ActiveShiftSideEffectsCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicShortcutsRefresher @Inject constructor(
    private val currentUserProvider: CurrentUserProvider,
    private val shiftsRepository: ShiftsRepository,
    private val activeShiftSideEffects: ActiveShiftSideEffectsCoordinator,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun refresh() {
        applicationScope.launch {
            val userId = currentUserProvider.currentUserId()
            val activeShift = userId?.let { shiftsRepository.observeActiveShift(it).first() }
            activeShiftSideEffects.refreshShortcuts(activeShift != null)
        }
    }
}
