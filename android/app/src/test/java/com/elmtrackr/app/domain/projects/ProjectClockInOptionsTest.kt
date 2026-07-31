package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.CompensationSource
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectWorkStatus
import com.elmtrackr.app.domain.money.ProjectFee
import com.elmtrackr.app.domain.money.TaxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Which projects can be clocked into, and what a clock-in actually records.
 *
 * The recurring safety rule: anything ambiguous resolves to ordinary hourly work.
 * A punch marked as project time with no project attached would be hours that
 * nobody pays for, which is worse than an hour of wages the user can correct.
 */
class ProjectClockInOptionsTest {

    private fun project(
        id: String,
        name: String = "Website rebuild",
        status: ProjectWorkStatus = ProjectWorkStatus.ACTIVE,
        archivedAt: Instant? = null,
        client: String? = "Acme Ltd",
        currency: String = "USD",
        updatedAt: Instant = Instant.parse("2026-07-01T00:00:00Z"),
    ) = Project(
        id = id,
        userId = "u1",
        name = name,
        clientName = client,
        workStatus = status,
        fee = ProjectFee.from(BigDecimal("10000"), currency, TaxMode.EXCLUSIVE, BigDecimal("18")),
        createdAt = Instant.EPOCH,
        updatedAt = updatedAt,
        archivedAt = archivedAt,
    )

    @Test
    fun `active and paused projects can be clocked into`() {
        assertTrue(ProjectClockInOptions.isClockable(project("p1", status = ProjectWorkStatus.ACTIVE)))
        assertTrue(ProjectClockInOptions.isClockable(project("p2", status = ProjectWorkStatus.PAUSED)))
    }

    @Test
    fun `draft completed cancelled and archived projects cannot`() {
        // A draft has no settled fee, so any rate derived from its hours would be
        // based on a number the user has not agreed. The rest are finished work:
        // new hours would silently move a closed project's effective rate.
        listOf(
            ProjectWorkStatus.DRAFT,
            ProjectWorkStatus.COMPLETED,
            ProjectWorkStatus.CANCELLED,
            ProjectWorkStatus.ARCHIVED,
        ).forEach { status ->
            assertFalse(status.name, ProjectClockInOptions.isClockable(project("p", status = status)))
        }
    }

    @Test
    fun `an archived timestamp excludes a project even when its status says otherwise`() {
        val archived = project("p1", status = ProjectWorkStatus.ACTIVE, archivedAt = Instant.EPOCH)
        assertFalse(ProjectClockInOptions.isClockable(archived))
    }

    @Test
    fun `options carry the client and currency and are ordered most recent first`() {
        val options = ProjectClockInOptions.options(
            listOf(
                project("old", name = "Old work", updatedAt = Instant.parse("2026-01-01T00:00:00Z")),
                project("new", name = "New work", updatedAt = Instant.parse("2026-07-20T00:00:00Z")),
                project("done", status = ProjectWorkStatus.COMPLETED),
            ),
        )

        assertEquals(listOf("new", "old"), options.map { it.projectId })
        assertEquals("Acme Ltd", options.first().clientName)
        assertEquals("USD", options.first().currencyCode)
    }

    @Test
    fun `two projects with the same name stay distinguishable by client`() {
        val options = ProjectClockInOptions.options(
            listOf(
                project("a", name = "Rebuild", client = "Acme Ltd"),
                project("b", name = "Rebuild", client = "Globex"),
            ),
        )
        assertEquals(setOf("Acme Ltd", "Globex"), options.map { it.clientName }.toSet())
    }

    @Test
    fun `a project with no client still shows its currency`() {
        val options = ProjectClockInOptions.options(listOf(project("a", client = null)))
        assertNull(options.single().clientName)
        assertEquals("USD", options.single().currencyCode)
    }

    @Test
    fun `resolve records project time for a clockable project`() {
        val projects = listOf(project("p1"))
        val selection = ProjectClockInOptions.resolve(
            paidProjectsEnabled = true,
            requested = CompensationSource.PROJECT,
            selectedProjectId = "p1",
            projects = projects,
        )
        assertEquals(CompensationSource.PROJECT, selection.compensationSource)
        assertEquals("p1", selection.projectId)
        assertEquals("Website rebuild", selection.projectNameSnapshot)
    }

    @Test
    fun `resolve falls back to hourly work when the feature is off`() {
        val selection = ProjectClockInOptions.resolve(
            paidProjectsEnabled = false,
            requested = CompensationSource.PROJECT,
            selectedProjectId = "p1",
            projects = listOf(project("p1")),
        )
        assertEquals(CompensationSource.EMPLOYEE, selection.compensationSource)
        assertNull(selection.projectId)
    }

    @Test
    fun `resolve falls back when the selected project is no longer clockable`() {
        val selection = ProjectClockInOptions.resolve(
            paidProjectsEnabled = true,
            requested = CompensationSource.PROJECT,
            selectedProjectId = "p1",
            projects = listOf(project("p1", status = ProjectWorkStatus.COMPLETED)),
        )
        assertEquals(CompensationSource.EMPLOYEE, selection.compensationSource)
        assertNull(selection.projectId)
    }

    @Test
    fun `resolve falls back when no project is selected`() {
        val selection = ProjectClockInOptions.resolve(
            paidProjectsEnabled = true,
            requested = CompensationSource.PROJECT,
            selectedProjectId = null,
            projects = listOf(project("p1")),
        )
        assertEquals(CompensationSource.EMPLOYEE, selection.compensationSource)
    }

    @Test
    fun `resolve never attaches a project to hourly work`() {
        val selection = ProjectClockInOptions.resolve(
            paidProjectsEnabled = true,
            requested = CompensationSource.EMPLOYEE,
            selectedProjectId = "p1",
            projects = listOf(project("p1")),
        )
        assertEquals(CompensationSource.EMPLOYEE, selection.compensationSource)
        assertNull(selection.projectId)
        assertNull(selection.projectNameSnapshot)
    }
}
