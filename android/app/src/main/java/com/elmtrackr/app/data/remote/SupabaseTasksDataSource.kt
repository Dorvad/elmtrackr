package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseTasksDataSource(
    private val client: SupabaseClient,
) : RemoteTaskDataSource {

    override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteTaskRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso ->
                filter { gte(COLUMN_UPDATED_AT, iso) }
            }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<RemoteTaskRow>()

    override suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow =
        client.from(TABLE).insert(task) {
            select()
        }.decodeSingle<RemoteTaskRow>()

    override suspend fun update(remoteId: String, task: RemoteTaskUpdate) {
        client.from(TABLE).update(task) {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    override suspend fun delete(remoteId: String) {
        client.from(TABLE).delete {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    private companion object {
        const val TABLE = "tasks"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
