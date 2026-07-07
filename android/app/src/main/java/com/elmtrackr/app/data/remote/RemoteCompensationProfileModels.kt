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
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
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
)
