package com.elmtrackr.app.ui.refunds

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.common.AppTimePickerDialog
import com.elmtrackr.app.ui.common.appLocale
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.ui.shifts.RideProviderSelector
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import com.elmtrackr.app.ui.common.LocalWorkZone
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val refundTimeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun refundDateFmt(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", appLocale())

@Composable
fun RefundClaimsSection(
    shift: Shift,
    currency: CurrencyCode,
    modifier: Modifier = Modifier,
    viewModel: RefundClaimViewModel = hiltViewModel(),
) {
    LaunchedEffect(shift) { viewModel.setShift(shift) }
    val state by viewModel.uiState.collectAsState()
    var pendingDeleteClaimId by rememberSaveable { mutableStateOf<String?>(null) }

    pendingDeleteClaimId?.let { claimId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteClaimId = null },
            title = { Text(stringResource(R.string.refunds_delete_dialog_title)) },
            text = { Text(stringResource(R.string.refunds_delete_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteClaimId = null
                    viewModel.deleteClaim(claimId)
                }) { Text(stringResource(R.string.refunds_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteClaimId = null }) {
                    Text(stringResource(R.string.refunds_cancel))
                }
            },
        )
    }

    val launchDocumentScanner = rememberDocumentScannerLauncher(
        onScanStarted = viewModel::onDocumentScannerLaunched,
        onScanFailed = viewModel::onDocumentScannerFailed,
        onScanResult = viewModel::onDocumentScanned,
    )

    LaunchedEffect(state.launchDocumentScanner) {
        if (state.launchDocumentScanner) {
            launchDocumentScanner()
        }
    }

    state.receiptReview?.let { review ->
        ReceiptReviewDialog(
            review = review,
            defaultCurrency = currency,
            onMerchantChange = viewModel::updateReceiptReviewMerchant,
            onAmountChange = viewModel::updateReceiptReviewAmount,
            onCurrencyChange = viewModel::updateReceiptReviewCurrency,
            onDismiss = viewModel::dismissReceiptReview,
            onSave = viewModel::saveReceiptReview,
        )
    }

    if (state.isProcessingReceipt) {
        // Dismissible on purpose: OCR can stall on a large image, and a modal
        // spinner with no exit previously required force-stopping the app.
        Dialog(onDismissRequest = viewModel::cancelReceiptProcessing) {
            Surface(shape = RoundedCornerShape(CornerRadius.Medium)) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.refunds_reading_receipt))
                    }
                    TextButton(
                        onClick = viewModel::cancelReceiptProcessing,
                        modifier = Modifier.align(Alignment.End),
                    ) { Text(stringResource(R.string.refunds_cancel)) }
                }
            }
        }
    }

    state.receiptPreview?.let { preview ->
        ReceiptPreviewDialog(
            preview = preview,
            onDismiss = viewModel::dismissReceiptPreview,
        )
    }

    state.camera?.let { camera ->
        Dialog(
            onDismissRequest = viewModel::cancelCameraCapture,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            CameraScreen(
                outputFile = File(camera.outputPath),
                onPhotoCaptured = viewModel::photoCaptured,
                onCaptureFailed = viewModel::photoCaptureFailed,
                onClose = viewModel::cancelCameraCapture,
            )
        }
    }

    state.form?.let { form ->
        RefundClaimFormDialog(
            form = form,
            currency = currency,
            isSaving = state.isSaving,
            onProviderChange = viewModel::updateProvider,
            onAmountChange = viewModel::updateAmount,
            onRideAtChange = viewModel::updateRideAt,
            onNotesChange = viewModel::updateNotes,
            onTakePhoto = viewModel::startCameraCapture,
            onScanReceipt = viewModel::requestDocumentScan,
            onPickPhoto = viewModel::importReceiptPhoto,
            onRemovePendingPhoto = viewModel::removePendingPhoto,
            onViewCloudReceipt = viewModel::openReceipt,
            onViewLocalReceipt = viewModel::openLocalReceipt,
            onDismiss = viewModel::dismissForm,
            onSave = viewModel::saveForm,
        )
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.errorMessage?.let { MessageCard(it.asString(), isError = true, onDismiss = viewModel::clearMessages) }
        state.noticeMessage?.let { MessageCard(it.asString(), isError = false, onDismiss = viewModel::clearMessages) }

        val directions = listOf(
            RefundDirection.TO_WORK to state.toEligibility,
            RefundDirection.FROM_WORK to state.fromEligibility,
        ).mapNotNull { (direction, eligibility) ->
            eligibility?.takeIf { it.eligible }?.let { direction to it }
        }

        directions.forEach { (direction, eligibility) ->
            RefundClaimCard(
                shift = shift,
                claims = state.claims.filter { it.direction == direction },
                direction = direction,
                eligibility = eligibility,
                currency = currency,
                localReceiptClaimIds = state.localReceiptsByClaimId.keys,
                deletingClaimId = state.deletingClaimId,
                onAdd = { viewModel.openForm(direction) },
                onEdit = { edited -> viewModel.openForm(direction, edited) },
                onDelete = { deleted -> pendingDeleteClaimId = deleted.id },
                onNoRide = { viewModel.updateRefundAction(RefundAction.NO_RIDE_TAKEN) },
                onRemindLater = { viewModel.updateRefundAction(RefundAction.REMIND_LATER) },
                onUndoAction = { viewModel.updateRefundAction(null) },
                onViewReceipt = viewModel::openReceiptForClaim,
            )
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(CornerRadius.Medium),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(message, color = content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.refunds_ok)) }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RefundClaimCard(
    shift: Shift,
    claims: List<RefundClaim>,
    direction: RefundDirection,
    eligibility: RefundPolicy.Eligibility,
    currency: CurrencyCode,
    localReceiptClaimIds: Set<String>,
    deletingClaimId: String?,
    onAdd: () -> Unit,
    onEdit: (RefundClaim) -> Unit,
    onDelete: (RefundClaim) -> Unit,
    onNoRide: () -> Unit,
    onRemindLater: () -> Unit,
    onUndoAction: () -> Unit,
    onViewReceipt: (RefundClaim) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(CornerRadius.Medium),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(CornerRadius.Small)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = stringResource(R.string.refunds_claim_icon_description), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(directionLabelRes(direction)), fontWeight = FontWeight.Bold)
                    Text(
                        if (claims.isEmpty()) {
                            stringResource(R.string.refunds_no_rides_yet)
                        } else {
                            pluralStringResource(R.plurals.refunds_rides_saved, claims.size, claims.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                eligibility.reasons.forEach { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            claims.forEachIndexed { index, claim ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RefundClaimEntry(
                    claim = claim,
                    currency = currency,
                    hasLocalReceipt = claim.id in localReceiptClaimIds,
                    isDeleting = deletingClaimId == claim.id,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onViewReceipt = onViewReceipt,
                )
            }

            // A shift can hold any number of rides, so adding stays available
            // after the first one is saved.
            if (claims.isEmpty()) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (shift.refundAction == RefundAction.REMIND_LATER) R.string.refunds_add_receipt_now else R.string.refunds_add_ride))
                }
                if (direction == RefundDirection.FROM_WORK) {
                    RefundActionRow(
                        action = shift.refundAction,
                        onNoRide = onNoRide,
                        onRemindLater = onRemindLater,
                        onUndo = onUndoAction,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.refunds_add_another_ride))
                }
            }
        }
    }
}

