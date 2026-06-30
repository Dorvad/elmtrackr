package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseUserSettingsDataSource(
    private val client: SupabaseClient,
) : RemoteUserSettingsDataSource {

    override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteUserSettingsRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<RemoteUserSettingsRow>()

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
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
