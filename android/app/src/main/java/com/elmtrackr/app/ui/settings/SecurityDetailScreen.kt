package com.elmtrackr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.security.BiometricAvailability
import com.elmtrackr.app.ui.theme.Spacing

@Composable
internal fun SecurityDetailScreen(
    appLockEnabled: Boolean,
    biometricAvailability: BiometricAvailability,
    onBack: () -> Unit,
    onAppLockChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenH),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { SettingsDetailHeader(title = "Security", onBack = onBack) }
        item {
            SettingsSectionCard(title = "APP LOCK") {
                val canEnable = biometricAvailability == BiometricAvailability.AVAILABLE
                SettingsToggleRow(
                    title = "Require biometric to open",
                    description = when (biometricAvailability) {
                        BiometricAvailability.AVAILABLE ->
                            "Lock pay rates, receipts, and shift history when the app is in the background"
                        BiometricAvailability.NOT_ENROLLED ->
                            "Set up fingerprint, face unlock, or a device PIN in system settings first"
                        BiometricAvailability.UNAVAILABLE ->
                            "Biometric or device credential unlock is not available on this device"
                    },
                    checked = appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (canEnable || !enabled) onAppLockChange(enabled)
                    },
                    enabled = canEnable || appLockEnabled,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Local data is encrypted at rest with SQLCipher. Widget and notification actions are blocked while the app is locked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}
