package com.elmtrackr.app.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import android.provider.Settings
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
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.PremiumType
import com.elmtrackr.app.domain.model.Task
import com.elmtrackr.app.domain.model.UserSettings
import com.elmtrackr.app.notification.ReminderRulesCodec
import com.elmtrackr.app.security.BiometricAvailability
import com.elmtrackr.app.ui.auth.AuthUiState
import com.elmtrackr.app.ui.auth.SignedOutContent
import com.elmtrackr.app.ui.dashboard.DashboardReadyPreview
import com.elmtrackr.app.ui.dashboard.DashboardSkeleton
import com.elmtrackr.app.ui.dashboard.DashboardUiState
import com.elmtrackr.app.ui.onboarding.OnboardingProgress
import com.elmtrackr.app.ui.onboarding.PaySetupStep
import com.elmtrackr.app.ui.onboarding.ReviewStep
import com.elmtrackr.app.ui.onboarding.WelcomeStep
import com.elmtrackr.app.ui.onboarding.STEP_PAY
import com.elmtrackr.app.ui.onboarding.STEP_REVIEW
import com.elmtrackr.app.ui.onboarding.STEP_WELCOME
import com.elmtrackr.app.ui.reports.HoursReport
import com.elmtrackr.app.ui.reports.ReportsUiState
import com.elmtrackr.app.ui.settings.AppearanceDetailScreen
import com.elmtrackr.app.ui.settings.CompensationSettingsContent
import com.elmtrackr.app.ui.settings.CompensationSettingsUiState
import com.elmtrackr.app.ui.settings.FeaturesDetailScreen
import com.elmtrackr.app.ui.settings.HelpDetailScreen
import com.elmtrackr.app.ui.settings.LegalDocumentScreen
import com.elmtrackr.app.ui.settings.LegalDocuments
import com.elmtrackr.app.ui.auth.SignedInContent
import com.elmtrackr.app.ui.settings.PayDetailScreen
import com.elmtrackr.app.ui.settings.PremiumProfilesContent
import com.elmtrackr.app.ui.settings.PremiumProfilesUiState
import com.elmtrackr.app.ui.settings.ProfileDetailScreen
import com.elmtrackr.app.ui.settings.SecurityDetailScreen
import com.elmtrackr.app.ui.settings.ClockFaceGalleryScreen
import com.elmtrackr.app.ui.settings.SettingsDestination
import com.elmtrackr.app.ui.settings.SettingsHub
import com.elmtrackr.app.ui.settings.SettingsUiState
import com.elmtrackr.app.ui.settings.SyncDetailsContent
import com.elmtrackr.app.ui.shifts.MonthShiftSummary
import com.elmtrackr.app.ui.shifts.RideProviderSelector
import com.elmtrackr.app.ui.shifts.ShiftEditFormContent
import com.elmtrackr.app.ui.shifts.ShiftFormNavState
import com.elmtrackr.app.ui.shifts.ShiftRow
import com.elmtrackr.app.ui.tasks.TaskManagementContent
import com.elmtrackr.app.ui.tasks.TaskManagementUiState
import com.elmtrackr.app.ui.design.LocalReduceMotion
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class FullAppScreenshotJvmTest {
    @get:Rule
    val paparazzi = Paparazzi(
        // 360×800 dp content at density 2 → 720×1600 px canvas. Keeping the
        // canvas at 360×800 px cropped every capture to the top-left quarter.
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 720, screenHeight = 1600),
    )

    @Before
    fun disableAnimations() {
        Settings.Global.putFloat(
            paparazzi.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    @Test fun authSignIn() = capture("01-auth-sign-in") {
        // Named: SignedOutContent gained parameters and a positional list
        // silently slides when that happens.
        SignedOutContent(
            isLoading = false,
            errorMessage = null,
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onResetPassword = {},
            onClearError = {},
        )
    }

    @Test fun onboardingWelcome() = capture("02-onboarding-welcome") {
        OnboardingColumn(STEP_WELCOME) { WelcomeStep(replay = false, onNext = {}) }
    }


    @Test fun onboardingPay() = capture("04-onboarding-pay") {
        OnboardingColumn(STEP_PAY) { PaySetupStep("50", CurrencyCode.ILS, {}, {}, true, {}, {}) }
    }



    @Test fun onboardingReview() = capture("07-onboarding-review") {
        OnboardingColumn(STEP_REVIEW) {
            ReviewStep("Dor", 50.0, CurrencyCode.ILS, "Israel", listOf(5, 6), 2, null, {}, {})
        }
    }

    @Test fun dashboardReady() = capture("08-dashboard") {
        DashboardReadyPreview(state = sampleDashboardState())
    }

    @Test fun dashboardDark() = capture("26-dashboard-dark", darkTheme = true) {
        DashboardReadyPreview(state = sampleDashboardState())
    }

    @Test fun dashboardSprout() = capture("31-dashboard-sprout") {
        // 5.5 hours logged: five leaves on the vine, stem still climbing.
        DashboardReadyPreview(
            state = sampleDashboardState().copy(
                settings = sampleSettings().copy(clockStyle = ClockStyle.SPROUT),
                todayCompletedMinutes = 330,
            ),
        )
    }

    @Test fun dashboardMetro() = capture("32-dashboard-metro") {
        // 5.5 hours logged: the train parked past the fifth station, three to go.
        DashboardReadyPreview(
            state = sampleDashboardState().copy(
                settings = sampleSettings().copy(clockStyle = ClockStyle.METRO),
                todayCompletedMinutes = 330,
            ),
        )
    }

    @Test fun dashboardVinyl() = capture("33-dashboard-vinyl") {
        // 5.5 hours logged: five hour-grooves lit, tonearm two-thirds inward.
        DashboardReadyPreview(
            state = sampleDashboardState().copy(
                settings = sampleSettings().copy(clockStyle = ClockStyle.VINYL),
                todayCompletedMinutes = 330,
            ),
        )
    }

    @Test fun dashboardLuna() = capture("34-dashboard-luna") {
        // 5.5 hours logged: a waxing gibbous, terminator past centre.
        DashboardReadyPreview(
            state = sampleDashboardState().copy(
                settings = sampleSettings().copy(clockStyle = ClockStyle.LUNA),
                todayCompletedMinutes = 330,
            ),
        )
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
            startMillis = shift.startTime.toEpochMilli(),
            endMillis = shift.endTime!!.toEpochMilli(),
            hasEndTime = true,
            onHasEndTimeChange = {},
            breakMinutes = shift.breakMinutes,
            onBreakMinutesChange = {},
            notesText = shift.notes.orEmpty(),
            onNotesChange = {},
            premiumProfileId = shift.premiumProfileId,
            onPremiumProfileIdChange = {},
            premiumProfiles = emptyList(),
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

    @Test fun reportsHours() = capture("12-reports-hours") { ReportsColumn(insights = true) }
    @Test fun reportsInsightsDisabled() = capture("13-reports-no-insights") { ReportsColumn(insights = false) }
    @Test fun settingsHub() = capture("14-settings-hub") { SettingsHubScreen(SettingsDestination.HUB) }
    @Test fun settingsProfile() = capture("15-settings-profile") { SettingsHubScreen(SettingsDestination.PROFILE) }
    @Test fun settingsPay() = capture("16-settings-pay") { SettingsHubScreen(SettingsDestination.PAY) }
    @Test fun settingsAppearance() = capture("17-settings-appearance") { SettingsHubScreen(SettingsDestination.APPEARANCE) }
    @Test fun settingsFeatures() = capture("18-settings-features") { SettingsHubScreen(SettingsDestination.FEATURES) }
    @Test fun settingsHelp() = capture("19-settings-help") { SettingsHubScreen(SettingsDestination.HELP) }

    @Test fun settingsSecurity() = capture("20-settings-security") {
        SecurityDetailScreen(false, BiometricAvailability.AVAILABLE, {}, {})
    }

    @Test fun settingsSyncDetails() = capture("21-settings-sync-details") {
        SyncDetailsContent(
            details = SyncDetails(
                lastSuccessfulSyncAt = Instant.parse("2025-04-01T08:30:00Z"),
                lastSyncStatus = "Success",
                pendingByType = listOf(
                    SyncPendingByType(SyncEntityType.SHIFTS, 2, 14),
                    SyncPendingByType(SyncEntityType.TASKS, 0, 3),
                ),
                failedRows = listOf(
                    SyncFailedRow(
                        SyncEntityType.REFUND_CLAIMS, "claim-1", "Lime ride · Apr 12",
                        SyncStatus.FAILED, "Network timeout",
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

    @Test fun settingsCompensationDark() = capture("27-settings-compensation-dark", darkTheme = true) {
        val preset = RegionPresets.forRegion(RegionCode.IL)
        val profile = CompensationProfile(
            "cp1", "user", "Israel default", RegionCode.IL, "ILS", "Asia/Jerusalem",
            50.0, preset.rules, preset.stackingPolicy, isDefault = true,
        )
        CompensationSettingsContent(
            state = CompensationSettingsUiState.Ready(
                profiles = listOf(profile),
                profile = profile,
                settings = sampleSettings(),
                presets = RegionPresets.all,
                currencyOptions = listOf("ILS" to "₪ Israeli shekel", "USD" to "$ US dollar"),
                timezoneOptions = listOf("Asia/Jerusalem", "UTC"),
                isSaving = false,
                saveMessage = null,
            ),
            onBack = {},
            onSelectProfile = {},
            onCreateProfile = {},
            onDeleteProfile = {},
            onSave = {},
            onDismissMessage = {},
        )
    }

    @Test fun settingsCompensation() = capture("22-settings-compensation") {
        val preset = RegionPresets.forRegion(RegionCode.IL)
        val profile = CompensationProfile(
            "cp1", "user", "Israel default", RegionCode.IL, "ILS", "Asia/Jerusalem",
            50.0, preset.rules, preset.stackingPolicy, isDefault = true,
        )
        CompensationSettingsContent(
            state = CompensationSettingsUiState.Ready(
                profiles = listOf(profile),
                profile = profile,
                settings = sampleSettings(),
                presets = RegionPresets.all,
                currencyOptions = listOf("ILS" to "₪ Israeli shekel", "USD" to "$ US dollar"),
                timezoneOptions = listOf("Asia/Jerusalem", "UTC"),
                isSaving = false,
                saveMessage = null,
            ),
            onBack = {},
            onSelectProfile = {},
            onCreateProfile = {},
            onDeleteProfile = {},
            onSave = {},
            onDismissMessage = {},
        )
    }

    @Test fun settingsTasks() = capture("23-settings-tasks") {
        TaskManagementContent(
            state = TaskManagementUiState.Ready(sampleTasks(), emptyList()),
            onBack = {},
            onSave = { _, _, _, _, _ -> },
            onArchive = {},
            onDismissMessage = {},
        )
    }

    // Reduce-motion is on in captures, so this renders the loader's static
    // full-mark frame — the reduce-motion behavior the spec asks for.
    @Test fun authLoading() = capture("38-auth-loading") {
        SignedInContent()
    }

    @Test fun authLoadingDark() = capture("39-auth-loading-dark", darkTheme = true) {
        SignedInContent()
    }

    @Test fun settingsPremiumProfiles() = capture("35-settings-premium-profiles") {
        PremiumProfilesContent(
            state = PremiumProfilesUiState.Ready(profiles = samplePremiumProfiles(), editor = null),
            onCreate = {},
            onEdit = {},
            onDelete = {},
            onDismissEditor = {},
            onSave = {},
            onNameChange = {},
            onMultiplierChange = {},
            onPremiumTypeChange = {},
        )
    }

    @Test fun settingsPremiumProfilesDark() = capture("36-settings-premium-profiles-dark", darkTheme = true) {
        PremiumProfilesContent(
            state = PremiumProfilesUiState.Ready(profiles = samplePremiumProfiles(), editor = null),
            onCreate = {},
            onEdit = {},
            onDelete = {},
            onDismissEditor = {},
            onSave = {},
            onNameChange = {},
            onMultiplierChange = {},
            onPremiumTypeChange = {},
        )
    }

    @Test fun settingsTerms() = capture("24-settings-terms") {
        LegalDocumentScreen("Terms of Service", LegalDocuments.termsOfService, LegalDocuments.LAST_UPDATED, {})
    }

    @Test fun rideProviderSelector() = capture("25-ride-provider") {
        RideProviderSelector(RefundProvider.LIME, {})
    }

    // RTL layout regressions: same screens forced right-to-left, catching
    // mirrored chevrons, slide directions, and start/end padding issues.
    @Test fun onboardingWelcomeRtl() = capture("28-onboarding-welcome-rtl") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OnboardingColumn(STEP_WELCOME) { WelcomeStep(replay = false, onNext = {}) }
        }
    }

    @Test fun settingsHubRtl() = capture("29-settings-hub-rtl") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            SettingsHubScreen(SettingsDestination.HUB)
        }
    }

    @Test fun settingsTasksRtl() = capture("37-settings-tasks-rtl") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            TaskManagementContent(
                state = TaskManagementUiState.Ready(sampleTasks(), emptyList()),
                onBack = {},
                onSave = { _, _, _, _, _ -> },
                onArchive = {},
                onDismissMessage = {},
            )
        }
    }

    @Test fun shiftsListRtl() = capture("30-shifts-list-rtl") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val settings = sampleSettings()
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                MonthShiftSummary(sampleShifts(), settings)
                ShiftRow(sampleShifts()[0], settings, showRefunds = true, onClick = {})
                ShiftRow(sampleShifts()[1], settings, showRefunds = true, onClick = {})
            }
        }
    }



    private fun capture(name: String, darkTheme: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.snapshot(name = name) {
            CompositionLocalProvider(
                LocalDensity provides Density(2f, 1f),
                LocalReduceMotion provides true,
            ) {
                ElmTrackrTheme(darkTheme = darkTheme) {
                    // Match AuroraListScreen: a Surface derives the content color,
                    // so default text renders correctly in dark captures too.
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(360.dp, 800.dp),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ) {
                        Box(Modifier.size(360.dp, 800.dp)) { content() }
                    }
                }
            }
        }
    }

    @Composable
    private fun OnboardingColumn(step: Int, content: @Composable () -> Unit) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp).verticalScroll(rememberScrollState()),
        ) {
            OnboardingProgress(
                step = step,
                totalSteps = 10,
                titleRes = com.elmtrackr.app.R.string.onboarding_step_review,
            )
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
                    year = 2025, month = 4,
                    report = MonthlyReport(2025, 4, 5604, 2400, 3204, 1896, 12, emptyList()),
                    weeklyTotals = emptyList(),
                    paySummary = PayrollCalculator.sumMonthlyPay(shifts, settings),
                    rawShifts = shifts,
                    settings = settings,
                    previousMonthMinutes = 4620,
                    insights = if (insights) ReportInsightsBuilder.build(shifts, settings) else null,
                    zone = java.time.ZoneOffset.UTC,
                ),
                onPreviousMonth = {}, onNextMonth = {}, canGoNext = true,
                onExportCsv = {}, onExportPdf = {},
            )
        }
    }

    @Composable
    private fun SettingsHubScreen(destination: SettingsDestination) {
        val state = SettingsUiState.Ready(
            settings = sampleSettings(),
            profile = Profile("p1", "dor@example.com", "Dor", Instant.EPOCH, Instant.EPOCH),
        )
        when (destination) {
            SettingsDestination.HUB -> SettingsHub(
                state, "Dor", "50.0", CurrencyCode.ILS, "8", "40", listOf(5, 6),
                "Asia/Jerusalem", ClockStyle.MINIMAL, true, false, true, true, true,
                AuthUiState.NotConfigured, {}, {},
            )
            SettingsDestination.PROFILE -> ProfileDetailScreen(
                state, AuthUiState.NotConfigured, "Dor", {}, {}, {}, {},
            )
            SettingsDestination.PAY -> PayDetailScreen(
                state, "50.0", {}, CurrencyCode.ILS, {}, "8", {}, "40", {}, listOf(5, 6), {},
                "Asia/Jerusalem", {}, {}, {}, {}, {},
            )
            SettingsDestination.APPEARANCE -> AppearanceDetailScreen(
                state, ClockStyle.MINIMAL, {}, true, {}, false, {}, {}, {}, {},
            )
            SettingsDestination.CLOCK_FACES -> ClockFaceGalleryScreen(
                selected = ClockStyle.MINIMAL,
                availablePacks = com.elmtrackr.app.ui.settings.ClockFaceGroup.entries.toSet(),
                onSelect = {},
                onInstallPack = {},
                onRemovePack = {},
                onBack = {},
            )
            SettingsDestination.FEATURES -> FeaturesDetailScreen(
                true, {}, false, {}, true, {}, true, {},
                ReminderRulesCodec.DEFAULT_RULES, {}, {}, {}, {},
            )
            SettingsDestination.HELP -> HelpDetailScreen(
                state, AuthUiState.NotConfigured, {}, {}, {}, {}, {},
            )
            else -> Unit
        }
    }

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
        "settings", "user", hourlyRate = 50.0, currency = CurrencyCode.ILS,
        featuresTravelRefunds = true, featuresInsights = true,
    )

    private fun sampleShifts() = listOf(
        Shift("one", "user", Instant.parse("2025-04-08T06:00:00Z"), Instant.parse("2025-04-08T15:30:00Z"), breakMinutes = 30),
        Shift("two", "user", Instant.parse("2025-04-12T20:00:00Z"), Instant.parse("2025-04-13T04:00:00Z"), notes = "Late coverage", isSpecialDay = true),
    )

    private fun sampleTasks() = listOf(
        // Named rather than positional: Task gained a field between hourlyRate and
        // isArchived, and positional args silently slid one column to the left.
        Task(
            id = "t1", userId = "user", name = "Delivery", icon = "🚚", color = "#5B4DF2",
            hourlyRate = 55.0, isArchived = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ),
        Task(
            id = "t2", userId = "user", name = "Support", icon = "🎧", color = "#16C8D6",
            hourlyRate = 48.0, isArchived = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ),
    )

    private fun samplePremiumProfiles() = listOf(
        PremiumProfile("p1", "user", "Holiday", 1.5, PremiumType.ADDITIVE),
        PremiumProfile("p2", "user", "Shabbat", 1.5, PremiumType.HIGHEST_ONLY, isDefault = true),
        PremiumProfile("p3", "user", "Night shift", 1.25, PremiumType.ADDITIVE),
    )
}
