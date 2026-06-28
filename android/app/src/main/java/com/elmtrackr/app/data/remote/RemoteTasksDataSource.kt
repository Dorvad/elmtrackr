package com.elmtrackr.app.data.remote

import com.elmtrackr.app.data.local.entity.TaskEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject

class RemoteTasksDataSource(
    private val supabaseClient: SupabaseClient,
) {
    suspend fun fetchTasks(userId: String): List<JsonObject> =
        supabaseClient.from("tasks")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<JsonObject>()

    suspend fun upsertTask(entity: TaskEntity) {
        supabaseClient.from("tasks")
            .upsert(entity.toRemoteJson())
    }

    suspend fun deleteTask(remoteId: String) {
        supabaseClient.from("tasks")
            .delete {
                filter { eq("id", remoteId) }
            }
    }
}
