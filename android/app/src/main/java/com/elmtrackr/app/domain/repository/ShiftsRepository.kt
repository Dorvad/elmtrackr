package com.elmtrackr.app.domain.repository

import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.Shift
import kotlinx.coroutines.flow.Flow

interface ShiftsRepository {

    fun observeShifts(userId: String): Flow<List<Shift>>

    fun observeActiveShift(userId: String): Flow<Shift?>

    suspend fun getShiftById(localId: String): Shift?

    /** Creates a new shift with only a start time (clock-in). */
    suspend fun clockIn(userId: String, compensationProfileId: String? = null): Shift

    /** Sets end time on an active shift (clock-out). */
    suspend fun clockOut(
        localId: String,
        breakMinutes: Int = 0,
        notes: String? = null,
        compensationSnapshot: CompensationSnapshot? = null,
    ): Shift

    /** Creates a fully-specified manual shift entry (PENDING_CREATE sync status). */
    suspend fun createManualShift(shift: Shift): Shift

    suspend fun updateShift(shift: Shift): Shift

    suspend fun deleteShift(localId: String)

    fun observeShiftsByMonth(userId: String, year: Int, month: Int): Flow<List<Shift>>

    fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<Shift>>

    fun observePendingSyncShifts(userId: String): Flow<List<Shift>>
}

