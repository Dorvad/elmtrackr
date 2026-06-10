package com.elmtrackr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.elmtrackr.app.navigation.AppNavGraph
import com.elmtrackr.app.ui.theme.ElmTrackrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElmTrackrTheme {
                AppNavGraph()
            }
        }
    }
}
