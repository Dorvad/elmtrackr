package com.elmtrackr.wear.tile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.elmtrackr.wear.ElmTrackrWearApp
import java.util.UUID

/**
 * Invisible tap target for the tile.
 *
 * Theme.NoDisplay is a known crash on Wear: the platform requires finish()
 * before onResume(), and several watch builds still deliver onResume after
 * onCreate has already called finish(). Theme.Translucent.NoTitleBar keeps
 * the trampoline invisible without that contract. finish() still runs in
 * onCreate so the window never stays around for the punch round-trip.
 *
 * Exported on purpose. A tile's `LaunchAction` is dispatched by the Wear OS
 * tile host, which is a different app running under a different uid, so an
 * activity the host cannot start is an activity the tile cannot reach: taps on
 * the tile's punch button are refused by the activity manager and nothing
 * happens. The action is still authenticated with a private per-install token:
 * only the tile layout this app rendered knows it, so another watch app cannot
 * clock the user in or out by guessing the two action strings.
 */
class WearPunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nothing here may throw: an exported activity that crashes on a
        // malformed intent is a crash the store review harness will find by
        // fuzzing it. getStringExtra can throw on an intent carrying an extras
        // bundle this process cannot unmarshal.
        val action = runCatching { intent?.getStringExtra(EXTRA_ACTION) }.getOrNull()
        if (!isAuthorizedTileIntent(this, intent)) {
            finish()
            return
        }
        val app = ElmTrackrWearApp.from(this)
        when (action) {
            ACTION_IN -> app?.punchFromTile(isPunchIn = true)
            ACTION_OUT -> app?.punchFromTile(isPunchIn = false)
        }
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing) finish()
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_TOKEN = "tile_token"
        const val ACTION_IN = "punch_in"
        const val ACTION_OUT = "punch_out"

        fun isAuthorizedTileIntent(context: Context, intent: Intent?): Boolean {
            val supplied = runCatching { intent?.getStringExtra(EXTRA_TOKEN) }.getOrNull()
            return supplied != null && supplied == tileLaunchToken(context)
        }

        fun tileLaunchToken(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(KEY_TOKEN, null)?.let { return it }
            val token = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TOKEN, token).commit()
            return token
        }

        private const val PREFS = "wear_tile_launch"
        private const val KEY_TOKEN = "punch_token"
    }
}
