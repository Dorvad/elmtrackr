package com.elmtrackr.app.fake

import com.elmtrackr.app.data.remote.RemoteShiftsDataSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class FakeRemoteShiftsDataSource : RemoteShiftsDataSource {

    val upserted = mutableListOf<JsonObject>()
    val deleted = mutableListOf<String>()
    private val stored = mutableListOf<JsonObject>()
    var shouldThrow: Exception? = null

    fun addRemoteItem(vararg items: JsonObject) { stored.addAll(items) }

    override suspend fun fetchAll(userId: String): List<JsonObject> {
        shouldThrow?.let { throw it }
        return stored.filter { it["user_id"]?.jsonPrimitive?.content == userId }
    }

    override suspend fun upsert(data: JsonObject) {
        shouldThrow?.let { throw it }
        upserted.add(data)
        val id = data["id"]?.jsonPrimitive?.content ?: return
        stored.removeIf { it["id"]?.jsonPrimitive?.content == id }
        stored.add(data)
    }

    override suspend fun delete(remoteId: String) {
        shouldThrow?.let { throw it }
        deleted.add(remoteId)
        stored.removeIf { it["id"]?.jsonPrimitive?.content == remoteId }
    }
}
