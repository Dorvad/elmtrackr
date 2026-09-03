package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RemoteCompensationProfileRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("currency_code") val currencyCode: String,
    val timezone: String,
    @SerialName("base_hourly_rate") val baseHourlyRate: Double? = null,
    @SerialName("rules_json") val rulesJson: JsonElement,
    @SerialName("stacking_policy") val stackingPolicy: String,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_until") val effectiveUntil: String? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_archived") val isArchived: Boolean,
    /**
     * The job this profile pays for. Every profile owns exactly one workplace,
     * created with it, so leave entitlement outlives a wage change.
     *
     * Nullable and defaulted: the column is never backfilled, so NULL genuinely
     * means a profile written before workplaces existed.
     */
    @SerialName("workplace_id") val workplaceId: String? = null,
    /** Visual identity. Absent on rows written by a client predating the columns. */
    val color: String? = null,
    val icon: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    /** Set when the row is a tombstone. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** See RemoteShiftRow.clientUpdatedAt. */
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteCompensationProfileInsert(
    /**
     * Client-generated UUID. Sending the id makes create retries idempotent:
     * a push that succeeded remotely but lost its response hits the primary
     * key on the next attempt instead of inserting a duplicate row.
     */
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("currency_code") val currencyCode: String,
    val timezone: String,
    @SerialName("base_hourly_rate") val baseHourlyRate: Double? = null,
    @SerialName("rules_json") val rulesJson: JsonElement,
    @SerialName("stacking_policy") val stackingPolicy: String,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_until") val effectiveUntil: String? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("workplace_id") val workplaceId: String? = null,
    /** Visual identity. Absent on rows written by a client predating the columns. */
    val color: String? = null,
    val icon: String? = null,
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)

@Serializable
data class RemoteCompensationProfileUpdate(
    val name: String,
    @SerialName("region_code") val regionCode: String,
    @SerialName("currency_code") val currencyCode: String,
    val timezone: String,
    @SerialName("base_hourly_rate") val baseHourlyRate: Double? = null,
    @SerialName("rules_json") val rulesJson: JsonElement,
    @SerialName("stacking_policy") val stackingPolicy: String,
    @SerialName("effective_from") val effectiveFrom: String,
    @SerialName("effective_until") val effectiveUntil: String? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_archived") val isArchived: Boolean,
    @SerialName("workplace_id") val workplaceId: String? = null,
    /** Visual identity. Absent on rows written by a client predating the columns. */
    val color: String? = null,
    val icon: String? = null,
    /** Non-null makes this update a tombstone. */
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** See RemoteShiftUpdate.clientUpdatedAt — this is the write's guard. */
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)
