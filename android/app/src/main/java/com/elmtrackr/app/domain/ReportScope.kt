package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings

/**
 * Which job a monthly report covers: all of them, or one work profile.
 *
 * Only the *reported* shifts are narrowed. The pay-week context a report is priced
 * against stays whole, so a shift is worth exactly the same in a one-job report as
 * in the all-jobs one — narrowing the view must never re-price the work. Weekly
 * overtime is attributed across jobs by the engines today, and that rule is theirs
 * to own, not this filter's.
 */
sealed interface ReportScope {
    data object AllJobs : ReportScope

    data class Job(val profileId: String) : ReportScope

    companion object {
        /**
         * The profile a shift is reported under, or null when it belongs to none of
         * [profiles].
         *
         * A shift with no profile of its own belongs to the default job — every shift
         * recorded before work profiles existed is one of these. A shift whose
         * profile was deleted belongs to no current job: it is still counted under
         * [AllJobs], but filing it under another job would put hours in a report for
         * work that was not done there.
         */
        fun profileIdOf(shift: Shift, settings: UserSettings, profiles: List<CompensationProfile>): String? {
            val id = shift.compensationProfileId ?: settings.defaultCompensationProfileId
                ?: return (profiles.firstOrNull { it.isDefault } ?: profiles.singleOrNull())?.id
            return profiles.firstOrNull { it.id == id || it.remoteId == id }?.id
        }

        /** The shifts of [shifts] that [scope] reports. [AllJobs] keeps every one. */
        fun filter(
            scope: ReportScope,
            shifts: List<Shift>,
            settings: UserSettings,
            profiles: List<CompensationProfile>,
        ): List<Shift> = when (scope) {
            AllJobs -> shifts
            is Job -> shifts.filter { profileIdOf(it, settings, profiles) == scope.profileId }
        }

        /**
         * Whether leave taken at [workplaceId] belongs in [scope].
         *
         * Leave is recorded per workplace, and a work profile names its workplace.
         * A default profile created before profiles had workplaces names none; it
         * takes leave from any workplace no other profile claims, which is where
         * that leave was recorded.
         */
        fun includesLeave(
            scope: ReportScope,
            workplaceId: String,
            profiles: List<CompensationProfile>,
        ): Boolean {
            val job = (scope as? Job) ?: return true
            val profile = profiles.firstOrNull { it.id == job.profileId } ?: return false
            profile.workplaceId?.let { return it == workplaceId }
            return profile.isDefault && profiles.none { it.workplaceId == workplaceId }
        }

        /**
         * [requested] while it still names a job, else [AllJobs] — so a deleted profile,
         * or a list shrunk to one job, never leaves the report filtered to nothing.
         */
        fun valid(requested: ReportScope, profiles: List<CompensationProfile>): ReportScope = when {
            profiles.size < 2 -> AllJobs
            requested is Job && profiles.none { it.id == requested.profileId } -> AllJobs
            else -> requested
        }
    }
}
