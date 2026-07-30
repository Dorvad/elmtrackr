package com.elmtrackr.app.domain.projects

import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import com.elmtrackr.app.domain.money.Money
import com.elmtrackr.app.domain.money.MoneyByCurrency
import com.elmtrackr.app.domain.money.MoneyPolicy
import java.time.LocalDate

/**
 * Project money for a period, kept in the four bases it actually belongs to.
 *
 * These are **not** interchangeable and are never added together:
 *
 *  - [contractedBase] — the value of work agreed. Recognised when the project was
 *    priced, not when anyone paid.
 *  - [billed] — what clients were asked for in this period. Recognised on the
 *    billing date.
 *  - [received] — cash that arrived in this period. Recognised on the payment
 *    date, which may be months after the work.
 *  - [outstanding] — asked for and not yet received. A balance, not a flow.
 *
 * Adding cash received to hourly earnings would produce a "gross income" that
 * means nothing: hourly pay is work performed in the month, while a project
 * payment may settle work from last quarter. Nothing in this file combines them,
 * and [com.elmtrackr.app.ui.reports] presents them as separate lines.
 *
 * Every figure is a [MoneyByCurrency], so two currencies can never be summed
 * either — the app has no exchange rates and invents none.
 */
data class ProjectPeriodTotals(
    /** Agreed fees before tax, for projects counted in this period. */
    val contractedBase: MoneyByCurrency = MoneyByCurrency.EMPTY,
    /** Tax on those agreed fees. Shown separately; never revenue. */
    val contractedTax: MoneyByCurrency = MoneyByCurrency.EMPTY,
    /** Agreed fees including tax — what clients would pay in total. */
    val contractedClientTotal: MoneyByCurrency = MoneyByCurrency.EMPTY,

    /** Billed in this period, including tax. */
    val billed: MoneyByCurrency = MoneyByCurrency.EMPTY,
    /** The base portion of what was billed. */
    val billedBase: MoneyByCurrency = MoneyByCurrency.EMPTY,
    /** The tax portion of what was billed. */
    val billedTax: MoneyByCurrency = MoneyByCurrency.EMPTY,

    /** Cash received in this period, including any tax it contained. */
    val received: MoneyByCurrency = MoneyByCurrency.EMPTY,
    /**
     * The base portion of cash received — project revenue before tax.
     *
     * Allocated pro rata from the billing record the payment settles, which is the
     * only basis the data supports: a payment does not itself say how much of it
     * was tax. See [allocateBase].
     */
    val receivedBase: MoneyByCurrency = MoneyByCurrency.EMPTY,
    /** The tax portion of cash received, displayed separately from revenue. */
    val receivedTax: MoneyByCurrency = MoneyByCurrency.EMPTY,

    /** Billed and not yet received, as at the end of the period. A balance. */
    val outstanding: MoneyByCurrency = MoneyByCurrency.EMPTY,
    /** Outstanding whose due date has passed. */
    val overdue: MoneyByCurrency = MoneyByCurrency.EMPTY,

    /** Project minutes tracked in the period. Time may be summed; money may not. */
    val trackedMinutes: Int = 0,
    /** Distinct days with project time, for an average. */
    val activeDays: Int = 0,
    val projectCount: Int = 0,
) {
    /** Every currency appearing anywhere, so a report can render one block each. */
    val currencies: List<String>
        get() = (
            contractedBase.currencies + contractedTax.currencies +
                billed.currencies + received.currencies + outstanding.currencies +
                overdue.currencies
            ).distinct().sorted()

    val hasMultipleCurrencies: Boolean get() = currencies.size > 1
}

object ProjectPeriodTotalsBuilder {

