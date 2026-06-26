package com.elmtrackr.app.fake

import com.elmtrackr.app.data.remote.RemoteProfileDataSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class FakeRemoteProfileDataSource : RemoteProfileDataSource {

    val upserted = mutableListOf<JsonObject>()
    private val stored = mutableListOf<JsonObject>()

    fun addRemoteItem(vararg items: JsonObject) { stored.addAll(items) }

    override suspend fun fetchAll(userId: String): List<JsonObject> =
        stored.filter { it["id"]?.jsonPrimitive?.content == userId }

    override suspend fun upsert(data: JsonObject) {
        upserted.add(data)
        val id = data["id"]?.jsonPrimitive?.content ?: return
        stored.removeIf { it["id"]?.jsonPrimitive?.content == id }
        stored.add(data)
    }
}
