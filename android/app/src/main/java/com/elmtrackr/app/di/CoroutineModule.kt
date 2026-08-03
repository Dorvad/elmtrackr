package com.elmtrackr.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.elmtrackr.app.monitoring.CrashReporting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    /**
     * The app-wide scope for work that must outlive any screen.
     *
     * The handler is not decoration. `SupervisorJob` stops one child's failure
     * from cancelling its siblings, but it does **not** stop an uncaught
     * exception in a root `launch` from reaching the thread's default handler —
     * which kills the process. Everything launched here runs without a user
     * waiting on it, so a throw should be reported and swallowed, never fatal.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable ->
                    CrashReporting.report(throwable)
                },
        )

    @Provides
    @ComputationDispatcher
    fun provideComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
