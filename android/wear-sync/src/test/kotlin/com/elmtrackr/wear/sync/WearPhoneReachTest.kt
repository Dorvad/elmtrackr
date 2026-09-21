package com.elmtrackr.wear.sync

import com.elmtrackr.wear.sync.WearPhoneReach.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPhoneReachTest {

    private val farPhone = Candidate(id = "far", isNearby = false)
    private val nearPhone = Candidate(id = "near", isNearby = true)

    @Test
    fun noNodesMeansNoPhone() {
        assertNull(WearPhoneReach.choose(emptyList(), emptyList()))
    }

    @Test
    fun aNodeAdvertisingTheAppIsConfirmedAndPreferred() {
        val target = WearPhoneReach.choose(
            capabilityNodes = listOf(farPhone),
            connectedNodes = listOf(nearPhone, farPhone),
        )

        assertEquals("far", target?.nodeId)
        assertTrue(target!!.runsElmTrackr)
    }

    @Test
    fun aConnectedNodeWithoutTheCapabilityIsStillUsedButUnconfirmed() {
        val target = WearPhoneReach.choose(
            capabilityNodes = emptyList(),
            connectedNodes = listOf(farPhone, nearPhone),
        )

        assertEquals("near", target?.nodeId)
        assertFalse(target!!.runsElmTrackr)
    }

    @Test
    fun nearbyNodesWinWithinEachGroup() {
        val target = WearPhoneReach.choose(
            capabilityNodes = listOf(farPhone, nearPhone),
            connectedNodes = emptyList(),
        )

        assertEquals("near", target?.nodeId)
    }

    @Test
    fun anUnconfirmedPhoneGetsTheShortBudget() {
        val confirmed = WearPhoneTarget("a", runsElmTrackr = true)
        val unconfirmed = WearPhoneTarget("b", runsElmTrackr = false)

        assertEquals(WearPhoneReach.CONFIRMED_PUNCH_TIMEOUT_MS, WearPhoneReach.punchResultTimeoutMillis(confirmed))
        assertEquals(WearPhoneReach.UNCONFIRMED_PUNCH_TIMEOUT_MS, WearPhoneReach.punchResultTimeoutMillis(unconfirmed))
        assertTrue(
            WearPhoneReach.punchResultTimeoutMillis(unconfirmed) < WearPhoneReach.punchResultTimeoutMillis(confirmed),
        )
        assertTrue(WearPhoneReach.refreshPollAttempts(unconfirmed) < WearPhoneReach.refreshPollAttempts(confirmed))
    }

    @Test
    fun theCapabilityNamesMatchTheResourcesEachSideDeclares() {
        // The watch declares its capability in res/values/wear.xml, the phone in its
        // own; both files are read by the platform by name and nothing else checks
        // that the constant the other side queries is the same string.
        assertEquals("elmtrackr_wear_app", WearCapabilities.WATCH_APP)
        assertEquals("elmtrackr_phone_app", WearCapabilities.PHONE_APP)
    }
}
