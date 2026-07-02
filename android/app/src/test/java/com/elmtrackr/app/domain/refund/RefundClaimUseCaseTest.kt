package com.elmtrackr.app.domain.refund

import com.elmtrackr.app.domain.model.ReceiptUpload
import com.elmtrackr.app.domain.model.RefundAction
import com.elmtrackr.app.domain.model.RefundDirection
import com.elmtrackr.app.domain.model.RefundProvider
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.fake.FakeCurrentUserProvider
import com.elmtrackr.app.fake.FakeReceiptsRepository
import com.elmtrackr.app.fake.FakeRefundReceiptStorage
import com.elmtrackr.app.fake.FakeRefundsRepository
import com.elmtrackr.app.fake.FakeShiftsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RefundClaimUseCaseTest {
    private val refundsRepository = FakeRefundsRepository()
    private val shiftsRepository = FakeShiftsRepository()
    private val currentUserProvider = FakeCurrentUserProvider("local-user")
    private val receiptStorage = FakeRefundReceiptStorage()
    private val receiptsRepository = FakeReceiptsRepository()

    private fun upsertUseCase() = UpsertRefundClaim(
        refundsRepository = refundsRepository,
        shiftsRepository = shiftsRepository,
        currentUserProvider = currentUserProvider,
        refundReceiptStorage = receiptStorage,
    )

    private fun deleteUseCase() = DeleteRefundClaim(
        refundsRepository = refundsRepository,
        shiftsRepository = shiftsRepository,
        refundReceiptStorage = receiptStorage,
        receiptsRepository = receiptsRepository,
    )

    @Test
    fun `upsert uploads receipt and marks shift submitted`() = runTest {
        val shift = specialDayShift()
        shiftsRepository.setShifts(shift)

        val result = upsertUseCase()(
            RefundClaimUpsertInput(
                shiftId = shift.id,
                direction = RefundDirection.FROM_WORK,
                provider = RefundProvider.TAXI,
                amount = 42.5,
                rideAt = shift.endTime!!,
                notes = "Late ride",
                receipt = ReceiptUpload("receipt.jpg", byteArrayOf(1, 2, 3)),
            ),
        )

        assertFalse(result.receiptUploadFailed)
        assertTrue(result.claim.receiptPath?.endsWith("receipt.jpg") == true)
        assertEquals(RefundAction.SUBMITTED, shiftsRepository.getShiftById(shift.id)?.refundAction)
        assertEquals(1, receiptStorage.uploads.size)
    }

    @Test
    fun `upsert saves claim without receipt when upload fails`() = runTest {
        val shift = specialDayShift()
        shiftsRepository.setShifts(shift)
        receiptStorage.failUploads = true

        val result = upsertUseCase()(
            RefundClaimUpsertInput(
                shiftId = shift.id,
                direction = RefundDirection.TO_WORK,
                provider = RefundProvider.LIME,
                amount = 20.0,
                rideAt = shift.startTime,
                notes = "",
                receipt = ReceiptUpload("receipt.jpg", byteArrayOf(1)),
            ),
        )

        assertTrue(result.receiptUploadFailed)
        assertNull(result.claim.receiptPath)
        assertEquals(1, refundsRepository.observeClaimsForShift(shift.id).first().size)
        assertEquals(RefundAction.SUBMITTED, shiftsRepository.getShiftById(shift.id)?.refundAction)
    }

    @Test
    fun `delete soft-deletes claim before deleting receipt file`() = runTest {
        val events = mutableListOf<String>()
        val shift = specialDayShift()
        shiftsRepository.setShifts(shift.copy(refundAction = RefundAction.SUBMITTED))
        refundsRepository.onDeleteClaim = { events += "claim" }
        receiptStorage.onDeletePath = { events += "file" }
        val claim = refundsRepository.addClaim(
            shiftLocalId = shift.id,
            userId = shift.userId,
            direction = RefundDirection.TO_WORK,
            provider = RefundProvider.TAXI,
            amount = 20.0,
            rideAt = shift.startTime,
            receiptPath = "remote/receipt.jpg",
        )

        deleteUseCase()(claim.id)

        assertEquals(listOf("claim", "file"), events)
        assertTrue(refundsRepository.observeClaimsForShift(shift.id).first().isEmpty())
        assertNull(shiftsRepository.getShiftById(shift.id)?.refundAction)
    }

    @Test
    fun `delete removes linked local receipt`() = runTest {
        val shift = specialDayShift()
        shiftsRepository.setShifts(shift.copy(refundAction = RefundAction.SUBMITTED))
        val claim = refundsRepository.addClaim(
            shiftLocalId = shift.id,
            userId = shift.userId,
            direction = RefundDirection.TO_WORK,
            provider = RefundProvider.TAXI,
            amount = 20.0,
            rideAt = shift.startTime,
        )
        receiptsRepository.seedForClaim(claim.id, "/tmp/local-receipt.jpg")

        val result = deleteUseCase()(claim.id)

        assertFalse(result.localReceiptDeleteFailed)
        assertNull(receiptsRepository.getByRefundClaimId(claim.id))
        assertEquals(listOf(claim.id), receiptsRepository.deletedClaimIds)
        assertEquals(listOf("/tmp/local-receipt.jpg"), receiptsRepository.deletedImagePaths)
        assertTrue(refundsRepository.observeClaimsForShift(shift.id).first().isEmpty())
    }

    @Test
    fun `delete reports local receipt cleanup failure`() = runTest {
        val shift = specialDayShift()
        shiftsRepository.setShifts(shift)
        val claim = refundsRepository.addClaim(
            shiftLocalId = shift.id,
            userId = shift.userId,
            direction = RefundDirection.TO_WORK,
            provider = RefundProvider.TAXI,
            amount = 20.0,
            rideAt = shift.startTime,
        )
        receiptsRepository.seedForClaim(claim.id, "/tmp/local-receipt.jpg")
        receiptsRepository.deleteFileFailed = true

        val result = deleteUseCase()(claim.id)

        assertTrue(result.localReceiptDeleteFailed)
        assertNull(receiptsRepository.getByRefundClaimId(claim.id))
    }

    private fun specialDayShift() = Shift(
        id = "special",
        userId = "local-user",
        startTime = Instant.parse("2024-01-08T12:00:00Z"),
        endTime = Instant.parse("2024-01-08T17:00:00Z"),
        isSpecialDay = true,
    )
}
