package com.elmtrackr.app.fake

import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.data.local.preferences.PurchasePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePurchasePreferences(
    ownedProductIds: Set<String> = emptySet(),
    grandfathered: Set<String> = emptySet(),
    grandfatheringDone: Boolean = false,
) : PurchasePreferences {

    private val state = MutableStateFlow(
        AppPreferenceValues(
            ownedProductIds = ownedProductIds,
            grandfatheredClockFacePacks = grandfathered,
            clockFacePacksGrandfathered = grandfatheringDone,
        ),
    )

    override val preferences: Flow<AppPreferenceValues> = state.asStateFlow()

    val ownedProductIds: Set<String> get() = state.value.ownedProductIds
    val grandfatheredClockFacePacks: Set<String> get() = state.value.grandfatheredClockFacePacks
    val grandfatheringDone: Boolean get() = state.value.clockFacePacksGrandfathered

    override suspend fun setOwnedProductIds(productIds: Set<String>) {
        state.value = state.value.copy(ownedProductIds = productIds)
    }

    override suspend fun setGrandfatheredClockFacePacks(packNames: Set<String>) {
        state.value = state.value.copy(
            grandfatheredClockFacePacks = packNames,
            clockFacePacksGrandfathered = true,
        )
    }
}
