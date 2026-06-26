package com.elmtrackr.app.fake

import com.elmtrackr.app.domain.CurrentUserProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCurrentUserProvider(initialUserId: String? = "local-user") : CurrentUserProvider {
    private val current = MutableStateFlow(initialUserId)
    override val userId: Flow<String?> = current
    fun setUserId(userId: String?) { current.value = userId }
}
