package com.elmtrackr.app.data.remote

interface RemoteTaskDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteTaskRow>
    suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow
    suspend fun update(remoteId: String, task: RemoteTaskUpdate)
    suspend fun delete(remoteId: String)
}
