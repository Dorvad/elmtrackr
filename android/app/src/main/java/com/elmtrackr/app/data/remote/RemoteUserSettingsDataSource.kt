package com.elmtrackr.app.data.remote

interface RemoteUserSettingsDataSource {
    suspend fun fetchAll(): List<RemoteUserSettingsRow>
    suspend fun upsert(settings: RemoteUserSettingsUpsert): RemoteUserSettingsRow
    suspend fun update(remoteId: String, settings: RemoteUserSettingsUpdate)
}
