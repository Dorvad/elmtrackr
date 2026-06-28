package com.elmtrackr.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistedEnumParsingTest {

    @Test
    fun refundAction_fromPersisted_handles_web_snake_case() {
        assertEquals(RefundAction.NO_RIDE_TAKEN, RefundAction.fromPersisted("no_ride_taken"))
        assertEquals(RefundAction.SUBMITTED, RefundAction.fromPersisted("submitted"))
        assertNull(RefundAction.fromPersisted("legacy_value"))
    }

    @Test
    fun refundProvider_fromPersisted_handles_web_labels() {
        assertEquals(RefundProvider.LIME, RefundProvider.fromPersisted("Lime"))
        assertEquals(RefundProvider.OTHER, RefundProvider.fromPersisted("unknown"))
    }

    @Test
    fun refundDirection_fromPersisted_handles_web_snake_case() {
        assertEquals(RefundDirection.TO_WORK, RefundDirection.fromPersisted("to_work"))
        assertEquals(RefundDirection.FROM_WORK, RefundDirection.fromPersisted("from_work"))
    }

    @Test
    fun regionCode_fromPersisted_handles_web_values() {
        assertEquals(RegionCode.GB, RegionCode.fromPersisted("gb"))
        assertEquals(RegionCode.IL, RegionCode.fromPersisted("unknown"))
    }
}
