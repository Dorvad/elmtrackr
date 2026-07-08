package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// ---- FakeShiftDao ----

class FakeShiftDao : ShiftDao {
    private val store = mutableMapOf<String, ShiftEntity>()
    private val _flow = MutableStateFlow<List<ShiftEntity>>(emptyList())

    private fun refresh() { _flow.value = store.values.toList() }

    override suspend fun adoptLegacyUser(userId: String) {
        store.replaceAll { _, value -> if (value.userId == "local-user") value.copy(userId = userId) else value }
        refresh()
    }

    override fun observeShifts(userId: String): Flow<List<ShiftEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && e.deletedAt == null } }

    override fun observeActiveShift(userId: String): Flow<ShiftEntity?> =
        _flow.map { it.firstOrNull { e -> e.userId == userId && e.endTime == null && e.deletedAt == null } }

    override suspend fun getShiftById(localId: String): ShiftEntity? = store[localId]

    override suspend fun getShiftByStartTime(userId: String, startTime: Long): ShiftEntity? =
        store.values.firstOrNull { it.userId == userId && it.startTime == startTime && it.deletedAt == null }

    override suspend fun insertShift(shift: ShiftEntity) { store[shift.localId] = shift; refresh() }

    override suspend fun updateShift(shift: ShiftEntity) { store[shift.localId] = shift; refresh() }

    override suspend fun upsertShift(shift: ShiftEntity) { store[shift.localId] = shift; refresh() }

    override suspend fun softDeleteShift(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let { store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt) }
        refresh()
    }

    override suspend fun detachTaskFromShifts(userId: String, taskId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId && value.taskId == taskId) value.copy(taskId = null) else value
        }
        refresh()
    }

    override fun observePendingSyncShifts(userId: String): Flow<List<ShiftEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && e.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE) } }

    override suspend fun getPendingSyncShifts(userId: String): List<ShiftEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) {
        store[localId]?.let { store[localId] = it.copy(syncStatus = syncStatus, remoteId = remoteId, lastSyncedAt = lastSyncedAt, lastSyncError = lastSyncError) }
        refresh()
    }

    override suspend fun markNeverSyncedPendingCreate(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId && value.remoteId == null &&
                value.syncStatus == SyncStatus.SYNCED && value.deletedAt == null
            ) {
                value.copy(syncStatus = SyncStatus.PENDING_CREATE)
            } else {
                value
            }
        }
        refresh()
    }

    override suspend fun getAllShiftsForUser(userId: String): List<ShiftEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun hasAnyShifts(userId: String): Boolean =
        store.values.any { it.userId == userId && it.deletedAt == null }

    override suspend fun hasPendingSyncShifts(userId: String): Boolean =
        store.values.any {
            it.userId == userId &&
                it.syncStatus in listOf(
                    SyncStatus.PENDING_CREATE,
                    SyncStatus.PENDING_UPDATE,
                    SyncStatus.PENDING_DELETE,
                    SyncStatus.FAILED,
                )
        }

    override suspend fun getActiveShifts(userId: String): List<ShiftEntity> =
        store.values.filter { it.userId == userId && it.endTime == null && it.deletedAt == null }

    override suspend fun getShiftByRemoteId(remoteId: String): ShiftEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override fun observeShiftsByDateRange(userId: String, fromEpoch: Long, toEpoch: Long): Flow<List<ShiftEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && e.startTime >= fromEpoch && e.startTime < toEpoch && e.deletedAt == null } }

    override fun observeShiftsForDay(userId: String, fromEpoch: Long, toEpoch: Long): Flow<List<ShiftEntity>> =
        observeShiftsByDateRange(userId, fromEpoch, toEpoch)

    override fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<ShiftEntity>> =
        _flow.map {
            it.filter { e -> e.userId == userId && e.endTime != null && e.deletedAt == null }
                .sortedByDescending { e -> e.endTime }
                .take(limit)
        }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
}

// ---- FakeRefundClaimDao ----

class FakeRefundClaimDao : RefundClaimDao {
    private val store = mutableMapOf<String, RefundClaimEntity>()
    private val _flow = MutableStateFlow<List<RefundClaimEntity>>(emptyList())

    private fun refresh() { _flow.value = store.values.toList() }

    override suspend fun adoptLegacyUser(userId: String) {
        store.replaceAll { _, value -> if (value.userId == "local-user") value.copy(userId = userId) else value }
        refresh()
    }

