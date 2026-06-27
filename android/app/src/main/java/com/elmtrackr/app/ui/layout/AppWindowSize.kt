package com.elmtrackr.app.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ui.theme.Spacing

/** Android's standard smallest-width breakpoint for 7" tablets. */
private val TabletSmallestWidth = 600.dp

/**
 * Distinguishes phone from tablet using [Configuration.smallestScreenWidthDp],
 * which stays stable across orientation changes.
 */
enum class DeviceFormFactor {
    Phone,
    Tablet,
}

@Composable
fun rememberDeviceFormFactor(): DeviceFormFactor {
    val configuration = LocalConfiguration.current
    return remember(configuration.smallestScreenWidthDp) {
        deviceFormFactor(configuration.smallestScreenWidthDp)
    }
}

fun deviceFormFactor(smallestScreenWidthDp: Int): DeviceFormFactor =
    if (smallestScreenWidthDp >= TabletSmallestWidth.value.toInt()) {
        DeviceFormFactor.Tablet
    } else {
        DeviceFormFactor.Phone
    }

/** Max content width for centered phone layouts (matches web `max-w-md`). */
val PhoneContentMaxWidth = 448.dp

/** Horizontal gutter on tablet main content. */
val TabletContentPadding = Spacing.lg

@Composable
fun rememberContentMaxWidth(): Dp {
    val formFactor = rememberDeviceFormFactor()
    return remember(formFactor) {
        when (formFactor) {
            DeviceFormFactor.Phone -> PhoneContentMaxWidth
            DeviceFormFactor.Tablet -> Dp.Unspecified
        }
    }
}

@Composable
fun isTabletLayout(): Boolean = rememberDeviceFormFactor() == DeviceFormFactor.Tablet
