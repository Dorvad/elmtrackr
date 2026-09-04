package com.elmtrackr.app.data.remote

interface RemoteUserSettingsDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int): List<RemoteUserSettingsRow>
    suspend fun upsert(settings: RemoteUserSettingsUpsert): RemoteUserSettingsRow
    /**
     * Returns the updated row, or null when the write was rejected because the
     * server holds a newer edit. Mirrors [RemoteShiftDataSource.update]; before
     * this it returned Unit and every push was treated as applied.
     */
    suspend fun update(remoteId: String, settings: RemoteUserSettingsUpdate): RemoteUserSettingsRow?
}
