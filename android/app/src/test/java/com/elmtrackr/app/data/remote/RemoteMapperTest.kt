package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.ShiftEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.local.entity.UserSettingsEntity
import com.elmtrackr.app.data.local.mapper.toDomain
import com.elmtrackr.app.domain.model.ClockStyle
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class RemoteMapperTest {
    private val now = Instant.parse("2026-06-21T12:34:56Z").toEpochMilli()

    @Test
    fun `shift payload uses ISO timestamps and excludes local delete metadata`() {
        val entity = ShiftEntity(
            localId = "local", remoteId = "remote", userId = "user",
            startTime = now, endTime = now + 3_600_000, breakMinutes = 15,
            notes = null, isSpecialDay = false, refundAction = "SUBMITTED",
            createdAt = now, updatedAt = now, deletedAt = now,
            syncStatus = SyncStatus.PENDING_UPDATE, lastSyncError = null, lastSyncedAt = null,
        )

        val json = entity.toRemoteJson()

        assertEquals("2026-06-21T12:34:56Z", json["start_time"]?.jsonPrimitive?.content)
        assertEquals("submitted", json["refund_action"]?.jsonPrimitive?.content)
        assertFalse("deleted_at" in json)
        assertEquals(now, json.toShiftEntity().startTime)
        assertEquals("SUBMITTED", json.toShiftEntity().refundAction)
    }

    @Test
    fun `refund payload uses web casing and pull normalizes for local enums`() {
        val entity = RefundClaimEntity(
            localId = "local", remoteId = "remote", shiftLocalId = "shift", userId = "user",
            direction = "FROM_WORK", provider = "LIME", amount = 12.5, rideAt = now,
            notes = null, receiptPath = null, createdAt = now, updatedAt = now, deletedAt = null,
            syncStatus = SyncStatus.PENDING_CREATE, lastSyncError = null, lastSyncedAt = null,
        )

        val json = entity.toRemoteJson(shiftRemoteId = "remote-shift")

        assertEquals("from_work", json["direction"]?.jsonPrimitive?.content)
        assertEquals("Lime", json["provider"]?.jsonPrimitive?.content)
        assertEquals("FROM_WORK", json.toRefundClaimEntity("shift").direction)
        assertEquals("LIME", json.toRefundClaimEntity("shift").provider)
    }

    @Test
    fun `settings payload uses integer array and lowercase clock style`() {
        val entity = UserSettingsEntity(
            localId = "local", remoteId = "remote", userId = "user", timezone = "Asia/Jerusalem",
            dailyOvertimeThresholdMinutes = 480, weeklyOvertimeThresholdMinutes = 2400,
            weekendDays = "5,6", hourlyRate = 50.0, currency = "EUR", onboardingCompleted = true,
            onboardingCompletedAt = now, featuresTravelRefunds = true, featuresPaidProjects = false,
            featuresInsights = true, featuresClockStyles = true, clockStyle = "AURORA",
            createdAt = now, updatedAt = now, deletedAt = null, syncStatus = SyncStatus.PENDING_UPDATE,
            lastSyncError = null, lastSyncedAt = null,
        )

        val json = entity.toRemoteJson()

        assertEquals(listOf(5, 6), json["weekend_days"]!!.jsonArray.map { it.jsonPrimitive.content.toInt() })
        assertEquals("aurora", json["clock_style"]?.jsonPrimitive?.content)
        assertEquals("EUR", json["currency"]?.jsonPrimitive?.content)
        assertEquals("5,6", json.toUserSettingsEntity().weekendDays)
        assertEquals("AURORA", json.toUserSettingsEntity().clockStyle)
        assertEquals("EUR", json.toUserSettingsEntity().currency)
    }

    @Test
    fun `settings pull maps unknown fellowship clock style to CLASSIC`() {
        val json = buildJsonObject {
            put("id", "remote")
            put("user_id", "user")
            put("timezone", "UTC")
            put("daily_overtime_threshold_minutes", 480)
            put("weekly_overtime_threshold_minutes", 2400)
            put("weekend_days", buildJsonArray { add(JsonPrimitive(5)); add(JsonPrimitive(6)) })
            put("onboarding_completed", true)
            put("features_travel_refunds", false)
            put("features_paid_projects", false)
            put("features_insights", true)
            put("features_clock_styles", true)
            put("clock_style", "fellowship")
            put("created_at", "2026-06-21T12:34:56Z")
            put("updated_at", "2026-06-21T12:34:56Z")
        }

        assertEquals("CLASSIC", json.toUserSettingsEntity().clockStyle)
        assertEquals(ClockStyle.CLASSIC, json.toUserSettingsEntity().toDomain().clockStyle)
    }

    @Test
    fun `settings pull preserves local currency when remote schema is older`() {
        val entity = UserSettingsEntity(
            localId = "local", remoteId = "remote", userId = "user", timezone = "UTC",
            dailyOvertimeThresholdMinutes = 480, weeklyOvertimeThresholdMinutes = 2400,
            weekendDays = "5,6", hourlyRate = null, currency = "EUR", onboardingCompleted = true,
            onboardingCompletedAt = now, featuresTravelRefunds = true, featuresPaidProjects = false,
            featuresInsights = true, featuresClockStyles = true, clockStyle = "CLASSIC",
            createdAt = now, updatedAt = now, deletedAt = null, syncStatus = SyncStatus.SYNCED,
            lastSyncError = null, lastSyncedAt = now,
        )

        val oldSchemaJson = entity.toRemoteJson(includeCurrency = false)

        assertFalse("currency" in oldSchemaJson)
        assertEquals("EUR", oldSchemaJson.toUserSettingsEntity(fallbackCurrency = "EUR").currency)
    }

    @Test
    fun `shift pull maps null compensation snapshot to absent local value`() {
        val json = buildJsonObject {
            put("id", "remote")
            put("user_id", "user")
            put("start_time", "2026-06-21T12:34:56Z")
            put("break_minutes", 0)
            put("is_special_day", false)
            put("compensation_snapshot_json", JsonNull)
            put("created_at", "2026-06-21T12:34:56Z")
            put("updated_at", "2026-06-21T12:34:56Z")
        }

        assertEquals(null, json.toShiftEntity().compensationSnapshotJson)
    }
}
