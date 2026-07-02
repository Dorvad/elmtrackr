package com.elmtrackr.app.data.remote

interface RemoteProfileDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteProfileRow>
    suspend fun update(userId: String, profile: RemoteProfileUpdate): RemoteProfileRow
}
