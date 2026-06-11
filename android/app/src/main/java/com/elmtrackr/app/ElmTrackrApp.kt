package com.elmtrackr.app

import android.app.Application
import com.elmtrackr.app.data.auth.SupabaseClientProvider
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import com.elmtrackr.app.data.remote.SupabaseProfileDataSource
import com.elmtrackr.app.data.remote.SupabaseRefundsDataSource
import com.elmtrackr.app.data.remote.SupabaseSettingsDataSource
import com.elmtrackr.app.data.remote.SupabaseShiftsDataSource
import com.elmtrackr.app.data.repository.LocalRefundsRepository
import com.elmtrackr.app.data.repository.LocalReportsRepository
import com.elmtrackr.app.data.repository.LocalSettingsRepository
import com.elmtrackr.app.data.repository.LocalShiftsRepository
import com.elmtrackr.app.data.repository.SupabaseAuthRepository
import com.elmtrackr.app.data.repository.SyncRepositoryImpl
import com.elmtrackr.app.domain.repository.AuthRepository
import com.elmtrackr.app.domain.repository.SyncRepository
import com.elmtrackr.app.sync.SyncScheduler

class ElmTrackrApp : Application() {

    val database: ElmTrackrDatabase by lazy { ElmTrackrDatabase.getInstance(this) }

    val appPreferences: AppPreferencesRepository by lazy { AppPreferencesRepository(this) }

    private val syncScheduler: SyncScheduler by lazy { SyncScheduler(this) }

    val syncRepository: SyncRepository by lazy {
        val client = SupabaseClientProvider.get()
        SyncRepositoryImpl(
            shiftDao = database.shiftDao(),
            refundClaimDao = database.refundClaimDao(),
            settingsDao = database.settingsDao(),
            profileDao = database.profileDao(),
            remoteShifts = client?.let { SupabaseShiftsDataSource(it) },
            remoteRefunds = client?.let { SupabaseRefundsDataSource(it) },
            remoteSettings = client?.let { SupabaseSettingsDataSource(it) },
            remoteProfile = client?.let { SupabaseProfileDataSource(it) },
        )
    }

    val shiftsRepository: LocalShiftsRepository by lazy {
        LocalShiftsRepository(database.shiftDao(), syncScheduler)
    }

    val settingsRepository: LocalSettingsRepository by lazy {
        LocalSettingsRepository(database.settingsDao(), syncScheduler)
    }

    val reportsRepository: LocalReportsRepository by lazy {
        LocalReportsRepository(database.shiftDao(), database.settingsDao())
    }

    val refundsRepository: LocalRefundsRepository by lazy {
        LocalRefundsRepository(database.refundClaimDao(), syncScheduler)
    }

    // SupabaseAuthRepository self-checks BuildConfig: returns NotConfigured state
    // gracefully when SUPABASE_URL / SUPABASE_ANON_KEY are absent (e.g. CI).
    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(database.profileDao(), appPreferences)
    }

    override fun onCreate() {
        super.onCreate()
        // Kick off periodic background sync (no-op when Supabase not configured)
        syncScheduler.schedulePeriodic()
    }
}
