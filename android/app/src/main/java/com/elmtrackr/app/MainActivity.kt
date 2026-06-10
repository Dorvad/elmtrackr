package com.elmtrackr.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.elmtrackr.app.navigation.AppNavGraph
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Handle deep link that launched the activity
        intent?.data?.toString()?.let { handleDeepLink(it) }
        setContent {
            ElmTrackrTheme {
                AppNavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when activity is already running (singleTask)
        intent.data?.toString()?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(uriString: String) {
        lifecycleScope.launch {
            (application as ElmTrackrApp).authRepository.handleDeepLink(uriString)
        }
    }
}
