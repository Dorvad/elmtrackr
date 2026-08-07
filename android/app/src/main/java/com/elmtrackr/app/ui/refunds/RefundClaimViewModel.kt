package com.elmtrackr.app.ui.refunds

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elmtrackr.app.ui.common.UserFacingError
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.data.receipts.PhotoFileManager
import com.elmtrackr.app.data.receipt.ReceiptImageStore
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.RefundPolicy
import com.elmtrackr.app.domain.model.Receipt
import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.RefundClaim
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.receipt.ReceiptScanPipeline
import com.elmtrackr.app.domain.refund.DeleteRefundClaim
import com.elmtrackr.app.domain.refund.GetRefundClaimsForShift
import com.elmtrackr.app.domain.refund.RefundClaimUpsertInput
import com.elmtrackr.app.domain.refund.UpsertRefundClaim
import com.elmtrackr.app.domain.repository.ReceiptsRepository
import com.elmtrackr.app.domain.repository.RefundReceiptStorage
import com.elmtrackr.app.domain.repository.ShiftsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class RefundClaimViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val getRefundClaimsForShift: GetRefundClaimsForShift,
    private val upsertRefundClaim: UpsertRefundClaim,
    private val deleteRefundClaim: DeleteRefundClaim,
    private val shiftsRepository: ShiftsRepository,
    private val refundReceiptStorage: RefundReceiptStorage?,
    private val receiptsRepository: ReceiptsRepository,
    private val receiptScanPipeline: ReceiptScanPipeline,
    private val receiptImageStore: ReceiptImageStore,
    private val currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val photoFileManager = PhotoFileManager(context)

    private val appContext = context
    private var claimsJob: Job? = null

    private val _uiState = MutableStateFlow(RefundClaimUiState())
    val uiState: StateFlow<RefundClaimUiState> = _uiState.asStateFlow()

    fun setShift(shift: Shift) {
        val previousShiftId = _uiState.value.shift?.id
        if (previousShiftId == shift.id) {
            updateShiftState(shift, isLoading = false)
            return
        }

        dismissForm()
        updateShiftState(shift, isLoading = true)
        _uiState.update {
            it.copy(
                claims = emptyList(),
                localReceiptsByClaimId = emptyMap(),
            )
        }

        claimsJob?.cancel()
        claimsJob = viewModelScope.launch {
            getRefundClaimsForShift(shift.id)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = UserFacingError.message(error, R.string.refunds_err_load),
                        )
                    }
                }
                .collect { claims ->
                    val localReceipts = loadLocalReceiptsForClaims(claims)
                    _uiState.update {
                        it.copy(
                            claims = claims,
                            localReceiptsByClaimId = localReceipts,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun openForm(direction: RefundDirection, claim: RefundClaim? = null) {
        cleanupPendingPhoto(_uiState.value.form?.pendingPhotoPath)
        val shift = _uiState.value.shift ?: return
        val rideAt = claim?.rideAt ?: when (direction) {
            RefundDirection.TO_WORK -> shift.startTime
            RefundDirection.FROM_WORK -> shift.endTime ?: shift.startTime
        }
        _uiState.update {
            it.copy(
                errorMessage = null,
                noticeMessage = null,
                receiptReview = null,
                form = RefundClaimFormUiState(
                    claimId = claim?.id,
                    direction = direction,
                    provider = claim?.provider ?: RefundProvider.LIME,
                    amountText = claim?.amount?.toString() ?: "",
                    rideAtMillis = rideAt.toEpochMilli(),
                    notes = claim?.notes.orEmpty(),
                    existingReceiptPath = claim?.receiptPath,
                ),
            )
        }
        if (claim != null) {
            viewModelScope.launch { restoreLocalReceiptIntoForm(claim.id) }
        }
    }

    fun dismissForm() {
        cleanupPendingPhoto(_uiState.value.form?.pendingPhotoPath)
        cleanupPendingPhoto(_uiState.value.camera?.outputPath)
        _uiState.update { it.copy(form = null, camera = null, receiptReview = null, isSaving = false) }
    }

    fun updateProvider(provider: RefundProvider) {
        updateForm { it.copy(provider = provider) }
    }

    fun updateAmount(value: String) {
        if (value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }) {
            updateForm { it.copy(amountText = value) }
        }
    }

    fun updateRideAt(millis: Long) {
        updateForm { it.copy(rideAtMillis = millis) }
    }

    fun updateNotes(value: String) {
        updateForm { it.copy(notes = value) }
    }

    fun requestDocumentScan() {
        if (_uiState.value.form == null) return
        _uiState.update { it.copy(launchDocumentScanner = true, errorMessage = null) }
    }

    fun onDocumentScannerLaunched() {
        _uiState.update { it.copy(launchDocumentScanner = false) }
    }

    fun onDocumentScannerFailed(message: String?) {
        _uiState.update {
            it.copy(
                launchDocumentScanner = false,
                errorMessage = message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.refunds_err_scanner),
            )
        }
    }

    fun onDocumentScanned(uri: Uri?) {
        _uiState.update { it.copy(launchDocumentScanner = false) }
        if (uri == null) return
        processReceiptImage(uri)
    }

    fun startCameraCapture() {
        val shift = _uiState.value.shift ?: return
        val form = _uiState.value.form ?: return
        cleanupPendingPhoto(form.pendingPhotoPath)
        val output = photoFileManager.createPendingPhotoFile(shift.id, form.direction)
        _uiState.update {
            it.copy(
                camera = ReceiptCameraUiState(output.absolutePath),
                form = form.copy(pendingPhotoPath = null, pendingPhotoName = null),
                errorMessage = null,
            )
        }
    }

    fun cancelCameraCapture() {
        cleanupPendingPhoto(_uiState.value.camera?.outputPath)
        _uiState.update { it.copy(camera = null) }
    }

    fun photoCaptured(path: String) {
        val file = File(path)
        if (!file.exists() || file.length() <= 0) {
            cleanupPendingPhoto(path)
            _uiState.update {
                it.copy(camera = null, errorMessage = UiText.Res(R.string.refunds_err_photo_not_saved))
            }
            return
        }
        _uiState.update { it.copy(camera = null, errorMessage = null) }
        viewModelScope.launch {
            // Move the capture out of the pending-photos directory (purged by the
            // orphan cleanup worker) into permanent receipt storage before OCR.
            val shift = _uiState.value.shift
            val form = _uiState.value.form
            if (shift == null || form == null) {
                cleanupPendingPhoto(path)
                return@launch
            }
            _uiState.update { it.copy(isProcessingReceipt = true, errorMessage = null) }
            val stored = receiptImageStore.copyToLocalStorage(Uri.fromFile(file), shift.id, form.direction)
            cleanupPendingPhoto(path)
            if (stored == null) {
                _uiState.update {
                    it.copy(
                        isProcessingReceipt = false,
                        errorMessage = UiText.Res(R.string.refunds_err_photo_store),
                    )
                }
                return@launch
            }
            processReceiptImageFromPath(stored.absolutePath)
        }
    }

    fun photoCaptureFailed(message: String?) {
        cleanupPendingPhoto(_uiState.value.camera?.outputPath)
        _uiState.update {
            it.copy(camera = null, errorMessage = message?.let { m -> UiText.Raw(m) } ?: UiText.Res(R.string.refunds_err_capture))
        }
    }

    fun importReceiptPhoto(uri: Uri) {
        processReceiptImage(uri)
    }

    fun removePendingPhoto() {
        cleanupPendingPhoto(_uiState.value.form?.pendingPhotoPath)
        updateForm {
            it.copy(
                pendingPhotoPath = null,
                pendingPhotoName = null,
                localReceiptImagePath = null,
            )
        }
    }

    fun dismissReceiptReview() {
        val review = _uiState.value.receiptReview ?: return
        if (review.receiptId == null) {
            receiptImageStore.delete(review.localImagePath)
        }
        _uiState.update { it.copy(receiptReview = null) }
    }

    fun updateReceiptReviewMerchant(value: String) {
        updateReceiptReview { it.copy(merchantName = value) }
    }

    fun updateReceiptReviewAmount(value: String) {
        if (value.count { it == '.' } <= 1 && value.all { it.isDigit() || it == '.' }) {
            updateReceiptReview { it.copy(amountText = value) }
        }
    }

    fun updateReceiptReviewCurrency(value: String) {
        updateReceiptReview { it.copy(currency = value.uppercase().take(3)) }
    }

    fun saveReceiptReview() {
        val review = _uiState.value.receiptReview ?: return
        val form = _uiState.value.form ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(receiptReview = review.copy(isSaving = true), errorMessage = null)
            }

            runCatching {
                val amount = review.amountText.toDoubleOrNull()
                val now = Instant.now()
                val receiptId = review.receiptId ?: UUID.randomUUID().toString()
                receiptsRepository.save(
                    Receipt(
                        id = receiptId,
                        userId = currentUserProvider.currentUserId(),
                        refundClaimId = form.claimId,
                        localImageUri = review.localImagePath,
                        merchantName = review.merchantName.ifBlank { null },
                        amount = amount,
                        currency = review.currency.ifBlank { null },
                        receiptDate = review.receiptDateMillis?.let(Instant::ofEpochMilli),
                        rawOcrText = review.rawOcrText,
                        parserVersion = com.elmtrackr.app.domain.receipt.ReceiptParser.VERSION,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }.onSuccess { saved ->
                val file = File(saved.localImageUri)
                updateForm {
                    it.copy(
                        linkedReceiptId = saved.id,
                        localReceiptImagePath = saved.localImageUri,
                        pendingPhotoPath = saved.localImageUri,
                        pendingPhotoName = file.name,
                        amountText = saved.amount?.toString() ?: it.amountText,
                        // rideAtMillis stays seeded from the shift start/end times;
                        // the receipt date is stored as metadata only.
                        notes = buildNotesWithMerchant(it.notes, saved.merchantName),
                    )
                }

                _uiState.update {
                    it.copy(
                        receiptReview = null,
                        noticeMessage = if (saved.amount == null) {
                            UiText.Res(R.string.refunds_notice_receipt_saved_enter_amount)
                        } else {
                            UiText.Res(R.string.refunds_notice_receipt_saved_verify)
                        },
                    )
                }
                if (form.claimId != null) {
                    refreshLocalReceipts()
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        receiptReview = review.copy(isSaving = false),
                        errorMessage = UserFacingError.message(error, R.string.refunds_err_save_receipt_local),
                    )
                }
            }
        }
    }

    fun saveForm() {
        val form = _uiState.value.form ?: return
        val shift = _uiState.value.shift ?: return
        val amount = form.amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { it.copy(errorMessage = UiText.Res(R.string.refunds_err_amount)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, noticeMessage = null) }
            val receipt = form.pendingPhotoPath?.let { photoFileManager.toReceiptUpload(it) }
            if (form.pendingPhotoPath != null && receipt == null) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = UiText.Res(R.string.refunds_err_photo_missing_large))
                }
                return@launch
            }

            runCatching {
                upsertRefundClaim(
                    RefundClaimUpsertInput(
                        shiftId = shift.id,
                        claimId = form.claimId,
                        direction = form.direction,
                        provider = form.provider,
                        amount = amount,
                        rideAt = Instant.ofEpochMilli(form.rideAtMillis),
                        notes = form.notes,
                        receipt = receipt,
                    ),
                )
            }.onSuccess { result ->
                form.linkedReceiptId?.let { receiptId ->
                    receiptsRepository.linkToClaim(receiptId, result.claim.id)
                }
                if (result.receiptUploadFailed && form.linkedReceiptId == null) {
                    // The retry pass finds work through the receipt row, so a
                    // failed upload with no row would never be retried.
                    persistReceiptForRetry(result.claim.id, form.pendingPhotoPath)
                }
                // Never delete the image when its upload failed. The claim saved
                // without a receipt_path, so this file is the only copy of the
                // photo that exists anywhere — deleting it turned a retryable
                // upload failure into a lost receipt. The sync pipeline picks it
                // up from the linked receipt row and uploads it later.
                if (!result.receiptUploadFailed) {
                    cleanupPendingPhoto(form.pendingPhotoPath?.takeIf { it != form.localReceiptImagePath })
                }
                refreshShift(shift.id)
                refreshLocalReceipts()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        form = null,
                        noticeMessage = if (result.receiptUploadFailed) {
                            UiText.Res(R.string.refunds_notice_saved_no_receipt)
                        } else null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = UserFacingError.message(error, R.string.refunds_err_save_claim))
                }
            }
        }
    }

    fun deleteClaim(claimId: String) {
        viewModelScope.launch {
            val shiftId = _uiState.value.shift?.id
            _uiState.update { it.copy(deletingClaimId = claimId, errorMessage = null, noticeMessage = null) }
            runCatching { deleteRefundClaim(claimId) }
                .onSuccess { result ->
                    if (shiftId != null) {
                        refreshShift(shiftId)
                        refreshLocalReceipts()
                    }
                    _uiState.update {
                        it.copy(
                            deletingClaimId = null,
                            noticeMessage = when {
                                result.receiptDeleteFailed && result.localReceiptDeleteFailed ->
                                    UiText.Res(R.string.refunds_notice_deleted_cleanup_retry)
                                result.receiptDeleteFailed ->
                                    UiText.Res(R.string.refunds_notice_deleted_cloud_retry)
                                result.localReceiptDeleteFailed ->
                                    UiText.Res(R.string.refunds_notice_deleted_local_failed)
                                else -> null
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            deletingClaimId = null,
                            errorMessage = UserFacingError.message(error, R.string.refunds_err_delete_claim),
                        )
                    }
                }
        }
    }

    fun updateRefundAction(action: RefundAction?) {
        val shift = _uiState.value.shift ?: return
        viewModelScope.launch {
            val updated = shift.copy(refundAction = action, updatedAt = Instant.now())
            runCatching { shiftsRepository.updateShift(updated) }
                .onSuccess { updateShiftState(it, isLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = UserFacingError.message(error, R.string.refunds_err_update_status))
                    }
                }
        }
    }

    fun openReceiptForClaim(claim: RefundClaim) {
        viewModelScope.launch {
            val localReceipt = receiptsRepository.getByRefundClaimId(claim.id)
                ?: _uiState.value.localReceiptsByClaimId[claim.id]
            if (localReceipt != null && File(localReceipt.localImageUri).exists()) {
                _uiState.update {
                    it.copy(
                        receiptPreview = ReceiptPreviewUiState(localImagePath = localReceipt.localImageUri),
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val cloudPath = claim.receiptPath
            if (cloudPath == null) {
                _uiState.update { it.copy(errorMessage = UiText.Res(R.string.refunds_err_no_receipt)) }
                return@launch
            }
            openCloudReceipt(cloudPath)
        }
    }

    fun openReceipt(path: String) {
        viewModelScope.launch { openCloudReceipt(path) }
    }

    fun openLocalReceipt(imagePath: String) {
        if (!File(imagePath).exists()) {
            _uiState.update { it.copy(errorMessage = UiText.Res(R.string.refunds_err_receipt_file_missing)) }
            return
        }
        _uiState.update {
            it.copy(
                receiptPreview = ReceiptPreviewUiState(localImagePath = imagePath),
                errorMessage = null,
            )
        }
    }

    fun dismissReceiptPreview() {
        _uiState.update { it.copy(receiptPreview = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, noticeMessage = null) }
    }

    private suspend fun openCloudReceipt(path: String) {
        _uiState.update {
            it.copy(
                receiptPreview = ReceiptPreviewUiState(isLoading = true),
                errorMessage = null,
            )
        }
        val url = refundReceiptStorage?.let { storage ->
            runCatching { storage.createSignedUrl(path) }.getOrNull()
        }
        if (url == null) {
            _uiState.update {
                it.copy(
                    receiptPreview = null,
                    errorMessage = UiText.Res(R.string.refunds_err_preview_unavailable),
                )
            }
        } else {
            _uiState.update {
                it.copy(receiptPreview = ReceiptPreviewUiState(signedUrl = url))
            }
        }
    }

    private suspend fun restoreLocalReceiptIntoForm(claimId: String) {
        val receipt = receiptsRepository.getByRefundClaimId(claimId) ?: return
        val file = File(receipt.localImageUri)
        if (!file.exists()) return

        updateForm { form ->
            form.copy(
                linkedReceiptId = receipt.id,
                localReceiptImagePath = receipt.localImageUri,
                pendingPhotoPath = receipt.localImageUri,
                pendingPhotoName = file.name,
                amountText = receipt.amount?.toString() ?: form.amountText,
                notes = buildNotesWithMerchant(form.notes, receipt.merchantName),
            )
        }
    }

    private suspend fun loadLocalReceiptsForClaims(claims: List<RefundClaim>): Map<String, Receipt> =
        claims.mapNotNull { claim ->
            receiptsRepository.getByRefundClaimId(claim.id)?.let { claim.id to it }
        }.toMap()

    private suspend fun refreshLocalReceipts() {
        val claims = _uiState.value.claims
        _uiState.update { it.copy(localReceiptsByClaimId = loadLocalReceiptsForClaims(claims)) }
    }

    private fun processReceiptImage(uri: Uri) {
        val shift = _uiState.value.shift ?: return
        val form = _uiState.value.form ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingReceipt = true, errorMessage = null) }
            val previousPath = form.pendingPhotoPath?.takeIf { it != form.localReceiptImagePath }
            // Null on oversize; a throw is still possible on IO failure, and the
            // modal spinner must not outlive it.
            val copied = runCatching {
                receiptImageStore.copyToLocalStorage(uri, shift.id, form.direction)
            }.getOrNull()
            if (copied == null) {
                _uiState.update {
                    it.copy(
                        isProcessingReceipt = false,
                        errorMessage = UiText.Res(R.string.refunds_err_image_too_large),
                    )
                }
                return@launch
            }
            cleanupPendingPhoto(previousPath)
            processReceiptImageFromPath(copied.absolutePath)
        }
    }

    private suspend fun processReceiptImageFromPath(path: String) {
        _uiState.update { it.copy(isProcessingReceipt = true, errorMessage = null) }
        // The progress dialog is modal and non-dismissible, so any throw in the OCR
        // pipeline (decode OOM, missing Tesseract data) used to strand the user
        // behind a spinner with force-stop as the only exit. Always clear the flag,
        // and surface the failure instead of swallowing it.
        val parseResult = try {
            receiptScanPipeline.recognizeAndParse(path)
        } catch (error: Throwable) {
            _uiState.update {
                it.copy(
                    isProcessingReceipt = false,
                    errorMessage = UiText.Res(R.string.refunds_err_scan_failed),
                )
            }
            return
        }
        val ocrFailed = parseResult.confidence == ReceiptParseConfidence.NONE &&
            parseResult.rawOcrText.isBlank()

        _uiState.update {
            it.copy(
                isProcessingReceipt = false,
                receiptReview = ReceiptReviewUiState(
                    localImagePath = path,
                    merchantName = parseResult.merchantName.orEmpty(),
                    amountText = parseResult.amount?.toString().orEmpty(),
                    currency = parseResult.currency.orEmpty(),
                    receiptDateMillis = parseResult.receiptDate?.toEpochMilli(),
                    rawOcrText = parseResult.rawOcrText,
                    confidence = parseResult.confidence,
                    ocrFailed = ocrFailed,
                ),
            )
        }
    }

    /** Escape hatch for the modal processing dialog. */
    fun cancelReceiptProcessing() {
        _uiState.update { it.copy(isProcessingReceipt = false) }
    }

    private suspend fun refreshShift(shiftId: String) {
        shiftsRepository.getShiftById(shiftId)?.let { updateShiftState(it, isLoading = false) }
    }

    private fun updateShiftState(shift: Shift, isLoading: Boolean) {
        _uiState.update {
            it.copy(
                shift = shift,
                toEligibility = RefundPolicy.checkToWorkEligibility(shift),
                fromEligibility = RefundPolicy.checkFromWorkEligibility(shift),
                isLoading = isLoading,
            )
        }
    }

    private fun updateForm(transform: (RefundClaimFormUiState) -> RefundClaimFormUiState) {
        _uiState.update { state ->
            val form = state.form ?: return@update state
            state.copy(form = transform(form))
        }
    }

    private fun updateReceiptReview(transform: (ReceiptReviewUiState) -> ReceiptReviewUiState) {
        _uiState.update { state ->
            val review = state.receiptReview ?: return@update state
            state.copy(receiptReview = transform(review))
        }
    }

    private fun buildNotesWithMerchant(existingNotes: String, merchantName: String?): String {
        if (merchantName.isNullOrBlank()) return existingNotes
        val marker = "Merchant: $merchantName"
        return if (existingNotes.contains(merchantName, ignoreCase = true)) {
            existingNotes
        } else if (existingNotes.isBlank()) {
            marker
        } else {
            "$existingNotes\n$marker"
        }
    }

    /**
     * Records a locally stored receipt image against [claimId] so the sync
     * pipeline's retry pass can find and upload it.
     */
    private suspend fun persistReceiptForRetry(claimId: String, imagePath: String?) {
        val path = imagePath ?: return
        if (!File(path).exists()) return
        val userId = currentUserProvider.currentUserId() ?: return
        val now = Instant.now()
        runCatching {
            receiptsRepository.save(
                Receipt(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    refundClaimId = claimId,
                    localImageUri = path,
                    merchantName = null,
                    amount = null,
                    currency = null,
                    receiptDate = null,
                    rawOcrText = null,
                    parserVersion = "",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun cleanupPendingPhoto(path: String?) {
        photoFileManager.delete(path)
    }
}
