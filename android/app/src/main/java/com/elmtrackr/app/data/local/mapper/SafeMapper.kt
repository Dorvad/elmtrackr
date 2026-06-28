package com.elmtrackr.app.data.local.mapper

internal inline fun <T, R> List<T>.mapToDomain(transform: (T) -> R): List<R> =
    mapNotNull { item -> runCatching { transform(item) }.getOrNull() }

internal inline fun <T, R> T?.toDomainOrNull(transform: (T) -> R): R? =
    this?.let { value -> runCatching { transform(value) }.getOrNull() }
