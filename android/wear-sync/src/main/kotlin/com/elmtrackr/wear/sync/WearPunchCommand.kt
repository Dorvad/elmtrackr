package com.elmtrackr.wear.sync

import kotlinx.serialization.Serializable

/**
 * Payload on [WearMessages.PUNCH_IN] / [WearMessages.PUNCH_OUT].
 *
 * Empty bytes (older watches) mean "now". A watch that punched while the phone
 * was in a locker sends the original wrist time so the phone does not record
 * the shift as starting when the two devices next see each other.
 */
@Serializable
data class WearPunchCommand(
    val epochMillis: Long = 0L,
)

@Serializable
data class WearPunchEvent(
    val id: String,
    val isPunchIn: Boolean,
    val epochMillis: Long,
)

@Serializable
data class WearPunchEventLog(
    val events: List<WearPunchEvent> = emptyList(),
)