    override fun observeClaimsForUser(userId: String): Flow<List<RefundClaimEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && e.deletedAt == null } }

    override fun observeClaimsForShift(shiftLocalId: String): Flow<List<RefundClaimEntity>> =
        _flow.map { it.filter { e -> e.shiftLocalId == shiftLocalId && e.deletedAt == null } }

    override suspend fun getClaimById(localId: String): RefundClaimEntity? = store[localId]

    override suspend fun insertClaim(claim: RefundClaimEntity) { store[claim.localId] = claim; refresh() }

    override suspend fun updateClaim(claim: RefundClaimEntity) { store[claim.localId] = claim; refresh() }

    override suspend fun upsertClaim(claim: RefundClaimEntity) { store[claim.localId] = claim; refresh() }

    override suspend fun softDeleteClaim(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let { store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt) }
        refresh()
    }

    override suspend fun softDeleteClaimsForShift(
        shiftLocalId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store.replaceAll { _, value ->
            if (value.shiftLocalId == shiftLocalId && value.deletedAt == null) {
                value.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
            } else {
                value
            }
        }
        refresh()
    }

    override fun observePendingSyncClaims(userId: String): Flow<List<RefundClaimEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && e.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE) } }

    override suspend fun getPendingSyncClaims(userId: String): List<RefundClaimEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun hasPendingSyncClaims(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) {
        store[localId]?.let { store[localId] = it.copy(syncStatus = syncStatus, remoteId = remoteId, lastSyncedAt = lastSyncedAt, lastSyncError = lastSyncError) }
        refresh()
    }

    override suspend fun markNeverSyncedPendingCreate(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId && value.remoteId == null &&
                value.syncStatus == SyncStatus.SYNCED && value.deletedAt == null
            ) {
                value.copy(syncStatus = SyncStatus.PENDING_CREATE)
            } else {
                value
            }
        }
        refresh()
    }

    override suspend fun getAllClaimsForUser(userId: String): List<RefundClaimEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getClaimByRemoteId(remoteId: String): RefundClaimEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
}

// ---- FakeSettingsDao ----

class FakeSettingsDao : SettingsDao {
    private val store = mutableMapOf<String, UserSettingsEntity>()
    private val _flow = MutableStateFlow<List<UserSettingsEntity>>(emptyList())

    private fun refresh() { _flow.value = store.values.toList() }

    override suspend fun adoptLegacyUser(userId: String) {
        store.replaceAll { _, value -> if (value.userId == "local-user") value.copy(userId = userId) else value }
        refresh()
    }

    override fun observeSettings(userId: String): Flow<UserSettingsEntity?> =
        _flow.map { it.firstOrNull { e -> e.userId == userId && e.deletedAt == null } }

    override suspend fun getSettings(userId: String): UserSettingsEntity? =
        store.values.firstOrNull { it.userId == userId && it.deletedAt == null }

    override suspend fun insertSettings(settings: UserSettingsEntity) { store[settings.localId] = settings; refresh() }

    override suspend fun updateSettings(settings: UserSettingsEntity) { store[settings.localId] = settings; refresh() }

    override suspend fun upsertSettings(settings: UserSettingsEntity) { store[settings.localId] = settings; refresh() }

    override fun observePendingSyncSettings(userId: String): Flow<List<UserSettingsEntity>> =
        _flow.map {
            it.filter { e ->
                e.userId == userId && e.syncStatus in listOf(
                    SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE,
                )
            }
        }

    override suspend fun getPendingSyncSettings(userId: String): List<UserSettingsEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun hasPendingSyncSettings(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) {
        store[localId]?.let { store[localId] = it.copy(syncStatus = syncStatus, remoteId = remoteId, lastSyncedAt = lastSyncedAt, lastSyncError = lastSyncError) }
        refresh()
    }

    override suspend fun markNeverSyncedPendingCreate(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId && value.remoteId == null &&
                value.syncStatus == SyncStatus.SYNCED && value.deletedAt == null
            ) {
                value.copy(syncStatus = SyncStatus.PENDING_CREATE)
            } else {
                value
            }
        }
        refresh()
    }

    override suspend fun getAllSettingsForUser(userId: String): List<UserSettingsEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun getSettingsByRemoteId(remoteId: String): UserSettingsEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
}

// ---- FakeProfileDao ----

class FakeProfileDao : ProfileDao {
    val store = mutableMapOf<String, ProfileEntity>()
    private val _flow = MutableStateFlow<List<ProfileEntity>>(emptyList())

    private fun refresh() { _flow.value = store.values.toList() }

    override suspend fun adoptLegacyUser(userId: String) {
        store.replaceAll { _, value -> if (value.userId == "local-user") value.copy(userId = userId) else value }
        refresh()
    }

