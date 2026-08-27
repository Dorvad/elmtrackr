package com.elmtrackr.app.di

import com.elmtrackr.app.BuildConfig
import com.elmtrackr.app.billing.ClockFacePackEntitlements
import com.elmtrackr.app.billing.ClockFacePackStore
import com.elmtrackr.app.billing.FreeClockFacePackEntitlements
import com.elmtrackr.app.billing.FreeClockFacePackStore
import com.elmtrackr.app.billing.PlayClockFacePackStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Whether clock face packs cost money, decided in one place.
 *
 * `PAID_CLOCK_FACE_PACKS` is the switch, and it stays off until the products in
 * [com.elmtrackr.app.billing.ClockFacePackProducts] are live in Play Console.
 * That ordering is not caution for its own sake: a build that charges for
 * products Play has never heard of gets an empty `queryProductDetails` response
 * and shows every pack as unavailable, which is strictly worse than shipping them
 * free for another release.
 *
 * `@Provides` with a [Provider] rather than `@Binds`, so the Play implementation
 * — and the billing client underneath it — is never constructed in a build that
 * does not sell anything. Both entitlements and the storefront resolve to the
 * same `@Singleton` instance when they are on: they are two views of one state,
 * and two instances would mean two answers to who owns what.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideClockFacePackEntitlements(
        play: Provider<PlayClockFacePackStore>,
        free: Provider<FreeClockFacePackEntitlements>,
    ): ClockFacePackEntitlements =
        if (BuildConfig.PAID_CLOCK_FACE_PACKS) play.get() else free.get()

    @Provides
    @Singleton
    fun provideClockFacePackStore(
        play: Provider<PlayClockFacePackStore>,
        free: Provider<FreeClockFacePackStore>,
    ): ClockFacePackStore =
        if (BuildConfig.PAID_CLOCK_FACE_PACKS) play.get() else free.get()
}
