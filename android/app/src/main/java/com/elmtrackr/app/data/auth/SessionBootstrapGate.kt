package com.elmtrackr.app.data.auth

import kotlinx.coroutines.flow.StateFlow

interface SessionBootstrapGate {
    val sessionBootstrapComplete: StateFlow<Boolean>
}