    /**
     * Totals for [from]..[to] inclusive.
     *
     * Each basis is filtered on its own date: a billing record by its billing
     * date, a payment by its payment date. That is what keeps them honest — a
     * payment received in July for a June invoice belongs to July's cash and
     * June's billing, and appears in exactly one of each.
     *
     * [outstanding] and [overdue] are balances rather than flows, so they are taken
     * across all billing to date up to [to], not only records dated inside the
     * period. A debt does not stop existing because the period moved on.
     */
    fun build(
        projects: List<Project>,
        billingRecords: List<ProjectBillingRecord>,
        payments: List<ProjectPayment>,
        projectMinutesByProject: Map<String, Int> = emptyMap(),
        activeDays: Int = 0,
        from: LocalDate,
        to: LocalDate,
    ): ProjectPeriodTotals {
        val activeRecords = billingRecords.filter { it.isActive }
        val recordsById = activeRecords.associateBy { it.id }

        val billedInPeriod = activeRecords.filter { it.billedOn in from..to }
        val paidInPeriod = payments.filter { it.paidOn in from..to && it.billingRecordId in recordsById }

        // Balance as at the end of the period: everything billed up to [to],
        // less everything paid up to [to].
        val billedToDate = activeRecords.filter { !it.billedOn.isAfter(to) }
        val paidToDate = payments.filter {
            !it.paidOn.isAfter(to) && it.billingRecordId in recordsById
        }
        val outstandingByRecord = billedToDate.associateWith { record ->
            val paid = Money.sum(
                paidToDate.filter { it.billingRecordId == record.id }.map { it.amount },
                record.currencyCode,
            )
            (record.billedTotal - paid).coerceAtLeastZero()
        }

        return ProjectPeriodTotals(
            contractedBase = MoneyByCurrency.from(projects.map { it.fee.base }),
            contractedTax = MoneyByCurrency.from(projects.map { it.fee.tax }),
            contractedClientTotal = MoneyByCurrency.from(projects.map { it.fee.clientTotal }),

            billed = MoneyByCurrency.from(billedInPeriod.map { it.billedTotal }),
            billedBase = MoneyByCurrency.from(billedInPeriod.map { it.fee.base }),
            billedTax = MoneyByCurrency.from(billedInPeriod.map { it.fee.tax }),

            received = MoneyByCurrency.from(paidInPeriod.map { it.amount }),
            receivedBase = MoneyByCurrency.from(
                paidInPeriod.mapNotNull { payment ->
                    recordsById[payment.billingRecordId]?.let { allocateBase(payment.amount, it) }
                },
            ),
            receivedTax = MoneyByCurrency.from(
                paidInPeriod.mapNotNull { payment ->
                    recordsById[payment.billingRecordId]?.let { record ->
                        payment.amount - allocateBase(payment.amount, record)
                    }
                },
            ),

            outstanding = MoneyByCurrency.from(outstandingByRecord.values),
            overdue = MoneyByCurrency.from(
                outstandingByRecord
                    .filter { (record, balance) ->
                        balance.isPositive && record.dueOn?.isBefore(to) == true
                    }
                    .values,
            ),

            trackedMinutes = projectMinutesByProject.values.sum(),
            activeDays = activeDays,
            projectCount = projects.size,
        )
    }

    /**
     * The base (pre-tax) portion of [payment] against [record].
     *
     * A payment carries no tax breakdown of its own, so it is split in the same
     * proportion as the bill it settles. For a fully paid bill this returns the
     * bill's own base exactly; for a partial payment it apportions.
     *
     * With no tax, or a zero-total bill, the whole payment is base — there is no
     * tax to carve out.
     */
    internal fun allocateBase(payment: Money, record: ProjectBillingRecord): Money {
        val total = record.billedTotal
        if (!total.isPositive) return payment
        if (!record.fee.tax.isPositive) return payment
        // Exact when the payment settles the bill in full, which is the common case.
        if (payment == total) return record.fee.base

        val ratio = record.fee.base.amount.divide(total.amount, MoneyPolicy.CALCULATION_CONTEXT)
        return Money.of(
            payment.amount.multiply(ratio, MoneyPolicy.CALCULATION_CONTEXT),
            payment.currencyCode,
        )
    }
}
