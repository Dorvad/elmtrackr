package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RemoteShiftRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("break_minutes") val breakMinutes: Int = 0,
    val notes: String? = null,
    @SerialName("is_special_day") val isSpecialDay: Boolean = false,
    @SerialName("refund_action") val refundAction: String? = null,
    @SerialName("compensation_profile_id") val compensationProfileId: String? = null,
    @SerialName("compensation_snapshot_json") val compensationSnapshotJson: JsonElement? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("task_name_snapshot") val taskNameSnapshot: String? = null,
    @SerialName("task_icon_snapshot") val taskIconSnapshot: String? = null,
    @SerialName("task_hourly_rate_snapshot") val taskHourlyRateSnapshot: Double? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class RemoteShiftInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("break_minutes") val breakMinutes: Int = 0,
    val notes: String? = null,
    @SerialName("is_special_day") val isSpecialDay: Boolean = false,
    @SerialName("refund_action") val refundAction: String? = null,
    @SerialName("compensation_profile_id") val compensationProfileId: String? = null,
    @SerialName("compensation_snapshot_json") val compensationSnapshotJson: JsonElement? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("task_name_snapshot") val taskNameSnapshot: String? = null,
    @SerialName("task_icon_snapshot") val taskIconSnapshot: String? = null,
    @SerialName("task_hourly_rate_snapshot") val taskHourlyRateSnapshot: Double? = null,
)

@Serializable
data class RemoteShiftUpdate(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("break_minutes") val breakMinutes: Int = 0,
    val notes: String? = null,
    @SerialName("is_special_day") val isSpecialDay: Boolean = false,
    @SerialName("refund_action") val refundAction: String? = null,
    @SerialName("compensation_profile_id") val compensationProfileId: String? = null,
    @SerialName("compensation_snapshot_json") val compensationSnapshotJson: JsonElement? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("task_name_snapshot") val taskNameSnapshot: String? = null,
    @SerialName("task_icon_snapshot") val taskIconSnapshot: String? = null,
    @SerialName("task_hourly_rate_snapshot") val taskHourlyRateSnapshot: Double? = null,
)
