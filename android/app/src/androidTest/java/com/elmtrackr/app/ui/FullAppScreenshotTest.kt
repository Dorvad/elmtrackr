package com.elmtrackr.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elmtrackr.app.data.local.entity.SyncStatus
import com.elmtrackr.app.data.sync.SyncDetails
import com.elmtrackr.app.data.sync.SyncEntityType
import com.elmtrackr.app.data.sync.SyncFailedRow
import com.elmtrackr.app.data.sync.SyncPendingByType
import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.ReportInsightsBuilder
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.MonthlyReport
import com.elmtrackr.app.domain.model.Profile
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.security.BiometricAvailability
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.auth.SignedOutContent
import com.elmtrackr.app.ui.dashboard.DashboardReadyPreview
import com.elmtrackr.app.ui.dashboard.DashboardSkeleton
import com.elmtrackr.app.ui.dashboard.DashboardUiState
import com.elmtrackr.app.ui.onboarding.FeaturesStep
import com.elmtrackr.app.ui.onboarding.OnboardingProgress
import com.elmtrackr.app.ui.onboarding.PaySetupStep
import com.elmtrackr.app.ui.onboarding.ProfileStep
import com.elmtrackr.app.ui.onboarding.ReviewStep
import com.elmtrackr.app.ui.onboarding.WelcomeStep
import com.elmtrackr.app.ui.onboarding.WorkWeekStep
import com.elmtrackr.app.ui.reports.HoursReport
import com.elmtrackr.app.ui.reports.ReportsUiState
import com.elmtrackr.app.ui.settings.AppearanceDetailScreen
import com.elmtrackr.app.ui.settings.CompensationSettingsContent
import com.elmtrackr.app.ui.settings.CompensationSettingsUiState
import com.elmtrackr.app.ui.settings.FeaturesDetailScreen
import com.elmtrackr.app.ui.settings.HelpDetailScreen
import com.elmtrackr.app.ui.settings.LegalDocumentScreen
import com.elmtrackr.app.ui.settings.LegalDocuments
import com.elmtrackr.app.ui.settings.PayDetailScreen
import com.elmtrackr.app.ui.settings.ProfileDetailScreen
import com.elmtrackr.app.ui.settings.SecurityDetailScreen
import com.elmtrackr.app.ui.settings.SettingsDestination
import com.elmtrackr.app.ui.settings.SettingsHub
import com.elmtrackr.app.ui.settings.SettingsUiState
import com.elmtrackr.app.ui.settings.SyncDetailsContent
import com.elmtrackr.app.ui.tasks.TaskManagementContent
import com.elmtrackr.app.ui.tasks.TaskManagementUiState
import com.elmtrackr.app.ui.shifts.MonthShiftSummary
import com.elmtrackr.app.ui.shifts.RideProviderSelector
import com.elmtrackr.app.ui.shifts.ShiftEditFormContent
import com.elmtrackr.app.ui.shifts.ShiftFormNavState
import com.elmtrackr.app.ui.shifts.ShiftRow
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Captures PNG screenshots of every major app screen for visual review.
 * Run: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elmtrackr.app.ui.FullAppScreenshotTest
 */
