package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.ClockStyle

/**
 * The clock faces, grouped for browsing.
 *
 * Twenty faces in one flat grid is a wall: nothing tells the user which ones
 * are alike, so choosing means reading every label. The groups are the browsing
 * unit — each holds at most [GROUP_SIZE] faces, which is what makes the store
 * lay out as clean 2×2 blocks rather than a ragged list.
 *
 * Grouped by what the face *is*, not when it shipped. A user who liked Sand is
 * far more likely to want Tide than Retro.
 *
 * A group is also the unit a user adds or removes: only [ESSENTIALS] is bundled,
 * the rest are packs. See [ClockFacePacks] for what "installed" actually buys.
 */
enum class ClockFaceGroup(
    val faces: List<ClockStyle>,
    val isBundled: Boolean = false,
    /**
     * The app version this group first shipped in, for the store's "New"
     * ribbon — see [isNewIn]. Null means it predates the ribbon (or is the
     * launch bundle) and is never marked new. A version string rather than a
     * boolean so the ribbon expires on its own instead of waiting for someone
     * to remember to remove it.
     */
    val since: String? = null,
) {
    /**
     * The defaults. Read the number, get on with the day.
     *
     * Bundled and not removable: the appearance screen must always have four faces
     * to offer and the dashboard must always have something to draw, so there has
     * to be a floor that cannot be taken away.
     */
    ESSENTIALS(
        listOf(ClockStyle.CLASSIC, ClockStyle.MINIMAL, ClockStyle.FOCUS, ClockStyle.BOLD),
        isBundled = true,
    ),

    /** Faces whose whole job is showing how far through the day you are. */
    PROGRESS(
        listOf(ClockStyle.AURORA, ClockStyle.DIAL, ClockStyle.STRAND, ClockStyle.BLOCKS),
        since = "1.1.1",
    ),

    /** Mood first: colour, glow, grain. */
    ATMOSPHERE(
        listOf(ClockStyle.NIGHT, ClockStyle.RETRO, ClockStyle.PULSE, ClockStyle.PRISM),
        since = "1.1.1",
    ),

    /** The day as something that grows, flows or fills. */
    NATURE(
        listOf(ClockStyle.SAND, ClockStyle.TIDE, ClockStyle.SPROUT, ClockStyle.LUNA),
        since = "1.1.1",
    ),

    /** The day as a trip with a destination. */
    JOURNEYS(
        listOf(ClockStyle.ORBIT, ClockStyle.METRO, ClockStyle.VINYL, ClockStyle.SUMMIT),
        since = "1.1.1",
    ),

    /**
     * The shift as what it earns. Abstract on purpose: gold, coins and meters
     * stand in for money, and no face ever prints an amount — the dashboard is
     * glanceable in public, and the metaphor needs nothing beyond the day
     * progress every face already reads.
     */
    PAYDAY(
        listOf(ClockStyle.METER, ClockStyle.STACKS, ClockStyle.JAR, ClockStyle.TICKER),
        since = "1.3.0",
    ),

    /**
     * The shift as numbers, for people who want the numbers.
     *
     * The one pack whose faces print figures. Every other face is a metaphor —
     * [PAYDAY]'s KDoc above says so explicitly, and that rule still holds for
     * Payday: gold and meters stand in for money and no Payday face prints an
     * amount, because the dashboard is glanceable in public.
     *
     * These four are the deliberate exception, and it is a choice of *notation*
     * rather than decoration: the same shift in four formats — aligned telemetry
     * rows, a time series against the target, an analog gauge, and a grid of
     * five-minute cells. Someone who picks this pack is asking to see the
     * numbers, and a pack that then hid them would be pointless.
     *
     * The consequence is real and worth naming: a Readout or Sparkline face
     * shows earnings on screen, so this pack trades the public glanceability the
     * rest of the catalogue protects. That is the user's call, made by choosing
     * the pack — which is why it is a pack of its own and not a change to the
     * shared faces.
     *
     * All four are [drawsOwnReading], so the composed centre readout is
     * suppressed for them; they would otherwise print the elapsed time twice.
     */
    NERDS(
        listOf(ClockStyle.READOUT, ClockStyle.SPARKLINE, ClockStyle.GAUGE, ClockStyle.MATRIX),
        since = "1.4.0",
    ),
    ;

    /**
     * Whether the store shows the "New" ribbon on this group.
     *
     * New while [currentVersion] is in the same minor-release series as [since]
     * or the one after it — a pack shipped in 1.1.x stays new through 1.2.x and
     * drops the ribbon at 1.3.0, with no release checklist involved. Anything
     * unparseable, and a major-version jump, mean not new: a wrong claim of
     * newness is worse than a missing ribbon.
     */
    fun isNewIn(currentVersion: String): Boolean {
        val shipped = parseVersion(since ?: return false) ?: return false
        val current = parseVersion(currentVersion) ?: return false
        return current.first == shipped.first && current.second - shipped.second in 0..1
    }

    companion object {
        /** Faces per group, and per row-pair in the store's picker grids. */
        const val GROUP_SIZE = 4

        /** Always present, never removable. */
        val bundled: List<ClockFaceGroup> get() = entries.filter { it.isBundled }

        /** Everything the user can add or remove. */
        val packs: List<ClockFaceGroup> get() = entries.filter { !it.isBundled }

        /** The group [face] belongs to. Every face belongs to exactly one. */
        fun of(face: ClockStyle): ClockFaceGroup =
            entries.first { face in it.faces }

        /** Major and minor of a `major.minor[.patch]` version, or null. */
        private fun parseVersion(version: String): Pair<Int, Int>? {
            val parts = version.trim().split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
            return major to minor
        }
    }
}

