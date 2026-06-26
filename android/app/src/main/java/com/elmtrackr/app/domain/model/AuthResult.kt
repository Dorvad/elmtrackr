package com.elmtrackr.app.domain.model

sealed interface AuthResult {
    data object Success : AuthResult
    data object NotConfigured : AuthResult
    data class Error(val message: String) : AuthResult
}
