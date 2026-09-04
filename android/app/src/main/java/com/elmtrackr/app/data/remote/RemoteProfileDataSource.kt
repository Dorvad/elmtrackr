package com.elmtrackr.app.data.remote

interface RemoteProfileDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteProfileRow>
    /** Null when the server holds a newer edit than the one being written. */
    suspend fun update(userId: String, profile: RemoteProfileUpdate): RemoteProfileRow?
}
