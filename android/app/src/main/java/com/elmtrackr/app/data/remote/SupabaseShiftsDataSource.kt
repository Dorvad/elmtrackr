package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseShiftsDataSource(
    private val client: SupabaseClient,
) : RemoteShiftDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteShiftRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            // Ordering by updated_at alone is not a total order, and Postgres is
            // free to return tied rows in a different order on every request.
            // Paging through a non-deterministic order silently skips rows: a row
            // that sorted into page 1 the first time can sort into page 2 the next,
            // after the client has already moved past it. The id breaks every tie.
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteShiftRow>()

    override suspend fun findById(remoteId: String): RemoteShiftRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteShiftRow>().firstOrNull()

    override suspend fun findByUserAndStartTime(
        userId: String,
        startTimeIso: String,
    ): RemoteShiftRow? =
        client.from(TABLE).select {
            filter {
                eq(COLUMN_USER_ID, userId)
                eq(COLUMN_START_TIME, startTimeIso)
                exact(COLUMN_DELETED_AT, null)
            }
            limit(1)
        }.decodeList<RemoteShiftRow>().firstOrNull()

    override suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow =
        client.from(TABLE).insert(shift) {
            select()
        }.decodeSingle<RemoteShiftRow>()

    /**
     * The `lte` on `client_updated_at` is the edit-version guard: it makes the
     * write conditional on the server not already holding a newer edit. Asking
     * for the row back is what turns that into a signal — a rejected write is a
     * successful request that simply matched nothing, so without `select()` it is
     * indistinguishable from a write that landed.
     */
    override suspend fun update(remoteId: String, shift: RemoteShiftUpdate): RemoteShiftRow? =
        client.from(TABLE).update(shift) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, shift.clientUpdatedAt)
            }
        }.decodeList<RemoteShiftRow>().firstOrNull()

    private companion object {
        const val TABLE = "shifts"
        const val COLUMN_ID = "id"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_START_TIME = "start_time"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_DELETED_AT = "deleted_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