    override fun observeProfile(userId: String): Flow<ProfileEntity?> =
        _flow.map { it.firstOrNull { e -> e.userId == userId && e.deletedAt == null } }

    override suspend fun getProfile(userId: String): ProfileEntity? =
        store.values.firstOrNull { it.userId == userId && it.deletedAt == null }

    override suspend fun insertProfile(profile: ProfileEntity) { store[profile.localId] = profile; refresh() }

    override suspend fun updateProfile(profile: ProfileEntity) { store[profile.localId] = profile; refresh() }

    override suspend fun upsertProfile(profile: ProfileEntity) { store[profile.localId] = profile; refresh() }

    override suspend fun getPendingSyncProfiles(userId: String): List<ProfileEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) {
        store[localId]?.let { store[localId] = it.copy(syncStatus = syncStatus, remoteId = remoteId, lastSyncedAt = lastSyncedAt, lastSyncError = lastSyncError) }
        refresh()
    }

    override suspend fun getProfileByRemoteId(remoteId: String): ProfileEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun markNeverSyncedPendingUpdate(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId && value.syncStatus == SyncStatus.SYNCED &&
                value.lastSyncedAt == null && value.deletedAt == null
            ) {
                value.copy(syncStatus = SyncStatus.PENDING_UPDATE)
            } else {
                value
            }
        }
        refresh()
    }

    override suspend fun hasPendingSyncProfiles(userId: String): Boolean =
        store.values.any {
            it.userId == userId &&
                it.syncStatus in listOf(
                    SyncStatus.PENDING_CREATE,
                    SyncStatus.PENDING_UPDATE,
                    SyncStatus.PENDING_DELETE,
                    SyncStatus.FAILED,
                )
        }

    override fun observePendingSyncProfiles(userId: String): Flow<List<ProfileEntity>> =
        _flow.map {
            it.filter { e ->
                e.userId == userId &&
                    e.syncStatus in listOf(
                        SyncStatus.PENDING_CREATE,
                        SyncStatus.PENDING_UPDATE,
                        SyncStatus.PENDING_DELETE,
                        SyncStatus.FAILED,
                    )
            }
        }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
}

// ---- FakeCompensationProfileDao ----

class FakeCompensationProfileDao : com.elmtrackr.app.data.local.dao.CompensationProfileDao {
    private val store = mutableMapOf<String, com.elmtrackr.app.data.local.entity.CompensationProfileEntity>()
    private val _flow = MutableStateFlow<List<com.elmtrackr.app.data.local.entity.CompensationProfileEntity>>(emptyList())

    private fun refresh() { _flow.value = store.values.toList() }

    override fun observeProfiles(userId: String): Flow<List<com.elmtrackr.app.data.local.entity.CompensationProfileEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && !e.isArchived && e.deletedAt == null } }

    override suspend fun getByUser(userId: String): List<com.elmtrackr.app.data.local.entity.CompensationProfileEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getDefaultProfile(userId: String): com.elmtrackr.app.data.local.entity.CompensationProfileEntity? =
        store.values.firstOrNull { it.userId == userId && it.isDefault && it.deletedAt == null }

    override suspend fun getByLocalId(localId: String): com.elmtrackr.app.data.local.entity.CompensationProfileEntity? =
        store[localId]

    override suspend fun getById(userId: String, localId: String): com.elmtrackr.app.data.local.entity.CompensationProfileEntity? =
        store[localId]?.takeIf { it.userId == userId }

    override suspend fun getByRemoteId(remoteId: String): com.elmtrackr.app.data.local.entity.CompensationProfileEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getPendingSyncProfiles(userId: String): List<com.elmtrackr.app.data.local.entity.CompensationProfileEntity> =
        store.values.filter {
            it.userId == userId && it.syncStatus in listOf(
                SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED,
            )
        }

    override suspend fun hasPendingSyncProfiles(userId: String): Boolean =
        store.values.any {
            it.userId == userId && it.syncStatus in listOf(
                SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED,
            )
        }

    override fun observePendingSyncProfiles(userId: String): Flow<List<com.elmtrackr.app.data.local.entity.CompensationProfileEntity>> =
        _flow.map {
            it.filter { e ->
                e.userId == userId && e.syncStatus in listOf(
                    SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE,
                )
            }
        }

    override suspend fun markNeverSyncedPendingCreate(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId && value.remoteId == null &&
                value.syncStatus == SyncStatus.SYNCED && value.deletedAt == null
            ) {
                value.copy(syncStatus = SyncStatus.PENDING_CREATE)
            } else {
                value
            }
        }
        refresh()
    }

    override suspend fun getAllProfilesForUser(userId: String): List<com.elmtrackr.app.data.local.entity.CompensationProfileEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun adoptLegacyUser(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == "local-user") value.copy(userId = userId) else value
        }
        refresh()
    }

    override suspend fun insert(profile: com.elmtrackr.app.data.local.entity.CompensationProfileEntity) {
        store[profile.localId] = profile
        refresh()
    }

    override suspend fun update(profile: com.elmtrackr.app.data.local.entity.CompensationProfileEntity) {
        store[profile.localId] = profile
        refresh()
    }

    override suspend fun upsert(profile: com.elmtrackr.app.data.local.entity.CompensationProfileEntity) {
        store[profile.localId] = profile
        refresh()
    }

    override suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(syncStatus = status, remoteId = remoteId, lastSyncedAt = syncedAt, lastSyncError = error)
        }
        refresh()
    }

    override suspend fun clearDefaultForUser(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId) value.copy(isDefault = false) else value
        }
        refresh()
    }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
}

