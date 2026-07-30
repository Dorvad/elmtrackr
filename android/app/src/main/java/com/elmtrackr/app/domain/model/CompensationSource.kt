package com.elmtrackr.app.domain.model

/**
 * What a shift's time is compensated by. This is the single switch that decides
 * whether a shift participates in employee-pay calculation at all.
 *
 * Prompt 0 deferred this discriminator so the data model could land without
 * touching wage behaviour; it arrives here as the nullable column it was planned
 * to be, where an absent value means [EMPLOYEE] — exactly what every existing
 * row means.
 *
 * The distinction matters because a project is paid a fixed fee. Paying a
 * project shift as hourly wages too would count the same hour twice: once in the
 * project's fee and again in the employee's gross, and it would push genuine
 * employee hours over daily and weekly overtime thresholds they never reached.
 */
enum class CompensationSource {
    /**
     * Ordinary hourly work. Wages, overtime, night, weekend and holiday premiums
     * all apply. The default, and the meaning of every pre-existing shift.
     */
    EMPLOYEE,

    /**
     * Time tracked against a fixed-fee project. Contributes to the project's
     * tracked hours, effective hourly rate and budget usage, and to nothing on
     * the employee-pay side: no wage, no overtime accumulation, no premium.
     */
    PROJECT,
    ;

    /** True when this shift's time feeds wages, overtime and premiums. */
    val isEmployeePaid: Boolean get() = this == EMPLOYEE

    /** True when this shift's time belongs to a project rather than to wages. */
    val isProjectTime: Boolean get() = this == PROJECT

    /** Lowercase snake_case wire form, matching the Supabase contract style. */
    fun toWire(): String = name.lowercase()

    companion object {
        /**
         * Parses a persisted or synced value. Falls back to [EMPLOYEE] — the
         * meaning of a null column and the safe default, since treating unknown
         * data as project time would silently remove hours from someone's pay.
         */
        fun fromPersisted(raw: String?): CompensationSource {
            if (raw.isNullOrBlank()) return EMPLOYEE
            val normalized = raw.trim().uppercase().replace('-', '_')
            return entries.find { it.name == normalized } ?: EMPLOYEE
        }
    }
}
