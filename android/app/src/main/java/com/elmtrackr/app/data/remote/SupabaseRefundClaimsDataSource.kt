package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class SupabaseRefundClaimsDataSource(
    private val client: SupabaseClient,
) : RemoteRefundClaimDataSource {

    override suspend fun fetchAll(): List<RemoteRefundClaimRow> =
        client.from(TABLE).select().decodeList<RemoteRefundClaimRow>()

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
    }
}
