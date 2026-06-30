package com.elmtrackr.app.data.remote

interface RemoteShiftDataSource {
    suspend fun fetchAll(): List<RemoteShiftRow>
    suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow
    suspend fun update(remoteId: String, shift: RemoteShiftUpdate)
    suspend fun delete(remoteId: String)
}
