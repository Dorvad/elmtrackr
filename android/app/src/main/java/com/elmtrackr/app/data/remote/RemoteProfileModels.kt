package com.elmtrackr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteProfileRow(
    val id: String,
    val email: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    /**
     * When the *device* last edited this row. Nullable only so decoding survives a
     * database that has not had 20260903000000 applied.
     */
    @SerialName("client_updated_at") val clientUpdatedAt: String? = null,
)

@Serializable
data class RemoteProfileUpdate(
    @SerialName("full_name") val fullName: String? = null,
    /**
     * The device's edit time, and the guard the write is filtered on: an update
     * carrying an older edit than the row already holds matches nothing and is
     * reported as a conflict rather than applied.
     */
    @SerialName("client_updated_at") val clientUpdatedAt: String,
)
