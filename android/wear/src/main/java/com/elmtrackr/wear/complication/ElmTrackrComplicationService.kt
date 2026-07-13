package com.elmtrackr.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.elmtrackr.wear.ElmTrackrWearApp
import com.elmtrackr.wear.R
import com.elmtrackr.wear.WearMainActivity
import com.elmtrackr.wear.sync.WearDisplayMath
import com.elmtrackr.wear.sync.WearShiftSnapshot
import com.elmtrackr.wear.ui.WearLabels

class ElmTrackrComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val app = applicationContext as ElmTrackrWearApp
        app.wearStateRepository.refreshFromDataLayer()
        val snapshot = app.wearStateRepository.snapshot.value
        val tapAction = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return buildData(request.complicationType, snapshot, tapAction)
    }

    /**
     * Texts are resolved here (not in the shared WearDisplayMath, which cannot
     * see resources) so complications follow the watch language. The count-up
     * uses minute precision — complications refresh too rarely for seconds.
     */
    private fun buildData(
        type: ComplicationType,
        snapshot: WearShiftSnapshot,
        tapAction: PendingIntent?,
    ): ComplicationData? {
        val shortText = when {
            snapshot.isActive && snapshot.shiftStartEpochMillis > 0L ->
                WearDisplayMath.elapsedHm(snapshot.shiftStartEpochMillis)
            snapshot.isActive -> getString(R.string.wear_comp_in)
            snapshot.startTimeLabel != "--:--" -> snapshot.startTimeLabel
            else -> getString(R.string.wear_comp_out)
        }
        val longText = when {
            snapshot.isActive && snapshot.shiftStartEpochMillis > 0L -> getString(
                R.string.wear_comp_clocked_in,
                WearDisplayMath.elapsedHm(snapshot.shiftStartEpochMillis),
            )
            snapshot.isActive -> getString(R.string.wear_on_shift)
            else -> WearLabels.lastPunch(this, snapshot)
                .ifBlank { getString(R.string.wear_clocked_out) }
        }
        val statusText = getString(
            if (snapshot.isActive) R.string.wear_on_shift else R.string.wear_clocked_out,
        )

        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(shortText).build(),
                contentDescription = PlainComplicationText.Builder(longText).build(),
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(longText).build(),
                contentDescription = PlainComplicationText.Builder(statusText).build(),
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = WearDisplayMath.progressPercent(
                    snapshot.todayMinutes,
                    snapshot.dailyGoalMinutes,
                ).toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = PlainComplicationText.Builder(longText).build(),
            )
                .setText(PlainComplicationText.Builder(shortText).build())
                .setTapAction(tapAction)
                .build()

            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = buildData(
        type,
        WearShiftSnapshot(
            signedIn = true,
            isActive = true,
            shiftStartEpochMillis = System.currentTimeMillis() - 3_600_000L,
            startTimeLabel = "09:00",
            todayMinutes = 240,
        ),
        tapAction = null,
    )

    companion object {
        fun requestUpdateAll(context: Context) {
            val manager = androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, ElmTrackrComplicationService::class.java),
            )
            manager.requestUpdateAll()
        }
    }
}
