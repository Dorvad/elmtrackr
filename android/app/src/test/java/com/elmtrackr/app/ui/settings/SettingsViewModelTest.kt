package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.LOCAL_USER_ID
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeSettingsRepository()

    private fun buildVm() = SettingsViewModel(repo)

    @Test
    fun `initial state is Loading`() {
        assertEquals(SettingsUiState.Loading, buildVm().uiState.value)
    }

    @Test
    fun `Ready state when settings exist`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        repo.setSettings(UserSettings("cfg", "u1", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals("cfg", ready!!.settings.id)
        job.cancel()
    }

    @Test
    fun `saveSettings updates repository`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        val initial = UserSettings("cfg", "u1", hourlyRate = null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        repo.setSettings(initial)
        advanceUntilIdle()

        vm.saveSettings(initial.copy(hourlyRate = 75.0))
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertEquals(75.0, ready?.settings?.hourlyRate)
        job.cancel()
    }

    @Test
    fun `ensureSettingsExist creates defaults when no settings`() = runTest {
        val vm = buildVm()
        val states = mutableListOf<SettingsUiState>()
        val job = launch { vm.uiState.collect { states.add(it) } }

        vm.ensureSettingsExist()
        advanceUntilIdle()

        val ready = states.filterIsInstance<SettingsUiState.Ready>().lastOrNull()
        assertNotNull(ready)
        assertEquals(LOCAL_USER_ID, ready!!.settings.userId)
        job.cancel()
    }
}
