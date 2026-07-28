package com.elmtrackr.app.data.local

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.PremiumProfileDao
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import androidx.room.withTransaction
import com.elmtrackr.app.domain.repository.ReceiptsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Removes all Room rows for a user after account deletion or local wipe. */
@Singleton
class LocalUserDataCleaner @Inject constructor(
    private val database: ElmTrackrDatabase,
    private val shiftDao: ShiftDao,
    private val settingsDao: SettingsDao,
    private val profileDao: ProfileDao,
    private val refundClaimDao: RefundClaimDao,
    private val receiptsRepository: ReceiptsRepository,
    private val compensationProfileDao: CompensationProfileDao,
    private val premiumProfileDao: PremiumProfileDao,
    private val taskDao: TaskDao,
) {
    suspend fun clearUserData(userId: String) {
        // Receipt images are files, so they cannot participate in the transaction;
        // clearing them first means a failure leaves rows pointing at deleted files
        // rather than files with no rows to find them by.
        receiptsRepository.deleteAllForUser(userId)
        // One transaction for the row deletes: process death midway through the old
        // eight independent calls left an account half-deleted (shifts gone, profile
        // and settings intact) with nothing to resume it.
        database.withTransaction {
            shiftDao.deleteAllForUser(userId)
            refundClaimDao.deleteAllForUser(userId)
            settingsDao.deleteAllForUser(userId)
            compensationProfileDao.deleteAllForUser(userId)
            premiumProfileDao.deleteAllForUser(userId)
            taskDao.deleteAllForUser(userId)
            profileDao.deleteAllForUser(userId)
        }
    }
}
