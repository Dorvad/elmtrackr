package com.elmtrackr.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.ColorBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ModifiersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.material.Text
import androidx.wear.tiles.material.Typography
import com.elmtrackr.wear.ElmTrackrWearApp
import com.elmtrackr.wear.R
import com.elmtrackr.wear.WearMainActivity
import com.elmtrackr.wear.sync.WearAuroraColors
import com.elmtrackr.wear.sync.WearDisplayMath
import com.elmtrackr.wear.sync.WearShiftSnapshot
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Glanceable tile: black face per the Wear OS quality guidelines, the
 * ELMTRACKR wordmark on top, and the whole face as one tap target.
 * Clocked out shows the white bolt button + PUNCH IN; clocked in shows
 * the day-goal ring around a live count-up.
 */
class ElmTrackrTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                runCatching { buildTile() }
                    .onSuccess { completer.set(it) }
                    .onFailure { completer.setException(it) }
            }
            "ElmTrackrTileRequest"
        }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .addIdToImageMapping(ID_BACKGROUND, drawableResource(R.drawable.tile_bg_gradient))
                    .addIdToImageMapping(ID_BOLT_BUTTON, drawableResource(R.drawable.tile_bolt_button))
                    .build(),
            )
            "ElmTrackrTileResources"
        }

    private fun drawableResource(resId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(resId)
                    .build(),
            )
            .build()

    private suspend fun buildTile(): TileBuilders.Tile {
        val app = ElmTrackrWearApp.from(this)
        app?.wearStateRepository?.refreshFromDataLayer()
        // No repository means no phone state to show, which is exactly the
        // signed-out face — the tile still renders instead of erroring out and
        // leaving a blank slot in the tile carousel.
        val snapshot = app?.wearStateRepository?.snapshot?.value ?: WearShiftSnapshot.signedOut()

        val root = when {
            !snapshot.signedIn -> signedOutFace()
            snapshot.isActive -> activeFace(snapshot)
            else -> idleFace(snapshot)
        }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Keep the count-up honest: ask the system to re-render every
            // minute while a shift runs, and hourly otherwise.
            .setFreshnessIntervalMillis(if (snapshot.isActive) 60_000L else 3_600_000L)
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(root).build())
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    // --- Faces ---

    private fun idleFace(snapshot: WearShiftSnapshot): LayoutElementBuilders.LayoutElement {
        val display = WearDisplayMath.displayFor(snapshot)
        val detail = listOfNotNull(
            com.elmtrackr.wear.ui.WearLabels.lastPunch(this, snapshot).ifBlank { null }
                ?: snapshot.startTimeLabel.takeIf { it != "--:--" },
            display.todayShort.takeIf { snapshot.todayMinutes > 0 }
                ?.let { getString(R.string.wear_today, it) },
        ).joinToString(" · ")

        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(ID_BOLT_BUTTON)
                    .setWidth(DimensionBuilders.dp(52f))
                    .setHeight(DimensionBuilders.dp(52f))
                    .build(),
            )
            .addContent(spacer(8f))
            .addContent(
                text(getString(R.string.punch_in).uppercase(), Typography.TYPOGRAPHY_TITLE2, INK),
            )
            .addContent(spacer(3f))
            .addContent(text(getString(R.string.wear_clocked_out), Typography.TYPOGRAPHY_CAPTION2, INK2))
            .apply {
                if (detail.isNotBlank()) {
                    addContent(spacer(2f))
                    // Two lines so large system fonts wrap instead of clipping.
                    addContent(text(detail, Typography.TYPOGRAPHY_CAPTION3, OUTLINE, maxLines = 2))
                }
            }
            .build()

        return face(clickAction = punchAction(isActive = false), center = center)
    }

    private fun activeFace(snapshot: WearShiftSnapshot): LayoutElementBuilders.LayoutElement {
        val display = WearDisplayMath.displayFor(snapshot)
        val statusRow = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(text("●", Typography.TYPOGRAPHY_CAPTION2, SUCCESS))
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(4f)).build(),
            )
            .addContent(text(getString(R.string.wear_on_shift), Typography.TYPOGRAPHY_CAPTION2, INK2))
            .build()

        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(statusRow)
            .addContent(spacer(2f))
            .addContent(
                text(
                    WearDisplayMath.elapsedHm(snapshot.shiftStartEpochMillis),
                    Typography.TYPOGRAPHY_DISPLAY1,
                    INK,
                ),
            )
            .addContent(spacer(2f))
            .addContent(text(getString(R.string.wear_tap_to_stop), Typography.TYPOGRAPHY_CAPTION1, OUTLINE))
            .build()

        return face(
            clickAction = punchAction(isActive = true),
            center = center,
            ringPercent = display.progressPercent,
        )
    }

    private fun signedOutFace(): LayoutElementBuilders.LayoutElement {
        val center = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text(getString(R.string.tile_sign_in), Typography.TYPOGRAPHY_TITLE2, INK))
            .addContent(spacer(3f))
            .addContent(text(getString(R.string.tile_on_phone), Typography.TYPOGRAPHY_CAPTION1, OUTLINE))
            .build()

        return face(clickAction = openAppAction(), center = center)
    }

    /**
     * Shared face scaffold: black background, wordmark on top, optional
     * day-goal ring, centered content, single full-face tap target.
     */
    private fun face(
        clickAction: ModifiersBuilders.Clickable,
        center: LayoutElementBuilders.LayoutElement,
        ringPercent: Int? = null,
    ): LayoutElementBuilders.LayoutElement {
        val builder = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickAction)
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(ID_BACKGROUND)
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
                    .build(),
            )

        if (ringPercent != null) {
            builder.addContent(ring(sweepDegrees = 360f, color = RING_TRACK))
            builder.addContent(
                ring(sweepDegrees = (ringPercent.coerceIn(0, 100) * 3.6f).coerceAtLeast(8f), color = RING),
            )
        }

        builder.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_TOP)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setTop(DimensionBuilders.dp(10f))
                                .build(),
                        )
                        .build(),
                )
                .addContent(text(getString(R.string.wear_brand), Typography.TYPOGRAPHY_CAPTION3, INK2))
                .build(),
        )

        builder.addContent(
            LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .addContent(center)
                .build(),
        )

        return builder.build()
    }

    /** Ring inset from the bezel; anchored at 12 o'clock, sweeping clockwise. */
    private fun ring(sweepDegrees: Float, color: Int): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setAll(DimensionBuilders.dp(26f))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Arc.Builder()
                    .setAnchorAngle(DimensionBuilders.degrees(0f))
                    .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                    .addContent(
                        LayoutElementBuilders.ArcLine.Builder()
                            .setLength(DimensionBuilders.degrees(sweepDegrees))
                            .setThickness(DimensionBuilders.dp(6f))
                            .setColor(ColorBuilders.argb(color))
                            .build(),
                    )
                    .build(),
            )
            .build()

    // --- Small helpers ---

    private fun text(value: String, typography: Int, color: Int, maxLines: Int = 1): Text =
        Text.Builder(applicationContext, value)
            .setTypography(typography)
            .setColor(ColorBuilders.argb(color))
            .setMaxLines(maxLines)
            .build()

    private fun spacer(heightDp: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(heightDp))
            .build()

    private fun punchAction(isActive: Boolean): ModifiersBuilders.Clickable {
        val action = if (isActive) WearPunchTrampolineActivity.ACTION_OUT else WearPunchTrampolineActivity.ACTION_IN
        return ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(WearPunchTrampolineActivity::class.java.name)
                            .setPackageName(applicationContext.packageName)
                            .addKeyToExtraMapping(
                                WearPunchTrampolineActivity.EXTRA_ACTION,
                                ActionBuilders.stringExtra(action),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun openAppAction(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setClassName(WearMainActivity::class.java.name)
                            .setPackageName(applicationContext.packageName)
                            .build(),
                    )
                    .build(),
            )
            .build()

    companion object {
        // v4: the bolt mark became the Aurora gradient disc with a white bolt,
        // matching the phone. Tile renderers cache resources by this version,
        // so a bump is the only thing that makes them re-read the drawable —
        // without it, updated watches keep drawing the old white-disc mark.
        private const val RESOURCES_VERSION = "4"
        private const val ID_BACKGROUND = "bg_gradient"
        private const val ID_BOLT_BUTTON = "bolt_button"

        // The tile builders take packed ints rather than Compose Colors, so the
        // palette is restated here. Every value is WearAuroraColors, and the
        // WearTileColorParityTest asserts that it stays that way.
        private val INK = WearAuroraColors.INK
        private val INK2 = WearAuroraColors.INK2
        private val OUTLINE = WearAuroraColors.OUTLINE
        private val SUCCESS = WearAuroraColors.SUCCESS

        /**
         * The goal ring on the tile.
         *
         * Flat aqua where the app's ring is the full indigo→plum→aqua ramp:
         * `ArcLine` takes a single `argb` colour and the tile layout format has
         * no gradient stroke, so a ramp is not expressible here. Aqua is the
         * ramp's end stop and the brightest of the three on black (10.3:1),
         * which is the right one to keep if only one survives.
         */
        private val RING = WearAuroraColors.AQUA

        /** The unfilled remainder of the ring. Outline at roughly 22% over black. */
        private const val RING_TRACK = 0x38A4B0C3.toInt()
    }
}
