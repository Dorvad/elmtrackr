package com.elmtrackr.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class InAppUpdatePolicyTest {

    private fun status(
        updateAvailable: Boolean = true,
        flexibleAllowed: Boolean = true,
        immediateAllowed: Boolean = true,
        priority: Int = 0,
        stalenessDays: Int? = null,
        updateInProgress: Boolean = false,
    ) = InAppUpdateStatus(
        updateAvailable = updateAvailable,
        flexibleAllowed = flexibleAllowed,
        immediateAllowed = immediateAllowed,
        priority = priority,
        stalenessDays = stalenessDays,
        updateInProgress = updateInProgress,
    )

    @Test
    fun `no update available yields NONE`() {
        assertEquals(
            InAppUpdateAction.NONE,
            InAppUpdatePolicy.decide(status(updateAvailable = false)),
        )
    }

    @Test
    fun `available low-priority update defaults to flexible`() {
        assertEquals(
            InAppUpdateAction.FLEXIBLE,
            InAppUpdatePolicy.decide(status(priority = 0, stalenessDays = 0)),
        )
    }

    @Test
    fun `high priority update uses immediate flow`() {
        assertEquals(
            InAppUpdateAction.IMMEDIATE,
            InAppUpdatePolicy.decide(status(priority = InAppUpdatePolicy.IMMEDIATE_PRIORITY)),
        )
    }

    @Test
    fun `priority just below threshold stays flexible`() {
        assertEquals(
            InAppUpdateAction.FLEXIBLE,
            InAppUpdatePolicy.decide(status(priority = InAppUpdatePolicy.IMMEDIATE_PRIORITY - 1)),
        )
    }

    @Test
    fun `very stale update escalates to immediate`() {
        assertEquals(
            InAppUpdateAction.IMMEDIATE,
            InAppUpdatePolicy.decide(
                status(priority = 0, stalenessDays = InAppUpdatePolicy.IMMEDIATE_STALENESS_DAYS),
            ),
        )
    }

    @Test
    fun `staleness just below threshold stays flexible`() {
        assertEquals(
            InAppUpdateAction.FLEXIBLE,
            InAppUpdatePolicy.decide(
                status(priority = 0, stalenessDays = InAppUpdatePolicy.IMMEDIATE_STALENESS_DAYS - 1),
            ),
        )
    }

    @Test
    fun `null staleness is treated as fresh`() {
        assertEquals(
            InAppUpdateAction.FLEXIBLE,
            InAppUpdatePolicy.decide(status(priority = 0, stalenessDays = null)),
        )
    }

    @Test
    fun `high priority falls back to flexible when immediate is disallowed`() {
        assertEquals(
            InAppUpdateAction.FLEXIBLE,
            InAppUpdatePolicy.decide(
                status(priority = 5, immediateAllowed = false, flexibleAllowed = true),
            ),
        )
    }

    @Test
    fun `falls back to immediate when only immediate is allowed`() {
        assertEquals(
            InAppUpdateAction.IMMEDIATE,
            InAppUpdatePolicy.decide(
                status(priority = 0, flexibleAllowed = false, immediateAllowed = true),
            ),
        )
    }

    @Test
    fun `no allowed update type yields NONE`() {
        assertEquals(
            InAppUpdateAction.NONE,
            InAppUpdatePolicy.decide(
                status(flexibleAllowed = false, immediateAllowed = false),
            ),
        )
    }

    @Test
    fun `interrupted developer-triggered update resumes immediate`() {
        assertEquals(
            InAppUpdateAction.IMMEDIATE,
            InAppUpdatePolicy.decide(
                status(updateAvailable = false, updateInProgress = true, immediateAllowed = true),
            ),
        )
    }

    @Test
    fun `interrupted update without immediate permission yields NONE`() {
        assertEquals(
            InAppUpdateAction.NONE,
            InAppUpdatePolicy.decide(
                status(updateInProgress = true, immediateAllowed = false),
            ),
        )
    }
}
