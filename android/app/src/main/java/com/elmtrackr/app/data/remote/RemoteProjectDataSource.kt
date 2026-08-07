package com.elmtrackr.app.data.remote

/**
 * The three Paid Projects tables, following the same contract as the other
 * remote sources: keyset paging with an offset, and updates that report a
 * conflict rather than overwriting a newer edit. See [RemoteShiftDataSource].
 */
interface RemoteProjectDataSource {
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int, offset: Int = 0): List<RemoteProjectRow>
    suspend fun findById(remoteId: String): RemoteProjectRow?
    suspend fun insert(project: RemoteProjectInsert): RemoteProjectRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(remoteId: String, project: RemoteProjectUpdate): RemoteProjectRow?
}

interface RemoteProjectBillingRecordDataSource {
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteProjectBillingRecordRow>

    suspend fun findById(remoteId: String): RemoteProjectBillingRecordRow?
    suspend fun insert(record: RemoteProjectBillingRecordInsert): RemoteProjectBillingRecordRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(
        remoteId: String,
        record: RemoteProjectBillingRecordUpdate,
    ): RemoteProjectBillingRecordRow?
}

interface RemoteProjectPaymentDataSource {
    suspend fun fetchUpdatedSince(
        sinceIso: String?,
        limit: Int,
        offset: Int = 0,
    ): List<RemoteProjectPaymentRow>

    suspend fun findById(remoteId: String): RemoteProjectPaymentRow?
    suspend fun insert(payment: RemoteProjectPaymentInsert): RemoteProjectPaymentRow

    /** @return null when a newer edit already exists remotely. */
    suspend fun update(
        remoteId: String,
        payment: RemoteProjectPaymentUpdate,
    ): RemoteProjectPaymentRow?
}
