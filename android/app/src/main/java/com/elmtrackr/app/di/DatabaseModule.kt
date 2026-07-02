package com.elmtrackr.app.di

import android.content.Context
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.dao.CompensationProfileDao
import com.elmtrackr.app.data.local.dao.ProfileDao
import com.elmtrackr.app.data.local.dao.ReceiptDao
import com.elmtrackr.app.data.local.dao.RefundClaimDao
import com.elmtrackr.app.data.local.dao.SettingsDao
import com.elmtrackr.app.data.local.dao.ShiftDao
import com.elmtrackr.app.data.local.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ElmTrackrDatabase =
        ElmTrackrDatabase.getInstance(context)

    @Provides
    fun provideShiftDao(database: ElmTrackrDatabase): ShiftDao = database.shiftDao()

    @Provides
    fun provideSettingsDao(database: ElmTrackrDatabase): SettingsDao = database.settingsDao()

    @Provides
    fun provideProfileDao(database: ElmTrackrDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideRefundClaimDao(database: ElmTrackrDatabase): RefundClaimDao = database.refundClaimDao()

    @Provides
    fun provideReceiptDao(database: ElmTrackrDatabase): ReceiptDao = database.receiptDao()

    @Provides
    fun provideCompensationProfileDao(database: ElmTrackrDatabase): CompensationProfileDao =
        database.compensationProfileDao()

    @Provides
    fun provideTaskDao(database: ElmTrackrDatabase): TaskDao = database.taskDao()
}
