package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.PayrollCalculator
import com.elmtrackr.app.domain.compensation.CompensationResolver
import com.elmtrackr.app.domain.model.CurrencyCode
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The load-bearing guarantee of the Paid Projects data model: attaching a
 * project to a shift changes nothing about the employee's pay.
 *
 * CompensationResolver substitutes a shift's *task* hourly rate for the base pay
 * rate, so anything that reaches it becomes wages. A project fee must never get
 * near it — which is why a shift carries a project id and a name snapshot, and
 * deliberately no project rate.
 */
class PayrollProjectIsolationTest {

    private val settings = UserSettings(
        id = "s1",
        userId = "u1",
        timezone = "Asia/Jerusalem",
        hourlyRate = 62.5,
        currency = CurrencyCode.ILS,
        currencyCode = "ILS",
        regionCode = RegionCode.IL,
        weekendDays = listOf(5, 6),
        dailyOvertimeThresholdMinutes = 516,
        weeklyOvertimeThresholdMinutes = 2520,
    )

    /** A fortnight of varied shifts: weekdays, a weekend, long days, breaks. */
    private fun shifts(): List<Shift> {
        val start = Instant.parse("2026-07-06T06:00:00Z")
        return (0 until 14).map { day ->
            val from = start.plus(day.toLong(), ChronoUnit.DAYS)
            Shift(
                id = "shift-$day",
                userId = "u1",
                startTime = from,
                endTime = from.plus(if (day % 3 == 0) 11 else 8, ChronoUnit.HOURS),
                breakMinutes = if (day % 2 == 0) 30 else 0,
                isSpecialDay = day == 5,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        }
    }

    private fun withProject(shifts: List<Shift>) = shifts.mapIndexed { index, shift ->
        shift.copy(
            projectId = "project-${index % 3}",
            projectNameSnapshot = "Client work ${index % 3}",
        )
    }

    @Test
    fun `monthly pay is identical with and without project links`() {
        val plain = shifts()
        val linked = withProject(plain)

        val before = PayrollCalculator.sumMonthlyPay(plain, settings)
        val after = PayrollCalculator.sumMonthlyPay(linked, settings)

        assertEquals(before.totalGross, after.totalGross, 0.0)
        assertEquals(before.regularGross, after.regularGross, 0.0)
        assertEquals(before.overtimeGross, after.overtimeGross, 0.0)
        assertEquals(before.weekendGross, after.weekendGross, 0.0)
        assertEquals(before.holidayGross, after.holidayGross, 0.0)
        assertEquals(before.nightGross, after.nightGross, 0.0)
        assertEquals(before.deductionsGross, after.deductionsGross, 0.0)
        assertEquals(before.netGross, after.netGross, 0.0)
        assertEquals(before.currencyCode, after.currencyCode)
    }

    @Test
    fun `per-shift pay breakdowns are identical with and without project links`() {
        val plain = shifts()
        val linked = withProject(plain)

        plain.indices.forEach { index ->
            val before = PayrollCalculator.calculateShiftPayInContext(plain[index], plain, settings)
            val after = PayrollCalculator.calculateShiftPayInContext(linked[index], linked, settings)
            assertEquals(before?.totalGross, after?.totalGross)
            assertEquals(before?.overtimeGross, after?.overtimeGross)
            assertEquals(before?.isSpecial, after?.isSpecial)
            assertEquals(
                before?.brackets?.map { it.label to it.minutes },
                after?.brackets?.map { it.label to it.minutes },
            )
        }
    }

    @Test
    fun `the resolved compensation is unaffected by a project link`() {
        val plain = shifts().first()
        val linked = plain.copy(projectId = "p1", projectNameSnapshot = "Client work")

        val before = CompensationResolver.resolveShiftCompensation(plain, settings, emptyList())
        val after = CompensationResolver.resolveShiftCompensation(linked, settings, emptyList())

        assertEquals(before.baseHourlyRate, after.baseHourlyRate)
        assertEquals(before.currencyCode, after.currencyCode)
        assertEquals(before.regionCode, after.regionCode)
        assertEquals(before.rules, after.rules)
    }

    @Test
    fun `pay calculation sources never mention projects`() {
        // A source-level guard: the day someone reaches for a project field
        // inside wage calculation, this fails.
        val roots = listOf(
            "src/main/java/com/elmtrackr/app/domain/PayrollCalculator.kt",
            "src/main/java/com/elmtrackr/app/domain/compensation",
            "src/main/java/com/elmtrackr/app/domain/premium",
        ).map { File(it) }

        val checked = mutableListOf<File>()
        val offenders = mutableListOf<String>()
        roots.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                checked += file
                file.readLines().forEachIndexed { index, line ->
                    if (line.contains("projectId") || line.contains("projectNameSnapshot")) {
                        offenders += "${file.path}:${index + 1}: $line"
                    }
                }
            }
        }

        assertTrue("Pay calculation sources were not found; fix the test paths", checked.isNotEmpty())
        assertEquals(
            "Wage calculation must not read project fields",
            emptyList<String>(),
            offenders,
        )
    }
}
