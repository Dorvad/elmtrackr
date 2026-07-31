package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import java.time.LocalDate

/**
 * Filters on the project report.
 *
 * Every field is optional and null means "no restriction", so an empty filter is
 * the unfiltered report rather than an empty one.
 *
 * The date range is inclusive on both ends and applies to *activity* — a project
 * appears when it was worked on, billed or paid inside the range, not merely
 * because it exists. Which of those counts is decided per section by
 * [ProjectPeriodTotalsBuilder]; this filter selects which projects are in scope.
 */
data class ProjectReportFilter(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val projectId: String? = null,
    /** Matched case-insensitively as a substring, like the list search. */
    val clientName: String? = null,
    val workStatus: ProjectWorkStatus? = null,
    val billingStatus: ProjectBillingStatus? = null,
    val currencyCode: String? = null,
    /** Archived projects are history; excluded unless asked for explicitly. */
    val includeArchived: Boolean = false,
) {
    val isEmpty: Boolean
        get() = from == null && to == null && projectId == null && clientName.isNullOrBlank() &&
            workStatus == null && billingStatus == null && currencyCode == null && !includeArchived

    companion object {
        /** The whole of [year]/[month], the default the reports screen opens on. */
        fun forMonth(year: Int, month: Int): ProjectReportFilter {
            val first = LocalDate.of(year, month, 1)
            return ProjectReportFilter(from = first, to = first.plusMonths(1).minusDays(1))
        }
    }
}

object ProjectReportFilterEngine {

    /**
     * Applies every non-null criterion to [summaries].
     *
     * The date range is deliberately *not* applied here: a project's own dates say
     * nothing about whether it had activity in the range, and dropping it on that
     * basis would hide a project that was paid this month for work agreed last
     * year. Range filtering happens where the activity is, in
     * [ProjectPeriodTotalsBuilder].
     */
    fun apply(
        summaries: List<ProjectSummary>,
        filter: ProjectReportFilter,
    ): List<ProjectSummary> = summaries.filter { summary ->
        matchesArchived(summary, filter) &&
            matchesProject(summary, filter) &&
            matchesClient(summary, filter) &&
            matchesWorkStatus(summary, filter) &&
            matchesBillingStatus(summary, filter) &&
            matchesCurrency(summary, filter)
    }

    private fun matchesArchived(summary: ProjectSummary, filter: ProjectReportFilter): Boolean =
        filter.includeArchived ||
            filter.workStatus == ProjectWorkStatus.ARCHIVED ||
            !summary.project.isArchived

    private fun matchesProject(summary: ProjectSummary, filter: ProjectReportFilter): Boolean =
        filter.projectId == null || summary.project.id == filter.projectId

    private fun matchesClient(summary: ProjectSummary, filter: ProjectReportFilter): Boolean {
        val needle = filter.clientName?.trim()?.lowercase()
        if (needle.isNullOrEmpty()) return true
        return summary.project.clientName?.lowercase()?.contains(needle) == true
    }

    private fun matchesWorkStatus(summary: ProjectSummary, filter: ProjectReportFilter): Boolean =
        filter.workStatus == null || summary.project.workStatus == filter.workStatus

    private fun matchesBillingStatus(summary: ProjectSummary, filter: ProjectReportFilter): Boolean =
        filter.billingStatus == null || summary.billing.status == filter.billingStatus

    private fun matchesCurrency(summary: ProjectSummary, filter: ProjectReportFilter): Boolean =
        filter.currencyCode == null || summary.project.currencyCode == filter.currencyCode

    /** Currencies present, so the filter can offer only the ones that exist. */
    fun availableCurrencies(summaries: List<ProjectSummary>): List<String> =
        summaries.map { it.project.currencyCode }.distinct().sorted()

    /** Clients present, for the same reason. */
    fun availableClients(summaries: List<ProjectSummary>): List<String> =
        summaries.mapNotNull { it.project.clientName?.takeIf { name -> name.isNotBlank() } }
            .distinct()
            .sortedBy { it.lowercase() }

    fun availableProjects(summaries: List<ProjectSummary>): List<Project> =
        summaries.map { it.project }.sortedBy { it.name.lowercase() }
}
