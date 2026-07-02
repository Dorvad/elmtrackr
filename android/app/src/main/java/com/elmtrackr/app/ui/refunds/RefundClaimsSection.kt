package com.elmtrackr.app.ui.refunds

import android.graphics.BitmapFactory
import android.net.Uri
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
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val refundDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val refundTimeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val refundDateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.getDefault())

@Composable
fun RefundClaimsSection(
    shift: Shift,
    currency: CurrencyCode,
    modifier: Modifier = Modifier,
    viewModel: RefundClaimViewModel = hiltViewModel(),
) {
    LaunchedEffect(shift) { viewModel.setShift(shift) }
    val state by viewModel.uiState.collectAsState()

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
        Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(CornerRadius.Medium)) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Reading receipt text…")
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
        state.errorMessage?.let { MessageCard(it, isError = true, onDismiss = viewModel::clearMessages) }
        state.noticeMessage?.let { MessageCard(it, isError = false, onDismiss = viewModel::clearMessages) }

        val directions = buildList<Pair<RefundDirection, RefundPolicy.Eligibility>> {
            state.toEligibility?.takeIf { it.eligible }?.let { add(RefundDirection.TO_WORK to it) }
            state.fromEligibility?.takeIf { it.eligible }?.let { add(RefundDirection.FROM_WORK to it) }
        }

        if (directions.isEmpty()) {
            Text(
                "This shift is not eligible for travel reimbursement.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            directions.forEach { (direction, eligibility) ->
                val claim = state.claims.firstOrNull { it.direction == direction }
                RefundClaimCard(
                    shift = shift,
                    claim = claim,
                    direction = direction,
                    eligibility = eligibility,
                    currency = currency,
                    hasLocalReceipt = claim?.id?.let { state.localReceiptsByClaimId.containsKey(it) } == true,
                    isDeleting = state.deletingClaimId == claim?.id,
                    onAdd = { viewModel.openForm(direction) },
                    onEdit = { edited -> viewModel.openForm(direction, edited) },
                    onDelete = { deleted -> viewModel.deleteClaim(deleted.id) },
                    onNoRide = { viewModel.updateRefundAction(RefundAction.NO_RIDE_TAKEN) },
                    onRemindLater = { viewModel.updateRefundAction(RefundAction.REMIND_LATER) },
                    onUndoAction = { viewModel.updateRefundAction(null) },
                    onViewReceipt = viewModel::openReceiptForClaim,
                )
            }
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
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RefundClaimCard(
    shift: Shift,
    claim: RefundClaim?,
    direction: RefundDirection,
    eligibility: RefundPolicy.Eligibility,
    currency: CurrencyCode,
    hasLocalReceipt: Boolean,
    isDeleting: Boolean,
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
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Refund claim", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(directionLabel(direction), fontWeight = FontWeight.Bold)
                    Text(
                        if (claim == null) "No claim saved yet" else "Claim ready for export",
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

            if (claim == null) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(if (shift.refundAction == RefundAction.REMIND_LATER) "Add receipt now" else "Add refund claim")
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
                Text(
                    "${claim.provider.name.lowercase().replaceFirstChar { it.uppercase() }} - ${MoneyFormatter.format(claim.amount, currency)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    claim.rideAt.atZone(ZoneId.systemDefault()).format(refundDateTimeFmt),
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
                    ) { Text("Edit") }
                    if (claim.receiptPath != null || hasLocalReceipt) {
                        TextButton(
                            onClick = { onViewReceipt(claim) },
                            enabled = !isDeleting,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("View receipt") }
                    }
                    TextButton(
                        onClick = { onDelete(claim) },
                        enabled = !isDeleting,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        if (isDeleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
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
            OutlinedButton(onClick = onNoRide, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("No ride") }
            OutlinedButton(onClick = onRemindLater, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Remind later") }
        }
        RefundAction.NO_RIDE_TAKEN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Marked as no ride taken", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Undo") }
        }
        RefundAction.REMIND_LATER -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reminder kept for later", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = onNoRide, modifier = Modifier.heightIn(min = 48.dp)) { Text("No ride") }
            TextButton(onClick = onUndo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Undo") }
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
    val zone = ZoneId.systemDefault()
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
                    if (form.claimId == null) "Add ${directionLabel(form.direction).lowercase()}" else "Edit ${directionLabel(form.direction).lowercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Capture the receipt, amount, provider, and ride time for this claim.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                RideProviderSelector(selected = form.provider, onSelect = onProviderChange)

                OutlinedTextField(
                    value = form.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (${currency.symbol})") },
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
                    label = { Text("Notes (optional)") },
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
                        Text("Cancel")
                    }
                    Button(
                        onClick = onSave,
                        enabled = !isSaving && (form.amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (form.claimId == null) "Save" else "Update")
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
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Receipt photo", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Receipt photo", fontWeight = FontWeight.Bold)
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Pending receipt photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius.Medium)),
                )
                Text(
                    pendingPhotoName ?: "New receipt photo",
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
                            contentDescription = if (existingReceiptPath == null) "No receipt attached" else "Existing receipt attached",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                pendingPhotoPath != null || localReceiptImagePath != null -> "Receipt attached"
                                existingReceiptPath != null -> "Cloud receipt attached"
                                else -> "No receipt attached yet"
                            },
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
                    Text("Scan receipt")
                }
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Attach receipt")
                }
            }

            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (pendingPhotoPath == null) "Use device camera" else "Retake with camera")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pendingPhotoPath != null) {
                    TextButton(onClick = onRemovePendingPhoto, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove receipt photo", modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove photo", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = { onViewLocalReceipt(pendingPhotoPath) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = "View receipt", modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View receipt")
                    }
                }
                if (pendingPhotoPath == null && existingReceiptPath != null) {
                    TextButton(onClick = { onViewCloudReceipt(existingReceiptPath) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Visibility, contentDescription = "View receipt", modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View cloud receipt")
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
            value = zdt.format(refundDateFmt),
            onValueChange = {},
            readOnly = true,
            label = { Text("Ride date") },
            trailingIcon = {
                IconButton(onClick = onPickDate, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = "Pick ride date", modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.weight(1.5f),
        )
        OutlinedTextField(
            value = zdt.format(refundTimeFmt),
            onValueChange = {},
            readOnly = true,
            label = { Text("Ride time") },
            trailingIcon = {
                IconButton(onClick = onPickTime, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Schedule, contentDescription = "Pick ride time", modifier = Modifier.size(18.dp))
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerWrapper(currentMillis: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val initUtcMidnight = Instant.ofEpochMilli(currentMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initUtcMidnight)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerWrapper(
    currentMillis: Long,
    zone: ZoneId,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val zdt = Instant.ofEpochMilli(currentMillis).atZone(zone)
    val state = rememberTimePickerState(initialHour = zdt.hour, initialMinute = zdt.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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

private fun directionLabel(direction: RefundDirection): String = when (direction) {
    RefundDirection.TO_WORK -> "Ride to work"
    RefundDirection.FROM_WORK -> "Ride from work"
}
