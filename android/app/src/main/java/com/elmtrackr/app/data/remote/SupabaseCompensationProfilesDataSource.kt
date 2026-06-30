package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class SupabaseCompensationProfilesDataSource(
    private val client: SupabaseClient,
) : RemoteCompensationProfileDataSource {

    override suspend fun fetchAll(): List<RemoteCompensationProfileRow> =
        client.from(TABLE).select().decodeList<RemoteCompensationProfileRow>()

    override suspend fun insert(profile: RemoteCompensationProfileInsert): RemoteCompensationProfileRow =
        client.from(TABLE).insert(profile) {
            select()
        }.decodeSingle<RemoteCompensationProfileRow>()

    override suspend fun update(remoteId: String, profile: RemoteCompensationProfileUpdate) {
        client.from(TABLE).update(profile) {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    override suspend fun delete(remoteId: String) {
        client.from(TABLE).delete {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    private companion object {
        const val TABLE = "compensation_profiles"
        const val COLUMN_ID = "id"
    }
}
