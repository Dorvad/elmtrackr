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

    override suspend fun insertShift(shift: ShiftEntity) { store[shift.localId] = shift; refresh() }

    override suspend fun updateShift(shift: ShiftEntity) { store[shift.localId] = shift; refresh() }

    override suspend fun upsertShift(shift: ShiftEntity) { store[shift.localId] = shift; refresh() }

    override suspend fun softDeleteShift(localId: String, deletedAt: Long, syncStatus: SyncStatus, updatedAt: Long) {
        store[localId]?.let { store[localId] = it.copy(deletedAt = deletedAt, syncStatus = syncStatus, updatedAt = updatedAt) }
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

    override suspend fun getAllShiftsForUser(userId: String): List<ShiftEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getActiveShifts(userId: String): List<ShiftEntity> =
        store.values.filter { it.userId == userId && it.endTime == null && it.deletedAt == null }

    override suspend fun getShiftByRemoteId(remoteId: String): ShiftEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }

    override fun observeShiftsByDateRange(userId: String, fromEpoch: Long, toEpoch: Long): Flow<List<ShiftEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && e.startTime >= fromEpoch && e.startTime < toEpoch && e.deletedAt == null } }
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

    override fun observePendingSyncClaims(userId: String): Flow<List<RefundClaimEntity>> =
        _flow.map { it.filter { e -> e.userId == userId && e.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE) } }

    override suspend fun getPendingSyncClaims(userId: String): List<RefundClaimEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) {
        store[localId]?.let { store[localId] = it.copy(syncStatus = syncStatus, remoteId = remoteId, lastSyncedAt = lastSyncedAt, lastSyncError = lastSyncError) }
        refresh()
    }

    override suspend fun getAllClaimsForUser(userId: String): List<RefundClaimEntity> =
        store.values.filter { it.userId == userId && it.deletedAt == null }

    override suspend fun getClaimByRemoteId(remoteId: String): RefundClaimEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }
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

    override suspend fun getPendingSyncSettings(userId: String): List<UserSettingsEntity> =
        store.values.filter { it.userId == userId && it.syncStatus in listOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE, SyncStatus.FAILED) }

    override suspend fun updateSyncState(localId: String, syncStatus: SyncStatus, remoteId: String?, lastSyncedAt: Long?, lastSyncError: String?) {
        store[localId]?.let { store[localId] = it.copy(syncStatus = syncStatus, remoteId = remoteId, lastSyncedAt = lastSyncedAt, lastSyncError = lastSyncError) }
        refresh()
    }

    override suspend fun getAllSettingsForUser(userId: String): List<UserSettingsEntity> =
        store.values.filter { it.userId == userId }

    override suspend fun getSettingsByRemoteId(remoteId: String): UserSettingsEntity? =
        store.values.firstOrNull { it.remoteId == remoteId }
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
}
