package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseProfilesDataSource(
    private val client: SupabaseClient,
) : RemoteProfileDataSource {

    override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteProfileRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<RemoteProfileRow>()

    override suspend fun update(userId: String, profile: RemoteProfileUpdate): RemoteProfileRow? =
        client.from(TABLE).update(profile) {
            select()
            filter {
                eq(COLUMN_ID, userId)
                // Same edit-version guard as every other table. decodeList, not
                // decodeSingle: a rejected write returns no rows, and decodeSingle
                // would throw on that rather than reporting the conflict.
                lte(COLUMN_CLIENT_UPDATED_AT, profile.clientUpdatedAt)
            }
        }.decodeList<RemoteProfileRow>().firstOrNull()

    private companion object {
        const val TABLE = "profiles"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
