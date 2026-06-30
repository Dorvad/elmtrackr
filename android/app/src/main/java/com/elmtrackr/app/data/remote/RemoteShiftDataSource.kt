package com.elmtrackr.app.data.remote

interface RemoteShiftDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteShiftRow>
    suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow
    suspend fun update(remoteId: String, shift: RemoteShiftUpdate)
    suspend fun delete(remoteId: String)
}
