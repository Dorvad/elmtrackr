package com.elmtrackr.app.ui.refunds

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        updateShiftState(shift, isLoading = false)
        if (previousShiftId == shift.id) return

        claimsJob?.cancel()
        claimsJob = viewModelScope.launch {
            getRefundClaimsForShift(shift.id)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to load refund claims",
                        )
                    }
                }
                .collect { claims ->
                    _uiState.update { it.copy(claims = claims, isLoading = false) }
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
                errorMessage = message ?: "Unable to open the receipt scanner",
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
                it.copy(camera = null, errorMessage = "The receipt photo was not saved. Try again.")
            }
            return
        }
        _uiState.update { it.copy(camera = null, errorMessage = null) }
        viewModelScope.launch { processReceiptImageFromPath(path) }
    }

    fun photoCaptureFailed(message: String?) {
        cleanupPendingPhoto(_uiState.value.camera?.outputPath)
        _uiState.update {
            it.copy(camera = null, errorMessage = message ?: "Unable to capture receipt photo")
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

    fun updateReceiptReviewDate(millis: Long?) {
        updateReceiptReview { it.copy(receiptDateMillis = millis) }
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
                        rideAtMillis = saved.receiptDate?.toEpochMilli() ?: it.rideAtMillis,
                        notes = buildNotesWithMerchant(it.notes, saved.merchantName),
                    )
                }

                _uiState.update {
                    it.copy(
                        receiptReview = null,
                        noticeMessage = if (saved.amount == null) {
                            "Receipt saved locally. Enter the refund amount before submitting the claim."
                        } else {
                            "Receipt saved locally. Verify the claim details before submitting."
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        receiptReview = review.copy(isSaving = false),
                        errorMessage = error.message ?: "Unable to save receipt locally",
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
            _uiState.update { it.copy(errorMessage = "Enter a valid refund amount") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, noticeMessage = null) }
            val receipt = form.pendingPhotoPath?.let { photoFileManager.toReceiptUpload(it) }
            if (form.pendingPhotoPath != null && receipt == null) {
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "The receipt photo is missing or larger than 10 MB.")
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
                cleanupPendingPhoto(form.pendingPhotoPath?.takeIf { it != form.localReceiptImagePath })
                refreshShift(shift.id)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        form = null,
                        noticeMessage = if (result.receiptUploadFailed) {
                            "Claim saved without the new receipt. Receipt upload is unavailable; you can attach it later."
                        } else null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = error.message ?: "Unable to save the refund claim")
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
                    if (shiftId != null) refreshShift(shiftId)
                    _uiState.update {
                        it.copy(
                            deletingClaimId = null,
                            noticeMessage = if (result.receiptDeleteFailed) {
                                "Claim deleted. Receipt cleanup will retry later."
                            } else null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            deletingClaimId = null,
                            errorMessage = error.message ?: "Unable to delete the refund claim",
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
                        it.copy(errorMessage = error.message ?: "Unable to update refund status")
                    }
                }
        }
    }

    fun openReceipt(path: String, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            val url = refundReceiptStorage?.let { storage ->
                runCatching { storage.createSignedUrl(path) }.getOrNull()
            }
            if (url == null) {
                _uiState.update { it.copy(errorMessage = "Receipt preview is unavailable right now.") }
            } else {
                onOpen(url)
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, noticeMessage = null) }
    }

    private fun processReceiptImage(uri: Uri) {
        val shift = _uiState.value.shift ?: return
        val form = _uiState.value.form ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingReceipt = true, errorMessage = null) }
            val previousPath = form.pendingPhotoPath?.takeIf { it != form.localReceiptImagePath }
            val copied = receiptImageStore.copyToLocalStorage(uri, shift.id, form.direction)
            if (copied == null) {
                _uiState.update {
                    it.copy(
                        isProcessingReceipt = false,
                        errorMessage = "Choose an image smaller than 10 MB.",
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
        val parseResult = receiptScanPipeline.recognizeAndParse(path)
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

    private fun cleanupPendingPhoto(path: String?) {
        photoFileManager.delete(path)
    }
}
