package com.elmtrackr.app.fake

import com.elmtrackr.app.util.NetworkStatusMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(initialOnline: Boolean = true) : NetworkStatusMonitor {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: Flow<Boolean> = _isOnline

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}
