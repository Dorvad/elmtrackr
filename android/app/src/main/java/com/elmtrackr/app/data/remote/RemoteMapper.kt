package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

// ---- JSON field helpers ----

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.instantMillis(key: String): Long? = str(key)?.let { value ->
    value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.content == "true"
private fun JsonObject.dbl(key: String): Double? = this[key]?.jsonPrimitive?.content?.toDoubleOrNull()
private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
private fun JsonObject.requireStr(key: String): String = str(key) ?: error("Missing field: $key")
private fun JsonObject.requireInstantMillis(key: String): Long =
    instantMillis(key) ?: error("Missing or invalid timestamp field: $key")
private fun epochIso(value: Long): String = Instant.ofEpochMilli(value).toString()
private fun JsonObject.intArray(key: String): List<Int> =
    (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()

// ---- ShiftEntity ↔ JsonObject ----

fun ShiftEntity.toRemoteJson(
    overrideId: String? = null,
    compensationProfileRemoteId: String? = compensationProfileId,
): JsonObject = buildJsonObject {
    put("id", overrideId ?: remoteId ?: error("No remote id for shift $localId"))
    put("user_id", userId)
    put("start_time", epochIso(startTime))
    endTime?.let { put("end_time", epochIso(it)) }
    put("break_minutes", breakMinutes)
    notes?.let { put("notes", it) }
    put("is_special_day", isSpecialDay)
    refundAction?.let { put("refund_action", it.lowercase()) }
    compensationProfileRemoteId?.let { put("compensation_profile_id", it) }
    compensationSnapshotJson?.let {
        put("compensation_snapshot_json", Json.parseToJsonElement(it))
    }
    put("created_at", epochIso(createdAt))
    put("updated_at", epochIso(updatedAt))
}

fun JsonObject.toShiftEntity(existingLocalId: String? = null): ShiftEntity {
    val remoteId = requireStr("id")
    return ShiftEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        userId = requireStr("user_id"),
        startTime = requireInstantMillis("start_time"),
        endTime = instantMillis("end_time"),
        breakMinutes = int("break_minutes"),
        notes = str("notes"),
        isSpecialDay = bool("is_special_day"),
        refundAction = str("refund_action")?.uppercase(),
        compensationProfileId = str("compensation_profile_id"),
        compensationSnapshotJson = this["compensation_snapshot_json"]?.toString(),
        createdAt = requireInstantMillis("created_at"),
        updatedAt = requireInstantMillis("updated_at"),
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}

// ---- RefundClaimEntity ↔ JsonObject ----

fun RefundClaimEntity.toRemoteJson(overrideId: String? = null, shiftRemoteId: String): JsonObject =
    buildJsonObject {
        put("id", overrideId ?: remoteId ?: error("No remote id for claim $localId"))
        put("user_id", userId)
        put("shift_id", shiftRemoteId)
        put("direction", direction.lowercase())
        put("provider", provider.lowercase().replaceFirstChar(Char::uppercase))
        put("amount", amount)
        put("ride_at", epochIso(rideAt))
        notes?.let { put("notes", it) }
        receiptPath?.let { put("receipt_path", it) }
        put("created_at", epochIso(createdAt))
        put("updated_at", epochIso(updatedAt))
    }

fun JsonObject.toRefundClaimEntity(shiftLocalId: String, existingLocalId: String? = null): RefundClaimEntity {
    val remoteId = requireStr("id")
    return RefundClaimEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        shiftLocalId = shiftLocalId,
        userId = requireStr("user_id"),
        direction = requireStr("direction").uppercase(),
        provider = requireStr("provider").uppercase(),
        amount = dbl("amount") ?: 0.0,
        rideAt = requireInstantMillis("ride_at"),
        notes = str("notes"),
        receiptPath = str("receipt_path"),
        createdAt = requireInstantMillis("created_at"),
        updatedAt = requireInstantMillis("updated_at"),
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}

// ---- UserSettingsEntity ↔ JsonObject ----

fun UserSettingsEntity.toRemoteJson(
    overrideId: String? = null,
    includeCurrency: Boolean = true,
    defaultCompensationProfileRemoteId: String? = defaultCompensationProfileId,
): JsonObject = buildJsonObject {
    put("id", overrideId ?: remoteId ?: error("No remote id for settings $localId"))
    put("user_id", userId)
    put("timezone", timezone)
    put("daily_overtime_threshold_minutes", dailyOvertimeThresholdMinutes)
    put("weekly_overtime_threshold_minutes", weeklyOvertimeThresholdMinutes)
    put("weekend_days", buildJsonArray {
        weekendDays.split(',').mapNotNull { it.trim().toIntOrNull() }.forEach { add(JsonPrimitive(it)) }
    })
    hourlyRate?.let { put("hourly_rate", it) }
    if (includeCurrency) put("currency", currency)
    regionCode?.let { put("region_code", it) }
    currencyCode?.let { put("currency_code", it) }
    defaultCompensationProfileRemoteId?.let { put("default_compensation_profile_id", it) }
    put("onboarding_completed", onboardingCompleted)
    onboardingCompletedAt?.let { put("onboarding_completed_at", epochIso(it)) }
    put("features_travel_refunds", featuresTravelRefunds)
    put("features_paid_projects", featuresPaidProjects)
    put("features_insights", featuresInsights)
    put("features_clock_styles", featuresClockStyles)
    put("clock_style", clockStyle.lowercase())
    put("created_at", epochIso(createdAt))
    put("updated_at", epochIso(updatedAt))
}

fun JsonObject.toUserSettingsEntity(
    existingLocalId: String? = null,
    fallbackCurrency: String? = null,
): UserSettingsEntity {
    val remoteId = requireStr("id")
    return UserSettingsEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        userId = requireStr("user_id"),
        timezone = str("timezone") ?: "UTC",
        dailyOvertimeThresholdMinutes = int("daily_overtime_threshold_minutes"),
        weeklyOvertimeThresholdMinutes = int("weekly_overtime_threshold_minutes"),
        weekendDays = intArray("weekend_days").ifEmpty { listOf(5, 6) }.joinToString(","),
        hourlyRate = dbl("hourly_rate"),
        currency = (str("currency_code") ?: str("currency") ?: fallbackCurrency ?: "ILS").uppercase(),
        regionCode = str("region_code"),
        currencyCode = str("currency_code") ?: str("currency"),
        defaultCompensationProfileId = str("default_compensation_profile_id"),
        onboardingCompleted = bool("onboarding_completed"),
        onboardingCompletedAt = instantMillis("onboarding_completed_at"),
        featuresTravelRefunds = bool("features_travel_refunds"),
        featuresPaidProjects = bool("features_paid_projects"),
        featuresInsights = bool("features_insights"),
        featuresClockStyles = bool("features_clock_styles"),
        clockStyle = (str("clock_style") ?: "classic").uppercase(),
        createdAt = requireInstantMillis("created_at"),
        updatedAt = requireInstantMillis("updated_at"),
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}

// ---- ProfileEntity ↔ JsonObject ----

fun ProfileEntity.toRemoteJson(): JsonObject = buildJsonObject {
    put("id", remoteId ?: userId)
    put("email", email)
    fullName?.let { put("full_name", it) }
    put("created_at", epochIso(createdAt))
    put("updated_at", epochIso(updatedAt))
}

fun JsonObject.toProfileEntity(userId: String, existingLocalId: String? = null): ProfileEntity {
    val remoteId = requireStr("id")
    return ProfileEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        userId = userId,
        email = str("email") ?: "",
        fullName = str("full_name"),
        createdAt = requireInstantMillis("created_at"),
        updatedAt = requireInstantMillis("updated_at"),
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}

// ---- CompensationProfileEntity ↔ JsonObject ----

fun CompensationProfileEntity.toRemoteJson(overrideId: String? = null): JsonObject = buildJsonObject {
    put("id", overrideId ?: remoteId ?: error("No remote id for compensation profile $localId"))
    put("user_id", userId)
    put("name", name)
    put("region_code", regionCode)
    put("currency_code", currencyCode)
    put("timezone", timezone)
    baseHourlyRate?.let { put("base_hourly_rate", it) }
    put("rules_json", Json.parseToJsonElement(rulesJson))
    put("stacking_policy", stackingPolicy)
    put("effective_from", epochIso(effectiveFrom))
    effectiveUntil?.let { put("effective_until", epochIso(it)) }
    put("is_default", isDefault)
    put("is_archived", isArchived)
    put("created_at", epochIso(createdAt))
    put("updated_at", epochIso(updatedAt))
}

fun JsonObject.toCompensationProfileEntity(existingLocalId: String? = null): CompensationProfileEntity {
    val remoteId = requireStr("id")
    val rulesJsonValue = this["rules_json"]
    val rulesJson = when (rulesJsonValue) {
        is JsonObject -> rulesJsonValue.toString()
        else -> rulesJsonValue?.jsonPrimitive?.content ?: "{}"
    }
    return CompensationProfileEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        userId = requireStr("user_id"),
        name = requireStr("name"),
        regionCode = requireStr("region_code"),
        currencyCode = requireStr("currency_code"),
        timezone = str("timezone") ?: "UTC",
        baseHourlyRate = dbl("base_hourly_rate"),
        rulesJson = rulesJson,
        stackingPolicy = str("stacking_policy") ?: "highest_only",
        effectiveFrom = requireInstantMillis("effective_from"),
        effectiveUntil = instantMillis("effective_until"),
        isDefault = bool("is_default"),
        isArchived = bool("is_archived"),
        createdAt = requireInstantMillis("created_at"),
        updatedAt = requireInstantMillis("updated_at"),
        deletedAt = null,
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}
