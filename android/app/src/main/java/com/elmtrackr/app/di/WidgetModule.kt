package com.elmtrackr.app.di

import com.elmtrackr.app.widget.AppWidgetPinInspector
import com.elmtrackr.app.widget.WidgetPinInspector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetPinInspector(impl: AppWidgetPinInspector): WidgetPinInspector
}
