package com.elmtrackr.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.elmtrackr.app.ui.theme.CornerRadius
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elmtrackr.app.R
import com.elmtrackr.app.language.AppLanguage
import com.elmtrackr.app.ui.common.asString
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.ui.design.AppLogo
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.settings.IanaTimezonePicker
import com.elmtrackr.app.ui.settings.currencyDisplayName
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.elmtrackr.app.security.BiometricAuthPrompt
import com.elmtrackr.app.security.BiometricAvailability
import com.elmtrackr.app.security.BiometricCapability
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.Spacing
import java.util.TimeZone

/** 516 → "8.6", 2520 → "42" — matches what the hours text fields accept. */
private fun minutesToHoursText(minutes: Int): String =
    (minutes / 60.0).toString().removeSuffix(".0")

@Composable
fun OnboardingScreen(
    replay: Boolean = false,
    onCompleted: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val initialSettings by viewModel.initialSettings.collectAsState()
    val initialProfile by viewModel.initialProfile.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var regionCode by rememberSaveable { mutableStateOf(ONBOARDING_DEFAULT_REGION) }
    var currencyCode by rememberSaveable { mutableStateOf("ILS") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var hourlyRateText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(CurrencyCode.ILS) }
    // Seeded from the preset for the pre-selected region rather than from
    // constants. These used to default to 8 / 40 / Fri-Sat while regionCode
    // defaulted to IL, whose preset is 8.6 h / 42 h — and the preset was applied
    // only inside onSelectRegion. A user who accepted the region already chosen
    // for them silently got the wrong overtime thresholds, which feed straight
    // into pay.
    val defaultPresetRules = remember { onboardingDefaultRules() }
    var dailyOtText by rememberSaveable {
        mutableStateOf(minutesToHoursText(defaultPresetRules.dailyStandardMinutes))
    }
    var weeklyOtText by rememberSaveable {
        mutableStateOf(minutesToHoursText(defaultPresetRules.weeklyStandardMinutes))
    }
    var weekendDays by rememberSaveable { mutableStateOf(defaultPresetRules.weekendDays) }
    var timezone by rememberSaveable { mutableStateOf(TimeZone.getDefault().id) }
    var travelRefunds by rememberSaveable { mutableStateOf(false) }
    var insights by rememberSaveable { mutableStateOf(true) }
    var paidProjects by rememberSaveable { mutableStateOf(false) }
    // Null means "follow the work setup chosen in the Region and Pay steps",
    // which the user has not necessarily reached at first composition.
    var projectRegionCode by rememberSaveable { mutableStateOf<RegionCode?>(null) }
    var projectCurrency by rememberSaveable { mutableStateOf<CurrencyCode?>(null) }
    var projectTaxLabel by rememberSaveable { mutableStateOf("") }
    var projectTaxRateText by rememberSaveable { mutableStateOf("") }
    var projectTaxInclusive by rememberSaveable { mutableStateOf(false) }
    var enableAppLock by rememberSaveable { mutableStateOf(false) }
    var initializedFromSettings by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    // Nullable on purpose: production hosts the wizard in an AppCompatActivity,
    // but a plain cast crashed at composition for every step in any other host
    // (previews, tests) even though only the Security step needs it.
    val activity = context as? FragmentActivity
    // Re-checked on every resume: the user can leave for system settings,
    // enroll a fingerprint, and come back mid-wizard.
    var biometricAvailability by remember { mutableStateOf(BiometricCapability.check(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                biometricAvailability = BiometricCapability.check(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val appLockPromptTitle = stringResource(R.string.onboarding_biometric_prompt_title)
    val appLockPromptSubtitle = stringResource(R.string.onboarding_biometric_prompt_subtitle)

    val scrollState = rememberScrollState()

    val initialAppLock by viewModel.initialAppLockEnabled.collectAsState()
    LaunchedEffect(initialSettings?.id, initialProfile?.id, initialAppLock, replay) {
        val settings = initialSettings ?: return@LaunchedEffect
        val profile = initialProfile ?: return@LaunchedEffect
        // A replay waits for the app-lock preference too, so the Security toggle
        // seeds with the truth instead of always-off.
        if (replay && initialAppLock == null) return@LaunchedEffect
        // On replay the wizard holds a spinner until this seed lands (see the
        // gate below), so a fast tap-through can never outrun the settings
        // emission and finish with wizard defaults written over real settings.
        // On first run seeding stays opportunistic: new users usually have no
        // settings row yet, and past the early steps their typed values win.
        if (!initializedFromSettings && (replay || step <= 3)) {
            displayName = profile.fullName.orEmpty()
            hourlyRateText = settings.hourlyRate?.toString().orEmpty()
            currency = settings.currency
            dailyOtText = (settings.dailyOvertimeThresholdMinutes / 60.0).toEditableHours()
            weeklyOtText = (settings.weeklyOvertimeThresholdMinutes / 60.0).toEditableHours()
            weekendDays = settings.weekendDays
            timezone = settings.timezone
            regionCode = settings.regionCode ?: regionCode
            currencyCode = settings.currencyCode ?: settings.currency.name
            travelRefunds = settings.featuresTravelRefunds
            insights = settings.featuresInsights
            paidProjects = settings.featuresPaidProjects
            projectRegionCode = settings.projectsDefaultRegionCode
            projectCurrency = settings.projectsDefaultCurrencyCode?.let { CurrencyCode.from(it) }
            projectTaxLabel = settings.projectsTaxLabel.orEmpty()
            projectTaxRateText = basisPointsToPercentText(settings.projectsTaxRateBasisPoints)
            projectTaxInclusive = settings.projectsTaxInclusive
            enableAppLock = initialAppLock ?: false
            initializedFromSettings = true
        }
    }

    LaunchedEffect(state) { if (state is OnboardingUiState.Completed) onCompleted() }
    LaunchedEffect(step) { scrollState.scrollTo(0) }

    BackHandler(enabled = replay && step == 1) { onCompleted() }
    BackHandler(enabled = step > STEP_WELCOME) {
        step = previousOnboardingStep(step)
    }

    val hourlyRate = hourlyRateText.toDoubleOrNull()
    val dailyOt = dailyOtText.toDoubleOrNull()
    val weeklyOt = weeklyOtText.toDoubleOrNull()
    val profileValid = displayName.trim().isNotEmpty()
    val payValid = hourlyRateText.isBlank() || (hourlyRate != null && hourlyRate > 0)
    val workWeekValid = dailyOt != null && dailyOt > 0 && dailyOt <= 24 &&
        weeklyOt != null && weeklyOt >= dailyOt && weeklyOt <= 168 && weekendDays.isNotEmpty()
    // Blank is valid and means "no tax": the app must not invent a rate.
    val projectTaxRate = projectTaxRateText.toDoubleOrNull()
    val projectTaxValid = projectTaxRateText.isBlank() ||
        (projectTaxRate != null && projectTaxRate >= 0 && projectTaxRate <= 100)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state is OnboardingUiState.Saving || state is OnboardingUiState.Completed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            return@Surface
        }
        // Replay edits existing settings, so nothing renders until they have
        // seeded the fields — settings always exist here (onboarding completed
        // once), so this cannot hang; a new user is unaffected.
        if (replay && !initializedFromSettings) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            return@Surface
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f),
                    ),
                ),
            ),
        ) {
            // In RTL, "forward" content should slide in from the left, so the
            // slide offsets follow the current layout direction.
            val slideSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingProgress(
                    step = step,
                    totalSteps = onboardingTotalSteps(),
                    titleRes = stepTitleRes(step),
                )
                Spacer(Modifier.height(24.dp))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(260)) { slideSign * it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(180)) { slideSign * -it / 4 } + fadeOut(tween(140)))
                        } else {
                            (slideInHorizontally(tween(260)) { slideSign * -it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(180)) { slideSign * it / 4 } + fadeOut(tween(140)))
                        }
                    },
                    label = "onboarding-step",
                ) { current ->
                    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                        when (current) {
                            STEP_WELCOME -> WelcomeStep(replay) {
                                step = nextOnboardingStep(STEP_WELCOME)
                            }
                            STEP_REGION -> RegionStep(
                                regionCode = regionCode,
                                currencyCode = currencyCode,
                                timezone = timezone,
                                onSelectRegion = { code ->
                                    regionCode = code
                                    RegionPresets.forRegion(code).let { preset ->
                                        currencyCode = preset.currencyCode
                                        // Keep the currency picker in sync — previously only the
                                        // code string changed and the selected chip lagged behind.
                                        currency = CurrencyCode.from(preset.currencyCode)
                                        timezone = preset.timezone
                                        // Overtime and weekend defaults follow the region preset
                                        // (e.g. Israel: 8.6 h / 42 h, Fri–Sat weekend).
                                        dailyOtText = minutesToHoursText(preset.rules.dailyStandardMinutes)
                                        weeklyOtText = minutesToHoursText(preset.rules.weeklyStandardMinutes)
                                        weekendDays = preset.rules.weekendDays
                                    }
                                },
                                onBack = { step = previousOnboardingStep(STEP_REGION) },
                                onNext = { step = nextOnboardingStep(STEP_REGION) },
                            )
                            STEP_PAY -> PaySetupStep(
                                hourlyRate = hourlyRateText,
                                currency = currency,
                                onHourlyRateChange = { hourlyRateText = it.decimalInput() },
                                onCurrencyChange = { currency = it; currencyCode = it.name },
                                valid = payValid,
                                onBack = { step = previousOnboardingStep(STEP_PAY) },
                                onNext = {
                                    if (payValid) step = nextOnboardingStep(STEP_PAY)
                                },
                            )
                            else -> ReviewStep(
                                displayName = displayName.trim(),
                                hourlyRate = hourlyRate,
                                currency = currency,
                                regionLabel = stringResource(RegionPresets.forRegion(regionCode).labelRes),
                                weekendDays = weekendDays,
                                enabledCount = listOf(travelRefunds, insights, paidProjects, enableAppLock)
                                    .count { it },
                                paidProjectsEnabled = paidProjects,
                                error = (state as? OnboardingUiState.ValidationError)?.errors?.values?.firstOrNull()?.asString(),
                                onBack = { step = previousOnboardingStep(STEP_REVIEW) },
                                onFinish = {
                                    val validDailyOt = dailyOt ?: return@ReviewStep
                                    val validWeeklyOt = weeklyOt ?: return@ReviewStep
                                    viewModel.completeOnboarding(
                                        OnboardingInput(
                                            displayName = displayName.trim(),
                                            regionCode = regionCode,
                                            currencyCode = currencyCode,
                                            timezone = timezone,
                                            dailyOvertimeHours = validDailyOt,
                                            weeklyOvertimeHours = validWeeklyOt,
                                            weekendDays = weekendDays,
                                            hourlyRate = hourlyRate,
                                            currency = currency,
                                            featuresTravelRefunds = travelRefunds,
                                            featuresPaidProjects = paidProjects,
                                            featuresInsights = insights,
                                            featuresClockStyles = true,
                                            clockStyle = ClockStyle.CLASSIC,
                                            projectsDefaultRegionCode =
                                                (projectRegionCode ?: regionCode).takeIf { paidProjects },
                                            projectsDefaultCurrencyCode =
                                                (projectCurrency ?: currency).name.takeIf { paidProjects },
                                            projectsTaxLabel = projectTaxLabel.trim()
                                                .takeIf { paidProjects && it.isNotBlank() },
                                            projectsTaxRateBasisPoints =
                                                if (paidProjects) percentTextToBasisPoints(projectTaxRateText) else 0,
                                            projectsTaxInclusive = paidProjects && projectTaxInclusive,
                                            preserveExisting = replay,
                                            enableAppLock = enableAppLock,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
internal fun OnboardingProgress(
    step: Int,
    totalSteps: Int,
    @StringRes titleRes: Int,
) {
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(
                    modifier = Modifier.size(34.dp),
                    cornerRadius = 11.dp,
                    contentDescription = null,
                )
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(stringResource(R.string.onboarding_brand), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(titleRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                stringResource(R.string.onboarding_step_counter, step, totalSteps),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { step / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@StringRes
private fun stepTitleRes(step: Int): Int = when (step) {
    STEP_WELCOME -> R.string.onboarding_step_welcome
    STEP_REGION -> R.string.onboarding_step_region
    STEP_PAY -> R.string.onboarding_step_pay
    else -> R.string.onboarding_step_review
}

/** "18" -> 1800. Blank or unparseable means no tax, never a guessed rate. */
internal fun percentTextToBasisPoints(text: String): Int {
    val percent = text.trim().toDoubleOrNull() ?: return 0
    if (percent <= 0.0 || percent > 100.0) return 0
    return kotlin.math.round(percent * 100).toInt()
}

/** 1800 -> "18", 0 -> "" so a cleared rate shows as empty rather than "0". */
internal fun basisPointsToPercentText(basisPoints: Int): String {
    if (basisPoints <= 0) return ""
    return (basisPoints / 100.0).toString().removeSuffix(".0")
}

@Composable
private fun LanguageOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    layoutDirection: LayoutDirection,
    onClick: () -> Unit,
) {
    // Each card renders in its own language's direction, so the Hebrew
    // option reads right-to-left even while the app is still in English.
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(CornerRadius.Medium)) else Modifier,
            ),
            shape = RoundedCornerShape(CornerRadius.Medium),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RegionStep(
    regionCode: RegionCode,
    currencyCode: String,
    timezone: String,
    onSelectRegion: (RegionCode) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    SetupHero(
        Icons.Filled.Tune,
        stringResource(R.string.onboarding_region_title),
        stringResource(R.string.onboarding_region_subtitle),
    )
    SetupCard {
        RegionPresets.all.forEach { preset ->
            val selected = preset.regionCode == regionCode
            Card(
                onClick = { onSelectRegion(preset.regionCode) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(preset.labelRes), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(preset.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_region_currency_timezone, currencyCode, timezone),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onboarding_region_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(18.dp))
    NavRow(onBack, onNext)
}

@Composable
internal fun PaySetupStep(
    hourlyRate: String,
    currency: CurrencyCode,
    onHourlyRateChange: (String) -> Unit,
    onCurrencyChange: (CurrencyCode) -> Unit,
    valid: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    SetupHero(Icons.Filled.Paid, stringResource(R.string.onboarding_pay_title), stringResource(R.string.onboarding_pay_subtitle))
    SetupCard {
        OutlinedTextField(
            value = hourlyRate,
            onValueChange = onHourlyRateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_pay_hourly_salary_label)) },
            prefix = { Text(currency.symbol) },
            placeholder = { Text(stringResource(R.string.onboarding_pay_rate_placeholder)) },
            supportingText = {
                Text(if (!valid) stringResource(R.string.onboarding_pay_rate_error) else stringResource(R.string.onboarding_pay_rate_helper))
            },
            isError = !valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_pay_currency_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        CurrencyCode.entries.chunked(2).forEach { rowCurrencies ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCurrencies.forEach { option ->
                    val selected = option == currency
                    Card(
                        onClick = { onCurrencyChange(option) },
                        modifier = Modifier.weight(1f).then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(CornerRadius.Medium)) else Modifier,
                        ),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(option.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(option.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(currencyDisplayName(option), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    NavRow(onBack, onNext, nextEnabled = valid)
}

@Composable
internal fun ReviewStep(
    displayName: String,
    hourlyRate: Double?,
    currency: CurrencyCode,
    regionLabel: String,
    weekendDays: List<Int>,
    enabledCount: Int,
    error: String?,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    paidProjectsEnabled: Boolean = false,
) {
    val dayLabels = stringArrayResource(R.array.weekday_short_labels)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(82.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(CornerRadius.Large)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.onboarding_review_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            stringResource(R.string.onboarding_tagline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        SetupCard {
            ReviewRow(stringResource(R.string.onboarding_review_name), displayName)
            ReviewRow(stringResource(R.string.onboarding_review_region), regionLabel)
            ReviewRow(stringResource(R.string.onboarding_review_hourly_salary), hourlyRate?.let { MoneyFormatter.format(it, currency) } ?: stringResource(R.string.onboarding_review_not_set))
            ReviewRow(stringResource(R.string.onboarding_review_weekend), weekendDays.joinToString(", ") { dayLabels[it] })
            ReviewRow(
                stringResource(R.string.onboarding_review_paid_projects),
                stringResource(
                    if (paidProjectsEnabled) {
                        R.string.onboarding_review_paid_projects_on
                    } else {
                        R.string.onboarding_review_paid_projects_off
                    },
                ),
            )
            ReviewRow(stringResource(R.string.onboarding_review_optional_features), stringResource(R.string.onboarding_review_enabled_count, enabledCount), showDivider = false)
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(22.dp))
        ElmGradientButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_save_and_start), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_back)) }
    }
}

@Composable
private fun ReviewRow(label: String, value: String, showDivider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
    if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).then(
            if (selected) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(CornerRadius.Medium))
            } else {
                Modifier
            },
        ),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SetupHero(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Box(
        Modifier.size(62.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(CornerRadius.Large)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(31.dp)) }
    Spacer(Modifier.height(15.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(5.dp))
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SetupCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}

@Composable
internal fun WelcomeStep(replay: Boolean, onNext: () -> Unit) {
    // Language lives here rather than on a screen of its own. It was the very
    // first thing the wizard asked, before the app had said what it was — and it
    // is a short list of options, which is not a screen's worth of decision.
    // Picking one applies it immediately and recreates the activity;
    // rememberSaveable in the host keeps the flow on this step.
    val context = LocalContext.current
    // Read off the configuration rather than AppLanguage.current(), so the card
    // marked is the language on screen even when the choice is the device's.
    val selectedLanguage = AppLanguage.forLanguageCode(LocalConfiguration.current.locales[0]?.language)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(118.dp).background(AuroraAqua.copy(alpha = .16f), RoundedCornerShape(CornerRadius.Large)))
            AppLogo(
                modifier = Modifier.size(96.dp),
                cornerRadius = 30.dp,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(if (replay) stringResource(R.string.onboarding_welcome_replay_title) else stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (replay) {
                stringResource(R.string.onboarding_welcome_replay_subtitle)
            } else {
                stringResource(R.string.onboarding_welcome_intro)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        SetupCard {
            // Each option is written in its own language so it stays readable
            // whichever one is currently active.
            LanguageOptionCard(
                title = "English",
                subtitle = "Track your hours in English",
                selected = selectedLanguage == AppLanguage.ENGLISH,
                layoutDirection = LayoutDirection.Ltr,
                onClick = { AppLanguage.apply(context, AppLanguage.ENGLISH) },
            )
            LanguageOptionCard(
                title = "עברית",
                subtitle = "לעקוב אחרי השעות שלך בעברית",
                selected = selectedLanguage == AppLanguage.HEBREW,
                layoutDirection = LayoutDirection.Rtl,
                onClick = { AppLanguage.apply(context, AppLanguage.HEBREW) },
            )
            LanguageOptionCard(
                title = "العربية",
                subtitle = "تتبّع ساعات عملك بالعربية",
                selected = selectedLanguage == AppLanguage.ARABIC,
                layoutDirection = LayoutDirection.Rtl,
                onClick = { AppLanguage.apply(context, AppLanguage.ARABIC) },
            )
            LanguageOptionCard(
                title = "Русский",
                subtitle = "Учёт рабочих часов на русском",
                selected = selectedLanguage == AppLanguage.RUSSIAN,
                layoutDirection = LayoutDirection.Ltr,
                onClick = { AppLanguage.apply(context, AppLanguage.RUSSIAN) },
            )
        }
        Spacer(Modifier.height(16.dp))
        SetupCard {
            Text(
                stringResource(R.string.onboarding_how_it_works),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            HowItWorksRow(
                number = 1,
                title = stringResource(R.string.onboarding_how_step1_title),
                description = stringResource(R.string.onboarding_how_step1_desc),
            )
            HowItWorksRow(
                number = 2,
                title = stringResource(R.string.onboarding_how_step2_title),
                description = stringResource(R.string.onboarding_how_step2_desc),
            )
            HowItWorksRow(
                number = 3,
                title = stringResource(R.string.onboarding_how_step3_title),
                description = stringResource(R.string.onboarding_how_step3_desc),
                showDivider = false,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_reassurance),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        ElmGradientButton(onClick = onNext) { Text(if (replay) stringResource(R.string.onboarding_review_setup_button) else stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.Bold) }
        if (!replay) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_welcome_setup_time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HowItWorksRow(number: Int, title: String, description: String, showDivider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(3.dp)); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun FeatureCard(title: String, description: String, enabled: Boolean, onChange: (Boolean) -> Unit, unavailable: Boolean = false) {
    Card(
        onClick = { if (!unavailable) onChange(!enabled) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = when {
                unavailable -> MaterialTheme.colorScheme.surface
                enabled    -> MaterialTheme.colorScheme.primaryContainer
                else       -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(AuroraIndigo.copy(alpha = if (unavailable) 0.05f else .11f), RoundedCornerShape(CornerRadius.Medium)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Bolt, null, tint = AuroraIndigo.copy(alpha = if (unavailable) 0.4f else 1f)) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (unavailable) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (unavailable) 0.5f else 1f))
            }
            Switch(enabled, onCheckedChange = onChange, enabled = !unavailable)
        }
    }
}

@Composable
private fun NavRow(onBack: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_back)) }
        ElmGradientButton(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.onboarding_continue), fontWeight = FontWeight.Bold) }
    }
}

private fun String.decimalInput(): String = buildString {
    var hasDecimal = false
    this@decimalInput.forEach { char ->
        when {
            char.isDigit() -> append(char)
            char == '.' && !hasDecimal && isNotEmpty() -> {
                append(char)
                hasDecimal = true
            }
        }
    }
}

private fun Double.toEditableHours(): String = if (this == toInt().toDouble()) toInt().toString() else toString()
