package com.elmtrackr.app.domain.model

/** Mirrors TypeScript RefundAction — null means "pending / unresolved". */
enum class RefundAction {
    NO_RIDE_TAKEN,
    REMIND_LATER,
    SUBMITTED,
}

enum class RefundProvider {
    LIME, DOTT, BIRD, TAXI, OTHER
}

enum class RefundDirection {
    TO_WORK, FROM_WORK
}

enum class ClockStyle {
    CLASSIC, MINIMAL, FOCUS, BOLD, NIGHT, RETRO, AURORA, PULSE, DIAL, STRAND, PRISM,
    SAND, BLOCKS, ORBIT, FELLOWSHIP,
    ;

    companion object {
        /** Parse a persisted/synced clock style without crashing on unknown values. */
        fun fromPersisted(raw: String?): ClockStyle {
            if (raw.isNullOrBlank()) return CLASSIC
            val normalized = raw.trim().uppercase()
            return entries.find { it.name == normalized } ?: CLASSIC
        }
    }
}
