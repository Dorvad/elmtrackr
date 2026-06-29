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
import com.elmtrackr.wear.WearMainActivity
import com.elmtrackr.wear.sync.WearDisplayMath

class ElmTrackrComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val app = applicationContext as ElmTrackrWearApp
        app.wearStateRepository.refreshFromDataLayer()
        val snapshot = app.wearStateRepository.snapshot.value
        val display = WearDisplayMath.displayFor(snapshot)
        val tapAction = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(display.complicationShortText).build(),
                contentDescription = PlainComplicationText.Builder(display.complicationLongText).build(),
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(display.complicationLongText).build(),
                contentDescription = PlainComplicationText.Builder(display.statusLabel).build(),
            )
                .setTapAction(tapAction)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = display.progressPercent.toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = PlainComplicationText.Builder(display.complicationLongText).build(),
            )
                .setText(PlainComplicationText.Builder(display.complicationShortText).build())
                .setTapAction(tapAction)
                .build()

            else -> null
        }
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val display = WearDisplayMath.displayFor(
            com.elmtrackr.wear.sync.WearShiftSnapshot(
                signedIn = true,
                isActive = true,
                shiftStartEpochMillis = System.currentTimeMillis() - 3_600_000L,
                startTimeLabel = "09:00",
                todayMinutes = 240,
            ),
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(display.complicationShortText).build(),
                contentDescription = PlainComplicationText.Builder(display.complicationLongText).build(),
            ).build()
            else -> null
        }
    }

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
