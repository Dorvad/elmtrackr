package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class SupabaseShiftsDataSource(
    private val client: SupabaseClient,
) : RemoteShiftDataSource {

    override suspend fun fetchAll(): List<RemoteShiftRow> =
        client.from(TABLE).select().decodeList<RemoteShiftRow>()

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
    }
}
