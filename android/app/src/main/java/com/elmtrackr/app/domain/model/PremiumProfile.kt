package com.elmtrackr.app.domain.model

import java.time.Instant

enum class PremiumType {
    HIGHEST_ONLY,
    ADDITIVE,
    MULTIPLICATIVE,
    BASE_PLUS_PREMIUM,
    PREMIUM_IN_REGULAR_RATE,
    EXCLUDED_FROM_REGULAR_RATE,
    ;

    companion object {
        fun fromPersisted(raw: String?): PremiumType {
            if (raw.isNullOrBlank()) return HIGHEST_ONLY
            return when (raw.trim().lowercase()) {
                "additive" -> ADDITIVE
                "multiplicative" -> MULTIPLICATIVE
                "base_plus_premium" -> BASE_PLUS_PREMIUM
                "premium_in_regular_rate" -> PREMIUM_IN_REGULAR_RATE
                "excluded_from_regular_rate" -> EXCLUDED_FROM_REGULAR_RATE
                else -> HIGHEST_ONLY
            }
        }

        fun toWire(type: PremiumType): String = when (type) {
            HIGHEST_ONLY -> "highest_only"
            ADDITIVE -> "additive"
            MULTIPLICATIVE -> "multiplicative"
            BASE_PLUS_PREMIUM -> "base_plus_premium"
            PREMIUM_IN_REGULAR_RATE -> "premium_in_regular_rate"
            EXCLUDED_FROM_REGULAR_RATE -> "excluded_from_regular_rate"
        }

        val selectable: List<PremiumType> = entries.toList()
    }
}

data class PremiumProfile(
    val id: String,
    val userId: String,
    val name: String,
    val multiplier: Double,
    val premiumType: PremiumType,
    val isDefault: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    val remoteId: String? = null,
) {
    val displayLabel: String
        get() = buildString {
            append(name)
            append(" · ")
            append("${(multiplier * 100).toInt()}%")
        }
}