@Composable
private fun RefundClaimEntry(
    claim: RefundClaim,
    currency: CurrencyCode,
    hasLocalReceipt: Boolean,
    isDeleting: Boolean,
    onEdit: (RefundClaim) -> Unit,
    onDelete: (RefundClaim) -> Unit,
    onViewReceipt: (RefundClaim) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(
                R.string.refunds_claim_summary,
                claim.provider.name.lowercase().replaceFirstChar { it.uppercase() },
                MoneyFormatter.format(claim.amount, currency),
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            // The work zone, not the device's: a ride at 23:30 work time reads
            // as the next day in a zone an hour ahead, so a traveller's claim
            // list disagreed with the shift it belongs to.
            claim.rideAt.atZone(LocalWorkZone.current).let { zdt ->
                stringResource(
                    R.string.refunds_date_at_time,
                    zdt.format(refundDateFmt()),
                    zdt.format(refundTimeFmt),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        claim.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onEdit(claim) },
                enabled = !isDeleting,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.refunds_edit)) }
            if (claim.receiptPath != null || hasLocalReceipt) {
                TextButton(
                    onClick = { onViewReceipt(claim) },
                    enabled = !isDeleting,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.refunds_view_receipt)) }
            }
            TextButton(
                onClick = { onDelete(claim) },
                enabled = !isDeleting,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (isDeleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.refunds_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RefundActionRow(
    action: RefundAction?,
    onNoRide: () -> Unit,
    onRemindLater: () -> Unit,
    onUndo: () -> Unit,
) {
    when (action) {
        null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNoRide, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.refunds_no_ride)) }
            OutlinedButton(onClick = onRemindLater, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.refunds_remind_later)) }
        }
        RefundAction.NO_RIDE_TAKEN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.refunds_marked_no_ride), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.refunds_undo)) }
        }
        RefundAction.REMIND_LATER -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.refunds_reminder_kept), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onNoRide, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.refunds_no_ride)) }
            TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.refunds_undo)) }
        }
        RefundAction.SUBMITTED -> Unit
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RefundClaimFormDialog(
    form: RefundClaimFormUiState,
    currency: CurrencyCode,
    isSaving: Boolean,
    onProviderChange: (com.elmtrackr.app.domain.model.RefundProvider) -> Unit,
    onAmountChange: (String) -> Unit,
    onRideAtChange: (Long) -> Unit,
    onNotesChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onScanReceipt: () -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onRemovePendingPhoto: () -> Unit,
    onViewCloudReceipt: (String) -> Unit,
    onViewLocalReceipt: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    // Provided by the shift form that hosts this dialog. Saving in the device
    // zone stored the ride against the wrong day near midnight, and did it
    // silently — the date shown afterwards was wrong in the same direction, so
    // nothing looked out of place.
    val zone = LocalWorkZone.current
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerWrapper(
            currentMillis = form.rideAtMillis,
            onConfirm = { millis ->
                onRideAtChange(applyDate(form.rideAtMillis, millis, zone))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TimePickerWrapper(
            currentMillis = form.rideAtMillis,
            zone = zone,
            onConfirm = { hour, minute ->
                onRideAtChange(applyTime(form.rideAtMillis, hour, minute, zone))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.Large),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(
                        if (form.claimId == null) R.string.refunds_form_title_add else R.string.refunds_form_title_edit,
                        stringResource(directionLabelRes(form.direction)).lowercase(),
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    stringResource(R.string.refunds_form_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                RideProviderSelector(selected = form.provider, onSelect = onProviderChange)

                OutlinedTextField(
                    value = form.amountText,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.refunds_amount_label, currency.symbol)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                RefundDateTimeRow(
                    millis = form.rideAtMillis,
                    zone = zone,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
                )

                OutlinedTextField(
                    value = form.notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.refunds_notes_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                ReceiptPhotoArea(
                    pendingPhotoPath = form.pendingPhotoPath,
                    pendingPhotoName = form.pendingPhotoName,
                    existingReceiptPath = form.existingReceiptPath,
                    localReceiptImagePath = form.localReceiptImagePath,
                    onTakePhoto = onTakePhoto,
                    onScanReceipt = onScanReceipt,
                    onPickPhoto = onPickPhoto,
                    onRemovePendingPhoto = onRemovePendingPhoto,
                    onViewCloudReceipt = onViewCloudReceipt,
                    onViewLocalReceipt = onViewLocalReceipt,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onDismiss, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.refunds_cancel))
                    }
                    Button(
                        onClick = onSave,
                        enabled = !isSaving && (form.amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (form.claimId == null) R.string.refunds_save else R.string.refunds_update))
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptPhotoArea(
    pendingPhotoPath: String?,
    pendingPhotoName: String?,
    existingReceiptPath: String?,
    localReceiptImagePath: String?,
    onTakePhoto: () -> Unit,
    onScanReceipt: () -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onRemovePendingPhoto: () -> Unit,
    onViewCloudReceipt: (String) -> Unit,
    onViewLocalReceipt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickPhoto(uri)
    }
    val bitmap = remember(pendingPhotoPath) {
        pendingPhotoPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = stringResource(R.string.refunds_receipt_photo), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.refunds_receipt_photo), fontWeight = FontWeight.Bold)
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.refunds_pending_receipt_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius.Medium)),
                )
                Text(
                    pendingPhotoName ?: stringResource(R.string.refunds_new_receipt_photo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(CornerRadius.Medium))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius.Medium)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = stringResource(if (existingReceiptPath == null) R.string.refunds_no_receipt_attached else R.string.refunds_existing_receipt_attached),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(
                                when {
                                    pendingPhotoPath != null || localReceiptImagePath != null -> R.string.refunds_receipt_attached
                                    existingReceiptPath != null -> R.string.refunds_cloud_receipt_attached
                                    else -> R.string.refunds_no_receipt_yet
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScanReceipt, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.refunds_scan_receipt))
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.refunds_attach_receipt))
                }
            }

            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (pendingPhotoPath == null) R.string.refunds_use_device_camera else R.string.refunds_retake_with_camera))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pendingPhotoPath != null) {
                    TextButton(onClick = onRemovePendingPhoto, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.refunds_remove_receipt_photo), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.refunds_remove_photo), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = { onViewLocalReceipt(pendingPhotoPath) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.refunds_view_receipt), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.refunds_view_receipt))
                    }
                }
                if (pendingPhotoPath == null && existingReceiptPath != null) {
                    TextButton(onClick = { onViewCloudReceipt(existingReceiptPath) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.refunds_view_receipt), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.refunds_view_cloud_receipt))
                    }
                }
            }
        }
    }
}

