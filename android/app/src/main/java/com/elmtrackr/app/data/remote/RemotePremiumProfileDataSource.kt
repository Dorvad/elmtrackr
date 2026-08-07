package com.elmtrackr.app.data.remote

interface RemotePremiumProfileDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemotePremiumProfileRow>

    suspend fun findById(remoteId: String): RemotePremiumProfileRow?

    suspend fun insert(profile: RemotePremiumProfileInsert): RemotePremiumProfileRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(
        remoteId: String,
        profile: RemotePremiumProfileUpdate,
    ): RemotePremiumProfileRow?
}
