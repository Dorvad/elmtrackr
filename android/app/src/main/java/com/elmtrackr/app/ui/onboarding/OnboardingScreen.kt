package com.elmtrackr.app.ui.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.elmtrackr.app.domain.model.ClockStyle
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.compensation.RegionPresets
import com.elmtrackr.app.domain.MoneyFormatter
import com.elmtrackr.app.ui.design.AppLogo
import com.elmtrackr.app.ui.design.ElmGradientButton
import com.elmtrackr.app.ui.settings.IanaTimezonePicker
import com.elmtrackr.app.ui.settings.WatchFacePreview
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.elmtrackr.app.security.BiometricAuthPrompt
import com.elmtrackr.app.security.BiometricAvailability
import com.elmtrackr.app.security.BiometricCapability
import com.elmtrackr.app.ui.theme.AuroraAqua
import com.elmtrackr.app.ui.theme.AuroraIndigo
import com.elmtrackr.app.ui.theme.Spacing
import java.util.TimeZone

private const val TOTAL_STEPS = 8
private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

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
    var regionCode by rememberSaveable { mutableStateOf(RegionCode.IL) }
    var currencyCode by rememberSaveable { mutableStateOf("ILS") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var hourlyRateText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(CurrencyCode.ILS) }
    var dailyOtText by rememberSaveable { mutableStateOf("8") }
    var weeklyOtText by rememberSaveable { mutableStateOf("40") }
    var weekendDays by rememberSaveable { mutableStateOf(listOf(5, 6)) }
    var timezone by rememberSaveable { mutableStateOf(TimeZone.getDefault().id) }
    var travelRefunds by rememberSaveable { mutableStateOf(false) }
    var paidProjects by rememberSaveable { mutableStateOf(false) }
    var insights by rememberSaveable { mutableStateOf(true) }
    var clockStyles by rememberSaveable { mutableStateOf(true) }
    var clockStyle by rememberSaveable { mutableStateOf(ClockStyle.CLASSIC) }
    var enableAppLock by rememberSaveable { mutableStateOf(false) }
    var initializedFromSettings by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as FragmentActivity
    val biometricAvailability = remember { BiometricCapability.check(context) }

    val scrollState = rememberScrollState()

    LaunchedEffect(initialSettings?.id, initialProfile?.id, replay) {
        val settings = initialSettings ?: return@LaunchedEffect
        val profile = initialProfile ?: return@LaunchedEffect
        if (!initializedFromSettings && step <= 2) {
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
            paidProjects = settings.featuresPaidProjects
            insights = settings.featuresInsights
            clockStyles = settings.featuresClockStyles
            clockStyle = settings.clockStyle
            initializedFromSettings = true
        }
    }

    LaunchedEffect(state) { if (state is OnboardingUiState.Completed) onCompleted() }
    LaunchedEffect(step) { scrollState.scrollTo(0) }

    BackHandler(enabled = replay && step == 1) { onCompleted() }
    BackHandler(enabled = step > 1) {
        step = when (step) {
            8 -> 7
            7 -> 6
            6 -> 5
            5 -> 4
            4 -> 3
            3 -> 2
            else -> 1
        }
    }

    val hourlyRate = hourlyRateText.toDoubleOrNull()
    val dailyOt = dailyOtText.toDoubleOrNull()
    val weeklyOt = weeklyOtText.toDoubleOrNull()
    val profileValid = displayName.trim().isNotEmpty()
    val payValid = hourlyRateText.isBlank() || (hourlyRate != null && hourlyRate > 0)
    val workWeekValid = dailyOt != null && dailyOt > 0 && dailyOt <= 24 &&
        weeklyOt != null && weeklyOt >= dailyOt && weeklyOt <= 168 && weekendDays.isNotEmpty()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state is OnboardingUiState.Saving || state is OnboardingUiState.Completed) {
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
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingProgress(step)
                Spacer(Modifier.height(24.dp))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(140)))
                        } else {
                            (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(140)))
                        }
                    },
                    label = "onboarding-step",
                ) { current ->
                    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                        when (current) {
                            1 -> WelcomeStep(replay) { step = 2 }
                            2 -> RegionStep(
                                regionCode = regionCode,
                                currencyCode = currencyCode,
                                timezone = timezone,
                                onSelectRegion = { code ->
                                    regionCode = code
                                    RegionPresets.forRegion(code).let { preset ->
                                        currencyCode = preset.currencyCode
                                        timezone = preset.timezone
                                    }
                                },
                                onBack = { step = 1 },
                                onNext = { step = 3 },
                            )
                            3 -> ProfileStep(
                                name = displayName,
                                email = initialProfile?.email.orEmpty(),
                                onNameChange = { displayName = it },
                                showError = !profileValid && displayName.isNotEmpty(),
                                onBack = { step = 2 },
                                onNext = { if (profileValid) step = 4 },
                            )
                            4 -> PaySetupStep(
                                hourlyRate = hourlyRateText,
                                currency = currency,
                                onHourlyRateChange = { hourlyRateText = it.decimalInput() },
                                onCurrencyChange = { currency = it; currencyCode = it.name },
                                valid = payValid,
                                onBack = { step = 3 },
                                onNext = { if (payValid) step = 5 },
                            )
                            5 -> WorkWeekStep(
                                weekendDays = weekendDays,
                                dailyOt = dailyOtText,
                                weeklyOt = weeklyOtText,
                                timezone = timezone,
                                onWeekendDaysChange = { weekendDays = it },
                                onDailyOtChange = { dailyOtText = it.decimalInput() },
                                onWeeklyOtChange = { weeklyOtText = it.decimalInput() },
                                onTimezoneChange = { timezone = it },
                                valid = workWeekValid,
                                onBack = { step = 4 },
                                onNext = { if (workWeekValid) step = 6 },
                            )
                            6 -> FeaturesStep(
                                travelRefunds, insights,
                                { travelRefunds = it }, { insights = it },
                                onBack = { step = 5 }, onNext = { step = 7 },
                            )
                            7 -> SecurityStep(
                                appLockEnabled = enableAppLock,
                                biometricAvailability = biometricAvailability,
                                onAppLockChange = { enabled ->
                                    if (!enabled) {
                                        enableAppLock = false
                                        return@SecurityStep
                                    }
                                    BiometricAuthPrompt.show(
                                        activity = activity,
                                        title = "Enable app lock",
                                        subtitle = "Confirm to require biometric unlock when opening ElmTrackr",
                                        onSuccess = { enableAppLock = true },
                                        onFailure = { },
                                    )
                                },
                                onBack = { step = 6 },
                                onNext = { step = 8 },
                            )
                            else -> ReviewStep(
                                displayName = displayName.trim(),
                                hourlyRate = hourlyRate,
                                currency = currency,
                                regionLabel = RegionPresets.forRegion(regionCode).label,
                                weekendDays = weekendDays,
                                enabledCount = listOf(travelRefunds, insights, enableAppLock).count { it },
                                error = (state as? OnboardingUiState.ValidationError)?.errors?.values?.firstOrNull(),
                                onBack = { step = 7 },
                                onFinish = {
                                    viewModel.completeOnboarding(
                                        OnboardingInput(
                                            displayName = displayName.trim(),
                                            regionCode = regionCode,
                                            currencyCode = currencyCode,
                                            timezone = timezone,
                                            dailyOvertimeHours = dailyOt!!,
                                            weeklyOvertimeHours = weeklyOt!!,
                                            weekendDays = weekendDays,
                                            hourlyRate = hourlyRate,
                                            currency = currency,
                                            featuresTravelRefunds = travelRefunds,
                                            featuresPaidProjects = false,
                                            featuresInsights = insights,
                                            featuresClockStyles = true,
                                            clockStyle = ClockStyle.CLASSIC,
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
internal fun OnboardingProgress(step: Int) {
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
                    Text("ELMTRACKR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Text(stepTitle(step), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "$step / $TOTAL_STEPS",
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { step / TOTAL_STEPS.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

private fun stepTitle(step: Int): String = when (step) {
    1 -> "Welcome"
    2 -> "Your region"
    3 -> "Your profile"
    4 -> "Pay preferences"
    5 -> "Work week"
    6 -> "Features"
    7 -> "Security"
    else -> "Review"
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
        "Where do you work?",
        "Choose a region preset to start with suggested compensation rules. You can customize everything later.",
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
                    Text(preset.label, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        preset.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Currency: $currencyCode · Timezone: $timezone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "ElmTrackr provides estimated compensation only — not legal or payroll advice.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(18.dp))
    NavRow(onBack, onNext)
}

@Composable
internal fun ProfileStep(
    name: String,
    email: String,
    onNameChange: (String) -> Unit,
    showError: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    SetupHero(Icons.Filled.Person, "Let’s make it yours", "What should we call you inside ElmTrackr?")
    SetupCard {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your name") },
            placeholder = { Text("e.g. Dor") },
            supportingText = { Text(if (showError) "Enter your name to continue" else "This appears on your dashboard and reports") },
            isError = showError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        if (email.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text("Signed in as $email", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(18.dp))
    NavRow(onBack, onNext, nextEnabled = name.isNotBlank())
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
    SetupHero(Icons.Filled.Paid, "Know what your time is worth", "Add your base hourly salary for live gross-pay estimates.")
    SetupCard {
        OutlinedTextField(
            value = hourlyRate,
            onValueChange = onHourlyRateChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hourly salary") },
            prefix = { Text(currency.symbol) },
            placeholder = { Text("0.00") },
            supportingText = {
                Text(if (!valid) "Enter a positive amount" else "Optional. Leave blank if you only want to track hours.")
            },
            isError = !valid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(16.dp))
        Text("Currency", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                                Text(option.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
internal fun WorkWeekStep(
    weekendDays: List<Int>,
    dailyOt: String,
    weeklyOt: String,
    timezone: String,
    onWeekendDaysChange: (List<Int>) -> Unit,
    onDailyOtChange: (String) -> Unit,
    onWeeklyOtChange: (String) -> Unit,
    onTimezoneChange: (String) -> Unit,
    valid: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var showTimezoneEditor by rememberSaveable { mutableStateOf(false) }
    SetupHero(Icons.Filled.CalendarMonth, "Set your work week", "Choose weekend days and when overtime starts.")
    SetupCard {
        Text("Weekend days", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Weekend and holiday pay rules use these days.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DAY_LABELS.forEachIndexed { day, label ->
                val selected = day in weekendDays
                Card(
                    onClick = {
                        onWeekendDaysChange(
                            if (selected) weekendDays - day else (weekendDays + day).sorted(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(CornerRadius.Small),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        label.take(2),
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (weekendDays.isEmpty()) {
            Text("Select at least one day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = dailyOt,
                onValueChange = onDailyOtChange,
                modifier = Modifier.weight(1f),
                label = { Text("Daily OT") },
                suffix = { Text("h") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = weeklyOt,
                onValueChange = onWeeklyOtChange,
                modifier = Modifier.weight(1f),
                label = { Text("Weekly OT") },
                suffix = { Text("h") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
        if (!valid && weekendDays.isNotEmpty()) {
            Text("Daily must be 1–24h; weekly must be at least daily and no more than 168h.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius.Medium))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Timezone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(timezone, fontWeight = FontWeight.SemiBold)
                Text(
                    "Set from your region in step 2",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showTimezoneEditor = !showTimezoneEditor }) {
                Text(if (showTimezoneEditor) "Hide timezone picker" else "Change timezone")
            }
        }
        if (showTimezoneEditor) {
            IanaTimezonePicker(
                selected = timezone,
                onSelect = onTimezoneChange,
            )
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
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(82.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(CornerRadius.Large)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Ready to start", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            "Clock in once. See hours, pay estimate, and overtime instantly.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        SetupCard {
            ReviewRow("Name", displayName)
            ReviewRow("Region", regionLabel)
            ReviewRow("Hourly salary", hourlyRate?.let { MoneyFormatter.format(it, currency) } ?: "Not set")
            ReviewRow("Weekend", weekendDays.joinToString(", ") { DAY_LABELS[it] })
            ReviewRow("Optional features", "$enabledCount enabled", showDivider = false)
        }
        error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(22.dp))
        ElmGradientButton(onClick = onFinish) { Text("Save and start tracking", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(118.dp).background(AuroraAqua.copy(alpha = .16f), RoundedCornerShape(CornerRadius.Large)))
            AppLogo(
                modifier = Modifier.size(96.dp),
                cornerRadius = 30.dp,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(if (replay) "Review your setup" else "Welcome to ElmTrackr", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (replay) {
                "Update your profile, pay, work week, and features."
            } else {
                "Clock in once. See hours, pay estimate, and overtime instantly."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        SetupCard {
            WelcomeBenefit(Icons.Filled.AccessTime, "One tap to clock in and out")
            WelcomeBenefit(Icons.Filled.Paid, "Live pay and overtime estimates")
            WelcomeBenefit(Icons.Filled.Analytics, "Reports build automatically", showDivider = false)
        }
        Spacer(Modifier.height(26.dp))
        ElmGradientButton(onClick = onNext) { Text(if (replay) "Review Setup" else "Get Started", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun WelcomeBenefit(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, showDivider: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(CornerRadius.Small)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
    if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
internal fun SecurityStep(
    appLockEnabled: Boolean,
    biometricAvailability: BiometricAvailability,
    onAppLockChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    SetupHero(
        Icons.Filled.Fingerprint,
        "Protect your data",
        "Optionally require fingerprint, face unlock, or your device PIN to open ElmTrackr.",
    )
    SetupCard {
        val canEnable = biometricAvailability == BiometricAvailability.AVAILABLE
        FeatureCard(
            title = "Require biometric to open",
            description = when (biometricAvailability) {
                BiometricAvailability.AVAILABLE ->
                    "Lock pay rates, receipts, and shift history when the app is in the background"
                BiometricAvailability.NOT_ENROLLED ->
                    "Set up fingerprint, face unlock, or a device PIN in system settings first"
                BiometricAvailability.UNAVAILABLE ->
                    "Biometric or device credential unlock is not available on this device"
            },
            enabled = appLockEnabled,
            onChange = { enabled ->
                if (canEnable || !enabled) onAppLockChange(enabled)
            },
            comingSoon = !canEnable && !appLockEnabled,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You can change this anytime in Settings → Security.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(18.dp))
    NavRow(onBack, onNext)
}

@Composable
internal fun FeaturesStep(
    travel: Boolean, insights: Boolean,
    onTravel: (Boolean) -> Unit, onInsights: (Boolean) -> Unit,
    onBack: () -> Unit, onNext: () -> Unit,
) {
    StepHeader("What do you need?", "Enable the features that fit your workflow. You can change these later in Settings.")
    FeatureCard("Travel Refunds", "Track transport reimbursements for eligible shifts.", travel, onTravel)
    FeatureCard("Insights & Analytics", "Smart summaries, weekly trends, and daily coaching.", insights, onInsights)
    Spacer(Modifier.height(20.dp)); NavRow(onBack, onNext)
}

@Composable
internal fun ClockStyleStep(selected: ClockStyle, onSelect: (ClockStyle) -> Unit, onBack: () -> Unit, onNext: () -> Unit) {
    StepHeader("Pick your style", "Choose how your clock widget looks on the home screen.")
    ClockStyle.entries.chunked(2).forEach { rowStyles ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            rowStyles.forEach { style -> ClockStyleCard(style, selected == style, Modifier.weight(1f)) { onSelect(style) } }
            if (rowStyles.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(10.dp)); NavRow(onBack, onNext)
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    Spacer(Modifier.height(3.dp)); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun FeatureCard(title: String, description: String, enabled: Boolean, onChange: (Boolean) -> Unit, comingSoon: Boolean = false) {
    Card(
        onClick = { if (!comingSoon) onChange(!enabled) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = when {
                comingSoon -> MaterialTheme.colorScheme.surface
                enabled    -> MaterialTheme.colorScheme.primaryContainer
                else       -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(AuroraIndigo.copy(alpha = if (comingSoon) 0.05f else .11f), RoundedCornerShape(CornerRadius.Medium)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Bolt, null, tint = AuroraIndigo.copy(alpha = if (comingSoon) 0.4f else 1f)) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = if (comingSoon) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (comingSoon) 0.5f else 1f))
            }
            Switch(enabled, onCheckedChange = onChange, enabled = !comingSoon)
        }
    }
}

@Composable
private fun ClockStyleCard(style: ClockStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WatchFacePreview(style, selected)
            Spacer(Modifier.height(7.dp))
            Text(style.name.lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NavRow(onBack: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
        ElmGradientButton(onClick = onNext, enabled = nextEnabled, modifier = Modifier.weight(1f)) { Text("Continue", fontWeight = FontWeight.Bold) }
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
