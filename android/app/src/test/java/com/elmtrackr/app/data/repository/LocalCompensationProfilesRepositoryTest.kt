package com.elmtrackr.app.data.repository

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.fake.FakeCompensationProfileDao
import com.elmtrackr.app.fake.FakeSettingsRepository
import com.elmtrackr.app.fake.FakeSyncTrigger
import com.elmtrackr.app.domain.model.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class LocalCompensationProfilesRepositoryTest {

    private val dao = FakeCompensationProfileDao()
    private val settingsRepo = FakeSettingsRepository()
    private val syncTrigger = FakeSyncTrigger()
    private val repo = LocalCompensationProfilesRepository(dao, settingsRepo, syncTrigger)

    private fun settings(defaultProfileId: String? = null) = UserSettings(
        id = "s1",
        userId = "u1",
        onboardingCompleted = true,
        defaultCompensationProfileId = defaultProfileId,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun entity(
        localId: String,
        name: String = "Main job",
        isDefault: Boolean = false,
        createdAt: Long = 1_000L,
        remoteId: String? = null,
        rulesJson: String = "{}",
    ) = CompensationProfileEntity(
        localId = localId,
        remoteId = remoteId,
        userId = "u1",
        name = name,
        regionCode = "IL",
        currencyCode = "ILS",
        timezone = "Asia/Jerusalem",
        baseHourlyRate = 50.0,
        rulesJson = rulesJson,
        stackingPolicy = "highest_only",
        effectiveFrom = 0L,
        effectiveUntil = null,
        isDefault = isDefault,
        isArchived = false,
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = createdAt,
    )

    private suspend fun activeProfiles() =
        dao.getByUser("u1").filter { !it.isArchived && it.deletedAt == null }

    @Test
    fun `ensureMigrated archives exact duplicates and keeps the default`() = runTest {
        settingsRepo.setSettings(settings())
        dao.insert(entity("p1", isDefault = true, createdAt = 1_000L, remoteId = "r1"))
        dao.insert(entity("p2", createdAt = 2_000L, remoteId = "r2"))
        dao.insert(entity("p3", createdAt = 3_000L, remoteId = "r3"))

        val result = repo.ensureMigrated("u1")

        assertNotNull(result)
        assertEquals("p1", result!!.id)
        assertEquals(listOf("p1"), activeProfiles().map { it.localId })
        val archived = dao.getByUser("u1").filter { it.isArchived }
        assertEquals(setOf("p2", "p3"), archived.map { it.localId }.toSet())
        assertTrue(archived.all { it.deletedAt != null && it.syncStatus == SyncStatus.PENDING_UPDATE })
        assertTrue(syncTrigger.scheduleCount > 0)
    }

    @Test
    fun `ensureMigrated leaves distinct profiles untouched`() = runTest {
        settingsRepo.setSettings(settings())
        dao.insert(entity("p1", name = "Main job", isDefault = true))
        dao.insert(entity("p2", name = "Side gig", createdAt = 2_000L))
        dao.insert(entity("p3", name = "Main job", rulesJson = """{"weekendEnabled":true}""", createdAt = 3_000L))

        repo.ensureMigrated("u1")

        assertEquals(setOf("p1", "p2", "p3"), activeProfiles().map { it.localId }.toSet())
    }

    @Test
    fun `archiving a duplicate remaps the settings default profile id`() = runTest {
        settingsRepo.setSettings(settings(defaultProfileId = "p2"))
        dao.insert(entity("p1", isDefault = true, createdAt = 1_000L))
        dao.insert(entity("p2", createdAt = 2_000L))

        repo.ensureMigrated("u1")

        assertEquals(listOf("p1"), activeProfiles().map { it.localId })
        assertEquals("p1", settingsRepo.getSettings("u1")?.defaultCompensationProfileId)
    }

    @Test
    fun `migration profile id is stable per user`() = runTest {
        settingsRepo.setSettings(settings())

        val created = repo.ensureMigrated("u1")

        assertNotNull(created)
        val expected = UUID.nameUUIDFromBytes(
            "compensation-migration:u1".toByteArray(Charsets.UTF_8),
        ).toString()
        assertEquals(expected, created!!.id)
        assertTrue(created.isDefault)

        // A second call finds the existing profile instead of creating another.
        val again = repo.ensureMigrated("u1")
        assertEquals(created.id, again?.id)
        assertEquals(1, activeProfiles().size)
    }
}
