package com.elmtrackr.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * The wall clock, as a dependency.
 *
 * Row timestamps decide sync conflicts — `client_updated_at` on the wire, `updatedAt`
 * locally — so a repository that reads the clock statically cannot be tested for which
 * of two edits wins. Injected, a test fixes "now" and asserts it.
 *
 * UTC on purpose: every stored timestamp is epoch millis, and a zoned clock would
 * invite `LocalDate.now(clock)` calls that quietly use the device zone where the work
 * zone is meant. Display code resolves its own zone through `WorkTimezone`.
 *
 * A Kotlin default on an `@Inject` constructor does not satisfy Dagger, so a binding is
 * required even where the parameter already has one.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