/**
 * Which face packs a user has, and what that does and does not mean.
 *
 * **It does not save disk space.** Every face is Kotlin drawing code compiled into
 * the APK, so installing or removing a pack changes zero bytes on the device. What
 * it does is keep the store to what the user asked for, and put a single
 * decision point in front of adding a pack — which is the seam a purchase flow
 * plugs into later. Real download-on-demand would need the faces moved into a
 * Play Feature Delivery module; that is a build-level change, not this one.
 */
object ClockFacePacks {

    /**
     * The packs actually available to the user right now.
     *
     * [stored] is what they have installed. The bundled groups are always added,
     * and so is the group holding [selected] — a stored selection must never
     * become unreachable, whether because this feature arrived after the user had
     * already picked a face from a pack, or because a stored set was lost. Derived
     * rather than migrated for that reason: there is no seeding step to forget to
     * run, and no state where the app cannot draw the user's own clock.
     */
    fun available(
        stored: Set<ClockFaceGroup>,
        selected: ClockStyle,
    ): Set<ClockFaceGroup> = stored + ClockFaceGroup.bundled + ClockFaceGroup.of(selected)

    /** Every face the user can choose from, in store order. */
    fun availableFaces(
        stored: Set<ClockFaceGroup>,
        selected: ClockStyle,
    ): List<ClockStyle> {
        val groups = available(stored, selected)
        return ClockFaceGroup.entries.filter { it in groups }.flatMap { it.faces }
    }

    /**
     * Whether [pack] can be removed, and the reason it cannot.
     *
     * A bundled pack is never removable. Neither is the one holding the selected
     * face — removing it would leave the dashboard drawing a face the user no
     * longer has. The caller switches the selection first; see
     * [fallbackAfterRemoving].
     */
    fun canRemove(pack: ClockFaceGroup, selected: ClockStyle): Boolean =
        !pack.isBundled && ClockFaceGroup.of(selected) != pack

    /**
     * The face to switch to when [pack] is removed while its face is selected.
     *
     * Always a bundled face, because a bundled pack is the only thing guaranteed
     * to still be there afterwards.
     */
    fun fallbackAfterRemoving(pack: ClockFaceGroup, selected: ClockStyle): ClockStyle? =
        if (ClockFaceGroup.of(selected) == pack) ClockFaceGroup.ESSENTIALS.faces.first() else null

    /** Parses stored pack names, dropping any that no longer exist. */
    fun resolve(rawNames: Collection<String>): Set<ClockFaceGroup> {
        val byName = ClockFaceGroup.entries.associateBy { it.name }
        return rawNames.mapNotNullTo(mutableSetOf()) { byName[it.trim().uppercase()] }
    }
}

/** How many faces the appearance screen offers before "browse all". */
const val CLOCK_FACE_QUICK_PICK_COUNT = ClockFaceGroup.GROUP_SIZE

/**
 * The four faces the appearance screen offers, most relevant first.
 *
 * [current] always leads, so the screen can never fail to show what is actually
 * selected — that was the risk in showing recents alone. The rest are the user's
 * own history, which is the point: someone who rotates between three faces
 * should never have to open the store, while someone who set a face once and
 * forgot about it sees a stable, sensible four.
 *
 * Padding comes from [ClockFaceGroup.ESSENTIALS] rather than the enum's
 * declaration order, so a first-time user is offered the defaults instead of
 * whichever faces happen to be declared first.
 */
fun clockFaceQuickPicks(
    current: ClockStyle,
    recents: List<ClockStyle>,
    available: List<ClockStyle> = ClockStyle.entries,
    count: Int = CLOCK_FACE_QUICK_PICK_COUNT,
): List<ClockStyle> {
    val offerable = available.toSet()
    val picks = LinkedHashSet<ClockStyle>()
    // The selected face leads unconditionally, even in the impossible case that it
    // is not in [available] — showing the user something other than what is set
    // would be a worse failure than showing one unavailable tile.
    picks += current
    picks += recents.filter { it in offerable }
    picks += ClockFaceGroup.ESSENTIALS.faces.filter { it in offerable }
    picks += available
    return picks.take(count)
}

/**
 * Resolves stored face names, dropping any that no longer exist.
 *
 * Deliberately *not* `ClockStyle.fromPersisted`, which is right for a single
 * stored selection and wrong here: its CLASSIC fallback would turn a face
 * removed in a later version into a phantom Classic entry in the history, and
 * two removed faces into two duplicate entries. A name that means nothing should
 * contribute nothing.
 */
fun resolveClockFaceRecents(rawNames: List<String>): List<ClockStyle> {
    val byName = ClockStyle.entries.associateBy { it.name }
    return rawNames.mapNotNull { byName[it.trim().uppercase()] }.distinct()
}

/**
 * Records [style] as the most recently used face.
 *
 * Bounded to [limit] because this list only ever feeds a fixed-size row; an
 * unbounded history would grow the preferences file for no visible benefit.
 */
fun updatedClockFaceRecents(
    recents: List<ClockStyle>,
    style: ClockStyle,
    limit: Int = CLOCK_FACE_QUICK_PICK_COUNT,
): List<ClockStyle> = (listOf(style) + recents).distinct().take(limit)
