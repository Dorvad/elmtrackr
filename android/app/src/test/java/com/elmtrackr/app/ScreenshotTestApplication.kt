package com.elmtrackr.app

import android.app.Application

/** Minimal Application for Robolectric UI screenshot tests (avoids Hilt / SQLCipher init). */
class ScreenshotTestApplication : Application()
