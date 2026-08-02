package com.elmtrackr.app.review

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

/**
 * The one place that talks to Google Play's in-app review API.
 *
 * Deliberately promise-free: Play decides on its own whether to show the
 * review UI (quota, account state, store availability) and reports nothing
 * back, so callers must treat this as "we offered Play the moment" and carry
 * on normally either way. Outside Google Play (sideloads, emulators, CI) the
 * tasks fail and this becomes a logged no-op.
 */
object PlayReviewFlowLauncher {

    suspend fun launch(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, reviewInfo).await()
            ReviewDiagnostics.recordLaunchHandedToPlay()
        } catch (e: Exception) {
            // Includes ReviewException / task failures: the app continues untouched.
            ReviewDiagnostics.recordLaunchFailed(e)
            Log.d(TAG, "In-app review flow unavailable: ${e.message}")
        }
    }

    private const val TAG = "InAppReview"
}
