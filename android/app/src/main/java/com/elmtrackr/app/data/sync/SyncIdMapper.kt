package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.PremiumProfileDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao

class SyncIdMapper(
    private val shiftDao: ShiftDao,
    private val compensationProfileDao: CompensationProfileDao,
    private val premiumProfileDao: PremiumProfileDao,
    private val taskDao: TaskDao,
) {
    suspend fun shiftLocalToRemote(localId: String): String? =
        shiftDao.getShiftById(localId)?.remoteId

    suspend fun shiftRemoteToLocal(remoteId: String): String? =
        shiftDao.getShiftByRemoteId(remoteId)?.localId

    suspend fun profileLocalToRemote(localId: String?): String? =
        localId?.let { compensationProfileDao.getByLocalId(it)?.remoteId }

    suspend fun profileRemoteToLocal(remoteId: String?): String? =
        remoteId?.let { compensationProfileDao.getByRemoteId(it)?.localId }

    suspend fun premiumProfileLocalToRemote(localId: String?): String? =
        localId?.let { premiumProfileDao.getByLocalId(it)?.remoteId }

    suspend fun premiumProfileRemoteToLocal(remoteId: String?): String? =
        remoteId?.let { premiumProfileDao.getByRemoteId(it)?.localId }

    suspend fun taskLocalToRemote(localId: String?): String? =
        localId?.let { taskDao.getByLocalId(it)?.remoteId }

    suspend fun taskRemoteToLocal(remoteId: String?): String? =
        remoteId?.let { taskDao.getByRemoteId(it)?.localId }
}
