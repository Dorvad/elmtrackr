package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class FakeSettingsRepository : SettingsRepository {

    private val _settings = MutableStateFlow<UserSettings?>(null)

    fun setSettings(settings: UserSettings?) { _settings.value = settings }

    override fun observeSettings(userId: String): Flow<UserSettings?> = _settings

    override suspend fun getSettings(userId: String): UserSettings? = _settings.value

    override suspend fun saveSettings(settings: UserSettings) { _settings.value = settings }

    override suspend fun createDefaultSettings(userId: String): UserSettings {
        val s = UserSettings(
            id = "default-settings",
            userId = userId,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        _settings.value = s
        return s
    }
}
