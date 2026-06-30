package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class SupabaseUserSettingsDataSource(
    private val client: SupabaseClient,
) : RemoteUserSettingsDataSource {

    override suspend fun fetchAll(): List<RemoteUserSettingsRow> =
        client.from(TABLE).select().decodeList<RemoteUserSettingsRow>()

    override suspend fun upsert(settings: RemoteUserSettingsUpsert): RemoteUserSettingsRow =
        client.from(TABLE).upsert(settings) {
            onConflict = COLUMN_USER_ID
            select()
        }.decodeSingle<RemoteUserSettingsRow>()

    override suspend fun update(remoteId: String, settings: RemoteUserSettingsUpdate) {
        client.from(TABLE).update(settings) {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    private companion object {
        const val TABLE = "user_settings"
        const val COLUMN_ID = "id"
        const val COLUMN_USER_ID = "user_id"
    }
}
