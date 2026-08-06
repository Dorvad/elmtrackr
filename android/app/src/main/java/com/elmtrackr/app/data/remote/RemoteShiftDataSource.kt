package com.elmtrackr.app.data.remote

interface RemoteShiftDataSource {
    /**
     * One page of rows with `updated_at >= sinceIso`, ordered by
     * `(updated_at, id)`.
     *
     * [offset] skips rows already applied at the boundary timestamp. The cursor
     * alone cannot express "I have seen 200 of the 500 rows that all share this
     * millisecond", and without it a block of same-timestamp rows larger than one
     * page can never be drained: every fetch returns the same first page.
     */
    suspend fun fetchUpdatedSince(sinceIso: String?, limit: Int, offset: Int = 0): List<RemoteShiftRow>

    suspend fun findById(remoteId: String): RemoteShiftRow?

    /** Live rows only — a tombstone must not block reusing its start time. */
    suspend fun findByUserAndStartTime(userId: String, startTimeIso: String): RemoteShiftRow?

    suspend fun insert(shift: RemoteShiftInsert): RemoteShiftRow

    /**
     * Applies [shift] only if the stored row's `client_updated_at` is no newer
     * than the one [shift] carries.
     *
     * @return the stored row, or null when a newer edit already exists remotely
     *   and this write was therefore rejected.
     */
    suspend fun update(remoteId: String, shift: RemoteShiftUpdate): RemoteShiftRow?
}
