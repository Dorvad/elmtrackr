package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseRefundClaimsDataSource(
    private val client: SupabaseClient,
) : RemoteRefundClaimDataSource {

    override suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int,
    ): List<RemoteRefundClaimRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            // See SupabaseShiftsDataSource.fetchUpdatedSince: the id makes the
            // ordering total so paging cannot skip a tied row.
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            order(COLUMN_ID, Order.ASCENDING)
            range(offset.toLong(), offset.toLong() + limit - 1)
        }.decodeList<RemoteRefundClaimRow>()

    override suspend fun findById(remoteId: String): RemoteRefundClaimRow? =
        client.from(TABLE).select {
            filter { eq(COLUMN_ID, remoteId) }
            limit(1)
        }.decodeList<RemoteRefundClaimRow>().firstOrNull()

    override suspend fun insert(claim: RemoteRefundClaimInsert): RemoteRefundClaimRow =
        client.from(TABLE).insert(claim) {
            select()
        }.decodeSingle<RemoteRefundClaimRow>()

    /** See SupabaseShiftsDataSource.update for why this is filtered and selected. */
    override suspend fun update(
        remoteId: String,
        claim: RemoteRefundClaimUpdate,
    ): RemoteRefundClaimRow? =
        client.from(TABLE).update(claim) {
            select()
            filter {
                eq(COLUMN_ID, remoteId)
                lte(COLUMN_CLIENT_UPDATED_AT, claim.clientUpdatedAt)
            }
        }.decodeList<RemoteRefundClaimRow>().firstOrNull()

    private companion object {
        const val TABLE = "refund_claims"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_CLIENT_UPDATED_AT = "client_updated_at"
    }
}
