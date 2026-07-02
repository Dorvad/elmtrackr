package com.elmtrackr.app.ui.refunds

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import com.elmtrackr.app.ui.theme.CornerRadius

@Composable
fun ReceiptReviewDialog(
    review: ReceiptReviewUiState,
    defaultCurrency: CurrencyCode,
    onMerchantChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!review.isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.Large),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Review receipt details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "Verify the scanned details before saving. OCR can miss or misread values, " +
                        "especially on faded or crumpled receipts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ConfidenceBanner(review)

                val bitmap = remember(review.localImagePath) {
                    runCatching { BitmapFactory.decodeFile(review.localImagePath)?.asImageBitmap() }.getOrNull()
                }
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = "Scanned receipt preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius.Medium)),
                    )
                }

                OutlinedTextField(
                    value = review.merchantName,
                    onValueChange = onMerchantChange,
                    label = { Text("Merchant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = review.amountText,
                        onValueChange = onAmountChange,
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = review.currency,
                        onValueChange = onCurrencyChange,
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(0.7f),
                        placeholder = { Text(defaultCurrency.name) },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !review.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onSave,
                        enabled = !review.isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (review.isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save receipt")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBanner(review: ReceiptReviewUiState) {
    val (container, content, message) = when {
        review.ocrFailed -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Text recognition failed. You can still save the receipt and enter details manually.",
        )
        review.confidence == ReceiptParseConfidence.NONE || review.amountText.isBlank() -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "No amount was detected. Save the receipt and enter the refund amount manually.",
        )
        review.confidence == ReceiptParseConfidence.LOW -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Low-confidence scan. Double-check every field before saving.",
        )
        review.confidence == ReceiptParseConfidence.MEDIUM -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Some details were detected. Please verify them before saving.",
        )
        else -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Details detected with good confidence. Please verify before saving.",
        )
    }

    Surface(color = container, shape = RoundedCornerShape(CornerRadius.Medium)) {
        Text(
            message,
            color = content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

