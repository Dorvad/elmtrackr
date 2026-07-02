package com.elmtrackr.app.fake

import com.elmtrackr.app.data.auth.SessionBootstrapGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSessionBootstrapGate(
    initialBootstrapComplete: Boolean = true,
) : SessionBootstrapGate {
    private val _sessionBootstrapComplete = MutableStateFlow(initialBootstrapComplete)
    override val sessionBootstrapComplete: StateFlow<Boolean> = _sessionBootstrapComplete.asStateFlow()

    fun setBootstrapComplete(complete: Boolean) {
        _sessionBootstrapComplete.value = complete
    }
}
