package com.elmtrackr.app.data.sync

import com.elmtrackr.app.data.local.entity.ShiftEntity

/**
 * The one-running-shift rule.
 *
 * Clocking in on two devices produces two open shifts, and both are real rows
 * that sync: the phone pushes one, the tablet pushes the other, and afterwards
 * every device pulls both. The app's whole model of "am I on the clock" assumes
 * there is at most one, so something has to collapse them.
 *
 * The pull path used to do this by refusing to materialise a second open shift
 * and holding the pull cursor at that row. That kept the local database
 * consistent but stalled the entire shifts pull — the held cursor was re-fetched
 * every sync and rejected every time, so *nothing newer than the duplicate ever
 * arrived* until the user happened to clock out. The duplicate also stayed on
 * the other device, because nothing ever resolved it.
 *
 * This resolves instead of stalling, and does so with a rule that is a pure
 * function of the rows themselves. That matters more than which rule it is: each
 * device runs it independently, on its own copy, with no coordination, so the
 * only way they converge is if they all reach the same answer from the same
 * input.
 *
 * **The winner is the open shift that started earliest.** Two clock-ins minutes
 * apart are one work session that the user began at the earlier time; keeping
 * the later row would silently shorten the day they actually worked. Ties break
 * on the remote id (the same value on every device) so the rule stays total,
 * though live rows cannot actually tie — `shifts_user_id_start_time_live_uidx`
 * makes (user_id, start_time) unique.
 *
 * Losing rows are soft-deleted, not dropped: a tombstone is what propagates the
 * decision to the other devices. Anything the user typed on a loser that the
 * winner does not have is copied over first, so resolving cannot lose a note or
 * a task selection.
 */
object RunningShiftResolver {

    data class Resolution(
        /** The shift that stays open, with any detail merged in from [duplicates]. */
        val winner: ShiftEntity,
        /** Open shifts to soft-delete. Never contains [winner]. */
        val duplicates: List<ShiftEntity>,
    ) {
        val hasDuplicates: Boolean get() = duplicates.isNotEmpty()
    }

    /**
     * @param openShifts every live open shift for one user — `endTime == null`
     *   and `deletedAt == null`. Callers pass the whole set; filtering happens
     *   here so the rule cannot be applied to a partial view by accident.
     * @return null when there is nothing to decide (no open shift at all).
     */
    fun resolve(openShifts: List<ShiftEntity>): Resolution? {
        val open = openShifts.filter { it.endTime == null && it.deletedAt == null }
        if (open.isEmpty()) return null

        val ordered = open.sortedWith(
            compareBy<ShiftEntity> { it.startTime }.thenBy { it.remoteId ?: it.localId },
        )
        val winner = ordered.first()
        val duplicates = ordered.drop(1)
        if (duplicates.isEmpty()) return Resolution(winner, emptyList())

        return Resolution(winner = mergeDetail(winner, duplicates), duplicates = duplicates)
    }

    /**
     * Fills gaps in the winner from the losers, oldest first, never overwriting a
     * value the winner already has.
     *
     * The winner is the earliest clock-in, which is often the most hurried one —
     * tapping the clock and picking the task on the second device afterwards is
     * an ordinary thing to do. Without this, resolving would throw that away.
     *
     * `updatedAt` is deliberately left alone when nothing is merged, so a
     * resolution that changes no field does not manufacture a write that other
     * devices then have to pull.
     */
    private fun mergeDetail(winner: ShiftEntity, losers: List<ShiftEntity>): ShiftEntity {
        var merged = winner
        for (loser in losers) {
            if (merged.notes.isNullOrBlank() && !loser.notes.isNullOrBlank()) {
                merged = merged.copy(notes = loser.notes)
            }
            if (merged.taskId == null && loser.taskId != null) {
                merged = merged.copy(
                    taskId = loser.taskId,
                    taskNameSnapshot = loser.taskNameSnapshot,
                    taskIconSnapshot = loser.taskIconSnapshot,
                    taskHourlyRateSnapshot = loser.taskHourlyRateSnapshot,
                )
            }
            if (merged.projectId == null && loser.projectId != null) {
                merged = merged.copy(
                    projectId = loser.projectId,
                    projectNameSnapshot = loser.projectNameSnapshot,
                    // Carried with the project link, never on its own: this is what
                    // decides whether the shift is paid as wages or as project time.
                    compensationSource = loser.compensationSource,
                )
            }
            if (merged.premiumProfileId == null && loser.premiumProfileId != null) {
                merged = merged.copy(premiumProfileId = loser.premiumProfileId)
            }
            if (merged.compensationProfileId == null && loser.compensationProfileId != null) {
                merged = merged.copy(compensationProfileId = loser.compensationProfileId)
            }
        }
        return merged
    }
}
