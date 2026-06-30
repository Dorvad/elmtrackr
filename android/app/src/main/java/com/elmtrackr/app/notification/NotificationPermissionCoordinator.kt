package com.elmtrackr.app.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.elmtrackr.app.data.local.preferences.AppPreferenceKeys
import com.elmtrackr.app.data.local.preferences.appPreferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

object NotificationPermissionCoordinator {

  fun hasPermission(activity: ComponentActivity): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
      activity,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }

  suspend fun markPromptShown(activity: ComponentActivity) {
    activity.appPreferencesDataStore.edit {
      it[AppPreferenceKeys.NOTIFICATION_PERMISSION_PROMPTED] = true
    }
  }

  fun registerLauncher(
    activity: ComponentActivity,
    onResult: (Boolean) -> Unit = {},
  ) = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    onResult(granted)
  }

  suspend fun shouldShowEducationalPrompt(activity: ComponentActivity): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    if (hasPermission(activity)) return false
    val prefs = activity.appPreferencesDataStore.data.first()
    return prefs[AppPreferenceKeys.NOTIFICATION_PERMISSION_PROMPTED] != true
  }
}
