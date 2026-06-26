package com.elmtrackr.app.fake

import com.elmtrackr.app.data.remote.RemoteRefundsDataSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class FakeRemoteRefundsDataSource : RemoteRefundsDataSource {

    val upserted = mutableListOf<JsonObject>()
    val deleted = mutableListOf<String>()
    private val stored = mutableListOf<JsonObject>()

    fun addRemoteItem(vararg items: JsonObject) { stored.addAll(items) }

    override suspend fun fetchAll(userId: String): List<JsonObject> =
        stored.filter { it["user_id"]?.jsonPrimitive?.content == userId }

    override suspend fun upsert(data: JsonObject) {
        upserted.add(data)
        val id = data["id"]?.jsonPrimitive?.content ?: return
        stored.removeIf { it["id"]?.jsonPrimitive?.content == id }
        stored.add(data)
    }

    override suspend fun delete(remoteId: String) {
        deleted.add(remoteId)
        stored.removeIf { it["id"]?.jsonPrimitive?.content == remoteId }
    }
}
