package com.elmtrackr.app

import android.app.Application

/** Lightweight Application for screenshot instrumentation tests (skips Hilt / SQLCipher init). */
class ScreenshotTestApplication : Application()
