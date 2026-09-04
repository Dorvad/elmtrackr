package com.elmtrackr.app.domain.model

/** Mirrors TypeScript RefundAction — null means "pending / unresolved". */
enum class RefundAction {
    NO_RIDE_TAKEN,
    REMIND_LATER,
    SUBMITTED,
    ;

    companion object {
        fun fromPersisted(raw: String?): RefundAction? {
            if (raw.isNullOrBlank()) return null
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized }
        }
    }
}

enum class RefundProvider {
    LIME, DOTT, BIRD, TAXI, OTHER;

    companion object {
        fun fromPersisted(raw: String?): RefundProvider {
            if (raw.isNullOrBlank()) return OTHER
            val normalized = raw.trim().uppercase()
            return entries.find { it.name == normalized } ?: OTHER
        }
    }
}

enum class RefundDirection {
    TO_WORK, FROM_WORK;

    companion object {
        fun fromPersisted(raw: String?): RefundDirection {
            if (raw.isNullOrBlank()) return TO_WORK
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized } ?: TO_WORK
        }
    }
}

enum class ClockStyle {
    CLASSIC, MINIMAL, FOCUS, BOLD, NIGHT, RETRO, AURORA, PULSE, DIAL, STRAND, PRISM,
    SAND, BLOCKS, ORBIT, TIDE, SPROUT, METRO, VINYL, LUNA, SUMMIT,
    METER, STACKS, JAR, TICKER,
    READOUT, SPARKLINE, GAUGE, MATRIX,
    ;

    companion object {
        /** Parse a persisted/synced clock style without crashing on unknown values. */
        fun fromPersisted(raw: String?): ClockStyle {
            if (raw.isNullOrBlank()) return CLASSIC
            val normalized = raw.trim().uppercase()
            if (normalized == "FELLOWSHIP") return CLASSIC
            return entries.find { it.name == normalized } ?: CLASSIC
        }
    }
}
