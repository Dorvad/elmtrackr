package com.elmtrackr.app.data.auth

import com.elmtrackr.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Lazily creates the Supabase client from BuildConfig fields.
 * Returns null (and the app shows "not configured" state) when
 * SUPABASE_URL / SUPABASE_ANON_KEY are empty — e.g. on CI without secrets.
 */
object SupabaseClientProvider {

    @Volatile private var _client: SupabaseClient? = null

    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun get(): SupabaseClient? {
        if (!isConfigured()) return null
        return _client ?: synchronized(this) {
            _client ?: build().also { _client = it }
        }
    }

    private fun build(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            // supabase-kt defaults to FlowType.IMPLICIT (verified in
            // AuthConfigDefaults for 3.2.2), which returns the access and refresh
            // tokens *in the callback URL itself*.
            //
            // The callback arrives on `elmtrackr://auth`, a custom scheme with
            // autoVerify="false" — any installed app may register the same scheme and
            // receive that intent. Under the implicit flow that hands the interceptor
            // a working session. Under PKCE the URL carries a single-use code that is
            // worthless without the verifier held by the app that started the flow, so
            // interception stops being a session compromise.
            //
            // This does not make the scheme safe; migrating the callback to a verified
            // HTTPS App Link is still the fix for that, and needs a hosted
            // .well-known/assetlinks.json carrying the release signing fingerprint.
            // PKCE is the half that can be done entirely in the client.
            flowType = FlowType.PKCE
        }
        install(Postgrest)
        install(Storage)
    }
}
