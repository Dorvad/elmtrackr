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

    override suspend fun update(
        remoteId: String,
        settings: RemoteUserSettingsUpdate,
    ): RemoteUserSettingsRow? =
        client.from(TABLE).update(settings) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                // The edit-version guard, exactly as SupabaseShiftsDataSource does
                // it: a write carrying an older edit than the stored one matches no
                // row, decodes to null, and the caller adopts the remote copy
                // instead of overwriting it.
                lte(COLUMN_CLIENT_UPDATED_AT, settings.clientUpdatedAt)
            }
        }.decodeList<RemoteUserSettingsRow>().firstOrNull()

    private companion object {
        const val TABLE = "user_settings"
        const val COLUMN_ID = "id"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
