package com.elmtrackr.app.data.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The nonce that ties one Google ID token to one sign-in attempt.
 *
 * It travels in two forms, and swapping them breaks sign-in outright, so both
 * are produced here rather than at the call site:
 *
 *  - Google is given [sha256Hex] of the raw value and copies that, verbatim,
 *    into the token's `nonce` claim.
 *  - Supabase is given the **raw** value, hashes it itself, and compares.
 *
 * Send the hash to Supabase and it hashes a hash, which never matches.
 */
internal object SignInNonce {

    /**
     * A random 256-bit value, hex encoded.
     *
     * [SecureRandom] rather than a random UUID: a UUID carries 122 random bits
     * with six fixed by its version and variant fields, and there is no reason to
     * give a replay check less entropy than it can hold.
     */
    fun random(): String {
        val bytes = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun sha256Hex(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private const val NONCE_BYTES = 32
}
