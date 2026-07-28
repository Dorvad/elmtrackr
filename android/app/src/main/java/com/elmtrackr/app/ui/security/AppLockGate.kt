package com.elmtrackr.app.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.security.AppLockController
import com.elmtrackr.app.security.BiometricAuthPrompt

/**
 * @param lockEnabled null while the persisted preference is still loading. Content must not be
 *   composed in that window: after process death the DataStore emission arrives a frame or two
 *   late, and treating "unknown" as "unlocked" briefly rendered the dashboard behind the lock.
 */
@Composable
fun AppLockGate(
    activity: FragmentActivity,
    lockEnabled: Boolean?,
    content: @Composable () -> Unit,
) {
    if (lockEnabled == null) {
        // Neutral scrim rather than the full lock UI: this state lasts a frame or
        // two, and users without app lock should never see a "locked" flash.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val needsUnlock = lockEnabled && !AppLockController.isUnlocked()
    val unlockTitle = stringResource(R.string.security_unlock_title)
    val unlockSubtitle = stringResource(R.string.security_unlock_subtitle)

    LaunchedEffect(needsUnlock) {
        if (needsUnlock) {
            BiometricAuthPrompt.show(
                activity = activity,
                title = unlockTitle,
                subtitle = unlockSubtitle,
                onSuccess = { AppLockController.unlock() },
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
                text = stringResource(R.string.security_locked_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.security_locked_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    BiometricAuthPrompt.show(
                        activity = activity,
                        title = unlockTitle,
                        subtitle = unlockSubtitle,
                        onSuccess = { AppLockController.unlock() },
                        onFailure = { },
                    )
                },
            ) {
                Text(stringResource(R.string.security_unlock_button))
            }
        }
    } else {
        content()
    }
}
