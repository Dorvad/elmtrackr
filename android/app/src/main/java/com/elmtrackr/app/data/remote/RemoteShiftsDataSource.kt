package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject

interface RemoteShiftsDataSource {
    suspend fun fetchAll(userId: String): List<JsonObject>
    suspend fun upsert(data: JsonObject)
    suspend fun delete(remoteId: String)
}

class SupabaseShiftsDataSource(private val client: SupabaseClient) : RemoteShiftsDataSource {

    override suspend fun fetchAll(userId: String): List<JsonObject> =
        client.postgrest["shifts"].select {
            filter { eq("user_id", userId) }
        }.decodeList()

    override suspend fun upsert(data: JsonObject) {
        client.postgrest["shifts"].upsert(data)
    }

    override suspend fun delete(remoteId: String) {
        client.postgrest["shifts"].delete {
            filter { eq("id", remoteId) }
        }
    }
}
