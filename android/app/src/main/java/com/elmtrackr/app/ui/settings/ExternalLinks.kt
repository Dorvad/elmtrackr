package com.elmtrackr.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun openExternalUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}
