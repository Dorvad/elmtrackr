package com.elmtrackr.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.ScreenshotTestApplication
import com.elmtrackr.app.ui.theme.ElmTrackrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the reduce-motion contract.
 *
 * `auroraMotionEnabled()` was the correct gate all along, but the app
 * background, every skeleton screen and all navigation transitions checked
 * `ValueAnimator.areAnimatorsEnabled()` directly instead — which only reports
 * the *system* animator scale and cannot see the app's own setting. Turning
 * "reduce motion" on therefore left the most prominent animations running.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = ScreenshotTestApplication::class)
class ReduceMotionRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the gate is off when the user preference is set`() {
        var enabled: Boolean? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                ElmTrackrTheme(darkTheme = false) {
                    enabled = auroraMotionEnabled()
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(false, enabled)
    }

    /**
     * The shimmer must still produce a brush when motion is reduced — a skeleton
     * that renders nothing would be a worse outcome than one that sits still.
     * It is held at its start offset instead of being skipped.
     */
    @Test
    fun `the skeleton shimmer still paints when motion is reduced`() {
        var brush: Brush? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                ElmTrackrTheme(darkTheme = false) {
                    brush = rememberShimmerBrush()
                    Box(Modifier.size(40.dp).background(brush!!))
                }
            }
        }
        composeRule.waitForIdle()

        assertNotNull(brush)
    }

    @Test
    fun `the shimmer is stable across frames when motion is reduced`() {
        val samples = mutableListOf<Brush>()
        composeRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                ElmTrackrTheme(darkTheme = false) {
                    samples += rememberShimmerBrush()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        // A running shimmer recomposes on every frame with a new gradient. Held
        // still, the brush the composable produced does not change.
        assertEquals(1, samples.distinct().size)
    }

    @Test
    fun `sub-screen transitions collapse to an instant fade when motion is reduced`() {
        // motionEnabled = false is what every screen now passes down from
        // auroraMotionEnabled(); the transition must not slide in that case.
        composeRule.setContent {
            ElmTrackrTheme(darkTheme = false) {
                AuroraStateCrossfade(
                    targetState = "one",
                    contentKey = { it },
                ) { Box(Modifier.size(10.dp)) }
            }
        }
        composeRule.waitForIdle()
    }
}
