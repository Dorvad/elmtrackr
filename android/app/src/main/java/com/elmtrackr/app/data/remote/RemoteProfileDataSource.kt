package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject

interface RemoteProfileDataSource {
    suspend fun fetchAll(userId: String): List<JsonObject>
    suspend fun upsert(data: JsonObject)
}

class SupabaseProfileDataSource(private val client: SupabaseClient) : RemoteProfileDataSource {

    override suspend fun fetchAll(userId: String): List<JsonObject> =
        client.postgrest["profiles"].select {
            filter { eq("id", userId) }
        }.decodeList()

    override suspend fun upsert(data: JsonObject) {
        client.postgrest["profiles"].upsert(data)
    }
}
