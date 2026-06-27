package com.elmtrackr.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import android.content.res.Configuration
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import com.elmtrackr.app.data.local.preferences.AppPreferenceValues
import com.elmtrackr.app.navigation.AppNavGraph
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not - the app works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.data?.toString()?.let { handleDeepLink(it) }
        requestNotificationPermissionIfNeeded()
        setContent {
            val app = application as ElmTrackrApp
            val configuration = LocalConfiguration.current
            val preferences by app.appPreferences.preferences
                .collectAsState(initial = AppPreferenceValues())
            val systemDark = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val darkTheme = when (preferences.selectedTheme) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            ElmTrackrTheme(darkTheme = darkTheme) {
                AppNavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as ElmTrackrApp).refreshDynamicShortcuts()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.toString()?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(uriString: String) {
        lifecycleScope.launch {
            (application as ElmTrackrApp).authRepository.handleDeepLink(uriString)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

