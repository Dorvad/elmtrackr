package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FakeShiftsRepository : ShiftsRepository {

    private val _shifts = MutableStateFlow<List<Shift>>(emptyList())

    fun setShifts(vararg shifts: Shift) { _shifts.value = shifts.toList() }

    val currentShifts: List<Shift> get() = _shifts.value

    override fun observeShifts(userId: String): Flow<List<Shift>> = _shifts

    override fun observeShiftsForProject(userId: String, projectId: String): Flow<List<Shift>> =
        _shifts.map { list -> list.filter { it.userId == userId && it.projectId == projectId } }

    override fun observeActiveShift(userId: String): Flow<Shift?> =
        _shifts.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun getShiftById(localId: String): Shift? =
        _shifts.value.firstOrNull { it.id == localId }

    override suspend fun clockIn(
        userId: String,
        compensationProfileId: String?,
        taskId: String?,
        taskNameSnapshot: String?,
        taskIconSnapshot: String?,
        taskHourlyRateSnapshot: Double?,
        compensationSource: CompensationSource,
        projectId: String?,
        projectNameSnapshot: String?,
    ): Shift {
        _shifts.value.firstOrNull { it.userId == userId && it.isActive }?.let { return it }

        val shift = Shift(
            id = "fake-${_shifts.value.size}",
            userId = userId,
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = null,
            compensationProfileId = compensationProfileId,
            taskId = taskId,
            taskNameSnapshot = taskNameSnapshot,
            taskIconSnapshot = taskIconSnapshot,
            taskHourlyRateSnapshot = taskHourlyRateSnapshot,
            compensationSource = compensationSource,
            projectId = projectId?.takeIf { compensationSource.isProjectTime },
            projectNameSnapshot = projectNameSnapshot?.takeIf { compensationSource.isProjectTime },
        )
        _shifts.value = _shifts.value + shift
        return shift
    }

    override suspend fun clockOut(
        localId: String,
        breakMinutes: Int,
        notes: String?,
        compensationSnapshot: CompensationSnapshot?,
    ): Shift {
        val shift = _shifts.value.first { it.id == localId }
        val updated = shift.copy(
            endTime = Instant.parse("2024-01-08T17:00:00Z"),
            breakMinutes = breakMinutes,
            notes = notes,
            compensationSnapshot = compensationSnapshot,
        )
        _shifts.value = _shifts.value.map { if (it.id == localId) updated else it }
        return updated
    }

    override suspend fun createManualShift(shift: Shift): Shift {
        _shifts.value = _shifts.value + shift
        return shift
    }

    override suspend fun updateShift(shift: Shift): Shift {
        _shifts.value = _shifts.value.map { if (it.id == shift.id) shift else it }
        return shift
    }

    override suspend fun deleteShift(localId: String) {
        _shifts.value = _shifts.value.filter { it.id != localId }
    }

    override fun observeShiftsByMonth(userId: String, year: Int, month: Int): Flow<List<Shift>> = _shifts

    override fun observeShiftsByMonthInZone(
        userId: String,
        year: Int,
        month: Int,
        zone: ZoneId,
    ): Flow<List<Shift>> = observeShiftsByMonth(userId, year, month)

    /**
     * Returns everything, like the other month queries here. The fake holds one flat
     * list and does no date filtering, so a wider window is trivially a superset —
     * production narrows to the pay-week range and the ViewModel filters back to the
     * month, which is asserted in [com.elmtrackr.app.data.repository.LocalShiftsRepositoryTest].
     */
    override fun observeShiftsForPayContext(
        userId: String,
        year: Int,
        month: Int,
        zone: ZoneId,
        weekStartDay: Int,
    ): Flow<List<Shift>> = _shifts

    override fun observeShiftsForDay(userId: String, zone: ZoneId, date: LocalDate): Flow<List<Shift>> =
        _shifts

    override fun observeRecentCompletedShifts(userId: String, limit: Int): Flow<List<Shift>> =
        _shifts.map { list ->
            list.filter { it.userId == userId && it.isCompleted }
                .sortedByDescending { it.endTime }
                .take(limit)
        }

    override suspend fun hasAnyShifts(userId: String): Boolean =
        _shifts.value.any { it.userId == userId }
}
