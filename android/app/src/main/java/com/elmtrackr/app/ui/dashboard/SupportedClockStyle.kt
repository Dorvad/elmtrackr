package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.ClockStyle

enum class SupportedClockStyle {
    CLASSIC, MINIMAL, FOCUS, BOLD, NIGHT, RETRO, AURORA, PULSE, DIAL, STRAND, PRISM,
}

fun ClockStyle.toSupportedOrDefault(): SupportedClockStyle = when (this) {
    ClockStyle.CLASSIC -> SupportedClockStyle.CLASSIC
    ClockStyle.MINIMAL -> SupportedClockStyle.MINIMAL
    ClockStyle.FOCUS   -> SupportedClockStyle.FOCUS
    ClockStyle.BOLD    -> SupportedClockStyle.BOLD
    ClockStyle.NIGHT   -> SupportedClockStyle.NIGHT
    ClockStyle.RETRO   -> SupportedClockStyle.RETRO
    ClockStyle.AURORA  -> SupportedClockStyle.AURORA
    ClockStyle.PULSE   -> SupportedClockStyle.PULSE
    ClockStyle.DIAL    -> SupportedClockStyle.DIAL
    ClockStyle.STRAND  -> SupportedClockStyle.STRAND
    ClockStyle.PRISM   -> SupportedClockStyle.PRISM
}
