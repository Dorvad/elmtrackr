package com.elmtrackr.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteUserSettingsModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes legacy user settings row without currency`() {
        val row = json.decodeFromString<RemoteUserSettingsRow>(
            """
            {
              "id": "settings-1",
              "user_id": "user-1",
              "timezone": "Asia/Jerusalem",
              "daily_overtime_threshold_minutes": 480,
              "weekly_overtime_threshold_minutes": 2400,
              "weekend_days": [5, 6],
              "created_at": "2024-06-01T08:00:00Z",
              "updated_at": "2024-06-01T08:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(null, row.currency)
        val entity = row.toLocalEntity()
        assertEquals("ILS", entity.currency)
        assertEquals("ILS", entity.currencyCode)
    }

    @Test
    fun `decodes legacy user settings row with explicit currency`() {
        val row = json.decodeFromString<RemoteUserSettingsRow>(
            """
            {
              "id": "settings-1",
              "user_id": "user-1",
              "timezone": "UTC",
              "daily_overtime_threshold_minutes": 480,
              "weekly_overtime_threshold_minutes": 2400,
              "weekend_days": [5, 6],
              "currency": "EUR",
              "created_at": "2024-06-01T08:00:00Z",
              "updated_at": "2024-06-01T08:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals("EUR", row.toLocalEntity().currency)
    }

    @Test
    fun `prefers currency_code when currency missing`() {
        val row = json.decodeFromString<RemoteUserSettingsRow>(
            """
            {
              "id": "settings-1",
              "user_id": "user-1",
              "timezone": "UTC",
              "daily_overtime_threshold_minutes": 480,
              "weekly_overtime_threshold_minutes": 2400,
              "weekend_days": [5, 6],
              "currency_code": "USD",
              "created_at": "2024-06-01T08:00:00Z",
              "updated_at": "2024-06-01T08:00:00Z"
            }
            """.trimIndent(),
        )

        val entity = row.toLocalEntity()
        assertEquals("USD", entity.currency)
        assertEquals("USD", entity.currencyCode)
    }
}
