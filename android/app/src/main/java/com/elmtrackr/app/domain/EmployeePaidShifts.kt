package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.Shift

/**
 * Drops project time, keeping only shifts that are compensated as employee work.
 *
 * Every wage, overtime, premium and pay-report calculation runs over this list,
 * never over the raw shift list. A project shift's hours belong to the project's
 * fixed fee; counting them here would both invent wages and push genuine employee
 * hours past daily and weekly overtime thresholds they never reached.
 *
 * Deliberately *not* applied to hours-only surfaces — the shift list, the widget
 * and Wear displays — where a user still expects to see time they tracked.
 */
internal fun List<Shift>.employeePaidOnly(): List<Shift> = filter { it.isEmployeePaid }
