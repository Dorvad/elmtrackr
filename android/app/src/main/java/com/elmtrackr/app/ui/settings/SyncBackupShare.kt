package com.elmtrackr.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.elmtrackr.app.R
import java.io.File

object SyncBackupShare {
    fun shareJson(context: Context, content: String, filename: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = dir.resolve(filename).apply { writeText(content) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_backup_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.settings_backup_share_chooser)),
        )
    }
}
