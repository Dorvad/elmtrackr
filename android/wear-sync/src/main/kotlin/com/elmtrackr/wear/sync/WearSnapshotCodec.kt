package com.elmtrackr.wear.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WearSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: WearShiftSnapshot): String =
        json.encodeToString(snapshot)

    fun decode(payload: String): WearShiftSnapshot? =
        runCatching { json.decodeFromString<WearShiftSnapshot>(payload) }.getOrNull()

    fun encodePunchResult(result: PunchResult): ByteArray =
        json.encodeToString(result).encodeToByteArray()

    fun decodePunchResult(payload: ByteArray): PunchResult? =
        runCatching { json.decodeFromString<PunchResult>(payload.decodeToString()) }.getOrNull()
}
