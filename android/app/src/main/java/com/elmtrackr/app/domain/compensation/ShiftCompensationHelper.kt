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
        // Resolve with any prior snapshot cleared. This helper exists to
        // freeze the rules that apply NOW; the resolver short-circuits to an
        // existing snapshot, so rebuilding an edited shift would otherwise
        // re-encode the stale rules and rule changes would never take effect.
        val resolved = CompensationResolver.resolveShiftCompensation(
            shift.copy(compensationSnapshot = null),
            settings,
            profiles,
        )
        return CompensationResolver.buildSnapshot(resolved)
    }
}
