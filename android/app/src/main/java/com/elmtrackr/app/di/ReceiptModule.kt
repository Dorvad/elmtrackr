package com.elmtrackr.app.di

import com.elmtrackr.app.data.receipt.MLKitReceiptTextRecognizer
import com.elmtrackr.app.data.repository.LocalReceiptsRepository
import com.elmtrackr.app.domain.receipt.DefaultReceiptScanPipeline
import com.elmtrackr.app.domain.receipt.ReceiptScanPipeline
import com.elmtrackr.app.domain.receipt.ReceiptTextRecognizer
import com.elmtrackr.app.domain.repository.ReceiptsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReceiptModule {

    @Binds
    @Singleton
    abstract fun bindReceiptsRepository(impl: LocalReceiptsRepository): ReceiptsRepository

    @Binds
    @Singleton
    abstract fun bindReceiptTextRecognizer(impl: MLKitReceiptTextRecognizer): ReceiptTextRecognizer

    @Binds
    @Singleton
    abstract fun bindReceiptScanPipeline(impl: DefaultReceiptScanPipeline): ReceiptScanPipeline
}
