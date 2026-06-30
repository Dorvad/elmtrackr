package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteRefundClaimRow(
    val id: String,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("user_id") val userId: String,
    val direction: String,
    val provider: String,
    val amount: Double,
    @SerialName("ride_at") val rideAt: String,
    val notes: String? = null,
    @SerialName("receipt_path") val receiptPath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class RemoteRefundClaimInsert(
    @SerialName("shift_id") val shiftId: String,
    @SerialName("user_id") val userId: String,
    val direction: String,
    val provider: String,
    val amount: Double,
    @SerialName("ride_at") val rideAt: String,
    val notes: String? = null,
    @SerialName("receipt_path") val receiptPath: String? = null,
)

@Serializable
data class RemoteRefundClaimUpdate(
    val direction: String,
    val provider: String,
    val amount: Double,
    @SerialName("ride_at") val rideAt: String,
    val notes: String? = null,
    @SerialName("receipt_path") val receiptPath: String? = null,
)
