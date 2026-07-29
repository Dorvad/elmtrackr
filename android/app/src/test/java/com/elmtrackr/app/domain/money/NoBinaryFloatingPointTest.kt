package com.elmtrackr.app.domain.money

import com.elmtrackr.app.data.local.entity.ProjectBillingRecordEntity
import com.elmtrackr.app.data.local.entity.ProjectEntity
import com.elmtrackr.app.data.local.entity.ProjectPaymentEntity
import com.elmtrackr.app.data.sync.ProjectBackupRow
import com.elmtrackr.app.data.sync.ProjectBillingRecordBackupRow
import com.elmtrackr.app.data.sync.ProjectPaymentBackupRow
import com.elmtrackr.app.domain.model.Project
import com.elmtrackr.app.domain.model.ProjectBillingRecord
import com.elmtrackr.app.domain.model.ProjectPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Structural guard: no Paid Projects type may carry a binary floating-point
 * field. Double and Float cannot represent most decimal amounts exactly, so a
 * single such field anywhere in this chain would let drift into stored money.
 *
 * Java reflection rather than kotlin-reflect, so the check needs no extra
 * dependency and also sees the generated fields of a data class.
 */
class NoBinaryFloatingPointTest {

    private val financialTypes = listOf(
        // Domain
        Project::class.java,
        ProjectBillingRecord::class.java,
        ProjectPayment::class.java,
        Money::class.java,
        ProjectFee::class.java,
        // Persistence
        ProjectEntity::class.java,
        ProjectBillingRecordEntity::class.java,
        ProjectPaymentEntity::class.java,
        // Backup wire format
        ProjectBackupRow::class.java,
        ProjectBillingRecordBackupRow::class.java,
        ProjectPaymentBackupRow::class.java,
    )

    private val floatingPointTypes = setOf(
        java.lang.Double.TYPE,
        java.lang.Float.TYPE,
        java.lang.Double::class.java,
        java.lang.Float::class.java,
    )

    @Test
    fun `no financial type declares a Double or Float field`() {
        val offenders = financialTypes.flatMap { type ->
            type.declaredFields
                .filterNot { it.isSynthetic }
                .filter { it.type in floatingPointTypes }
                .map { "${type.simpleName}.${it.name}: ${it.type.simpleName}" }
        }
        assertEquals("Floating-point fields found in financial types", emptyList<String>(), offenders)
    }

    @Test
    fun `no financial type exposes a Double or Float accessor`() {
        val offenders = financialTypes.flatMap { type ->
            type.declaredMethods
                .filterNot { it.isSynthetic }
                .filter { it.name.startsWith("get") || it.name.startsWith("component") }
                .filter { it.returnType in floatingPointTypes }
                .map { "${type.simpleName}.${it.name}(): ${it.returnType.simpleName}" }
        }
        assertEquals("Floating-point accessors found in financial types", emptyList<String>(), offenders)
    }

    @Test
    fun `money amounts are BigDecimal`() {
        assertEquals(BigDecimal::class.java, Money::class.java.getDeclaredField("amount").type)
        assertEquals(BigDecimal::class.java, ProjectEntity::class.java.getDeclaredField("baseFee").type)
        assertEquals(BigDecimal::class.java, ProjectEntity::class.java.getDeclaredField("taxAmount").type)
        assertEquals(BigDecimal::class.java, ProjectEntity::class.java.getDeclaredField("clientTotal").type)
        assertEquals(
            BigDecimal::class.java,
            ProjectPaymentEntity::class.java.getDeclaredField("amount").type,
        )
    }

    @Test
    fun `backup rows carry money as decimal strings not numbers`() {
        listOf(
            ProjectBackupRow::class.java.getDeclaredField("baseFee"),
            ProjectBackupRow::class.java.getDeclaredField("taxAmount"),
            ProjectBackupRow::class.java.getDeclaredField("clientTotal"),
            ProjectBillingRecordBackupRow::class.java.getDeclaredField("totalAmount"),
            ProjectPaymentBackupRow::class.java.getDeclaredField("amount"),
        ).forEach { field ->
            assertEquals(String::class.java, field.type)
        }
    }

    @Test
    fun `the existing task rate is still a Double and untouched by this change`() {
        // Employee pay keeps its own representation. Recording it here makes the
        // boundary explicit: project money is BigDecimal, wage estimation is not
        // being migrated as part of this work.
        val field = com.elmtrackr.app.data.local.entity.ShiftEntity::class.java
            .getDeclaredField("taskHourlyRateSnapshot")
        assertTrue(field.type == java.lang.Double::class.java)
    }
}
