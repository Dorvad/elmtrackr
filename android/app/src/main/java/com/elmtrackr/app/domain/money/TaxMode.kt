package com.elmtrackr.app.domain.money

/**
 * How a project's tax rate relates to the amount the user typed.
 *
 * Generic on purpose: the internal model never names a specific tax. The user
 * supplies their own label ("VAT", "GST", "Sales Tax", "Tax", …) and their own
 * rate. ElmTrackr never determines which tax applies, and gives no tax advice.
 */
enum class TaxMode {
    /** No tax on this project: base fee and client total are the same amount. */
    NONE,

    /** The user enters their fee before tax; tax is added on top. */
    EXCLUSIVE,

    /** The user enters the total charged to the client; tax is already inside it. */
    INCLUSIVE,
    ;

    val isEnabled: Boolean get() = this != NONE

    companion object {
        /** Parses a persisted or synced value without crashing on unknown input. */
        fun fromPersisted(raw: String?): TaxMode {
            if (raw.isNullOrBlank()) return NONE
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized } ?: NONE
        }
    }

    /** Lowercase snake_case wire form, matching the Supabase contract style. */
    fun toWire(): String = name.lowercase()
}
