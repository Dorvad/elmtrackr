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

    fun encodePunchCommand(command: WearPunchCommand): ByteArray =
        json.encodeToString(command).encodeToByteArray()

    fun decodePunchCommand(payload: ByteArray): WearPunchCommand? {
        if (payload.isEmpty()) return null
        return runCatching { json.decodeFromString<WearPunchCommand>(payload.decodeToString()) }.getOrNull()
    }

    fun encodePunchLog(log: WearPunchEventLog): String =
        json.encodeToString(log)

    fun decodePunchLog(payload: String): WearPunchEventLog =
        runCatching { json.decodeFromString<WearPunchEventLog>(payload) }
            .getOrDefault(WearPunchEventLog())
}
