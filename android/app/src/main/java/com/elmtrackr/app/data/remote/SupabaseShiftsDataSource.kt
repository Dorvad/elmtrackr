package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseShiftsDataSource(
    private val client: SupabaseClient,
) : RemoteShiftDataSource {

    override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteShiftRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<RemoteShiftRow>()

    override suspend fun findByUserAndStartTime(
        userId: String,
        startTimeIso: String,
    ): RemoteShiftRow? =
        client.from(TABLE).select {
            filter {
                eq(COLUMN_USER_ID, userId)
                eq(COLUMN_START_TIME, startTimeIso)
            }
            limit(1)
        }.decodeList<RemoteShiftRow>().firstOrNull()

    override suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow =
        client.from(TABLE).insert(shift) {
            select()
        }.decodeSingle<RemoteShiftRow>()

    override suspend fun update(remoteId: String, shift: RemoteShiftUpdate) {
        client.from(TABLE).update(shift) {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    override suspend fun delete(remoteId: String) {
        client.from(TABLE).delete {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    private companion object {
        const val TABLE = "shifts"
        const val COLUMN_ID = "id"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_START_TIME = "start_time"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
