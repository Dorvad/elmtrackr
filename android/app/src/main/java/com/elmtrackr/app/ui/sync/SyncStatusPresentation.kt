package com.elmtrackr.app.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.elmtrackr.app.ui.theme.AuroraOvertimeBg
import com.elmtrackr.app.ui.theme.AuroraOvertimeInk
import com.elmtrackr.app.ui.theme.auroraOvertimeBackground
import com.elmtrackr.app.ui.theme.auroraOvertimeInk
import com.elmtrackr.app.ui.theme.auroraSecondaryText
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import com.elmtrackr.app.ui.theme.auroraSyncedPillBackground

data class SyncStatusUi(
    val shortLabel: String,
    val detail: String,
    val contentDescription: String,
    val color: Color,
    val background: Color,
    val showSpinner: Boolean = false,
    val isActionable: Boolean = false,
)

internal data class SyncStatusCopy(
    val shortLabel: String,
    val detail: String,
    val contentDescription: String,
    val showSpinner: Boolean = false,
    val isActionable: Boolean = false,
    val useSyncedPalette: Boolean = false,
    val useLocalPalette: Boolean = false,
)

internal fun resolveSyncStatusCopy(
    isRemoteConfigured: Boolean,
    isOnline: Boolean,
    isSyncing: Boolean,
    pendingCount: Int,
    lastSyncStatus: String?,
    syncError: String?,
): SyncStatusCopy {
    if (!isRemoteConfigured) {
        return SyncStatusCopy(
            shortLabel = "Local only",
            detail = "Cloud sync is not configured. Data stays on this device.",
            contentDescription = "Local only mode. Cloud sync is not configured.",
            useLocalPalette = true,
        )
    }
    if (isSyncing) {
        return SyncStatusCopy(
            shortLabel = "Syncing…",
            detail = "Uploading your latest changes.",
            contentDescription = "Syncing your data",
            showSpinner = true,
        )
    }
    if (!isOnline) {
        val pendingSuffix = if (pendingCount > 0) " $pendingCount changes will upload when you're back online." else ""
        return SyncStatusCopy(
            shortLabel = "No network",
            detail = "You're offline.$pendingSuffix".trim(),
            contentDescription = "No network connection.$pendingSuffix",
            isActionable = pendingCount > 0 || syncError != null,
        )
    }
    if (syncError != null) {
        return SyncStatusCopy(
            shortLabel = "Couldn't sync",
            detail = "Tap to retry. $syncError",
            contentDescription = "Couldn't sync. Tap to retry. $syncError",
            isActionable = true,
        )
    }
    if (pendingCount > 0) {
        val noun = if (pendingCount == 1) "change" else "changes"
        return SyncStatusCopy(
            shortLabel = "$pendingCount waiting",
            detail = "$pendingCount $noun waiting to upload.",
            contentDescription = "$pendingCount $noun waiting to upload. Tap to sync now.",
            isActionable = true,
        )
    }
    if (lastSyncStatus?.startsWith("partial:") == true) {
        return SyncStatusCopy(
            shortLabel = "Couldn't sync",
            detail = "Some items failed last time. Tap to retry.",
            contentDescription = "Couldn't sync all changes. Tap to retry.",
            isActionable = true,
        )
    }
    return SyncStatusCopy(
        shortLabel = "All synced",
        detail = "Your data is up to date in the cloud.",
        contentDescription = "All synced. Your data is up to date.",
        useSyncedPalette = true,
    )
}

@Composable
fun resolveSyncStatus(
    isRemoteConfigured: Boolean,
    isOnline: Boolean,
    isSyncing: Boolean,
    pendingCount: Int,
    lastSyncStatus: String?,
    syncError: String?,
): SyncStatusUi {
    val copy = resolveSyncStatusCopy(
        isRemoteConfigured,
        isOnline,
        isSyncing,
        pendingCount,
        lastSyncStatus,
        syncError,
    )
    val (color, background) = when {
        copy.useLocalPalette -> auroraSecondaryText() to auroraSurfaceSub()
        copy.useSyncedPalette -> Color(0xFF5B4DF2) to auroraSyncedPillBackground()
        else -> auroraOvertimeInk() to auroraOvertimeBackground()
    }
    return SyncStatusUi(
        shortLabel = copy.shortLabel,
        detail = copy.detail,
        contentDescription = copy.contentDescription,
        color = color,
        background = background,
        showSpinner = copy.showSpinner,
        isActionable = copy.isActionable,
    )
}
