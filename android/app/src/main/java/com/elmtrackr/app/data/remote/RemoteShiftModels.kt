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
    @SerialName("premium_profile_id") val premiumProfileId: String? = null,
    @SerialName("force_regular_rate") val forceRegularRate: Boolean = false,
    @SerialName("refund_action") val refundAction: String? = null,
    @SerialName("compensation_profile_id") val compensationProfileId: String? = null,
    @SerialName("compensation_snapshot_json") val compensationSnapshotJson: JsonElement? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("task_name_snapshot") val taskNameSnapshot: String? = null,
    @SerialName("task_icon_snapshot") val taskIconSnapshot: String? = null,
    @SerialName("task_hourly_rate_snapshot") val taskHourlyRateSnapshot: Double? = null,
    /**
     * The job this shift was worked at.
     *
     * Nullable and defaulted for two separate reasons: the column is never
     * backfilled, so NULL legitimately means "written before workplaces existed",
     * and decoding has to survive a database that predates
     * `20260811000000_workplaces_and_leave.sql`.
     */
    @SerialName("workplace_id") val workplaceId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    /** Set when the row is a tombstone; the shift was deleted on some device. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /**
     * When the *device* last edited this row, as opposed to when the row reached
     * the server. Nullable only so decoding survives a database that has not yet
     * had `20260806000000_sync_tombstones_and_row_versions.sql` applied.
     */
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteShiftInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("break_minutes") val breakMinutes: Int = 0,
    val notes: String? = null,
    @SerialName("is_special_day") val isSpecialDay: Boolean = false,
    @SerialName("premium_profile_id") val premiumProfileId: String? = null,
    @SerialName("force_regular_rate") val forceRegularRate: Boolean = false,
    @SerialName("refund_action") val refundAction: String? = null,
    @SerialName("compensation_profile_id") val compensationProfileId: String? = null,
    @SerialName("compensation_snapshot_json") val compensationSnapshotJson: JsonElement? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("task_name_snapshot") val taskNameSnapshot: String? = null,
    @SerialName("task_icon_snapshot") val taskIconSnapshot: String? = null,
    @SerialName("task_hourly_rate_snapshot") val taskHourlyRateSnapshot: Double? = null,
    @SerialName("workplace_id") val workplaceId: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteShiftUpdate(
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("break_minutes") val breakMinutes: Int = 0,
    val notes: String? = null,
    @SerialName("is_special_day") val isSpecialDay: Boolean = false,
    @SerialName("premium_profile_id") val premiumProfileId: String? = null,
    @SerialName("force_regular_rate") val forceRegularRate: Boolean = false,
    @SerialName("refund_action") val refundAction: String? = null,
    @SerialName("compensation_profile_id") val compensationProfileId: String? = null,
    @SerialName("compensation_snapshot_json") val compensationSnapshotJson: JsonElement? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("task_name_snapshot") val taskNameSnapshot: String? = null,
    @SerialName("task_icon_snapshot") val taskIconSnapshot: String? = null,
    @SerialName("task_hourly_rate_snapshot") val taskHourlyRateSnapshot: Double? = null,
    @SerialName("workplace_id") val workplaceId: String? = null,
    /** Non-null makes this update a tombstone. See SupabaseShiftsDataSource.update. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /**
     * The device's edit time for this change. Also the guard the write is
     * filtered on, so an update carrying an older edit time than the row already
     * holds matches nothing and is reported as a conflict instead of applied.
     */
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)
