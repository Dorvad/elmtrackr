package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteTaskRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    @SerialName("hourly_rate") val hourlyRate: Double,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    /** Set when the row is a tombstone. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** See RemoteShiftRow.clientUpdatedAt. */
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteTaskInsert(
    /** Client-generated UUID so retried creates collide instead of duplicating. */
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val icon: String,
    val color: String? = null,
    @SerialName("hourly_rate") val hourlyRate: Double,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteTaskUpdate(
    val name: String,
    val icon: String,
    val color: String? = null,
    @SerialName("hourly_rate") val hourlyRate: Double,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    /** Non-null makes this update a tombstone. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** See RemoteShiftUpdate.clientUpdatedAt — this is the write's guard. */
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)
