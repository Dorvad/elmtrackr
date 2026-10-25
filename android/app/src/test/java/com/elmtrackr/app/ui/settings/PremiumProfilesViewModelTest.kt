package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakePremiumProfilesRepository
import com.elmtrackr.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumProfilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakePremiumProfilesRepository()

    private val existingDefault = PremiumProfile(
        id = "default", userId = "local-user", name = "Premium", multiplier = 1.5,
        premiumType = PremiumType.HIGHEST_ONLY, isDefault = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun vm() = PremiumProfilesViewModel(repo, FakeCurrentUserProvider())

    @Test
    fun `a new profile does not take the default from the existing one`() = runTest {
        repo.setProfiles(existingDefault)
        val vm = vm()
        vm.ensureLoaded()
        advanceUntilIdle()

        vm.openCreate()
        vm.updateName("Night")
        vm.updateMultiplier("1.25")
        vm.saveEditor()
        advanceUntilIdle()

        val saved = repo.getProfiles("local-user")
        assertTrue(saved.single { it.id == "default" }.isDefault)
        assertFalse(saved.single { it.name == "Night" }.isDefault)
    }

    @Test
    fun `a comma typed in the multiplier is kept`() = runTest {
        repo.setProfiles(existingDefault)
        val vm = vm()
        vm.ensureLoaded()
        advanceUntilIdle()

        vm.openCreate()
        vm.updateName("Weekend")
        vm.updateMultiplier("1,75")
        vm.saveEditor()
        advanceUntilIdle()

        assertEquals(1.75, repo.getProfiles("local-user").single { it.name == "Weekend" }.multiplier, 0.0)
    }
}
