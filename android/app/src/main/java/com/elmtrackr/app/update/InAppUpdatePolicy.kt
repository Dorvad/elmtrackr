package com.elmtrackr.app.update

/**
 * Which Google Play in-app update flow (if any) should be started.
 *
 * Deliberately free of any Play / Android types so the decision can be unit
 * tested on the JVM without an emulator or a Play Store connection.
 */
enum class InAppUpdateAction {
    /** No suitable update is available — do nothing. */
    NONE,

    /** Background download the user can keep working through; prompt to restart when ready. */
    FLEXIBLE,

    /** Blocking, full-screen update for high-priority or very stale releases. */
    IMMEDIATE,
}

/**
 * Plain-Kotlin snapshot of the fields we care about from Play's `AppUpdateInfo`.
 * Mapping the framework type into this struct keeps [InAppUpdatePolicy] pure.
 *
 * @param updateAvailable Play reports a newer version is available to install.
 * @param flexibleAllowed The platform permits the flexible flow for this update.
 * @param immediateAllowed The platform permits the immediate flow for this update.
 * @param priority Play "inAppUpdatePriority" for the release (0 default … 5 highest).
 * @param stalenessDays Days since the update became available on Play, or null if unknown.
 * @param updateInProgress A developer-triggered update was already running and was interrupted.
 */
data class InAppUpdateStatus(
    val updateAvailable: Boolean,
    val flexibleAllowed: Boolean,
    val immediateAllowed: Boolean,
    val priority: Int = 0,
    val stalenessDays: Int? = null,
    val updateInProgress: Boolean = false,
)

/**
 * Decides which in-app update flow to start, mirroring Google's recommended
 * priority/staleness model:
 *
 *  - Always resume an interrupted developer-triggered update with the immediate flow.
 *  - High priority (>= [IMMEDIATE_PRIORITY]) or very stale (>= [IMMEDIATE_STALENESS_DAYS])
 *    releases use the blocking immediate flow.
 *  - Otherwise offer the non-intrusive flexible flow.
 *  - Fall back to whichever flow the platform actually allows.
 */
object InAppUpdatePolicy {
    /** Releases at or above this Play priority are pushed via the immediate flow. */
    const val IMMEDIATE_PRIORITY = 4

    /** Releases at least this many days stale are escalated to the immediate flow. */
    const val IMMEDIATE_STALENESS_DAYS = 30

    fun decide(status: InAppUpdateStatus): InAppUpdateAction {
        if (status.updateInProgress) {
            return if (status.immediateAllowed) InAppUpdateAction.IMMEDIATE else InAppUpdateAction.NONE
        }
        if (!status.updateAvailable) return InAppUpdateAction.NONE

        val staleness = status.stalenessDays ?: 0
        val needsImmediate = status.priority >= IMMEDIATE_PRIORITY ||
            staleness >= IMMEDIATE_STALENESS_DAYS

        return when {
            needsImmediate && status.immediateAllowed -> InAppUpdateAction.IMMEDIATE
            status.flexibleAllowed -> InAppUpdateAction.FLEXIBLE
            status.immediateAllowed -> InAppUpdateAction.IMMEDIATE
            else -> InAppUpdateAction.NONE
        }
    }
}
