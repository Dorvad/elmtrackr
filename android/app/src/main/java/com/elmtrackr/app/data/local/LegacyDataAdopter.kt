package com.elmtrackr.app.data.local

import androidx.room.withTransaction
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyDataAdopter @Inject constructor(
    private val database: ElmTrackrDatabase,
    private val appPreferences: AppPreferencesRepository,
) {
    suspend fun adoptFor(userId: String) {
        if (appPreferences.currentPreferences().legacyDataAdopted) return
        database.withTransaction {
            database.shiftDao().adoptLegacyUser(userId)
            database.settingsDao().adoptLegacyUser(userId)
            database.profileDao().adoptLegacyUser(userId)
            database.refundClaimDao().adoptLegacyUser(userId)
            database.taskDao().adoptLegacyUser(userId)
            database.compensationProfileDao().adoptLegacyUser(userId)
            database.premiumProfileDao().adoptLegacyUser(userId)
            database.receiptDao().adoptOrphanedReceipts(userId)
        }
        appPreferences.setLegacyDataAdopted(true)
    }
}
