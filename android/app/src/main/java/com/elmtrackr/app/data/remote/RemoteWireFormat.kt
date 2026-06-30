package com.elmtrackr.app.data.remote

fun enumToSnakeWire(name: String): String = name.lowercase()

fun providerToWire(name: String): String =
    name.lowercase().replaceFirstChar { it.titlecase() }

fun regionToWire(name: String): String = name.lowercase()

fun clockStyleToWire(name: String): String = name.lowercase()

fun stackingPolicyToWire(name: String): String = name.lowercase()
