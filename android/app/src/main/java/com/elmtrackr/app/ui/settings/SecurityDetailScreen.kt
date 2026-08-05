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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.security.BiometricAvailability
import com.elmtrackr.app.security.BiometricCapability
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
        item { SettingsDetailHeader(title = stringResource(R.string.settings_security), onBack = onBack) }
        item {
            SettingsSectionCard(title = stringResource(R.string.security_section_app_lock)) {
                val canEnable = biometricAvailability == BiometricAvailability.AVAILABLE
                SettingsToggleRow(
                    title = stringResource(R.string.onboarding_security_app_lock_title),
                    description = when (biometricAvailability) {
                        BiometricAvailability.AVAILABLE ->
                            stringResource(R.string.onboarding_security_desc_available)
                        BiometricAvailability.NOT_ENROLLED ->
                            stringResource(R.string.onboarding_security_desc_not_enrolled)
                        BiometricAvailability.UNAVAILABLE ->
                            stringResource(R.string.onboarding_security_desc_unavailable)
                    },
                    checked = appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (canEnable || !enabled) onAppLockChange(enabled)
                    },
                    enabled = canEnable || appLockEnabled,
                )
                // The switch reads "on" while the lock is not being enforced, so
                // say why rather than leave the user believing they are covered.
                if (appLockEnabled && !BiometricCapability.canEnforceAppLock(biometricAvailability)) {
                    Spacer(Modifier.height(Spacing.s8))
                    Text(
                        text = stringResource(R.string.security_app_lock_suspended),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.security_encryption_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}
