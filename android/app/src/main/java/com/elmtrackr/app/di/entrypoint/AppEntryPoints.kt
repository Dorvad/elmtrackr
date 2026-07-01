package com.elmtrackr.app.di.entrypoint

import android.content.Context
import dagger.hilt.android.EntryPointAccessors

object AppEntryPoints {

    fun background(context: Context): BackgroundEntryPoint =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BackgroundEntryPoint::class.java,
        )
}
