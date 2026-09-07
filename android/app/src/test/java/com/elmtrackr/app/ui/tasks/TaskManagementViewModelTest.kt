package com.elmtrackr.app.ui.tasks

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.fake.FakeCompensationProfilesRepository
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeShiftsRepository
import com.elmtrackr.app.fake.FakeTasksRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * Creating and deleting a task, as the screen drives it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskManagementViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tasks = FakeTasksRepository()
    private val shifts = FakeShiftsRepository()
    private val profiles = FakeCompensationProfilesRepository()
    private val user = FakeCurrentUserProvider().apply { setUserId("u1") }

    private fun buildVm() = TaskManagementViewModel(tasks, shifts, user, profiles)

    private fun profile(id: String, isDefault: Boolean) = CompensationProfile(
        id = id,
        userId = "u1",
        name = id,
        regionCode = RegionCode.IL,
        currencyCode = CurrencyCode.ILS.name,
        timezone = "Asia/Jerusalem",
        baseHourlyRate = 50.0,
        rules = com.elmtrackr.app.domain.compensation.RegionPresets.forRegion(RegionCode.IL).rules,
        stackingPolicy = com.elmtrackr.app.domain.compensation.RegionPresets.forRegion(RegionCode.IL).stackingPolicy,
        isDefault = isDefault,
    )

    private fun task(id: String, name: String, profileId: String?) = Task(
        id = id,
        userId = "u1",
        name = name,
        icon = "🚗",
        color = null,
        hourlyRate = 60.0,
        compensationProfileId = profileId,
        isArchived = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.ready(
        vm: TaskManagementViewModel,
    ): TaskManagementUiState.Ready {
        val seen = mutableListOf<TaskManagementUiState>()
        val job = launch { vm.uiState.collect { seen.add(it) } }
        advanceUntilIdle()
        job.cancel()
        return seen.filterIsInstance<TaskManagementUiState.Ready>().last()
    }

    /**
     * A task belongs to one job, and the screen shows one job's tasks at a time.
     * So the name only has to be unique within that job — which is what the
     * editor checks against the list it is showing.
     *
     * The save path checked every task the account owns instead, so a name used
     * under another profile was rejected: the editor let the save through, the
     * sheet closed and discarded what had been typed, and a message came back
     * naming a clash with a task that is not on the screen and cannot be found
     * from it.
     */
    @Test
    fun `a name already used under another profile can still be created`() = runTest {
        profiles.setProfiles(profile("day-job", isDefault = true), profile("weekend", isDefault = false))
        tasks.setTasks(task("t1", "Driving", "day-job"))
        val vm = buildVm()
        vm.selectProfile("weekend")
        advanceUntilIdle()

        vm.saveTask(taskId = null, name = "Driving", icon = "🚗", color = null, hourlyRate = 80.0)
        advanceUntilIdle()

        val state = ready(vm)
        assertNull(state.errorMessage)
        assertTrue(
            "the task was not created",
            tasks.getActiveTasks("u1").any { it.name == "Driving" && it.compensationProfileId == "weekend" },
        )
    }

    /** Within one job the name still has to be unique. */
    @Test
    fun `a name already used in this profile is still rejected`() = runTest {
        profiles.setProfiles(profile("day-job", isDefault = true))
        tasks.setTasks(task("t1", "Driving", "day-job"))
        val vm = buildVm()
        advanceUntilIdle()

        vm.saveTask(taskId = null, name = "driving", icon = "🚗", color = null, hourlyRate = 80.0)
        advanceUntilIdle()

        assertEquals(1, tasks.getActiveTasks("u1").count { it.name.equals("driving", ignoreCase = true) })
        assertEquals(UiText.Res(R.string.tasks_error_name_exists), ready(vm).errorMessage)
    }

    /** Renaming a task to what it is already called is not a clash with itself. */
    @Test
    fun `a task can be saved without changing its name`() = runTest {
        profiles.setProfiles(profile("day-job", isDefault = true))
        tasks.setTasks(task("t1", "Driving", "day-job"))
        val vm = buildVm()
        advanceUntilIdle()

        vm.saveTask(taskId = "t1", name = "Driving", icon = "🚕", color = null, hourlyRate = 90.0)
        advanceUntilIdle()

        assertNull(ready(vm).errorMessage)
        assertEquals("🚕", tasks.getTaskById("u1", "t1")!!.icon)
    }

    @Test
    fun `deleting a task removes it from the list`() = runTest {
        profiles.setProfiles(profile("day-job", isDefault = true))
        tasks.setTasks(task("t1", "Driving", "day-job"))
        val vm = buildVm()
        advanceUntilIdle()

        vm.deleteTask("t1")
        advanceUntilIdle()

        assertTrue(ready(vm).tasks.none { it.id == "t1" })
    }
}
