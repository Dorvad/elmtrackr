package com.elmtrackr.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.data.sync.SyncDetails
import com.elmtrackr.app.data.sync.SyncEntityType
import com.elmtrackr.app.data.sync.SyncFailedRow
import com.elmtrackr.app.data.sync.SyncPendingByType
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.ui.components.states.ErrorState
import com.elmtrackr.app.ui.design.AuroraListScreen
import com.elmtrackr.app.ui.design.AuroraScreenHeader
import com.elmtrackr.app.ui.design.ElmCard
import com.elmtrackr.app.ui.design.ElmCardPadded
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.design.ElmIconTile
import com.elmtrackr.app.ui.design.ElmIconTileSize
import com.elmtrackr.app.ui.design.ElmSectionHeader
import com.elmtrackr.app.ui.design.auroraRowClickable
import com.elmtrackr.app.ui.projects.StatusPill
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.Spacing
import com.elmtrackr.app.ui.theme.auroraSemantics
import com.elmtrackr.app.ui.theme.auroraStatusPillInk
import com.elmtrackr.app.ui.theme.auroraSurfaceSub
import com.elmtrackr.app.ui.theme.auroraSyncedPillBackground
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    message: UiText?,
    onBack: () -> Unit,
    onRetryAll: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            AuroraScreenHeader(
                title = stringResource(R.string.settings_sync_details),
                subtitle = stringResource(R.string.settings_sync_details_tagline),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
            )
        }
        message?.let {
            item {
                Text(it.asString(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SyncHeroCard(
                details = details,
                isSyncing = isSyncing,
                isExporting = isExporting,
                onRetryAll = onRetryAll,
            )
        }
        item { ElmSectionHeader(stringResource(R.string.settings_section_on_device)) }
        item {
            ElmCard {
                details.pendingByType.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PendingTypeRow(row)
                }
            }
        }
        items(details.failedRows.size) { index ->
            FailedRowCard(
                row = details.failedRows[index],
                retryEnabled = !isSyncing && !isExporting,
                onRetry = onRetryAll,
            )
        }
        item { ElmSectionHeader(stringResource(R.string.settings_section_backup)) }
        item {
            BackupCard(
                isSyncing = isSyncing,
                isExporting = isExporting,
                isImporting = isImporting,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
            )
        }
        item { Spacer(Modifier.height(Spacing.s24)) }
    }
}

/**
 * One glanceable answer to "am I synced?": what's waiting, when the last sync
 * ran, and the button that fixes it — instead of an Actions card at the bottom.
 */
@Composable
private fun SyncHeroCard(
    details: SyncDetails,
    isSyncing: Boolean,
    isExporting: Boolean,
    onRetryAll: () -> Unit,
) {
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm", appLocale())
    ElmCardPadded {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ElmIconTile(
                background = auroraSyncedPillBackground(),
                size = ElmIconTileSize.Large,
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = auroraStatusPillInk(AuroraIndigo),
                )
            }
            Column(Modifier.padding(start = Spacing.s12)) {
                Text(
                    if (details.totalPending == 0) {
                        stringResource(R.string.settings_sync_hero_all_synced)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_sync_hero_waiting,
                            details.totalPending,
                            details.totalPending,
                        )
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                val lastSynced = details.lastSuccessfulSyncAt
                    ?.atZone(ZoneId.systemDefault())
                    ?.format(formatter)
                    ?.let { stringResource(R.string.settings_sync_last_synced, it) }
                    ?: stringResource(R.string.settings_not_synced_yet)
                val attention = details.totalFailed.takeIf { it > 0 }?.let {
                    pluralStringResource(R.plurals.settings_sync_rows_attention, it, it)
                }
                Text(
                    listOfNotNull(lastSynced, attention).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s14))
        ElmGradientButton(
            onClick = onRetryAll,
            enabled = !isSyncing && !isExporting,
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Spacing.s18),
                    strokeWidth = Spacing.s2,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(Spacing.s8))
            }
            Text(stringResource(if (isSyncing) R.string.settings_syncing else R.string.settings_sync_now))
        }
    }
}

