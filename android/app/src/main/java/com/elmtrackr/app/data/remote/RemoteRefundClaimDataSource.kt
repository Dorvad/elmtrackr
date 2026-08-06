package com.elmtrackr.app.data.remote

interface RemoteRefundClaimDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteRefundClaimRow>

    suspend fun findById(remoteId: String): RemoteRefundClaimRow?

    suspend fun insert(claim: RemoteRefundClaimInsert): RemoteRefundClaimRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, claim: RemoteRefundClaimUpdate): RemoteRefundClaimRow?
}
