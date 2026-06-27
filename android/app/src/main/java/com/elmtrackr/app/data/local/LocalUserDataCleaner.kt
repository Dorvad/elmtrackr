package com.elmtrackr.app.data.local

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao

/** Removes all Room rows for a user after account deletion or local wipe. */
class LocalUserDataCleaner(
    private val shiftDao: ShiftDao,
    private val settingsDao: SettingsDao,
    private val profileDao: ProfileDao,
    private val refundClaimDao: RefundClaimDao,
    private val compensationProfileDao: CompensationProfileDao,
) {
    suspend fun clearUserData(userId: String) {
        shiftDao.deleteAllForUser(userId)
        refundClaimDao.deleteAllForUser(userId)
        settingsDao.deleteAllForUser(userId)
        compensationProfileDao.deleteAllForUser(userId)
        profileDao.deleteAllForUser(userId)
    }
}
