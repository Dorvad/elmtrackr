package com.elmtrackr.app.data.remote

interface RemoteCompensationProfileDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteCompensationProfileRow>

    suspend fun findById(remoteId: String): RemoteCompensationProfileRow?

    suspend fun insert(profile: RemoteCompensationProfileInsert): RemoteCompensationProfileRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(
        remoteId: String,
        profile: RemoteCompensationProfileUpdate,
    ): RemoteCompensationProfileRow?
}
