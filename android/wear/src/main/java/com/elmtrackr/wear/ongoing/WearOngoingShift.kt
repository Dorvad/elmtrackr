package com.elmtrackr.wear.ongoing

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.elmtrackr.wear.R
import com.elmtrackr.wear.WearMainActivity
import com.elmtrackr.wear.runCatchingCancellable
import com.elmtrackr.wear.sync.WearLocalShift
import com.elmtrackr.wear.sync.WearShiftSnapshot

/**
 * The running shift as a Wear OS **Ongoing Activity**.
 *
 * Wear OS treats anything the wearer is in the middle of — a workout, a timer, a
 * shift — as an ongoing activity, and the quality guidelines require an app that
 * has one to surface it in three places: an indicator on the watch face, a chip in
 * the recent-apps list, and the tile. Play rejected wear 10056 for the first two
 * being absent. The tile already shows the running face; this class provides the
 * other two.
 *
 * An ongoing activity rides on an ongoing notification. The status shown next to
 * the indicator is a [Status.StopwatchPart] anchored at the shift's start, so the
 * system counts up on its own and nothing here has to tick. The chip's tap opens
 * the watch app.
 *
 * ### Why no foreground service
 *
 * Google's sample keeps the notification alive from a foreground service. A shift
 * runs for hours, a watch battery is small, and on API 34+ a foreground service
 * needs a declared type — for a timer that is `specialUse`, which is its own Play
 * Console declaration and review. The notification itself is held by the system
 * once posted, and the stopwatch is rendered by the system from the start time, so
 * the indicator survives this process dying. The trade is that an ongoing
 * notification is user-dismissible on newer Android; a dismissed indicator comes
 * back at the next snapshot, and the shift itself is unaffected.
 *
 * ### Permission
 *
 * From API 33 the notification needs `POST_NOTIFICATIONS`, a runtime permission the
 * launcher activity requests on first open. Without it this class does nothing,
 * quietly: a missing indicator is a degraded feature, never a crash.
 */
object WearOngoingShift {

    private const val TAG = "WearOngoingShift"
    private const val CHANNEL_ID = "elmtrackr_active_shift"
    const val NOTIFICATION_ID = 1001

    /** Called on every applied snapshot. Idempotent and safe to call from any thread. */
    fun sync(context: Context, snapshot: WearShiftSnapshot) {
        runCatchingCancellable {
            if (snapshot.isActive && snapshot.shiftStartEpochMillis > 0L) {
                show(context.applicationContext, snapshot)
            } else {
                clear(context.applicationContext)
            }
        }.onFailure { Log.w(TAG, "Could not update the ongoing shift", it) }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun show(context: Context, snapshot: WearShiftSnapshot) {
        // Spelled out here rather than only through canPost(): lint's
        // MissingPermission check wants to see the permission test in the method
        // that calls notify(), and a SecurityException is caught below regardless.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "POST_NOTIFICATIONS not granted; the ongoing shift indicator is off")
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.ongoing_channel_name))
                .setDescription(context.getString(R.string.ongoing_channel_description))
                .setShowBadge(false)
                .build(),
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val since = context.getString(
            R.string.running_since,
            WearLocalShift.formatHm(snapshot.shiftStartEpochMillis),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_complication)
            .setContentTitle(context.getString(R.string.wear_on_shift))
            .setContentText(since)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // The system renders "#time#" as a live stopwatch from the shift start, so
        // the indicator counts up without this process running.
        val status = Status.Builder()
            .addTemplate("#time#")
            .addPart("time", Status.StopwatchPart(snapshot.shiftStartEpochMillis))
            .build()
        OngoingActivity.Builder(context, NOTIFICATION_ID, notification)
            .setStaticIcon(R.drawable.ic_complication)
            .setAnimatedIcon(R.drawable.ic_complication)
            .setTitle(context.getString(R.string.wear_on_shift))
            .setTouchIntent(openApp)
            .setStatus(status)
            .build()
            .apply(context)

        try {
            manager.notify(NOTIFICATION_ID, notification.build())
        } catch (denied: SecurityException) {
            // The grant was revoked between the check and the post. Degraded, not fatal.
            Log.w(TAG, "Notification permission refused; the ongoing shift indicator is off", denied)
        }
    }

    private fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
