package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

/**
 * PostgREST implementations for the leave tables.
 *
 * Each one mirrors [SupabasePremiumProfilesDataSource] exactly: ordered by
 * `updated_at` then `id` so paging is stable across a page boundary where several
 * rows share a timestamp, and an update filtered on `client_updated_at` so a row
 * edited more recently on another device is left alone and adopted instead.
 */

class SupabaseWorkplaceDataSource(
    private val client: SupabaseClient,
) : RemoteWorkplaceDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteWorkplaceRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteWorkplaceRow>()

    override suspend fun findById(remoteId: String): RemoteWorkplaceRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteWorkplaceRow>().firstOrNull()

    override suspend fun insert(row: RemoteWorkplaceInsert): RemoteWorkplaceRow =
        client.from(TABLE).insert(row) { select() }.decodeSingle<RemoteWorkplaceRow>()

    override suspend fun update(remoteId: String, row: RemoteWorkplaceUpdate): RemoteWorkplaceRow? =
        client.from(TABLE).update(row) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, row.clientUpdatedAt)
            }
        }.decodeList<RemoteWorkplaceRow>().firstOrNull()

    private companion object {
        const val TABLE = "workplaces"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}

class SupabaseLeavePolicyDataSource(
    private val client: SupabaseClient,
) : RemoteLeavePolicyDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteLeavePolicyRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteLeavePolicyRow>()

    override suspend fun findById(remoteId: String): RemoteLeavePolicyRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteLeavePolicyRow>().firstOrNull()

    override suspend fun insert(row: RemoteLeavePolicyInsert): RemoteLeavePolicyRow =
        client.from(TABLE).insert(row) { select() }.decodeSingle<RemoteLeavePolicyRow>()

    override suspend fun update(remoteId: String, row: RemoteLeavePolicyUpdate): RemoteLeavePolicyRow? =
        client.from(TABLE).update(row) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, row.clientUpdatedAt)
            }
        }.decodeList<RemoteLeavePolicyRow>().firstOrNull()

    private companion object {
        const val TABLE = "leave_policies"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}

class SupabaseAbsenceEventDataSource(
    private val client: SupabaseClient,
) : RemoteAbsenceEventDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteAbsenceEventRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteAbsenceEventRow>()

    override suspend fun findById(remoteId: String): RemoteAbsenceEventRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteAbsenceEventRow>().firstOrNull()

    override suspend fun insert(row: RemoteAbsenceEventInsert): RemoteAbsenceEventRow =
        client.from(TABLE).insert(row) { select() }.decodeSingle<RemoteAbsenceEventRow>()

    override suspend fun update(remoteId: String, row: RemoteAbsenceEventUpdate): RemoteAbsenceEventRow? =
        client.from(TABLE).update(row) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, row.clientUpdatedAt)
            }
        }.decodeList<RemoteAbsenceEventRow>().firstOrNull()

    private companion object {
        const val TABLE = "absence_events"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}

class SupabaseAbsenceAllocationDataSource(
    private val client: SupabaseClient,
) : RemoteAbsenceAllocationDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteAbsenceAllocationRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteAbsenceAllocationRow>()

    override suspend fun findById(remoteId: String): RemoteAbsenceAllocationRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteAbsenceAllocationRow>().firstOrNull()

    override suspend fun insert(row: RemoteAbsenceAllocationInsert): RemoteAbsenceAllocationRow =
        client.from(TABLE).insert(row) { select() }.decodeSingle<RemoteAbsenceAllocationRow>()

    override suspend fun update(remoteId: String, row: RemoteAbsenceAllocationUpdate): RemoteAbsenceAllocationRow? =
        client.from(TABLE).update(row) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, row.clientUpdatedAt)
            }
        }.decodeList<RemoteAbsenceAllocationRow>().firstOrNull()

    private companion object {
        const val TABLE = "absence_allocations"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}

class SupabaseLeaveBalanceSnapshotDataSource(
    private val client: SupabaseClient,
) : RemoteLeaveBalanceSnapshotDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteLeaveBalanceSnapshotRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteLeaveBalanceSnapshotRow>()

    override suspend fun findById(remoteId: String): RemoteLeaveBalanceSnapshotRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteLeaveBalanceSnapshotRow>().firstOrNull()

    override suspend fun insert(row: RemoteLeaveBalanceSnapshotInsert): RemoteLeaveBalanceSnapshotRow =
        client.from(TABLE).insert(row) { select() }.decodeSingle<RemoteLeaveBalanceSnapshotRow>()

    override suspend fun update(remoteId: String, row: RemoteLeaveBalanceSnapshotUpdate): RemoteLeaveBalanceSnapshotRow? =
        client.from(TABLE).update(row) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, row.clientUpdatedAt)
            }
        }.decodeList<RemoteLeaveBalanceSnapshotRow>().firstOrNull()

    private companion object {
        const val TABLE = "leave_balance_snapshots"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
