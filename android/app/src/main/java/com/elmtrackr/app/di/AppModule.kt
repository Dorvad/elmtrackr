package com.elmtrackr.app.di

import android.content.Context
import com.elmtrackr.app.data.auth.AuthSessionCoordinator
import com.elmtrackr.app.data.auth.SessionBootstrapGate
import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.remote.RemoteCompensationProfileDataSource
import com.elmtrackr.app.data.remote.SupabaseCompensationProfilesDataSource
import com.elmtrackr.app.data.remote.SupabaseProfilesDataSource
import com.elmtrackr.app.data.remote.SupabaseRefundClaimsDataSource
import com.elmtrackr.app.data.remote.SupabaseRefundReceiptStorage
import com.elmtrackr.app.data.remote.SupabaseShiftsDataSource
import com.elmtrackr.app.data.remote.SupabaseTasksDataSource
import com.elmtrackr.app.data.remote.SupabaseUserSettingsDataSource
import com.elmtrackr.app.data.remote.SupabasePremiumProfilesDataSource
import com.elmtrackr.app.data.remote.RemotePremiumProfileDataSource
import com.elmtrackr.app.data.remote.RemoteProfileDataSource
import com.elmtrackr.app.data.remote.RemoteRefundClaimDataSource
import com.elmtrackr.app.data.remote.RemoteShiftDataSource
import com.elmtrackr.app.data.remote.RemoteTaskDataSource
import com.elmtrackr.app.data.remote.RemoteUserSettingsDataSource
import com.elmtrackr.app.data.sync.NoOpSyncTrigger
import com.elmtrackr.app.data.sync.PreferenceSyncCursorStore
import com.elmtrackr.app.data.sync.SyncCursorStore
import com.elmtrackr.app.data.sync.SyncScheduler
import com.elmtrackr.app.data.sync.SyncTrigger
import com.elmtrackr.app.data.receipts.PhotoFileManager
import com.elmtrackr.app.domain.repository.ReceiptFileReader
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.ui.settings.AppThemePreferenceStore
import com.elmtrackr.app.ui.settings.ThemePreferenceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSessionBootstrapGate(coordinator: AuthSessionCoordinator): SessionBootstrapGate =
        coordinator

    @Provides
    @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferencesRepository =
        AppPreferencesRepository(context)

    @Provides
    @Singleton
    fun provideSyncCursorStore(@ApplicationContext context: Context): SyncCursorStore =
        PreferenceSyncCursorStore(context)

    @Provides
    @Singleton
    fun provideSyncScheduler(@ApplicationContext context: Context): SyncScheduler =
        SyncScheduler(context)

    @Provides
    @Singleton
    fun provideSyncTrigger(scheduler: SyncScheduler): SyncTrigger =
        if (SupabaseClientProvider.isConfigured()) scheduler else NoOpSyncTrigger

    @Provides
    @Singleton
    fun provideThemePreferenceStore(appPreferences: AppPreferencesRepository): ThemePreferenceStore =
        AppThemePreferenceStore(appPreferences)

    @Provides
    fun provideRemoteTasksDataSource(): RemoteTaskDataSource? =
        SupabaseClientProvider.get()?.let { SupabaseTasksDataSource(it) }

    @Provides
    fun provideRemoteShiftsDataSource(): RemoteShiftDataSource? =
        SupabaseClientProvider.get()?.let { SupabaseShiftsDataSource(it) }

    @Provides
    fun provideRemoteRefundClaimsDataSource(): RemoteRefundClaimDataSource? =
        SupabaseClientProvider.get()?.let { SupabaseRefundClaimsDataSource(it) }

    @Provides
    fun provideRemoteUserSettingsDataSource(): RemoteUserSettingsDataSource? =
        SupabaseClientProvider.get()?.let { SupabaseUserSettingsDataSource(it) }

    @Provides
    fun provideRemotePremiumProfilesDataSource(): RemotePremiumProfileDataSource? =
        SupabaseClientProvider.get()?.let { SupabasePremiumProfilesDataSource(it) }

    @Provides
    fun provideRemoteCompensationProfilesDataSource(): RemoteCompensationProfileDataSource? =
        SupabaseClientProvider.get()?.let { SupabaseCompensationProfilesDataSource(it) }

    // Workplaces and leave. Nullable like every other remote: no Supabase
    // credentials means no data source, and syncAll returns NotConfigured before
    // the pipeline runs.
    @Provides
    fun provideRemoteWorkplacesDataSource(): com.elmtrackr.app.data.remote.RemoteWorkplaceDataSource? =
        SupabaseClientProvider.get()?.let { com.elmtrackr.app.data.remote.SupabaseWorkplaceDataSource(it) }

    @Provides
    fun provideRemoteLeavePoliciesDataSource(): com.elmtrackr.app.data.remote.RemoteLeavePolicyDataSource? =
        SupabaseClientProvider.get()?.let { com.elmtrackr.app.data.remote.SupabaseLeavePolicyDataSource(it) }

    @Provides
    fun provideRemoteAbsenceEventsDataSource(): com.elmtrackr.app.data.remote.RemoteAbsenceEventDataSource? =
        SupabaseClientProvider.get()?.let { com.elmtrackr.app.data.remote.SupabaseAbsenceEventDataSource(it) }

    @Provides
    fun provideRemoteAbsenceAllocationsDataSource():
        com.elmtrackr.app.data.remote.RemoteAbsenceAllocationDataSource? =
        SupabaseClientProvider.get()?.let {
            com.elmtrackr.app.data.remote.SupabaseAbsenceAllocationDataSource(it)
        }

    @Provides
    fun provideRemoteLeaveBalancesDataSource():
        com.elmtrackr.app.data.remote.RemoteLeaveBalanceSnapshotDataSource? =
        SupabaseClientProvider.get()?.let {
            com.elmtrackr.app.data.remote.SupabaseLeaveBalanceSnapshotDataSource(it)
        }

    @Provides
    fun provideRemoteProfilesDataSource(): RemoteProfileDataSource? =
        SupabaseClientProvider.get()?.let { SupabaseProfilesDataSource(it) }

    @Provides
    fun provideRefundReceiptStorage(): RefundReceiptStorage? =
        SupabaseClientProvider.get()?.let { SupabaseRefundReceiptStorage(it) }

    @Provides
    fun provideRemoteProjectsDataSource(): com.elmtrackr.app.data.remote.RemoteProjectDataSource? =
        SupabaseClientProvider.get()?.let {
            com.elmtrackr.app.data.remote.SupabaseProjectsDataSource(it)
        }

    @Provides
    fun provideRemoteProjectBillingRecordsDataSource():
        com.elmtrackr.app.data.remote.RemoteProjectBillingRecordDataSource? =
        SupabaseClientProvider.get()?.let {
            com.elmtrackr.app.data.remote.SupabaseProjectBillingRecordsDataSource(it)
        }

    @Provides
    fun provideRemoteProjectPaymentsDataSource():
        com.elmtrackr.app.data.remote.RemoteProjectPaymentDataSource? =
        SupabaseClientProvider.get()?.let {
            com.elmtrackr.app.data.remote.SupabaseProjectPaymentsDataSource(it)
        }

    @Provides
    fun provideReceiptFileReader(
        @ApplicationContext context: Context,
    ): ReceiptFileReader = PhotoFileManager(context)
}
