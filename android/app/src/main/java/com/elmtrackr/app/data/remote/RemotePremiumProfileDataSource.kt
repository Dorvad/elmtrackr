package com.elmtrackr.app.data.remote

interface RemotePremiumProfileDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemotePremiumProfileRow>
    suspend fun insert(profile: RemotePremiumProfileInsert): RemotePremiumProfileRow
    suspend fun update(remoteId: String, profile: RemotePremiumProfileUpdate)
    suspend fun delete(remoteId: String)
}
