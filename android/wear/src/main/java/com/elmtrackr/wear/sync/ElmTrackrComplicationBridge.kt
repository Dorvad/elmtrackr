package com.elmtrackr.wear.sync

import android.content.Context
import com.elmtrackr.wear.complication.ElmTrackrComplicationService

object ElmTrackrComplicationBridge {
    fun requestUpdateAll(context: Context) {
        ElmTrackrComplicationService.requestUpdateAll(context)
    }
}
