package com.elmtrackr.app.di

import javax.inject.Qualifier

/**
 * CPU-bound work dispatcher. Used by ViewModels whose transforms are heavy enough that
 * running them on the main dispatcher janks the UI (payroll aggregation over a month of
 * shifts), and injected so tests can substitute their own scheduler.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ComputationDispatcher
