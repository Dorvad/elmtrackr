package com.elmtrackr.app.data.remote

/**
 * Remote access for the five leave tables.
 *
 * One interface per table, each the same four operations the already-synced
 * tables use: a paged incremental fetch, a single read for conflict adoption, an
 * insert, and an update that returns null when a newer edit already exists
 * remotely. That last one is the write guard — see the `client_updated_at` filter
 * in the Supabase implementations.
 */

interface RemoteWorkplaceDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteWorkplaceRow>

    suspend fun findById(remoteId: String): RemoteWorkplaceRow?

    suspend fun insert(row: RemoteWorkplaceInsert): RemoteWorkplaceRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, row: RemoteWorkplaceUpdate): RemoteWorkplaceRow?
}

interface RemoteLeavePolicyDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteLeavePolicyRow>

    suspend fun findById(remoteId: String): RemoteLeavePolicyRow?

    suspend fun insert(row: RemoteLeavePolicyInsert): RemoteLeavePolicyRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, row: RemoteLeavePolicyUpdate): RemoteLeavePolicyRow?
}

interface RemoteAbsenceEventDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteAbsenceEventRow>

    suspend fun findById(remoteId: String): RemoteAbsenceEventRow?

    suspend fun insert(row: RemoteAbsenceEventInsert): RemoteAbsenceEventRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, row: RemoteAbsenceEventUpdate): RemoteAbsenceEventRow?
}

interface RemoteAbsenceAllocationDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteAbsenceAllocationRow>

    suspend fun findById(remoteId: String): RemoteAbsenceAllocationRow?

    suspend fun insert(row: RemoteAbsenceAllocationInsert): RemoteAbsenceAllocationRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, row: RemoteAbsenceAllocationUpdate): RemoteAbsenceAllocationRow?
}

interface RemoteLeaveBalanceSnapshotDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteLeaveBalanceSnapshotRow>

    suspend fun findById(remoteId: String): RemoteLeaveBalanceSnapshotRow?

    suspend fun insert(row: RemoteLeaveBalanceSnapshotInsert): RemoteLeaveBalanceSnapshotRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, row: RemoteLeaveBalanceSnapshotUpdate): RemoteLeaveBalanceSnapshotRow?
}
