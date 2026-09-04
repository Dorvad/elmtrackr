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

    /**
     * Payroll aggregation.
     *
     * `Dispatchers.Default` on purpose, and a cap was considered and rejected. The
     * audit listed "bare Dispatchers.Default" as hygiene, but Default is already bounded
     * to the core count, and the work that runs here is a handful of short transforms —
     * Dashboard, Shifts, Reports — not a fan-out. A `limitedParallelism` view would add
     * queueing those three screens do not have today, to fix a contention nobody has
     * measured. Measure first; if a cap is ever warranted it belongs here, behind that
     * measurement.
     *
     * `@Singleton` so every injection shares one dispatcher. It matters the moment this
     * ever becomes a `limitedParallelism` view, where a per-injection instance would be
     * a separate limit and quietly undo the cap.
     */
    @Provides
    @Singleton
    @ComputationDispatcher
    fun provideComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
