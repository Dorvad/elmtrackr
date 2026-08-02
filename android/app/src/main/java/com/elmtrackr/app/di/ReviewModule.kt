package com.elmtrackr.app.di

import com.elmtrackr.app.review.DataStoreReviewPromptStore
import com.elmtrackr.app.review.ReviewPromptCoordinator
import com.elmtrackr.app.review.ReviewPromptRecorder
import com.elmtrackr.app.review.ReviewPromptStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {

    @Binds
    @Singleton
    abstract fun bindReviewPromptStore(impl: DataStoreReviewPromptStore): ReviewPromptStore

    @Binds
    @Singleton
    abstract fun bindReviewPromptRecorder(impl: ReviewPromptCoordinator): ReviewPromptRecorder
}
