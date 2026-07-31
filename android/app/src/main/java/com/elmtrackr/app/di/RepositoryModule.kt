package com.elmtrackr.app.di

import com.elmtrackr.app.data.repository.LocalCompensationProfilesRepository
import com.elmtrackr.app.data.repository.LocalPremiumProfilesRepository
import com.elmtrackr.app.data.repository.PremiumProfilesRepository
import com.elmtrackr.app.data.repository.LocalProjectsRepository
import com.elmtrackr.app.data.repository.LocalRefundsRepository
import com.elmtrackr.app.data.repository.LocalReportsRepository
import com.elmtrackr.app.data.repository.LocalSettingsRepository
import com.elmtrackr.app.data.repository.LocalShiftsRepository
import com.elmtrackr.app.data.repository.LocalTasksRepository
import com.elmtrackr.app.data.repository.SupabaseAuthRepository
import com.elmtrackr.app.data.sync.SyncRepository
import com.elmtrackr.app.data.sync.SyncRepositoryImpl
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.PreferencesCurrentUserProvider
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.RefundsRepository
import com.elmtrackr.app.domain.repository.ProjectsRepository
import com.elmtrackr.app.domain.repository.ReportsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.repository.ShiftsRepository
import com.elmtrackr.app.domain.repository.TasksRepository
import com.elmtrackr.app.data.repository.CompensationProfilesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindShiftsRepository(impl: LocalShiftsRepository): ShiftsRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: LocalSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindReportsRepository(impl: LocalReportsRepository): ReportsRepository

    @Binds
    @Singleton
    abstract fun bindRefundsRepository(impl: LocalRefundsRepository): RefundsRepository

    @Binds
    @Singleton
    abstract fun bindTasksRepository(impl: LocalTasksRepository): TasksRepository

    @Binds
    @Singleton
    abstract fun bindProjectsRepository(impl: LocalProjectsRepository): ProjectsRepository

    @Binds
    @Singleton
    abstract fun bindPremiumProfilesRepository(
        impl: LocalPremiumProfilesRepository,
    ): PremiumProfilesRepository

    @Binds
    @Singleton
    abstract fun bindCompensationProfilesRepository(
        impl: LocalCompensationProfilesRepository,
    ): CompensationProfilesRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindCurrentUserProvider(impl: PreferencesCurrentUserProvider): CurrentUserProvider
}
