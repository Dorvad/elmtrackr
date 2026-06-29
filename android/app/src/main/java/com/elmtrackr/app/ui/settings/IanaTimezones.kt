package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.compensation.RegionPresets
import java.time.ZoneId
import java.util.TimeZone

object IanaTimezones {

    private val excludedPrefixes = listOf("Etc/", "SystemV/", "GMT", "UCT")

    val all: List<String> = ZoneId.getAvailableZoneIds()
        .asSequence()
        .filter { id -> id.contains("/") && excludedPrefixes.none { id.startsWith(it) } }
        .sorted()
        .toList()

    val common: List<String> = buildList {
        addAll(RegionPresets.timezoneOptions)
        add(TimeZone.getDefault().id)
        add("UTC")
    }.distinct()

    fun displayOptions(selected: String): List<String> = buildList {
        addAll(common)
        if (selected.isNotBlank() && selected !in common && selected in all) {
            add(selected)
        }
    }.distinct()

    fun normalize(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return TimeZone.getDefault().id
        return runCatching { ZoneId.of(trimmed).id }.getOrDefault(TimeZone.getDefault().id)
    }

    fun isValid(zoneId: String): Boolean = runCatching { ZoneId.of(zoneId) }.isSuccess
}
