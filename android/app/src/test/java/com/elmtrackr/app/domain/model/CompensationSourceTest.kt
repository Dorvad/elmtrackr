package com.elmtrackr.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The discriminator that decides whether a shift's hours are paid as wages.
 *
 * Every fallback here resolves to [CompensationSource.EMPLOYEE] on purpose:
 * reading unknown data as project time would silently drop hours out of
 * someone's pay, while reading it as employee work at worst pays hours that were
 * already accounted for — visible, and correctable by the user.
 */
class CompensationSourceTest {

    @Test
    fun `a null column reads as employee work`() {
        assertEquals(CompensationSource.EMPLOYEE, CompensationSource.fromPersisted(null))
    }

    @Test
    fun `a blank value reads as employee work`() {
        assertEquals(CompensationSource.EMPLOYEE, CompensationSource.fromPersisted(""))
        assertEquals(CompensationSource.EMPLOYEE, CompensationSource.fromPersisted("   "))
    }

    @Test
    fun `an unrecognised value reads as employee work rather than throwing`() {
        assertEquals(CompensationSource.EMPLOYEE, CompensationSource.fromPersisted("CLIENT"))
        assertEquals(CompensationSource.EMPLOYEE, CompensationSource.fromPersisted("42"))
    }

    @Test
    fun `casing and separators are tolerated`() {
        assertEquals(CompensationSource.PROJECT, CompensationSource.fromPersisted("project"))
        assertEquals(CompensationSource.PROJECT, CompensationSource.fromPersisted(" Project "))
        assertEquals(CompensationSource.EMPLOYEE, CompensationSource.fromPersisted("employee"))
    }

    @Test
    fun `every value round-trips through its persisted form`() {
        CompensationSource.entries.forEach { source ->
            assertEquals(source, CompensationSource.fromPersisted(source.name))
            assertEquals(source, CompensationSource.fromPersisted(source.toWire()))
        }
    }

    @Test
    fun `the two contexts are mutually exclusive`() {
        assertTrue(CompensationSource.EMPLOYEE.isEmployeePaid)
        assertFalse(CompensationSource.EMPLOYEE.isProjectTime)
        assertTrue(CompensationSource.PROJECT.isProjectTime)
        assertFalse(CompensationSource.PROJECT.isEmployeePaid)
    }

    @Test
    fun `a shift defaults to employee work`() {
        val shift = Shift(
            id = "s1",
            userId = "u1",
            startTime = java.time.Instant.parse("2026-07-01T09:00:00Z"),
            endTime = java.time.Instant.parse("2026-07-01T17:00:00Z"),
        )
        assertEquals(CompensationSource.EMPLOYEE, shift.compensationSource)
        assertTrue(shift.isEmployeePaid)
        assertFalse(shift.isProjectTime)
    }
}
