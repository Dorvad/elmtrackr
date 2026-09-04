package com.elmtrackr.app.shortcuts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Transparent trampoline for the dynamic Clock Out app shortcut.
 *
 * An app shortcut has to target an activity, so this exists only to be that
 * target. It does no work of its own: it hands the punch to
 * [HeadlessClockOutReceiver] and leaves.
 *
 * It used to do the work here, in a `lifecycleScope.launch` with a `try/finally`
 * and **no `catch`**, which was a live crash path. `clockOutActiveShift` reaches
 * `?: error("Shift not found")` when the shift disappears between the read and
 * the write — a concurrent pull's tombstone pass, or the shortcut tapped twice —
 * and an uncaught throw from a root `launch` goes to the thread's default handler,
 * which on Android means the process dies. A `SupervisorJob` would not have helped
 * either: it stops siblings being cancelled, not an uncaught throw.
 *
 * Delegating fixes two things at once. The receiver already handles failure
 * correctly, with `goAsync()` holding the process open and `runCatching` around
 * both steps, so there is one implementation of that rather than two. And nothing
 * is suspended before `finish()` any more, which retires the separate question of
 * whether finishing after suspended work under `Theme.NoDisplay` is safe — there
 * is no suspended work to finish after.
 */
class HeadlessTrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sendBroadcast(
            Intent(this, HeadlessClockOutReceiver::class.java)
                .setAction(HeadlessClockOutReceiver.ACTION_HEADLESS_CLOCK_OUT),
        )
        finish()
    }
}
