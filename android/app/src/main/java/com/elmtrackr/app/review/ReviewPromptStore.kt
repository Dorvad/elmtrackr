package com.elmtrackr.app.review

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locally persisted review-prompt bookkeeping: milestone timestamps, usage-day
 * tracking, and the throttling fields (last attempt, quiet-period markers).
 *
 * Device-local on purpose, like [com.elmtrackr.app.update.InAppUpdatePromptStore]:
 * whether *this device* already asked for a review is not user data and must
 * not sync — a second device should keep its own cooldowns.
 */
interface ReviewPromptStore {
    suspend fun snapshot(): StoredReviewPromptState

    /** Records an app foreground; bumps the usage-day count once per calendar day. */
    suspend fun recordUsage(nowMs: Long, epochDay: Long)

    suspend fun recordClockIn(nowMs: Long)
    suspend fun recordMonthlyReportViewed(nowMs: Long)
    suspend fun recordReportExported(nowMs: Long)

    /** Errors, failed purchases, missed reminders — anything that sours the moment. */
    suspend fun recordDiscouragingEvent(nowMs: Long)

    /** A review flow was handed to Play, whether or not it showed any UI. */
    suspend fun recordAttempt(nowMs: Long)
}

/** The persisted subset of [ReviewPromptInputs]; live signals are added by the coordinator. */
data class StoredReviewPromptState(
    val monthlyReportViewedAt: Long? = null,
    val reportExportedAt: Long? = null,
    val usageDayCount: Int = 0,
    val lastUsageEpochDay: Long? = null,
    val firstUsedAt: Long? = null,
    val lastClockInAt: Long? = null,
    val lastDiscouragingEventAt: Long? = null,
    val lastAttemptAt: Long? = null,
)

private val Context.reviewPromptDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "review_prompt",
)

@Singleton
class DataStoreReviewPromptStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReviewPromptStore {

    override suspend fun snapshot(): StoredReviewPromptState {
        val prefs = context.reviewPromptDataStore.data.first()
        return StoredReviewPromptState(
            monthlyReportViewedAt = prefs[KEY_MONTHLY_REPORT_VIEWED_AT],
            reportExportedAt = prefs[KEY_REPORT_EXPORTED_AT],
            usageDayCount = prefs[KEY_USAGE_DAY_COUNT] ?: 0,
            lastUsageEpochDay = prefs[KEY_LAST_USAGE_EPOCH_DAY],
            firstUsedAt = prefs[KEY_FIRST_USED_AT],
            lastClockInAt = prefs[KEY_LAST_CLOCK_IN_AT],
            lastDiscouragingEventAt = prefs[KEY_LAST_DISCOURAGING_EVENT_AT],
            lastAttemptAt = prefs[KEY_LAST_ATTEMPT_AT],
        )
    }

    override suspend fun recordUsage(nowMs: Long, epochDay: Long) {
        context.reviewPromptDataStore.edit { prefs ->
            if (prefs[KEY_FIRST_USED_AT] == null) prefs[KEY_FIRST_USED_AT] = nowMs
            if (prefs[KEY_LAST_USAGE_EPOCH_DAY] != epochDay) {
                prefs[KEY_LAST_USAGE_EPOCH_DAY] = epochDay
                prefs[KEY_USAGE_DAY_COUNT] = (prefs[KEY_USAGE_DAY_COUNT] ?: 0) + 1
            }
        }
    }

    override suspend fun recordClockIn(nowMs: Long) {
        context.reviewPromptDataStore.edit { it[KEY_LAST_CLOCK_IN_AT] = nowMs }
    }

    override suspend fun recordMonthlyReportViewed(nowMs: Long) {
        context.reviewPromptDataStore.edit { it[KEY_MONTHLY_REPORT_VIEWED_AT] = nowMs }
    }

    override suspend fun recordReportExported(nowMs: Long) {
        context.reviewPromptDataStore.edit { it[KEY_REPORT_EXPORTED_AT] = nowMs }
    }

    override suspend fun recordDiscouragingEvent(nowMs: Long) {
        context.reviewPromptDataStore.edit { it[KEY_LAST_DISCOURAGING_EVENT_AT] = nowMs }
    }

    override suspend fun recordAttempt(nowMs: Long) {
        context.reviewPromptDataStore.edit { it[KEY_LAST_ATTEMPT_AT] = nowMs }
    }

    private companion object {
        val KEY_MONTHLY_REPORT_VIEWED_AT = longPreferencesKey("monthly_report_viewed_at")
        val KEY_REPORT_EXPORTED_AT = longPreferencesKey("report_exported_at")
        val KEY_USAGE_DAY_COUNT = intPreferencesKey("usage_day_count")
        val KEY_LAST_USAGE_EPOCH_DAY = longPreferencesKey("last_usage_epoch_day")
        val KEY_FIRST_USED_AT = longPreferencesKey("first_used_at")
        val KEY_LAST_CLOCK_IN_AT = longPreferencesKey("last_clock_in_at")
        val KEY_LAST_DISCOURAGING_EVENT_AT = longPreferencesKey("last_discouraging_event_at")
        val KEY_LAST_ATTEMPT_AT = longPreferencesKey("last_attempt_at")
    }
}
