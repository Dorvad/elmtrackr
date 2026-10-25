package com.elmtrackr.app.ui.dashboard

import com.elmtrackr.app.data.sync.SyncHealth

/**
 * A sync problem worth interrupting the dashboard for.
 *
 * Only two qualify, and both need the user: a session that has expired, which no
 * retry will fix, and changes the server rejected, which stay on the phone until
 * someone looks. Ordinary pending changes are deliberately not here. Waiting to
 * upload is what offline-first means — shifts are saved on the phone and go up when
 * there is a connection — and a banner for it would sit on the dashboard every time
 * someone works without signal, teaching them to ignore the one that matters.
 */
sealed interface SyncAttention {
    data object SessionExpired : SyncAttention

    data class UploadFailed(val count: Int) : SyncAttention

    companion object {
        /** Kept in step with SyncRepositoryImpl.AUTH_EXPIRED_STATUS. */
        private const val AUTH_EXPIRED_PREFIX = "Session expired"

        fun of(health: SyncHealth?, lastSyncStatus: String?): SyncAttention? = when {
            lastSyncStatus?.startsWith(AUTH_EXPIRED_PREFIX) == true -> SessionExpired
            (health?.failedCount ?: 0) > 0 -> UploadFailed(health!!.failedCount)
            else -> null
        }
    }
}
