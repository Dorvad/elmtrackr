package com.elmtrackr.app.billing

import com.elmtrackr.app.ui.settings.ClockFaceGroup
import com.elmtrackr.app.ui.settings.ClockFacePacks

/**
 * Who owns which pack, and why.
 *
 * Ownership has exactly two sources and this is where they meet. Keeping the
 * rule pure — no Play, no DataStore, no coroutines — is what makes it testable
 * without a device, and what stops "does the user own this?" from being answered
 * differently in the gallery, the view model and the billing client.
 *
 * Ownership is not the same thing as *having* a pack. A pack the user added
 * stays in [ClockFacePacks.available] whether or not they own it, so nobody's
 * dashboard stops drawing when this file starts saying no. Ownership gates
 * **adding** a pack, which is the only moment a purchase is a fair thing to ask
 * for.
 */
object ClockFacePackOwnership {

    /**
     * Every pack the user may add.
     *
     * The union of what Play says they bought and what they already had when
     * packs became paid. Bundled packs are not listed: they are not owned, they
     * are simply always there, and putting them here would mean two places
     * answer the "is this free?" question.
     */
    fun owned(
        purchasedProductIds: Collection<String>,
        grandfathered: Set<ClockFaceGroup>,
    ): Set<ClockFaceGroup> =
        ClockFacePackProducts.packsGrantedBy(purchasedProductIds) +
            grandfathered.filterNot { it.isBundled }

    /**
     * The packs to grant permanently when a device first runs a build that
     * charges for them.
     *
     * ElmTrackr shipped every pack free through 1.2.4. Taking one back from
     * someone who is already using it would be a worse trade than any revenue it
     * could earn — it reads as a downgrade in the release notes, and the store
     * review it earns outlives the sale. So whatever is installed at the moment
     * of the switch is theirs for good, and the charge applies only to packs
     * they had not taken.
     *
     * Seeded from the device's own installed set because that is what pack
     * ownership was before Play knew about it: packs have always been
     * device-local (see [com.elmtrackr.app.data.local.preferences.ClockFacePreferences]),
     * so there is no account-scoped record of the free era to consult. A face
     * *selected* on another device still arrives through sync and stays usable —
     * [ClockFacePacks.available] adds the group holding the selection regardless
     * of ownership — it simply cannot be removed and re-added for free.
     */
    fun grandfatherSeed(installedPackNames: Collection<String>): Set<ClockFaceGroup> =
        ClockFacePacks.resolve(installedPackNames).filterNot { it.isBundled }.toSet()
}
