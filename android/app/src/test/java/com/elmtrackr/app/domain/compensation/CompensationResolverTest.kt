package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CompensationResolverTest {

    private val settings = UserSettings(
        id = "s1",
        userId = "u1",
        timezone = "UTC",
        defaultCompensationProfileId = "p1",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun profile(rules: CompensationRules) = CompensationProfile(
        id = "p1",
        userId = "u1",
        name = "Main job",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "UTC",
        baseHourlyRate = 50.0,
        rules = rules,
        stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        isDefault = true,
    )

    private fun snapshotWith(rules: CompensationRules) = CompensationSnapshot(
        profileId = "p1",
        profileName = "Main job",
        regionCode = RegionCode.IL,
        currencyCode = "ILS",
        timezone = "UTC",
        baseHourlyRate = 50.0,
        rules = rules,
        stackingPolicy = StackingPolicy.HIGHEST_ONLY,
        calculatedAt = Instant.EPOCH,
    )

    // 2024-06-07 is a Friday (JS day 5); 2024-06-08 a Saturday (JS day 6).
    private fun shift(
        start: String,
        end: String,
        snapshot: CompensationSnapshot? = null,
        forceRegularRate: Boolean = false,
    ) = Shift(
        id = "sh1",
        userId = "u1",
        startTime = Instant.parse(start),
        endTime = Instant.parse(end),
        compensationProfileId = "p1",
        compensationSnapshot = snapshot,
        forceRegularRate = forceRegularRate,
    )

    @Test
    fun `live profile rules win over a stale snapshot without editing the shift`() {
        val staleRules = CompensationRules(weekendEnabled = true, weekendDays = listOf(5, 6))
        val currentRules = CompensationRules(weekendEnabled = true, weekendDays = listOf(6))
        val friday = shift(
            "2024-06-07T09:00:00Z", "2024-06-07T17:00:00Z",
            snapshot = snapshotWith(staleRules),
        )

        assertFalse(
            CompensationResolver.isWeekendShift(friday, settings, listOf(profile(currentRules))),
        )
        assertEquals(
            listOf(6),
            CompensationResolver
                .resolveShiftCompensation(friday, settings, listOf(profile(currentRules)))
                .rules.weekendDays,
        )
    }

    @Test
    fun `snapshot still applies when the profile is gone`() {
        val staleRules = CompensationRules(weekendEnabled = true, weekendDays = listOf(5, 6))
        val friday = shift(
            "2024-06-07T09:00:00Z", "2024-06-07T17:00:00Z",
            snapshot = snapshotWith(staleRules),
        )

        val resolved = CompensationResolver.resolveShiftCompensation(friday, settings, emptyList())

        assertEquals(listOf(5, 6), resolved.rules.weekendDays)
    }

    @Test
    fun `force regular rate overrides a configured weekend day`() {
        val rules = CompensationRules(weekendEnabled = true, weekendDays = listOf(6))
        val saturday = shift(
            "2024-06-08T09:00:00Z", "2024-06-08T17:00:00Z",
            forceRegularRate = true,
        )

        assertFalse(
            CompensationResolver.isWeekendShift(saturday, settings, listOf(profile(rules))),
        )
        val resolved = CompensationResolver.resolveShiftCompensation(saturday, settings, listOf(profile(rules)))
        assertFalse(resolved.rules.weekendEnabled)
        assertFalse(resolved.rules.holidayEnabled)
    }

    @Test
    fun `saturday stays a weekend day without the override`() {
        val rules = CompensationRules(weekendEnabled = true, weekendDays = listOf(6))
        val saturday = shift("2024-06-08T09:00:00Z", "2024-06-08T17:00:00Z")

        assertTrue(
            CompensationResolver.isWeekendShift(saturday, settings, listOf(profile(rules))),
        )
    }

    // ── Mirroring a profile back into legacy settings ─────────────────────────

    /**
     * The compensation profile is the authority on pay, so a profile with no base
     * rate must leave settings with no rate.
     *
     * `UserSettings.apply` merges with `?:`, which cannot distinguish "not
     * specified" from "explicitly none". Clearing the profile's rate therefore used
     * to leave the old number in `settings.hourlyRate`, where it went on seeding the
     * Settings→Pay field and the four gates that decide whether to show pay at all —
     * so the user saw a rate they had just removed.
     */
    @Test
    fun `clearing the profile base rate clears the legacy settings rate`() {
        val settings = com.elmtrackr.app.domain.model.UserSettings(
            id = "s1",
            userId = "u1",
            hourlyRate = 50.0,
        )
        val cleared = profile(CompensationRules()).copy(baseHourlyRate = null)

        val result = settings.apply(CompensationResolver.profileToLegacySettingsUpdates(cleared))

        assertEquals(null, result.hourlyRate)
    }

    @Test
    fun `mirroring a profile with a rate still writes that rate`() {
        val settings = com.elmtrackr.app.domain.model.UserSettings(
            id = "s1",
            userId = "u1",
            hourlyRate = 50.0,
        )
        val withRate = profile(CompensationRules()).copy(baseHourlyRate = 72.5)

        val result = settings.apply(CompensationResolver.profileToLegacySettingsUpdates(withRate))

        assertEquals(72.5, result.hourlyRate)
    }

    /**
     * Every other caller of Updates relies on `?:` meaning "leave alone". Onboarding
     * and the settings save both pass partial updates, so an omitted rate must not
     * start wiping the stored one.
     */
    @Test
    fun `an update that omits the rate leaves it alone`() {
        val settings = com.elmtrackr.app.domain.model.UserSettings(
            id = "s1",
            userId = "u1",
            hourlyRate = 50.0,
        )

        val result = settings.apply(
            com.elmtrackr.app.domain.model.UserSettings.Updates(timezone = "Asia/Jerusalem"),
        )

        assertEquals(50.0, result.hourlyRate)
        assertEquals("Asia/Jerusalem", result.timezone)
    }

    /** The currency enum must stay in step with the string it mirrors. */
    @Test
    fun `mirroring a profile updates both currency representations`() {
        val settings = com.elmtrackr.app.domain.model.UserSettings(id = "s1", userId = "u1")
        val usd = profile(CompensationRules()).copy(currencyCode = "USD")

        val result = settings.apply(CompensationResolver.profileToLegacySettingsUpdates(usd))

        assertEquals("USD", result.currencyCode)
        assertEquals(com.elmtrackr.app.domain.model.CurrencyCode.USD, result.currency)
        assertEquals("USD", result.displayCurrencyCode())
    }
}
