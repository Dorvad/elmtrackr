package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings

object ShiftCompensationHelper {

    fun buildClockOutSnapshot(
        shift: Shift,
        settings: UserSettings,
        profiles: List<CompensationProfile>,
    ): CompensationSnapshot {
        val resolved = CompensationResolver.resolveShiftCompensation(shift, settings, profiles)
        return CompensationResolver.buildSnapshot(resolved)
    }
}
