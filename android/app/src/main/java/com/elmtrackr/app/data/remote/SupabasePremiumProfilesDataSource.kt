package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabasePremiumProfilesDataSource(
    private val client: SupabaseClient,
) : RemotePremiumProfileDataSource {

    override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemotePremiumProfileRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<RemotePremiumProfileRow>()

    override suspend fun insert(profile: RemotePremiumProfileInsert): RemotePremiumProfileRow =
        client.from(TABLE).insert(profile) { select() }.decodeSingle<RemotePremiumProfileRow>()

    override suspend fun update(remoteId: String, profile: RemotePremiumProfileUpdate) {
        client.from(TABLE).update(profile) { filter { eq(COLUMN_ID, remoteId) } }
    }

    override suspend fun delete(remoteId: String) {
        client.from(TABLE).delete { filter { eq(COLUMN_ID, remoteId) } }
    }

    private companion object {
        const val TABLE = "premium_profiles"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
