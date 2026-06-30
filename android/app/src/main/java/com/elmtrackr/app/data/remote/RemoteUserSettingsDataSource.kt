package com.elmtrackr.app.data.remote

interface RemoteUserSettingsDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteUserSettingsRow>
    suspend fun upsert(settings: RemoteUserSettingsUpsert): RemoteUserSettingsRow
    suspend fun update(remoteId: String, settings: RemoteUserSettingsUpdate)
}
