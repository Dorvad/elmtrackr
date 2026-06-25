package com.elmtrackr.app.data.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface AuthCallbackPayload {
    data class Code(val value: String) : AuthCallbackPayload
    data class Tokens(val accessToken: String, val refreshToken: String) : AuthCallbackPayload
}

object AuthCallbackParser {
    fun parse(uriString: String): AuthCallbackPayload {
        val uri = URI(uriString)
        require(uri.scheme == "elmtrackr" && uri.host == "auth" && uri.path == "/callback") {
            "Unsupported authentication callback"
        }
        val query = parameters(uri.rawQuery)
        val fragment = parameters(uri.rawFragment)
        (query["error_description"] ?: query["error"] ?: fragment["error_description"] ?: fragment["error"])
            ?.let { error(it) }
        query["code"]?.takeIf { it.isNotBlank() }?.let { return AuthCallbackPayload.Code(it) }
        val accessToken = fragment["access_token"] ?: error("Authentication callback did not contain a session")
        val refreshToken = fragment["refresh_token"] ?: error("Authentication callback is missing refresh token")
        return AuthCallbackPayload.Tokens(accessToken, refreshToken)
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
