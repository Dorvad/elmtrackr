package com.elmtrackr.app.review

import com.elmtrackr.app.fake.FakeAppPreferencesStore
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeReviewPromptStore
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.domain.model.Shift
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewPromptCoordinatorTest {

    private val shiftsRepo = FakeShiftsRepository()
    private val userProvider = FakeCurrentUserProvider(initialUserId = "u1")
    private val appPrefs = FakeAppPreferencesStore(onboardingCompleted = true)

    /** Store state that passes every policy rule via the export milestone. */
    private fun eligibleStore() = FakeReviewPromptStore(
        StoredReviewPromptState(
            reportExportedAt = System.currentTimeMillis() - 1_000,
            usageDayCount = ReviewPromptPolicy.MIN_USAGE_DAYS,
            firstUsedAt = System.currentTimeMillis() - ReviewPromptPolicy.MIN_APP_AGE_MS - 1_000,
        ),
    )

    @Test
    fun `eligible milestone emits a review request and records the attempt`() = runTest {
        val store = eligibleStore()
        val coordinator = ReviewPromptCoordinator(store, shiftsRepo, userProvider, appPrefs, this)
        val requests = mutableListOf<Unit>()
        val job = launch { coordinator.reviewRequests.collect { requests.add(it) } }
        advanceUntilIdle()

        coordinator.noteShiftCompleted()
        advanceUntilIdle()

        assertEquals(1, requests.size)
        assertNotNull(store.state.lastAttemptAt)
        job.cancel()
    }

    @Test
    fun `second milestone inside the cooldown does not emit again`() = runTest {
        val store = eligibleStore()
        val coordinator = ReviewPromptCoordinator(store, shiftsRepo, userProvider, appPrefs, this)
        val requests = mutableListOf<Unit>()
        val job = launch { coordinator.reviewRequests.collect { requests.add(it) } }
        advanceUntilIdle()

        coordinator.noteShiftCompleted()
        advanceUntilIdle()
        coordinator.noteReportExported()
        advanceUntilIdle()

        assertEquals(1, requests.size)
        job.cancel()
    }

    @Test
    fun `no request while a shift is active`() = runTest {
        shiftsRepo.setShifts(
            Shift("active", "u1", startTime = Instant.parse("2024-01-08T09:00:00Z"), endTime = null),
        )
        val store = eligibleStore()
        val coordinator = ReviewPromptCoordinator(store, shiftsRepo, userProvider, appPrefs, this)
        val requests = mutableListOf<Unit>()
        val job = launch { coordinator.reviewRequests.collect { requests.add(it) } }
        advanceUntilIdle()

        coordinator.noteReportExported()
        advanceUntilIdle()

        assertEquals(0, requests.size)
        assertNull(store.state.lastAttemptAt)
        job.cancel()
    }

    @Test
    fun `no request during onboarding`() = runTest {
        val store = eligibleStore()
        val coordinator = ReviewPromptCoordinator(
            store, shiftsRepo, userProvider, FakeAppPreferencesStore(onboardingCompleted = false), this,
        )
        val requests = mutableListOf<Unit>()
        val job = launch { coordinator.reviewRequests.collect { requests.add(it) } }
        advanceUntilIdle()

        coordinator.noteShiftCompleted()
        advanceUntilIdle()

        assertEquals(0, requests.size)
        assertNull(store.state.lastAttemptAt)
        job.cancel()
    }

    @Test
    fun `valid shift count comes from the database not the store`() = runTest {
        val start = Instant.parse("2024-01-08T09:00:00Z")
        shiftsRepo.setShifts(
            *(1..ReviewPromptPolicy.STRONG_MILESTONE_SHIFTS).map { i ->
                Shift("s$i", "u1", startTime = start, endTime = start.plusSeconds(8 * 3600))
            }.toTypedArray(),
        )
        val store = FakeReviewPromptStore(
            StoredReviewPromptState(
                usageDayCount = ReviewPromptPolicy.MIN_USAGE_DAYS,
                firstUsedAt = System.currentTimeMillis() - ReviewPromptPolicy.MIN_APP_AGE_MS - 1_000,
            ),
        )
        val coordinator = ReviewPromptCoordinator(store, shiftsRepo, userProvider, appPrefs, this)
        val requests = mutableListOf<Unit>()
        val job = launch { coordinator.reviewRequests.collect { requests.add(it) } }
        advanceUntilIdle()

        coordinator.noteShiftCompleted()
        advanceUntilIdle()

        assertEquals(1, requests.size)
        job.cancel()
    }

    @Test
    fun `inspectEligibility never mutates state`() = runTest {
        val store = eligibleStore()
        val before = store.state
        val coordinator = ReviewPromptCoordinator(store, shiftsRepo, userProvider, appPrefs, this)

        val inspection = coordinator.inspectEligibility()

        assertEquals(true, inspection.verdict.eligible)
        assertEquals(before, store.state)
    }

    @Test
    fun `discouraging events and clock-ins are recorded for the quiet periods`() = runTest {
        val store = eligibleStore()
        val coordinator = ReviewPromptCoordinator(store, shiftsRepo, userProvider, appPrefs, this)

        coordinator.noteDiscouragingEvent()
        coordinator.noteClockIn()
        advanceUntilIdle()

        assertNotNull(store.state.lastDiscouragingEventAt)
        assertNotNull(store.state.lastClockInAt)

        val requests = mutableListOf<Unit>()
        val job = launch { coordinator.reviewRequests.collect { requests.add(it) } }
        advanceUntilIdle()
        coordinator.noteShiftCompleted()
        advanceUntilIdle()

        // Both quiet periods are open, so the milestone must stay silent.
        assertEquals(0, requests.size)
        job.cancel()
    }

    @Test
    fun `usage days accumulate once per calendar day`() = runTest {
        val store = FakeReviewPromptStore()
        store.recordUsage(nowMs = 1_000, epochDay = 10)
        store.recordUsage(nowMs = 2_000, epochDay = 10)
        store.recordUsage(nowMs = 3_000, epochDay = 11)

        assertEquals(2, store.state.usageDayCount)
        assertEquals(1_000L, store.state.firstUsedAt)
    }
}
