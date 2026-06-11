package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.ProfileEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

// ---- JSON field helpers ----

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it != "null" }
private fun JsonObject.lng(key: String): Long? = this[key]?.jsonPrimitive?.content?.toLongOrNull()
private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.content == "true"
private fun JsonObject.dbl(key: String): Double? = this[key]?.jsonPrimitive?.content?.toDoubleOrNull()
private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
private fun JsonObject.requireStr(key: String): String = str(key) ?: error("Missing field: $key")
private fun JsonObject.requireLng(key: String): Long = lng(key) ?: error("Missing field: $key")

// ---- ShiftEntity ↔ JsonObject ----

fun ShiftEntity.toRemoteJson(overrideId: String? = null): JsonObject = buildJsonObject {
    put("id", overrideId ?: remoteId ?: error("No remote id for shift $localId"))
    put("user_id", userId)
    put("start_time", startTime)
    endTime?.let { put("end_time", it) }
    put("break_minutes", breakMinutes)
    notes?.let { put("notes", it) }
    put("is_special_day", isSpecialDay)
    refundAction?.let { put("refund_action", it) }
    put("created_at", createdAt)
    put("updated_at", updatedAt)
    deletedAt?.let { put("deleted_at", it) }
}

fun JsonObject.toShiftEntity(existingLocalId: String? = null): ShiftEntity {
    val remoteId = requireStr("id")
    return ShiftEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        userId = requireStr("user_id"),
        startTime = requireLng("start_time"),
        endTime = lng("end_time"),
        breakMinutes = int("break_minutes"),
        notes = str("notes"),
        isSpecialDay = bool("is_special_day"),
        refundAction = str("refund_action"),
        createdAt = requireLng("created_at"),
        updatedAt = requireLng("updated_at"),
        deletedAt = lng("deleted_at"),
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
        put("direction", direction)
        put("provider", provider)
        put("amount", amount)
        put("ride_at", rideAt)
        notes?.let { put("notes", it) }
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        deletedAt?.let { put("deleted_at", it) }
    }

fun JsonObject.toRefundClaimEntity(shiftLocalId: String, existingLocalId: String? = null): RefundClaimEntity {
    val remoteId = requireStr("id")
    return RefundClaimEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        shiftLocalId = shiftLocalId,
        userId = requireStr("user_id"),
        direction = requireStr("direction"),
        provider = requireStr("provider"),
        amount = dbl("amount") ?: 0.0,
        rideAt = requireLng("ride_at"),
        notes = str("notes"),
        receiptPath = str("receipt_path"),
        createdAt = requireLng("created_at"),
        updatedAt = requireLng("updated_at"),
        deletedAt = lng("deleted_at"),
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}

// ---- UserSettingsEntity ↔ JsonObject ----

fun UserSettingsEntity.toRemoteJson(overrideId: String? = null): JsonObject = buildJsonObject {
    put("id", overrideId ?: remoteId ?: error("No remote id for settings $localId"))
    put("user_id", userId)
    put("timezone", timezone)
    put("daily_overtime_threshold_minutes", dailyOvertimeThresholdMinutes)
    put("weekly_overtime_threshold_minutes", weeklyOvertimeThresholdMinutes)
    put("weekend_days", weekendDays)
    hourlyRate?.let { put("hourly_rate", it) }
    put("onboarding_completed", onboardingCompleted)
    onboardingCompletedAt?.let { put("onboarding_completed_at", it) }
    put("features_travel_refunds", featuresTravelRefunds)
    put("features_paid_projects", featuresPaidProjects)
    put("features_insights", featuresInsights)
    put("features_clock_styles", featuresClockStyles)
    put("clock_style", clockStyle)
    put("created_at", createdAt)
    put("updated_at", updatedAt)
    deletedAt?.let { put("deleted_at", it) }
}

fun JsonObject.toUserSettingsEntity(existingLocalId: String? = null): UserSettingsEntity {
    val remoteId = requireStr("id")
    return UserSettingsEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        userId = requireStr("user_id"),
        timezone = str("timezone") ?: "UTC",
        dailyOvertimeThresholdMinutes = int("daily_overtime_threshold_minutes"),
        weeklyOvertimeThresholdMinutes = int("weekly_overtime_threshold_minutes"),
        weekendDays = str("weekend_days") ?: "5,6",
        hourlyRate = dbl("hourly_rate"),
        onboardingCompleted = bool("onboarding_completed"),
        onboardingCompletedAt = lng("onboarding_completed_at"),
        featuresTravelRefunds = bool("features_travel_refunds"),
        featuresPaidProjects = bool("features_paid_projects"),
        featuresInsights = bool("features_insights"),
        featuresClockStyles = bool("features_clock_styles"),
        clockStyle = str("clock_style") ?: "digital",
        createdAt = requireLng("created_at"),
        updatedAt = requireLng("updated_at"),
        deletedAt = lng("deleted_at"),
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}

// ---- ProfileEntity ↔ JsonObject ----

fun ProfileEntity.toRemoteJson(): JsonObject = buildJsonObject {
    remoteId?.let { put("id", it) }
    put("email", email)
    fullName?.let { put("full_name", it) }
    put("created_at", createdAt)
    put("updated_at", updatedAt)
}

fun JsonObject.toProfileEntity(userId: String, existingLocalId: String? = null): ProfileEntity {
    val remoteId = requireStr("id")
    return ProfileEntity(
        localId = existingLocalId ?: remoteId,
        remoteId = remoteId,
        userId = userId,
        email = str("email") ?: "",
        fullName = str("full_name"),
        createdAt = requireLng("created_at"),
        updatedAt = requireLng("updated_at"),
        deletedAt = lng("deleted_at"),
        syncStatus = SyncStatus.SYNCED,
        lastSyncError = null,
        lastSyncedAt = Instant.now().toEpochMilli(),
    )
}
