package com.elmtrackr.app.di

import com.elmtrackr.app.data.local.preferences.AppLockPreferencesStore
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.local.preferences.AppPreferencesStore
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
}
