package com.elmtrackr.wear.tile

import android.app.Activity
import android.os.Bundle
import com.elmtrackr.wear.ElmTrackrWearApp

/**
 * Invisible tap target for the tile. Theme.NoDisplay requires finish() before
 * onResume() completes, so the punch itself runs on the application scope —
 * an in-activity coroutine here would both violate that contract and leak the
 * destroyed window while the punch round-trip (up to ~5s) is in flight.
 *
 * Exported on purpose. A tile's `LaunchAction` is dispatched by the Wear OS
 * tile host, which is a different app running under a different uid, so an
 * activity the host cannot start is an activity the tile cannot reach: taps on
 * the tile's punch button are refused by the activity manager and nothing
 * happens. The only thing an exported entry point buys another app on the
 * watch is the ability to trigger the same punch the user could trigger from
 * the tile, and only the two action values below are honoured — anything else
 * finishes without doing any work.
 */
class WearPunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nothing here may throw: an exported activity that crashes on a
        // malformed intent is a crash the store review harness will find by
        // fuzzing it. getStringExtra can throw on an intent carrying an extras
        // bundle this process cannot unmarshal.
        val action = runCatching { intent?.getStringExtra(EXTRA_ACTION) }.getOrNull()
        val app = ElmTrackrWearApp.from(this)
        when (action) {
            ACTION_IN -> app?.punchFromTile(isPunchIn = true)
            ACTION_OUT -> app?.punchFromTile(isPunchIn = false)
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_IN = "punch_in"
        const val ACTION_OUT = "punch_out"
    }
}
