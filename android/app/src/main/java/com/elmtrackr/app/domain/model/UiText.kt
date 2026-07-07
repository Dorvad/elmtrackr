package com.elmtrackr.app.domain.model

import androidx.annotation.StringRes

/**
 * Text destined for the UI, produced where no Context is available.
 * ViewModels, mappers, and repositories emit [Res] (a string resource plus
 * optional format args) so the message localizes at render time; [Raw]
 * passes through text that already arrived as a plain string (for example
 * a server-provided error we cannot classify).
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    data class Raw(val value: String) : UiText
}