class FakeTaskDao : com.elmtrackr.app.data.local.dao.TaskDao {
    private val store = mutableMapOf<String, com.elmtrackr.app.data.local.entity.TaskEntity>()
    private val _flow = MutableStateFlow<List<com.elmtrackr.app.data.local.entity.TaskEntity>>(emptyList())

    private fun refresh() { _flow.value = store.values.toList() }

    override fun observeActiveTasks(userId: String) =
        _flow.map { it.filter { e -> e.userId == userId && !e.isArchived && e.deletedAt == null } }

    override fun observeAllTasks(userId: String) =
        _flow.map { it.filter { e -> e.userId == userId && e.deletedAt == null } }

    override suspend fun getActiveTasks(userId: String) =
        store.values.filter { it.userId == userId && !it.isArchived && it.deletedAt == null }

    override suspend fun getByLocalId(localId: String) = store[localId]

    override suspend fun getById(userId: String, localId: String) =
        store[localId]?.takeIf { it.userId == userId }

    override suspend fun getByRemoteId(remoteId: String) =
        store.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun getPendingSyncTasks(userId: String) =
        store.values.filter { it.userId == userId && it.syncStatus != SyncStatus.SYNCED && it.deletedAt == null }

    override suspend fun hasPendingSyncTasks(userId: String): Boolean =
        store.values.any { it.userId == userId && it.syncStatus != SyncStatus.SYNCED && it.deletedAt == null }

    override fun observePendingSyncTasks(userId: String) =
        _flow.map { it.filter { e -> e.userId == userId && e.syncStatus != SyncStatus.SYNCED && e.deletedAt == null } }

    override suspend fun markNeverSyncedPendingCreate(userId: String) {
        store.replaceAll { _, value ->
            if (value.userId == userId && value.remoteId == null &&
                value.syncStatus == SyncStatus.SYNCED && value.deletedAt == null
            ) {
                value.copy(syncStatus = SyncStatus.PENDING_CREATE)
            } else {
                value
            }
        }
        refresh()
    }

    override suspend fun getAllTasksForUser(userId: String) =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun softDeleteTask(
        localId: String,
        deletedAt: Long,
        syncStatus: SyncStatus,
        updatedAt: Long,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt)
        }
        refresh()
    }

    override suspend fun upsert(task: com.elmtrackr.app.data.local.entity.TaskEntity) = insert(task)

    override suspend fun adoptLegacyUser(userId: String) {
        store.replaceAll { _, value -> if (value.userId == "local-user") value.copy(userId = userId) else value }
        refresh()
    }

    override suspend fun insert(task: com.elmtrackr.app.data.local.entity.TaskEntity) {
        store[task.localId] = task
        refresh()
    }

    override suspend fun updateSyncState(
        localId: String,
        status: SyncStatus,
        remoteId: String?,
        syncedAt: Long?,
        error: String?,
    ) {
        store[localId]?.let {
            store[localId] = it.copy(syncStatus = status, remoteId = remoteId, lastSyncedAt = syncedAt, lastSyncError = error)
        }
        refresh()
    }

    override suspend fun updateLastUsed(localId: String, lastUsedAt: Long, updatedAt: Long) {
        store[localId]?.let { store[localId] = it.copy(lastUsedAt = lastUsedAt, updatedAt = updatedAt) }
        refresh()
    }

    override suspend fun deleteAllForUser(userId: String) {
        store.entries.removeIf { it.value.userId == userId }
        refresh()
    }
}
