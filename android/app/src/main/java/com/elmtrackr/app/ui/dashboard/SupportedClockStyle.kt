package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.ClockStyle

enum class SupportedClockStyle { CLASSIC, MINIMAL, AURORA }

fun ClockStyle.toSupportedOrDefault(): SupportedClockStyle = when (this) {
    ClockStyle.CLASSIC -> SupportedClockStyle.CLASSIC
    ClockStyle.MINIMAL -> SupportedClockStyle.MINIMAL
    ClockStyle.AURORA  -> SupportedClockStyle.AURORA
    else               -> SupportedClockStyle.CLASSIC
}
