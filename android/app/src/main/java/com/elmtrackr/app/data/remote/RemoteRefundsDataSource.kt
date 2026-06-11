package com.elmtrackr.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject

interface RemoteRefundsDataSource {
    suspend fun fetchAll(userId: String): List<JsonObject>
    suspend fun upsert(data: JsonObject)
    suspend fun delete(remoteId: String)
}

class SupabaseRefundsDataSource(private val client: SupabaseClient) : RemoteRefundsDataSource {

    override suspend fun fetchAll(userId: String): List<JsonObject> =
        client.postgrest["refund_claims"].select {
            filter { eq("user_id", userId) }
        }.decodeList()

    override suspend fun upsert(data: JsonObject) {
        client.postgrest["refund_claims"].upsert(data)
    }

    override suspend fun delete(remoteId: String) {
        client.postgrest["refund_claims"].delete {
            filter { eq("id", remoteId) }
        }
    }
}
