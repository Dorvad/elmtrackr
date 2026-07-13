package com.elmtrackr.wear.tile

import android.app.Activity
import android.os.Bundle
import com.elmtrackr.wear.ElmTrackrWearApp

/**
 * Invisible tap target for the tile. Theme.NoDisplay requires finish() before
 * onResume() completes, so the punch itself runs on the application scope —
 * an in-activity coroutine here would both violate that contract and leak the
 * destroyed window while the punch round-trip (up to ~5s) is in flight.
 */
class WearPunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_IN -> (application as ElmTrackrWearApp).punchFromTile(isPunchIn = true)
            ACTION_OUT -> (application as ElmTrackrWearApp).punchFromTile(isPunchIn = false)
        }
        finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_IN = "punch_in"
        const val ACTION_OUT = "punch_out"
    }
}
