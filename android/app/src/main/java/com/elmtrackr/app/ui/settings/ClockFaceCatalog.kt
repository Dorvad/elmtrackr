package com.elmtrackr.app.ui.settings

import com.elmtrackr.app.domain.model.ClockStyle

/**
 * The clock faces, grouped for browsing.
 *
 * Nineteen faces in one flat grid is a wall: nothing tells the user which ones
 * are alike, so choosing means reading every label. The groups are the browsing
 * unit — each holds at most [GROUP_SIZE] faces, which is what makes the gallery
 * lay out as clean 2×2 blocks rather than a ragged list.
 *
 * Grouped by what the face *is*, not when it shipped. A user who liked Sand is
 * far more likely to want Tide than Retro.
 */
enum class ClockFaceGroup(val faces: List<ClockStyle>) {
    /** The defaults. Read the number, get on with the day. */
    ESSENTIALS(
        listOf(ClockStyle.CLASSIC, ClockStyle.MINIMAL, ClockStyle.FOCUS, ClockStyle.BOLD),
    ),

    /** Faces whose whole job is showing how far through the day you are. */
    PROGRESS(
        listOf(ClockStyle.AURORA, ClockStyle.DIAL, ClockStyle.STRAND, ClockStyle.BLOCKS),
    ),

    /** Mood first: colour, glow, grain. */
    ATMOSPHERE(
        listOf(ClockStyle.NIGHT, ClockStyle.RETRO, ClockStyle.PULSE, ClockStyle.PRISM),
    ),

    /** The day as something that grows, flows or fills. */
    NATURE(
        listOf(ClockStyle.SAND, ClockStyle.TIDE, ClockStyle.SPROUT, ClockStyle.LUNA),
    ),

    /** The day as a trip with a destination. */
    JOURNEYS(
        listOf(ClockStyle.ORBIT, ClockStyle.METRO, ClockStyle.VINYL),
    ),
    ;

    companion object {
        /**
         * Faces per group, and per row-pair in the gallery.
         *
         * [JOURNEYS] holds three rather than four. Padding it with a filler face
         * would be worse than the gap: the groups exist to mean something, and
         * one short group is honest about there being nineteen faces.
         */
        const val GROUP_SIZE = 4
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
 * should never have to open the gallery, while someone who set a face once and
 * forgot about it sees a stable, sensible four.
 *
 * Padding comes from [ClockFaceGroup.ESSENTIALS] rather than the enum's
 * declaration order, so a first-time user is offered the defaults instead of
 * whichever faces happen to be declared first.
 */
fun clockFaceQuickPicks(
    current: ClockStyle,
    recents: List<ClockStyle>,
    count: Int = CLOCK_FACE_QUICK_PICK_COUNT,
): List<ClockStyle> {
    val picks = LinkedHashSet<ClockStyle>()
    picks += current
    picks += recents
    picks += ClockFaceGroup.ESSENTIALS.faces
    picks += ClockStyle.entries
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
