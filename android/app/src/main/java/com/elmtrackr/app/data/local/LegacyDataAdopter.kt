package com.elmtrackr.app.data.local

import androidx.room.withTransaction
import com.elmtrackr.app.data.local.preferences.AppPreferencesRepository

class LegacyDataAdopter(
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
        }
        appPreferences.setLegacyDataAdopted(true)
    }
}
