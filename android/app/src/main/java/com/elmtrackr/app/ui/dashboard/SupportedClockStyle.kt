package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.domain.model.ClockStyle

enum class SupportedClockStyle {
    CLASSIC, MINIMAL, FOCUS, BOLD, NIGHT, RETRO, AURORA, PULSE, DIAL, STRAND, PRISM,
    SAND, BLOCKS, ORBIT, TIDE, SPROUT, METRO, VINYL, LUNA, SUMMIT,
    METER, STACKS, JAR, TICKER,
    READOUT, SPARKLINE, GAUGE, MATRIX,
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
    ClockStyle.SAND    -> SupportedClockStyle.SAND
    ClockStyle.BLOCKS  -> SupportedClockStyle.BLOCKS
    ClockStyle.ORBIT   -> SupportedClockStyle.ORBIT
    ClockStyle.TIDE    -> SupportedClockStyle.TIDE
    ClockStyle.SPROUT  -> SupportedClockStyle.SPROUT
    ClockStyle.METRO   -> SupportedClockStyle.METRO
    ClockStyle.VINYL   -> SupportedClockStyle.VINYL
    ClockStyle.LUNA    -> SupportedClockStyle.LUNA
    ClockStyle.SUMMIT  -> SupportedClockStyle.SUMMIT
    ClockStyle.METER   -> SupportedClockStyle.METER
    ClockStyle.STACKS  -> SupportedClockStyle.STACKS
    ClockStyle.JAR     -> SupportedClockStyle.JAR
    ClockStyle.TICKER  -> SupportedClockStyle.TICKER
    ClockStyle.READOUT   -> SupportedClockStyle.READOUT
    ClockStyle.SPARKLINE -> SupportedClockStyle.SPARKLINE
    ClockStyle.GAUGE     -> SupportedClockStyle.GAUGE
    ClockStyle.MATRIX    -> SupportedClockStyle.MATRIX
}
