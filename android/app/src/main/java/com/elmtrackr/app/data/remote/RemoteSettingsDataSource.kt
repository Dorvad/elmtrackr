package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject

interface RemoteSettingsDataSource {
    suspend fun fetchAll(userId: String): List<JsonObject>
    suspend fun upsert(data: JsonObject)
}

class SupabaseSettingsDataSource(private val client: SupabaseClient) : RemoteSettingsDataSource {

    override suspend fun fetchAll(userId: String): List<JsonObject> =
        client.postgrest["user_settings"].select {
            filter { eq("user_id", userId) }
        }.decodeList()

    override suspend fun upsert(data: JsonObject) {
        client.postgrest["user_settings"].upsert(data)
    }
}
