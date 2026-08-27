package com.elmtrackr.app.billing

import com.elmtrackr.app.ui.settings.ClockFaceGroup

/**
 * The Play products the clock face packs are sold as, and what each one grants.
 *
 * Pure Kotlin on purpose: this is the one place a product id is written down, and
 * nothing here needs a device, a Play connection or a build config to be checked.
 * The mapping is derived from [ClockFaceGroup] rather than listed by hand so a
 * pack added to the catalog cannot silently ship without a product — the id
 * appears the moment the group does, and [ClockFacePackProductsTest] fails if the
 * two ever disagree.
 *
 * **Ids are permanent.** Play will not let a product id be reused after the
 * product is deleted, and a renamed id is a different product that existing
 * buyers do not own. Renaming a [ClockFaceGroup] constant therefore breaks every
 * purchase already made against it; add a new group instead, or pin the old id
 * here explicitly.
 */
object ClockFacePackProducts {

    /**
     * Buys every pack at once, including packs added in later versions.
     *
     * Sold alongside the individual packs rather than instead of them: a single
     * expensive product asks for the whole decision up front, while a cheap
     * per-pack entry point lets someone who liked one face try one pack. The
     * grant is deliberately open-ended — someone who bought "everything" and
     * then sees a new pack locked has been told something untrue.
     */
    const val ALL_PACKS = "clock_faces_all_packs"

    /**
     * Prefix for the per-pack products, e.g. `clock_faces_nature`.
     *
     * Lowercase with underscores because Play only accepts lowercase letters,
     * digits, underscores and periods in a product id.
     */
    private const val PACK_PREFIX = "clock_faces_"

    /** The packs that are sold. Never includes the bundled one. */
    val purchasablePacks: List<ClockFaceGroup> get() = ClockFaceGroup.packs

    /**
     * The product [pack] is sold as, or null when it is bundled with the app.
     *
     * Null rather than a throw: "this pack is free" is an ordinary answer that
     * callers have to handle either way, and an exception would only move the
     * check to every call site.
     */
    fun productId(pack: ClockFaceGroup): String? =
        if (pack.isBundled) null else PACK_PREFIX + pack.name.lowercase()

    /** The pack sold as [id], or null for the bundle and unknown ids. */
    fun packOf(id: String): ClockFaceGroup? =
        purchasablePacks.firstOrNull { productId(it) == id }

    /**
     * Every product id to ask Play about, in the order the storefront shows them.
     *
     * One query for all of them: Play bills a round trip per call, not per
     * product, and partial results would leave some packs priced and others not.
     */
    val all: List<String> get() = purchasablePacks.mapNotNull(::productId) + ALL_PACKS

    /** The packs owning [productId] grants. Empty for an id this build does not know. */
    fun packsGrantedBy(productId: String): Set<ClockFaceGroup> = when (productId) {
        ALL_PACKS -> purchasablePacks.toSet()
        else -> packOf(productId)?.let { setOf(it) }.orEmpty()
    }

    /**
     * The packs [productIds] grants between them.
     *
     * Unknown ids contribute nothing rather than failing: a product retired in a
     * later version, or one added by a newer build sharing the same Play account,
     * must not stop the ids beside it from being honoured.
     */
    fun packsGrantedBy(productIds: Collection<String>): Set<ClockFaceGroup> =
        productIds.flatMapTo(mutableSetOf()) { packsGrantedBy(it) }
}