@RunWith(AndroidJUnit4::class)
class FullAppScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun authSignIn() = capture("01-auth-sign-in") {
        SignedOutContent(false, null, { _, _ -> }, { _, _ -> }, {}, {})
    }

    @Test fun onboardingWelcome() = capture("02-onboarding-welcome") {
        OnboardingColumn(1) { WelcomeStep(replay = false, onNext = {}) }
    }

    @Test fun onboardingProfile() = capture("03-onboarding-profile") {
        OnboardingColumn(2) { ProfileStep("Dor", "dor@example.com", {}, false, {}, {}) }
    }

    @Test fun onboardingPay() = capture("04-onboarding-pay") {
        OnboardingColumn(3) { PaySetupStep("50", CurrencyCode.ILS, {}, {}, true, {}, {}) }
    }

    @Test fun onboardingWorkWeek() = capture("05-onboarding-work-week") {
        OnboardingColumn(4) {
            WorkWeekStep(listOf(5, 6), "8", "40", "Asia/Jerusalem", {}, {}, {}, {}, true, {}, {})
        }
    }

    @Test fun onboardingFeatures() = capture("06-onboarding-features") {
        OnboardingColumn(6) { FeaturesStep(false, true, {}, {}, {}, {}) }
    }

    @Test fun onboardingReview() = capture("07-onboarding-review") {
        OnboardingColumn(8) {
            ReviewStep("Dor", 50.0, CurrencyCode.ILS, "Israel", listOf(5, 6), 2, null, {}, {})
        }
    }

    @Test fun dashboardReady() = capture("08-dashboard") {
        DashboardReadyPreview(state = sampleDashboardState())
    }

    @Test fun dashboardSkeleton() = capture("09-dashboard-loading") {
        DashboardSkeleton(Modifier.fillMaxSize().padding(16.dp))
    }

    @Test fun shiftsList() = capture("10-shifts-list") {
        val settings = sampleSettings()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            MonthShiftSummary(sampleShifts(), settings)
            ShiftRow(sampleShifts()[0], settings, showRefunds = true, onClick = {})
            ShiftRow(sampleShifts()[1], settings, showRefunds = true, onClick = {})
        }
    }

    @Test fun shiftEdit() = capture("11-shift-edit") {
        val settings = sampleSettings()
        val shift = sampleShifts()[0]
        val start = shift.startTime.toEpochMilli()
        val end = shift.endTime!!.toEpochMilli()
        ShiftEditFormContent(
            navState = ShiftFormNavState.Edit(shift),
            settings = settings,
            errors = emptyMap(),
            featuresTravelRefunds = true,
            onSave = {},
            onDelete = {},
            onClose = {},
            onPickStartDate = {},
            onPickStartTime = {},
            onPickEndDate = {},
            onPickEndTime = {},
            startMillis = start,
            endMillis = end,
            hasEndTime = true,
            onHasEndTimeChange = {},
            breakMinutes = shift.breakMinutes,
            onBreakMinutesChange = {},
            notesText = shift.notes.orEmpty(),
            onNotesChange = {},
            isSpecialDay = shift.isSpecialDay,
            onSpecialDayChange = {},
            profiles = emptyList(),
            compensationProfileId = null,
            onCompensationProfileIdChange = {},
            tasks = sampleTasks(),
            taskId = null,
            onTaskIdChange = {},
            showRefundSection = true,
            initialShift = shift,
        )
    }

    @Test fun reportsHours() = capture("12-reports-hours") {
        ReportsColumn(insights = true)
    }

    @Test fun reportsInsightsDisabled() = capture("13-reports-no-insights") {
        ReportsColumn(insights = false)
    }

    @Test fun settingsHub() = capture("14-settings-hub") {
        SettingsHubScreen(SettingsDestination.HUB)
    }

    @Test fun settingsProfile() = capture("15-settings-profile") {
        SettingsHubScreen(SettingsDestination.PROFILE)
    }

    @Test fun settingsPay() = capture("16-settings-pay") {
        SettingsHubScreen(SettingsDestination.PAY)
    }

    @Test fun settingsAppearance() = capture("17-settings-appearance") {
        SettingsHubScreen(SettingsDestination.APPEARANCE)
    }

    @Test fun settingsFeatures() = capture("18-settings-features") {
        SettingsHubScreen(SettingsDestination.FEATURES)
    }

    @Test fun settingsHelp() = capture("19-settings-help") {
        SettingsHubScreen(SettingsDestination.HELP)
    }

    @Test fun settingsSecurity() = capture("20-settings-security") {
        SecurityDetailScreen(
            appLockEnabled = false,
            biometricAvailability = BiometricAvailability.AVAILABLE,
            onBack = {},
            onAppLockChange = {},
        )
    }

    @Test fun settingsSyncDetails() = capture("21-settings-sync-details") {
        SyncDetailsContent(
            details = SyncDetails(
                lastSuccessfulSyncAt = Instant.parse("2025-04-01T08:30:00Z"),
                lastSyncStatus = "Success",
                pendingByType = listOf(
                    SyncPendingByType(SyncEntityType.SHIFTS, pendingCount = 2, syncedCount = 14),
                    SyncPendingByType(SyncEntityType.TASKS, pendingCount = 0, syncedCount = 3),
                ),
                failedRows = listOf(
                    SyncFailedRow(
                        entityType = SyncEntityType.REFUND_CLAIMS,
                        localId = "claim-1",
                        label = "Lime ride · Apr 12",
                        syncStatus = SyncStatus.FAILED,
                        errorMessage = "Network timeout",
                    ),
                ),
                totalPending = 2,
                totalFailed = 1,
            ),
            isSyncing = false,
            isExporting = false,
            isImporting = false,
            message = null,
            onBack = {},
            onRetryAll = {},
            onExportBackup = {},
            onImportBackup = {},
        )
    }

    @Test fun settingsCompensation() = capture("22-settings-compensation") {
        val preset = RegionPresets.forRegion(RegionCode.IL)
        CompensationSettingsContent(
            state = CompensationSettingsUiState.Ready(
                profile = CompensationProfile(
                    id = "cp1",
                    userId = "user",
                    name = "Israel default",
                    regionCode = RegionCode.IL,
                    currencyCode = "ILS",
                    timezone = "Asia/Jerusalem",
                    baseHourlyRate = 50.0,
                    rules = preset.rules,
                    stackingPolicy = preset.stackingPolicy,
                    isDefault = true,
                ),
                settings = sampleSettings(),
                presets = RegionPresets.all,
                currencyOptions = listOf("ILS" to "₪ Israeli shekel", "USD" to "$ US dollar"),
                timezoneOptions = listOf("Asia/Jerusalem", "UTC"),
                isSaving = false,
                saveMessage = null,
            ),
            onBack = {},
            onSave = { _, _, _, _, _, _, _ -> },
            onDismissMessage = {},
        )
    }

    @Test fun settingsTasks() = capture("23-settings-tasks") {
        TaskManagementContent(
            state = TaskManagementUiState.Ready(
                tasks = sampleTasks(),
                defaultRules = emptyList(),
            ),
            onBack = {},
            onSave = { _, _, _, _, _ -> },
            onArchive = {},
            onDismissMessage = {},
        )
    }

    @Test fun settingsTerms() = capture("24-settings-terms") {
        LegalDocumentScreen(
            title = "Terms of Service",
            sections = LegalDocuments.termsOfService,
            lastUpdated = LegalDocuments.LAST_UPDATED,
            onBack = {},
        )
    }

    @Test fun rideProviderSelector() = capture("25-ride-provider") {
        RideProviderSelector(RefundProvider.LIME, {})
    }

    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                ElmTrackrTheme(darkTheme = darkTheme) {
                    Box(
                        Modifier
                            .size(360.dp, 800.dp)
                            .background(MaterialTheme.colorScheme.background)
                            .testTag("screenshot"),
                    ) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("screenshot").captureToImage().asAndroidBitmap()
        saveScreenshot(name, bitmap)
    }

    private fun saveScreenshot(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dirs = listOf(
            File("/sdcard/Download/elmtrackr-screenshots"),
            File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots"),
        )
        dirs.forEach { dir ->
            dir.mkdirs()
            FileOutputStream(File(dir, "$name.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Composable
    private fun OnboardingColumn(step: Int, content: @Composable () -> Unit) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OnboardingProgress(step)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }

    @Composable
    private fun ReportsColumn(insights: Boolean) {
        val settings = sampleSettings().copy(featuresInsights = insights)
        val shifts = sampleShifts()
        Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
            HoursReport(
                ReportsUiState.Ready(
                    year = 2025,
                    month = 4,
                    report = MonthlyReport(2025, 4, 5604, 2400, 3204, 1896, 12, emptyList()),
                    weeklyTotals = emptyList(),
                    paySummary = PayrollCalculator.sumMonthlyPay(shifts, settings),
                    rawShifts = shifts,
                    settings = settings,
                    previousMonthMinutes = 4620,
                    insights = if (insights) ReportInsightsBuilder.build(shifts, settings) else null,
                ),
                onPreviousMonth = {},
                onNextMonth = {},
                canGoNext = true,
                onExportCsv = {},
                onExportPdf = {},
            )
        }
    }

    @Composable
    private fun SettingsHubScreen(destination: SettingsDestination) {
        val state = sampleSettingsState()
        when (destination) {
            SettingsDestination.HUB -> SettingsHub(
                state = state,
                displayName = "Dor",
                hourlyRateText = "50.0",
                currency = CurrencyCode.ILS,
                dailyOtText = "8",
                weeklyOtText = "40",
                weekendDays = listOf(5, 6),
                timezone = "Asia/Jerusalem",
                clockStyle = ClockStyle.MINIMAL,
                travelRefunds = true,
                insights = true,
                clockStyles = true,
                overtimeReminders = true,
                authState = AuthUiState.NotConfigured,
                onNavigate = {},
                onSignOut = {},
            )
            SettingsDestination.PROFILE -> ProfileDetailScreen(
                state = state,
                authState = AuthUiState.NotConfigured,
                displayName = "Dor",
                onDisplayNameChange = {},
                onBack = {},
                onResetPassword = {},
                onDeleteAccount = {},
            )
            SettingsDestination.PAY -> PayDetailScreen(
                state = state,
                hourlyRateText = "50.0",
                onHourlyRateChange = {},
                currency = CurrencyCode.ILS,
                onCurrencyChange = {},
                dailyOtText = "8",
                onDailyOtChange = {},
                weeklyOtText = "40",
                onWeeklyOtChange = {},
                weekendDays = listOf(5, 6),
                onWeekendDaysChange = {},
                timezone = "Asia/Jerusalem",
                onTimezoneChange = {},
                onBack = {},
                onOpenCompensation = {},
                onOpenPremiumProfiles = {},
                onOpenTasks = {},
            )
            SettingsDestination.APPEARANCE -> AppearanceDetailScreen(
                state = state,
                clockStyle = ClockStyle.MINIMAL,
                onClockStyleChange = {},
                clockStylesEnabled = true,
                onClockStylesEnabledChange = {},
                reduceMotionEnabled = false,
                onReduceMotionChange = {},
                onBack = {},
                onTheme = {},
            )
            SettingsDestination.FEATURES -> FeaturesDetailScreen(
                travelRefunds = true,
                onTravelRefundsChange = {},
                insights = true,
                onInsightsChange = {},
                overtimeReminders = true,
                onOvertimeRemindersChange = {},
                onBack = {},
            )
            SettingsDestination.HELP -> HelpDetailScreen(
                state = state,
                authState = AuthUiState.NotConfigured,
                onBack = {},
                onReplayOnboarding = {},
                onOpenTerms = {},
                onOpenSyncDetails = {},
                onSyncNow = {},
            )
            else -> Unit
        }
    }

    private fun sampleSettingsState() = SettingsUiState.Ready(
        settings = sampleSettings(),
        profile = Profile(
            id = "p1",
            email = "dor@example.com",
            fullName = "Dor",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        ),
    )

    private fun sampleDashboardState(): DashboardUiState.Ready {
        val settings = sampleSettings()
        val shifts = sampleShifts()
        return DashboardUiState.Ready(
            activeShift = null,
            monthlyReport = MonthlyReport(2025, 4, 5604, 2400, 3204, 1896, 12, emptyList()),
            settings = settings,
            recentShifts = shifts,
            displayName = "Dor",
            paySummary = PayrollCalculator.sumMonthlyPay(shifts, settings),
        )
    }

    private fun sampleSettings() = UserSettings(
        id = "settings",
        userId = "user",
        hourlyRate = 50.0,
        currency = CurrencyCode.ILS,
        featuresTravelRefunds = true,
        featuresInsights = true,
    )

    private fun sampleShifts() = listOf(
        Shift("one", "user", Instant.parse("2025-04-08T06:00:00Z"), Instant.parse("2025-04-08T15:30:00Z"), breakMinutes = 30),
        Shift("two", "user", Instant.parse("2025-04-12T20:00:00Z"), Instant.parse("2025-04-13T04:00:00Z"), notes = "Late coverage", isSpecialDay = true),
    )

    private fun sampleTasks() = listOf(
        Task("t1", "user", "Delivery", "🚚", "#5B4DF2", 55.0, false, Instant.EPOCH, Instant.EPOCH),
        Task("t2", "user", "Support", "🎧", "#16C8D6", 48.0, false, Instant.EPOCH, Instant.EPOCH),
    )
}
