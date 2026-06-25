package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.CompensationProfileEntity
import com.elmtrackr.app.data.remote.toRemoteJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RemoteCompensationProfilesDataSource(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun fetchProfiles(userId: String): List<JsonObject> =
        supabaseClient.from("compensation_profiles")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<JsonObject>()

    suspend fun upsertProfile(entity: CompensationProfileEntity) {
        supabaseClient.from("compensation_profiles")
            .upsert(entity.toRemoteJson())
    }

    suspend fun deleteProfile(remoteId: String) {
        supabaseClient.from("compensation_profiles")
            .delete {
                filter { eq("id", remoteId) }
            }
    }
}
