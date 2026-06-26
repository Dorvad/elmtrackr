package com.elmtrackr.app.shortcuts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class HeadlessTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sendBroadcast(Intent(HeadlessClockOutReceiver.ACTION_HEADLESS_CLOCK_OUT).setPackage(packageName))
        finish()
    }
}
