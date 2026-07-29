package com.elmtrackr.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds an in-app deep link until the tab navigation graph exists to consume
 * it. MainActivity receives intents before the shell has composed (and while
 * the user may still be on the auth or onboarding route), so the URI is
 * parked here rather than navigated immediately.
 *
 * Auth callbacks do not come through here — they are handled imperatively by
 * AuthRepository and never touch the nav graph.
 */
@Singleton
class DeepLinkRouter @Inject constructor() {

    private val _pending = MutableStateFlow<String?>(null)

    /** The URI awaiting navigation, or null when there is nothing pending. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun submit(uri: String) {
        _pending.value = uri
    }

    /** Clears [uri] only if it is still the pending one, so a link that
     *  arrived while the previous one was being handled is not dropped. */
    fun consume(uri: String) {
        _pending.compareAndSet(uri, null)
    }
}
