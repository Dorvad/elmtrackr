package com.elmtrackr.app.di

import com.elmtrackr.app.data.local.preferences.AppLockPreferencesStore
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.local.preferences.AppPreferencesStore
import com.elmtrackr.app.data.local.preferences.ClockFacePreferences
import com.elmtrackr.app.data.local.preferences.FeatureDiscoveryPreferences
import com.elmtrackr.app.data.local.preferences.OnboardingPreferences
import com.elmtrackr.app.data.local.preferences.PurchasePreferences
import com.elmtrackr.app.data.local.preferences.SetupChecklistPreferences
import com.elmtrackr.app.data.local.preferences.WearSyncPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindAppPreferencesStore(impl: AppPreferencesRepository): AppPreferencesStore

    @Binds
    @Singleton
    abstract fun bindAppLockPreferencesStore(impl: AppPreferencesRepository): AppLockPreferencesStore

    @Binds
    @Singleton
    abstract fun bindOnboardingPreferences(impl: AppPreferencesRepository): OnboardingPreferences

    @Binds
    @Singleton
    abstract fun bindSetupChecklistPreferences(impl: AppPreferencesRepository): SetupChecklistPreferences

    @Binds
    @Singleton
    abstract fun bindFeatureDiscoveryPreferences(
        impl: AppPreferencesRepository,
    ): FeatureDiscoveryPreferences

    @Binds
    @Singleton
    abstract fun bindClockFacePreferences(impl: AppPreferencesRepository): ClockFacePreferences

    @Binds
    @Singleton
    abstract fun bindPurchasePreferences(impl: AppPreferencesRepository): PurchasePreferences

    @Binds
    @Singleton
    abstract fun bindWearSyncPreferences(impl: AppPreferencesRepository): WearSyncPreferences
}