@Composable
private fun RefundDateTimeRow(
    millis: Long,
    zone: ZoneId,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    val zdt = Instant.ofEpochMilli(millis).atZone(zone)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = zdt.format(refundDateFmt()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.refunds_ride_date)) },
            trailingIcon = {
                IconButton(onClick = onPickDate, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.refunds_pick_ride_date), modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.weight(1.5f),
        )
        OutlinedTextField(
            value = zdt.format(refundTimeFmt),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.refunds_ride_time)) },
            trailingIcon = {
                IconButton(onClick = onPickTime, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.refunds_pick_ride_time), modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerWrapper(currentMillis: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    // See the note on `zone` above — the picker has to open on the same day the
    // rest of the form is working in.
    val initUtcMidnight = Instant.ofEpochMilli(currentMillis)
        .atZone(LocalWorkZone.current).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initUtcMidnight)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text(stringResource(R.string.refunds_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.refunds_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimePickerWrapper(
    currentMillis: Long,
    zone: ZoneId,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zdt = Instant.ofEpochMilli(currentMillis).atZone(zone)
    AppTimePickerDialog(
        initialHour = zdt.hour,
        initialMinute = zdt.minute,
        title = stringResource(R.string.refunds_select_time),
        confirmLabel = stringResource(R.string.refunds_ok),
        cancelLabel = stringResource(R.string.refunds_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

private fun applyDate(currentMillis: Long, utcMidnightMillis: Long, zone: ZoneId): Long {
    val currentTime = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalTime()
    val newDate = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return LocalDateTime.of(newDate, currentTime).atZone(zone).toInstant().toEpochMilli()
}

private fun applyTime(currentMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val currentDate = Instant.ofEpochMilli(currentMillis).atZone(zone).toLocalDate()
    return LocalDateTime.of(currentDate, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

@StringRes
private fun directionLabelRes(direction: RefundDirection): Int = when (direction) {
    RefundDirection.TO_WORK -> R.string.refunds_direction_to_work
    RefundDirection.FROM_WORK -> R.string.refunds_direction_from_work
}
