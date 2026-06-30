package com.elmtrackr.app.data.remote

interface RemoteRefundClaimDataSource {
    suspend fun fetchAll(): List<RemoteRefundClaimRow>
    suspend fun insert(claim: RemoteRefundClaimInsert): RemoteRefundClaimRow
    suspend fun update(remoteId: String, claim: RemoteRefundClaimUpdate)
    suspend fun delete(remoteId: String)
}
