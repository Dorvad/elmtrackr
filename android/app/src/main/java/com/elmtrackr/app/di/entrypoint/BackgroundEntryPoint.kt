package com.elmtrackr.app.di.entrypoint

import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncScheduler
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import com.elmtrackr.app.startup.DynamicShortcutsRefresher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundEntryPoint {
    fun shiftsRepository(): ShiftsRepository
    fun settingsRepository(): SettingsRepository
    fun tasksRepository(): TasksRepository
    fun refundsRepository(): RefundsRepository
    fun compensationProfilesRepository(): CompensationProfilesRepository
    fun currentUserProvider(): CurrentUserProvider
    fun authRepository(): AuthRepository
    fun syncRepository(): SyncRepository
    fun syncScheduler(): SyncScheduler
    fun appPreferences(): AppPreferencesRepository
    fun refundReceiptStorage(): RefundReceiptStorage?
    fun dynamicShortcutsRefresher(): DynamicShortcutsRefresher
}
