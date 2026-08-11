package com.elmtrackr.app.ui.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import com.elmtrackr.app.ui.theme.CornerRadius

/**
 * The shared text and number inputs.
 *
 * These live in the design layer because that is where the design-system budget
 * expects a wrapped Material control to live, and because four screens had
 * previously each grown their own `OutlinedTextField` with slightly different
 * shapes, error handling and keyboard options. A field is a component, not a
 * per-screen decision.
 */
@Composable
fun ElmTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            isError = errorText != null,
            // The error replaces the hint rather than sitting beside it: two
            // competing lines under one field is how people miss the one that
            // says what is wrong.
            supportingText = (errorText ?: supportingText)?.let { text ->
                { Text(text, style = MaterialTheme.typography.bodySmall) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            shape = RoundedCornerShape(CornerRadius.Medium),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A decimal input that keeps its own text.
 *
 * Deliberately string-in, string-out. Parsing on every keystroke means the field
 * cannot hold "0." or an empty string while someone is still typing, so the
 * caret jumps and a half-typed "1.5" becomes "1". The caller parses when it
 * needs a number.
 */
@Composable
fun ElmDecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    ElmTextField(
        label = label,
        value = value,
        onValueChange = { raw -> onValueChange(sanitizeDecimal(raw, value)) },
        modifier = modifier,
        placeholder = placeholder,
        supportingText = supportingText,
        errorText = errorText,
        keyboardType = KeyboardType.Decimal,
        imeAction = imeAction,
    )
}

/**
 * Accepts digits and a single separator, rejecting anything else by returning the
 * previous text.
 *
 * A comma becomes a full stop because a Hebrew or European keyboard offers a
 * comma on the decimal key, and `toDouble` does not accept one — the user types a
 * comma and the field silently keeps the integer part.
 */
fun sanitizeDecimal(raw: String, current: String): String {
    if (raw.isEmpty()) return raw
    val normalized = raw.replace(',', '.')
    if (!normalized.matches(DECIMAL)) return current
    return normalized
}

private val DECIMAL = Regex("""^\d*\.?\d*$""")
