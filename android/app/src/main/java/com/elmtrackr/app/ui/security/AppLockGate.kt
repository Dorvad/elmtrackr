package com.elmtrackr.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.elmtrackr.app.security.AppLockController
import com.elmtrackr.app.security.BiometricAuthPrompt

@Composable
fun AppLockGate(
    activity: FragmentActivity,
    lockEnabled: Boolean,
    onUnlocked: () -> Unit,
    content: @Composable () -> Unit,
) {
    val needsUnlock = lockEnabled && !AppLockController.isUnlocked()

    LaunchedEffect(needsUnlock) {
        if (needsUnlock) {
            BiometricAuthPrompt.show(
                activity = activity,
                title = "Unlock ElmTrackr",
                subtitle = "Confirm your identity to view pay and shift data",
                onSuccess = {
                    AppLockController.unlock()
                    onUnlocked()
                },
                onFailure = { },
            )
        }
    }

    if (needsUnlock) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "ElmTrackr is locked",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Use your fingerprint, face, or device PIN to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    BiometricAuthPrompt.show(
                        activity = activity,
                        title = "Unlock ElmTrackr",
                        subtitle = "Confirm your identity to view pay and shift data",
                        onSuccess = {
                            AppLockController.unlock()
                            onUnlocked()
                        },
                        onFailure = { },
                    )
                },
            ) {
                Text("Unlock")
            }
        }
    } else {
        content()
    }
}
