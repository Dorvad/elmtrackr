package com.elmtrackr.wear.tile

import com.elmtrackr.wear.R
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
import com.elmtrackr.wear.sync.WearAuroraColors
import com.elmtrackr.wear.sync.WearDisplayMath
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
                    .build(),
            )
            "ElmTrackrTileResources"
        }

    private suspend fun buildTile(): TileBuilders.Tile {
        val app = applicationContext as ElmTrackrWearApp
        app.wearStateRepository.refreshFromDataLayer()
        val snapshot = app.wearStateRepository.snapshot.value
        val display = WearDisplayMath.displayFor(snapshot)
        val punchAction = punchAction(snapshot.isActive)

        val primaryLabel = if (snapshot.signedIn) {
            if (snapshot.isActive) display.elapsedHms else display.primaryTimeLabel
        } else {
            getString(R.string.tile_sign_in)
        }
        val secondaryLabel = when {
            !snapshot.signedIn -> getString(R.string.tile_on_phone)
            snapshot.isActive -> getString(R.string.tile_tap_punch_out)
            display.snapshot.lastPunchLabel.isNotBlank() -> display.snapshot.lastPunchLabel
            else -> getString(R.string.tile_tap_punch_in)
        }

        val root = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(punchAction)
                    .build(),
            )
            .addContent(
                Text.Builder(applicationContext, primaryLabel)
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY2)
                    .setColor(ColorBuilders.argb(WearAuroraColors.ON_SURFACE))
                    .build(),
            )
            .addContent(
                Text.Builder(applicationContext, secondaryLabel)
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(ColorBuilders.argb(WearAuroraColors.INK2))
                    .build(),
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(root)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

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

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
