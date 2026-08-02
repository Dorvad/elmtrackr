package com.elmtrackr.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

fun openExternalUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

/**
 * Opens an email composer addressed to support. A separate action from
 * "Rate ElmTrackr" on purpose: feedback goes to us, ratings go to Play, and
 * the two must never be funneled through one another.
 */
fun openSendFeedbackEmail(context: Context, toAddress: String, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(toAddress))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No email app installed; the support address stays visible in Settings.
    }
}
