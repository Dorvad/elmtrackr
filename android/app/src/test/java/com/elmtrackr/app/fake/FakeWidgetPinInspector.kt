package com.elmtrackr.app.fake

import com.elmtrackr.app.widget.WidgetPinInspector

class FakeWidgetPinInspector(
    var pinSupported: Boolean = true,
    var pinned: Boolean = false,
) : WidgetPinInspector {

    var pinRequests: Int = 0
        private set

    override fun isPinSupported(): Boolean = pinSupported

    override fun hasPinnedWidget(): Boolean = pinned

    override fun requestPinWidget(): Boolean {
        pinRequests++
        return pinSupported
    }
}
