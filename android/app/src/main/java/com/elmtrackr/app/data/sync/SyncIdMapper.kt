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
    private val projectDao: com.elmtrackr.app.data.local.dao.ProjectDao,
    private val billingRecordDao: com.elmtrackr.app.data.local.dao.ProjectBillingRecordDao,
    private val workplaceDao: com.elmtrackr.app.data.local.dao.WorkplaceDao,
    private val absenceEventDao: com.elmtrackr.app.data.local.dao.AbsenceEventDao,
) {
    suspend fun workplaceLocalToRemote(localId: String?): String? =
        localId?.let { workplaceDao.getByLocalId(it)?.remoteId }

    suspend fun workplaceRemoteToLocal(remoteId: String?): String? =
        remoteId?.let { workplaceDao.getByRemoteId(it)?.localId }

    suspend fun absenceEventLocalToRemote(localId: String?): String? =
        localId?.let { absenceEventDao.getByLocalId(it)?.remoteId }

    suspend fun absenceEventRemoteToLocal(remoteId: String?): String? =
        remoteId?.let { absenceEventDao.getByRemoteId(it)?.localId }

    suspend fun projectLocalToRemote(localId: String?): String? =
        localId?.let { projectDao.getByLocalId(it)?.remoteId }

    suspend fun projectRemoteToLocal(remoteId: String?): String? =
        remoteId?.let { projectDao.getByRemoteId(it)?.localId }

    suspend fun billingRecordLocalToRemote(localId: String?): String? =
        localId?.let { billingRecordDao.getByLocalId(it)?.remoteId }

    suspend fun billingRecordRemoteToLocal(remoteId: String?): String? =
        remoteId?.let { billingRecordDao.getByRemoteId(it)?.localId }

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
