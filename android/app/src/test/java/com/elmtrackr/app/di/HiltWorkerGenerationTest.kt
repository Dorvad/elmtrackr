package com.elmtrackr.app.di

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Guards the `androidx.hilt:hilt-compiler` KSP processor.
 *
 * `@HiltWorker` needs two processors, not one. Dagger's `hilt-compiler` generates the
 * `<Worker>_Factory` for the `@AssistedInject` constructor, but only AndroidX's
 * `androidx.hilt:hilt-compiler` generates the `<Worker>_AssistedFactory` and the
 * `<Worker>_HiltModule` that binds it into [androidx.hilt.work.HiltWorkerFactory]'s map.
 *
 * Shipping `hilt-work` without that processor compiles cleanly and fails only at runtime:
 * the map is empty, `HiltWorkerFactory` returns null, WorkManager falls back to its default
 * factory and looks for a `(Context, WorkerParameters)` constructor that `@AssistedInject`
 * workers do not have. Logcat shows "Could not instantiate <Worker>" and the job is dropped,
 * so background sync, overtime reminders and widget refresh all stop without any crash or
 * user-visible error. That is exactly what happened in production, which is why this is
 * asserted rather than left to review.
 *
 * Every class annotated `@HiltWorker` must be listed here.
 */
class HiltWorkerGenerationTest {

    @Test
    fun `every HiltWorker has a generated assisted factory and Hilt module`() {
        for (worker in HILT_WORKERS) {
            assertNotNull(
                "$worker has no generated _AssistedFactory — is ksp(libs.androidx.hilt.compiler) still declared?",
                loadOrNull("${worker}_AssistedFactory"),
            )
            assertNotNull(
                "$worker has no generated _HiltModule, so HiltWorkerFactory cannot resolve it",
                loadOrNull("${worker}_HiltModule"),
            )
        }
    }

    private fun loadOrNull(name: String): Class<*>? =
        runCatching { Class.forName(name, false, javaClass.classLoader) }.getOrNull()

    private companion object {
        private val HILT_WORKERS = listOf(
            "com.elmtrackr.app.data.sync.SyncWorker",
            "com.elmtrackr.app.notification.ReminderRuleWorker",
            "com.elmtrackr.app.widget.WidgetRefreshWorker",
        )
    }
}
