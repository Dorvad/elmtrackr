package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseTasksDataSource(
    private val client: SupabaseClient,
) : RemoteTaskDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteTaskRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso ->
                filter { gte(COLUMN_UPDATED_AT, iso) }
            }
            // See SupabaseShiftsDataSource.fetchUpdatedSince.
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteTaskRow>()

    override suspend fun findById(remoteId: String): RemoteTaskRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteTaskRow>().firstOrNull()

    override suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow =
        client.from(TABLE).insert(task) {
            select()
        }.decodeSingle<RemoteTaskRow>()

    /** See SupabaseShiftsDataSource.update for why this is filtered and selected. */
    override suspend fun update(remoteId: String, task: RemoteTaskUpdate): RemoteTaskRow? =
        client.from(TABLE).update(task) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, task.clientUpdatedAt)
            }
        }.decodeList<RemoteTaskRow>().firstOrNull()

    private companion object {
        const val TABLE = "tasks"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
