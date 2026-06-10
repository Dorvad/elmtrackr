package com.elmtrackr.app.domain.model

import java.time.Instant

data class Profile(
    val id: String,
    val email: String,
    val fullName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
