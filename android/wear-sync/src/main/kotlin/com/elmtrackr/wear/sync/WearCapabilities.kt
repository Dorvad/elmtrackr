package com.elmtrackr.wear.sync

/**
 * Capability names each side advertises over the Wear data layer.
 *
 * A capability is the only signal the Wearable API gives one device about what is
 * installed on the other. Both names live here so the string the watch declares in
 * its `res/values/wear.xml` is the one the phone queries, and vice versa; a typo on
 * either side would otherwise read as "app not installed" with nothing failing.
 */
object WearCapabilities {

    /**
     * Declared by the watch app (`wear/src/main/res/values/wear.xml`). The phone's
     * Settings → Wear OS screen reads it to tell "watch paired" from "watch paired
     * and ElmTrackr installed on it".
     */
    const val WATCH_APP = "elmtrackr_wear_app"

    /**
     * Declared by the phone app (`app/src/main/res/values/wear.xml`). The watch reads
     * it before sending a punch: a paired phone that does not advertise it is not
     * running ElmTrackr, so the watch should not wait the full phone round-trip
     * budget for an answer that will never come.
     */
    const val PHONE_APP = "elmtrackr_phone_app"
}
