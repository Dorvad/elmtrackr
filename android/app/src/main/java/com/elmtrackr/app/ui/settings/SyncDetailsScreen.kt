package com.elmtrackr.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.data.sync.SyncDetails
import com.elmtrackr.app.data.sync.SyncFailedRow
import com.elmtrackr.app.data.sync.SyncPendingByType
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.theme.Spacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_BACKUP_IMPORT_BYTES = 25 * 1024 * 1024

@Composable
fun SyncDetailsScreen(
    onBack: () -> Unit,
    viewModel: SyncDetailsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AuroraListScreen {
        when (val state = uiState) {
            SyncDetailsUiState.Loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            is SyncDetailsUiState.Error -> ErrorState(state.message, onRetry = viewModel::retryAll)
            is SyncDetailsUiState.Ready -> {
                LaunchedEffect(state.message) {
                    if (state.message != null) {
                        kotlinx.coroutines.delay(2500)
                        viewModel.clearMessage()
                    }
                }
                val importPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        viewModel.importBackup {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                input.readBytes()
                                    .takeIf { it.size <= MAX_BACKUP_IMPORT_BYTES }
                                    ?.toString(Charsets.UTF_8)
                            }
                        }
                    }
                }
                SyncDetailsContent(
                    details = state.details,
                    isSyncing = state.isSyncing,
                    isExporting = state.isExporting,
                    isImporting = state.isImporting,
                    message = state.message,
                    onBack = onBack,
                    onRetryAll = viewModel::retryAll,
                    onExportBackup = {
                        viewModel.exportBackup { json ->
                            val stamp = java.time.LocalDate.now().toString()
                            SyncBackupShare.shareJson(context, json, "elmtrackr-backup-$stamp.json")
                        }
                    },
                    onImportBackup = {
                        // JSON backups often come back as text/* or octet-stream from
                        // Drive and file managers; accept broad types and validate content.
                        importPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    },
                )
            }
        }
    }
}

@Composable
internal fun SyncDetailsContent(
    details: SyncDetails,
    isSyncing: Boolean,
    isExporting: Boolean,
    isImporting: Boolean,
    message: String?,
    onBack: () -> Unit,
    onRetryAll: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault())

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = "Sync details", onBack = onBack) }
        item {
            Text(
                "See what is stored on this device, what is waiting to upload, and what failed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let {
            item {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SettingsSectionCard(title = "LAST SUCCESSFUL SYNC") {
                val label = details.lastSuccessfulSyncAt?.atZone(ZoneId.systemDefault())?.format(formatter)
                    ?: "Not synced on this device yet"
                SettingsInfoRow("When", label)
                details.lastSyncStatus?.let { status ->
                    Spacer(Modifier.height(8.dp))
                    SettingsInfoRow("Latest run", SyncStatusText.format(status) ?: status)
                }
            }
        }
        item {
            SettingsSectionCard(title = "PENDING BY TYPE") {
                if (details.totalPending == 0) {
                    Text(
                        "All local changes are synced.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    details.pendingByType.forEachIndexed { index, row ->
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        PendingTypeRow(row)
                    }
                }
            }
        }
        if (details.failedRows.isNotEmpty()) {
            item {
                SettingsSectionCard(title = "FAILED ROWS") {
                    details.failedRows.forEachIndexed { index, row ->
                        if (index > 0) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(10.dp))
                        }
                        FailedRowItem(row)
                    }
                }
            }
        }
        item {
            SettingsSectionCard(title = "ACTIONS") {
                ElmGradientButton(
                    onClick = onRetryAll,
                    enabled = !isSyncing && !isExporting,
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSyncing) "Syncing…" else "Retry all")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onExportBackup,
                    enabled = !isSyncing && !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isExporting) "Preparing backup…" else "Export local backup")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "JSON snapshot of local tasks, shifts, claims, settings, and pay profiles for support.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onImportBackup,
                    enabled = !isSyncing && !isExporting && !isImporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isImporting) "Importing…" else "Import local backup")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Restore a previously exported backup file. Existing items are never overwritten.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun PendingTypeRow(row: SyncPendingByType) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(row.entityType.label, fontWeight = FontWeight.SemiBold)
            Text(
                "${row.syncedCount} synced on device",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (row.pendingCount == 0) "Up to date" else "${row.pendingCount} pending",
            color = if (row.pendingCount == 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FailedRowItem(row: SyncFailedRow) {
    Column(Modifier.fillMaxWidth()) {
        Text(row.label, fontWeight = FontWeight.SemiBold)
        Text(
            "${row.entityType.label} · ${row.syncStatus.name.replace('_', ' ').lowercase()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.errorMessage?.let { error ->
            Spacer(Modifier.height(4.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
