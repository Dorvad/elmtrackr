package com.elmtrackr.app.domain.model

/**
 * How the client paid. Recorded for the user's own reconciliation — nothing in
 * the app acts on it, and it never affects an amount.
 *
 * Stored as the enum name. [fromPersisted] tolerates unknown values so a row
 * written by a newer version, or synced from another device, still loads: the
 * payment's *amount* is what matters, and losing a whole payment because its
 * method is unrecognised would corrupt a balance.
 */
enum class PaymentMethod {
    BANK_TRANSFER,
    CASH,
    CARD,
    CHECK,
    PAYPAL,
    PAYMENT_APP,
    OTHER,
    ;

    /** Lowercase snake_case wire form, matching the Supabase contract style. */
    fun toWire(): String = name.lowercase()

    companion object {
        /** Offered in the payment form, in this order. */
        val selectable: List<PaymentMethod> = entries

        /**
         * Parses a persisted or synced value. Returns null for an absent value —
         * the method is optional — and [OTHER] for one that is present but not
         * recognised, so the payment still loads.
         */
        fun fromPersisted(raw: String?): PaymentMethod? {
            if (raw.isNullOrBlank()) return null
            val normalized = raw.trim().uppercase().replace('-', '_').replace(' ', '_')
            return entries.find { it.name == normalized } ?: OTHER
        }
    }
}
