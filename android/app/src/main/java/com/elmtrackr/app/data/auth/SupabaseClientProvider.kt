package com.elmtrackr.app.data.auth

import com.elmtrackr.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

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
        install(Auth)
        install(Postgrest)
    }
}
