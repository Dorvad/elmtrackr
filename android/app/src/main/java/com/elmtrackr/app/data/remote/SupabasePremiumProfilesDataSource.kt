package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabasePremiumProfilesDataSource(
    private val client: SupabaseClient,
) : RemotePremiumProfileDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemotePremiumProfileRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            // See SupabaseShiftsDataSource.fetchUpdatedSince.
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemotePremiumProfileRow>()

    override suspend fun findById(remoteId: String): RemotePremiumProfileRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemotePremiumProfileRow>().firstOrNull()

    override suspend fun insert(profile: RemotePremiumProfileInsert): RemotePremiumProfileRow =
        client.from(TABLE).insert(profile) { select() }.decodeSingle<RemotePremiumProfileRow>()

    /** See SupabaseShiftsDataSource.update for why this is filtered and selected. */
    override suspend fun update(
        remoteId: String,
        profile: RemotePremiumProfileUpdate,
    ): RemotePremiumProfileRow? =
        client.from(TABLE).update(profile) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, profile.clientUpdatedAt)
            }
        }.decodeList<RemotePremiumProfileRow>().firstOrNull()

    private companion object {
        const val TABLE = "premium_profiles"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
