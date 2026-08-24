package com.elmtrackr.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nonce is the one part of Google sign-in that fails silently when it is
 * wrong: a mis-encoded hash produces a token Supabase rejects with a message
 * about the nonce claim, and nothing on the device says which of the two forms
 * went astray. So the encoding is pinned against published SHA-256 vectors.
 */
class SignInNonceTest {

    @Test
    fun `sha256Hex matches the published vector for abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SignInNonce.sha256Hex("abc"),
        )
    }

    @Test
    fun `sha256Hex is lowercase hex, zero padded, and 64 characters`() {
        val hash = SignInNonce.sha256Hex("elmtrackr-nonce")
        assertEquals("f50cad790d48707ca83b141477edb2a0eb608872f941d52d99fb08132c1b6d16", hash)
        // Zero padding is the trap: "%x" on a byte below 0x10 drops a character
        // and every hash after that point is one nibble short.
        assertEquals(64, hash.length)
        assertTrue(hash, hash.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `random produces a 256-bit hex value that differs each time`() {
        val first = SignInNonce.random()
        val second = SignInNonce.random()

        assertEquals(64, first.length)
        assertTrue(first, first.all { it.isDigit() || it in 'a'..'f' })
        assertNotEquals(first, second)
    }

    @Test
    fun `hashing is what makes the two forms different`() {
        // The whole contract in one line: what Google is given is not what
        // Supabase is given, and the app must never send the same string twice.
        val raw = SignInNonce.random()
        assertNotEquals(raw, SignInNonce.sha256Hex(raw))
    }
}
