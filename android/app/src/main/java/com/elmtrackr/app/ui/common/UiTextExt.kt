package com.elmtrackr.app.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.domain.model.UiText

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res ->
        if (args.isEmpty()) stringResource(id)
        else stringResource(id, *args.toTypedArray())
}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res ->
        if (args.isEmpty()) context.getString(id)
        else context.getString(id, *args.toTypedArray())
}
