package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

class SupabaseRefundClaimsDataSource(
    private val client: SupabaseClient,
) : RemoteRefundClaimDataSource {

    override suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteRefundClaimRow> =
        client.from(TABLE).select {
            sinceIso?.let { iso -> filter { gte(COLUMN_UPDATED_AT, iso) } }
            order(COLUMN_UPDATED_AT, Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList<RemoteRefundClaimRow>()

    override suspend fun insert(claim: RemoteRefundClaimInsert): RemoteRefundClaimRow =
        client.from(TABLE).insert(claim) {
            select()
        }.decodeSingle<RemoteRefundClaimRow>()

    override suspend fun update(remoteId: String, claim: RemoteRefundClaimUpdate) {
        client.from(TABLE).update(claim) {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    override suspend fun delete(remoteId: String) {
        client.from(TABLE).delete {
            filter { eq(COLUMN_ID, remoteId) }
        }
    }

    private companion object {
        const val TABLE = "refund_claims"
        const val COLUMN_ID = "id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
