package com.elmtrackr.app.data.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * What an authentication callback may carry: a PKCE authorisation code, and
 * nothing else.
 *
 * There used to be a second shape — an access and refresh token in the URL
 * fragment, the implicit flow's answer — and the app imported whatever session it
 * was handed. The client has been PKCE-only for some time
 * (`SupabaseClientProvider`), so none of the app's own flows produce that shape;
 * sign-up confirmation, password reset and browser sign-in all send a code
 * challenge and come back with `?code=`. What still produced it was anyone else.
 * `elmtrackr://auth` is a custom scheme with no verification, so another app, or
 * a link on a web page, could open
 * `elmtrackr://auth/callback#access_token=…&refresh_token=…` with a session of
 * its own choosing and the app would sign the user into that account — after
 * which every shift, note and pay figure they recorded synced to it.
 *
 * A code is not exposed to the same trick. It is only worth something together
 * with the verifier this device generated when it started the flow, so a code the
 * app did not ask for fails at the exchange.
 */
sealed interface AuthCallbackPayload {
    data class Code(val value: String, val isRecovery: Boolean = false) : AuthCallbackPayload
}

object AuthCallbackParser {
    fun parse(uriString: String): AuthCallbackPayload {
        val uri = URI(uriString)
        require(uri.scheme == "elmtrackr" && uri.host == "auth") {
            "Unsupported authentication callback"
        }
        val query = parameters(uri.rawQuery)
        val fragment = parameters(uri.rawFragment)
        (query["error_description"] ?: query["error"] ?: fragment["error_description"] ?: fragment["error"])
            ?.let { error(it) }
        val isRecovery = uri.path == "/reset-password" ||
            query["type"] == "recovery" ||
            fragment["type"] == "recovery"
        query["code"]?.takeIf { it.isNotBlank() }?.let {
            return AuthCallbackPayload.Code(it, isRecovery = isRecovery)
        }
        // A session in the fragment is refused, not imported. See AuthCallbackPayload.
        if (fragment.containsKey("access_token") || fragment.containsKey("refresh_token")) {
            error("Authentication callback carried a session instead of a code")
        }
        error("Authentication callback did not contain a code")
    }

    private fun parameters(raw: String?): Map<String, String> = raw
        ?.split('&')
        ?.mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 0) null else decode(part.substring(0, separator)) to decode(part.substring(separator + 1))
        }
        ?.toMap()
        .orEmpty()

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
