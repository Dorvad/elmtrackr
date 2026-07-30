package com.elmtrackr.app.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elmtrackr.app.MainActivity
import com.elmtrackr.app.R
import com.elmtrackr.app.domain.CurrentUserProvider
import com.elmtrackr.app.domain.money.MoneyFormat
import com.elmtrackr.app.domain.projects.BillingReminder
import com.elmtrackr.app.domain.projects.BillingReminderKind
import com.elmtrackr.app.domain.projects.ProjectBillingReminderPolicy
import com.elmtrackr.app.domain.repository.ProjectsRepository
import com.elmtrackr.app.domain.repository.SettingsRepository
import com.elmtrackr.app.domain.time.WorkTimezone
import com.elmtrackr.app.language.withAppLocale
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * Daily check for project payments that are due or overdue.
 *
 * Every gate lives in [ProjectBillingReminderPolicy], which is pure and tested:
 * the worker only reads data, asks the policy what to say, and posts it. Nothing
 * here decides whether a project is overdue.
 *
 * Runs once a day rather than watching for changes. A due date is a calendar
 * fact — nothing about it changes between midnights — so a daily pass is both
 * sufficient and the least intrusive option.
 */
@HiltWorker
class ProjectBillingReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val currentUserProvider: CurrentUserProvider,
    private val settingsRepository: SettingsRepository,
    private val projectsRepository: ProjectsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = currentUserProvider.currentUserId() ?: return Result.success()
        val settings = settingsRepository.getSettings(userId) ?: return Result.success()

        // Feature off: post nothing and take down anything already showing. Only
        // this feature's notifications are cancelled — the active-shift
        // notification is a different id and must survive. The billing data itself
        // is untouched, so reminders resume on re-enable.
        if (settings.featuresPaidProjects != true) {
            clearFor(context, projectsRepository.getProjects(userId).map { it.id })
            return Result.success()
        }

        val today = LocalDate.now(WorkTimezone.zoneFor(settings))
        val reminders = ProjectBillingReminderPolicy.remindersFor(
            paidProjectsEnabled = true,
            // The OS permission is the user's notification preference here: with it
            // withheld, a posted notification is silently dropped anyway.
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            projects = projectsRepository.getProjects(userId),
            billingRecords = projectsRepository.observeAllBillingRecords(userId).first(),
            payments = projectsRepository.observeAllPayments(userId).first(),
            today = today,
        )

        reminders.forEach { post(it) }
        return Result.success()
    }

    private fun post(reminder: BillingReminder) {
        val localized = context.withAppLocale()
        val locale = localized.resources.configuration.locales[0]
        val amount = MoneyFormat.format(reminder.outstanding, locale)
        val due = reminder.dueOn.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )

        val title = when (reminder.kind) {
            BillingReminderKind.UPCOMING ->
                localized.getString(R.string.project_reminder_upcoming_title, reminder.projectName)
            BillingReminderKind.DUE_TODAY ->
                localized.getString(R.string.project_reminder_due_today_title, reminder.projectName)
            BillingReminderKind.OVERDUE ->
                localized.getString(R.string.project_reminder_overdue_title, reminder.projectName)
        }
        val text = when (reminder.kind) {
            BillingReminderKind.UPCOMING ->
                localized.getString(R.string.project_reminder_upcoming_text, amount, due)
            BillingReminderKind.DUE_TODAY ->
                localized.getString(R.string.project_reminder_due_today_text, amount)
            BillingReminderKind.OVERDUE ->
                localized.getString(R.string.project_reminder_overdue_text, amount, due)
        }

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_PROJECT_BILLING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(reminder.projectId), notification)
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val WORK_NAME = "project_billing_reminders"

        /**
         * One id per project, so a second project's reminder does not replace the
         * first, and re-running the same day updates rather than stacking.
         */
        internal fun notificationId(projectId: String): Int =
            ID_BASE + (projectId.hashCode() and 0xFFFF)

        private const val ID_BASE = 5000

        /** Enqueues the daily pass. Idempotent — KEEP leaves an existing one alone. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ProjectBillingReminderWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Takes down billing reminders already on screen, by their own ids.
         *
         * Deliberately not `cancelAll()`: that would also dismiss the active-shift
         * notification, which has nothing to do with billing.
         */
        fun clearFor(context: Context, projectIds: List<String>) {
            val manager = NotificationManagerCompat.from(context)
            projectIds.forEach { id ->
                runCatching { manager.cancel(notificationId(id)) }
            }
        }
    }
}