@Composable
private fun PendingTypeRow(row: SyncPendingByType) {
    val pendingLabel = stringResource(R.string.settings_count_pending, row.pendingCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s16, vertical = Spacing.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElmIconTile(background = auroraSurfaceSub(), size = ElmIconTileSize.Small) {
            Icon(
                syncEntityTypeIcon(row.entityType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.s18),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.s12),
        ) {
            Text(syncEntityTypeLabel(row.entityType), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.settings_synced_on_device, row.syncedCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Red means failed and nothing else: pending is a neutral count pill,
        // synced is a quiet check.
        if (row.pendingCount > 0) {
            StatusPill(
                text = row.pendingCount.toString(),
                accent = AuroraIndigo,
                spoken = pendingLabel,
            )
        } else {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.settings_up_to_date),
                tint = auroraSemantics.successInk,
                modifier = Modifier
                    .size(Spacing.s20)
                    .background(auroraSemantics.successContainer, CircleShape)
                    .padding(Spacing.s2),
            )
        }
    }
}

/** A failure explains itself in plain words and carries its fix. */
@Composable
private fun FailedRowCard(
    row: SyncFailedRow,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
) {
    val retryLabel = stringResource(R.string.settings_retry)
    ElmCard(
        cornerRadius = CornerRadius.Medium,
        borderColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s16, vertical = Spacing.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ElmIconTile(
                background = MaterialTheme.colorScheme.errorContainer,
                size = ElmIconTileSize.Small,
            ) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(Spacing.s18),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.s12),
            ) {
                Text(row.label, fontWeight = FontWeight.SemiBold)
                Text(
                    row.errorMessage ?: syncEntityTypeLabel(row.entityType),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                retryLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .auroraRowClickable(onClick = onRetry, enabled = retryEnabled)
                    .semantics { contentDescription = retryLabel }
                    .background(auroraSurfaceSub(), RoundedCornerShape(50))
                    .padding(horizontal = Spacing.s14, vertical = Spacing.s10),
            )
        }
    }
}

/** Backup is its own job: an export/import tile pair with one shared hint. */
@Composable
private fun BackupCard(
    isSyncing: Boolean,
    isExporting: Boolean,
    isImporting: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    ElmCardPadded {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s10)) {
            BackupTile(
                label = stringResource(
                    if (isExporting) R.string.settings_preparing_backup else R.string.settings_export_backup,
                ),
                icon = Icons.Outlined.FileUpload,
                busy = isExporting,
                enabled = !isSyncing && !isExporting,
                onClick = onExportBackup,
                modifier = Modifier.weight(1f),
            )
            BackupTile(
                label = stringResource(
                    if (isImporting) R.string.settings_importing else R.string.settings_import_backup,
                ),
                icon = Icons.Outlined.FileDownload,
                busy = isImporting,
                enabled = !isSyncing && !isExporting && !isImporting,
                onClick = onImportBackup,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.s10))
        Text(
            stringResource(R.string.settings_backup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BackupTile(
    label: String,
    icon: ImageVector,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .auroraRowClickable(onClick = onClick, enabled = enabled)
            .background(auroraSurfaceSub(), RoundedCornerShape(CornerRadius.Medium))
            .padding(vertical = Spacing.s12)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s6),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(Spacing.s20), strokeWidth = Spacing.s2)
        } else {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(Spacing.s20))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}

private fun syncEntityTypeIcon(type: SyncEntityType): ImageVector = when (type) {
    SyncEntityType.TASKS -> Icons.Outlined.Sell
    SyncEntityType.SHIFTS -> Icons.Outlined.Schedule
    SyncEntityType.REFUND_CLAIMS -> Icons.AutoMirrored.Outlined.ReceiptLong
    SyncEntityType.USER_SETTINGS -> Icons.Outlined.Tune
    SyncEntityType.COMPENSATION_PROFILES -> Icons.Outlined.CreditCard
}

@Composable
private fun syncEntityTypeLabel(type: SyncEntityType): String = stringResource(
    when (type) {
        SyncEntityType.TASKS -> R.string.sync_entity_tasks
        SyncEntityType.SHIFTS -> R.string.sync_entity_shifts
        SyncEntityType.REFUND_CLAIMS -> R.string.sync_entity_refund_claims
        SyncEntityType.USER_SETTINGS -> R.string.sync_entity_settings
        SyncEntityType.COMPENSATION_PROFILES -> R.string.sync_entity_pay_profiles
    },
)
