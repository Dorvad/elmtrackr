package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeShiftsRepository : ShiftsRepository {

    private val _shifts = MutableStateFlow<List<Shift>>(emptyList())

    fun setShifts(vararg shifts: Shift) { _shifts.value = shifts.toList() }

    override fun observeShifts(userId: String): Flow<List<Shift>> = _shifts

    override fun observeActiveShift(userId: String): Flow<Shift?> =
        _shifts.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun getShiftById(localId: String): Shift? =
        _shifts.value.firstOrNull { it.id == localId }

    override suspend fun clockIn(userId: String): Shift {
        val shift = Shift(
            id = "fake-${_shifts.value.size}",
            userId = userId,
            startTime = Instant.parse("2024-01-08T09:00:00Z"),
            endTime = null,
        )
        _shifts.value = _shifts.value + shift
        return shift
    }

    override suspend fun clockOut(localId: String, breakMinutes: Int, notes: String?): Shift {
        val shift = _shifts.value.first { it.id == localId }
        val updated = shift.copy(
            endTime = Instant.parse("2024-01-08T17:00:00Z"),
            breakMinutes = breakMinutes,
            notes = notes,
        )
        _shifts.value = _shifts.value.map { if (it.id == localId) updated else it }
        return updated
    }

    override suspend fun updateShift(shift: Shift): Shift {
        _shifts.value = _shifts.value.map { if (it.id == shift.id) shift else it }
        return shift
    }

    override suspend fun deleteShift(localId: String) {
        _shifts.value = _shifts.value.filter { it.id != localId }
    }

    override fun observeShiftsByMonth(userId: String, year: Int, month: Int): Flow<List<Shift>> = _shifts

    override fun observePendingSyncShifts(): Flow<List<Shift>> = flowOf(emptyList())
}
