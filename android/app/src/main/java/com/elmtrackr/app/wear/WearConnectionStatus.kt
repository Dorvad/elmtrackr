package com.elmtrackr.app.wear

/** One paired watch as shown on the Settings → Wear OS screen. */
data class WearWatch(
    val id: String,
    val name: String,
    /** Bluetooth-adjacent right now, as opposed to reachable through the cloud. */
    val isNearby: Boolean,
    /** The ElmTrackr watch app advertised its capability from this node. */
    val appInstalled: Boolean,
)

/**
 * Snapshot of the phone–watch link for the Settings screen.
 *
 * Deliberately free of Play Services types so it can be assembled and unit
 * tested from plain values; the ViewModel maps `Node`/`CapabilityInfo` into
 * [from]'s primitive inputs.
 */
data class WearConnectionStatus(
    /** Wearable API reachable on this phone (Play Services present). */
    val wearApiAvailable: Boolean,
    val watches: List<WearWatch>,
) {
    val anyWatchConnected: Boolean get() = watches.isNotEmpty()
    val anyWatchWithApp: Boolean get() = watches.any { it.appInstalled }

    /** The watch to feature in the graphic: prefer one running the app, then a nearby one. */
    val featuredWatch: WearWatch?
        get() = watches.firstOrNull { it.appInstalled && it.isNearby }
            ?: watches.firstOrNull { it.appInstalled }
            ?: watches.firstOrNull { it.isNearby }
            ?: watches.firstOrNull()

    companion object {
        fun unavailable() = WearConnectionStatus(wearApiAvailable = false, watches = emptyList())

        fun from(
            connectedNodes: List<Triple<String, String, Boolean>>, // id, displayName, isNearby
            capabilityNodeIds: Set<String>,
        ): WearConnectionStatus = WearConnectionStatus(
            wearApiAvailable = true,
            watches = connectedNodes.map { (id, name, nearby) ->
                WearWatch(
                    id = id,
                    name = name,
                    isNearby = nearby,
                    appInstalled = id in capabilityNodeIds,
                )
            }.sortedWith(
                compareByDescending<WearWatch> { it.appInstalled }
                    .thenByDescending { it.isNearby }
                    .thenBy { it.name },
            ),
        )
    }
}
