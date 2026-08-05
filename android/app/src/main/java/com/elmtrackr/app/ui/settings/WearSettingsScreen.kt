package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.wear.WearConnectionStatus
import com.elmtrackr.app.wear.WearWatch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun WearSettingsScreen(
    onBack: () -> Unit,
    viewModel: WearSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncEnabled by viewModel.syncEnabled.collectAsState()
    WearSettingsContent(
        uiState = uiState,
        syncEnabled = syncEnabled,
        onBack = onBack,
        onRefresh = viewModel::refreshStatus,
        onSyncEnabledChange = viewModel::setSyncEnabled,
        onSyncNow = viewModel::syncNow,
    )
}

@Composable
internal fun WearSettingsContent(
    uiState: WearSettingsUiState,
    syncEnabled: Boolean?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    val status = uiState.status
    val connected = status?.anyWatchConnected == true
    val syncOn = syncEnabled == true
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = stringResource(R.string.settings_wear_title), onBack = onBack) }

        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WatchGraphic(
                    connected = connected && syncOn,
                    appInstalled = status?.anyWatchWithApp == true,
                    modifier = Modifier.size(WatchGraphicSize),
                )
                Spacer(Modifier.height(Spacing.s8))
                Text(
                    text = statusHeadline(status, syncOn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                status?.featuredWatch?.let { watch ->
                    Text(
                        text = watch.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item {
            SettingsSectionCard(title = stringResource(R.string.settings_wear_section_watch)) {
                when {
                    status == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = Spacing.s6),
                    ) {
                        CircularProgressIndicator(Modifier.size(Spacing.s16), strokeWidth = Spacing.s2)
                        Spacer(Modifier.width(Spacing.s10))
                        Text(
                            stringResource(R.string.settings_wear_checking),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    !status.wearApiAvailable -> Text(
                        stringResource(R.string.settings_wear_status_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    status.watches.isEmpty() -> Text(
                        stringResource(R.string.settings_wear_pairing_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Column {
                        status.watches.forEach { watch ->
                            SettingsInfoRow(
                                label = watch.name,
                                value = watchDetailLine(watch),
                            )
                        }
                        if (!status.anyWatchWithApp) {
                            Spacer(Modifier.height(Spacing.s6))
                            Text(
                                stringResource(R.string.settings_wear_app_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (status?.wearApiAvailable != false) {
                    Spacer(Modifier.height(Spacing.s10))
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !uiState.refreshing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.refreshing) {
                            CircularProgressIndicator(Modifier.size(Spacing.s16), strokeWidth = Spacing.s2)
                            Spacer(Modifier.width(Spacing.s8))
                        }
                        Text(stringResource(R.string.settings_wear_refresh))
                    }
                }
            }
        }

        item {
            SettingsSectionCard(title = stringResource(R.string.settings_wear_section_sync)) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_wear_sync_toggle),
                    description = stringResource(R.string.settings_wear_sync_toggle_desc),
                    checked = syncOn,
                    onCheckedChange = { if (syncEnabled != null) onSyncEnabledChange(it) },
                )
                if (syncEnabled == false) {
                    Spacer(Modifier.height(Spacing.s6))
                    Text(
                        stringResource(R.string.settings_wear_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.s10))
                OutlinedButton(
                    onClick = onSyncNow,
                    enabled = syncOn && connected && !uiState.syncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.syncing) {
                        CircularProgressIndicator(Modifier.size(Spacing.s16), strokeWidth = Spacing.s2)
                        Spacer(Modifier.width(Spacing.s8))
                    }
                    Text(stringResource(R.string.settings_wear_sync_now))
                }
                uiState.lastSyncSentAtMs?.let { sentAt ->
                    Spacer(Modifier.height(Spacing.s6))
                    Text(
                        stringResource(R.string.settings_wear_sync_sent, formatSyncTime(sentAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.s32)) }
    }
}

@Composable
private fun statusHeadline(status: WearConnectionStatus?, syncOn: Boolean): String = when {
    status == null -> stringResource(R.string.settings_wear_checking)
    !status.wearApiAvailable -> stringResource(R.string.settings_wear_status_unavailable_short)
    !status.anyWatchConnected -> stringResource(R.string.settings_wear_status_no_watch)
    !syncOn -> stringResource(R.string.settings_wear_status_sync_off)
    status.anyWatchWithApp -> stringResource(R.string.settings_wear_status_connected)
    else -> stringResource(R.string.settings_wear_status_connected_no_app)
}

@Composable
private fun watchDetailLine(watch: WearWatch): String {
    val proximity = stringResource(
        if (watch.isNearby) R.string.settings_wear_watch_nearby else R.string.settings_wear_watch_remote,
    )
    return if (watch.appInstalled) {
        "$proximity · ${stringResource(R.string.settings_wear_app_installed)}"
    } else {
        proximity
    }
}

private fun formatSyncTime(atMs: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(atMs))

@Preview(showBackground = true)
@Composable
private fun WearSettingsPreview() {
    ElmTrackrTheme {
        WearSettingsContent(
            uiState = WearSettingsUiState(
                status = WearConnectionStatus.from(
                    connectedNodes = listOf(Triple("node1", "Pixel Watch 3", true)),
                    capabilityNodeIds = setOf("node1"),
                ),
            ),
            syncEnabled = true,
            onBack = {},
            onRefresh = {},
            onSyncEnabledChange = {},
            onSyncNow = {},
        )
    }
}
