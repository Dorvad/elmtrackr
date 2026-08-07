package com.elmtrackr.app.data.remote

interface RemoteTaskDataSource {
    /** See RemoteShiftDataSource.fetchUpdatedSince for what [offset] is for. */
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int, offset: Int = 0): List<RemoteTaskRow>

    suspend fun findById(remoteId: String): RemoteTaskRow?

    suspend fun insert(task: RemoteTaskInsert): RemoteTaskRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, task: RemoteTaskUpdate): RemoteTaskRow?
}
