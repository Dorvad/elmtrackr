package com.elmtrackr.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Walks out to the hosting [Activity], or null if there is none.
 *
 * `LocalContext.current` is usually the Activity, but not always: a dialog window,
 * a preview and Compose's own theme overlays all hand back a [ContextWrapper]
 * around it. APIs that put a system sheet on screen — Credential Manager among
 * them — reject anything that is not an Activity, so a plain cast is a crash
 * waiting for the one screen that wraps.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
