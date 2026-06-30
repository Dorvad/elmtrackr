package com.elmtrackr.app.data.remote

interface RemoteCompensationProfileDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteCompensationProfileRow>
    suspend fun insert(profile: RemoteCompensationProfileInsert): RemoteCompensationProfileRow
    suspend fun update(remoteId: String, profile: RemoteCompensationProfileUpdate)
    suspend fun delete(remoteId: String)
}
