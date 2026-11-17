package com.elmtrackr.app.data.local.dao

import android.app.Application
import androidx.room.Room
import com.elmtrackr.app.data.local.ElmTrackrDatabase
import com.elmtrackr.app.data.local.entity.ReceiptEntity
import com.elmtrackr.app.data.local.entity.RefundClaimEntity
import com.elmtrackr.app.data.local.entity.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A scanned receipt stays attached to its refund claim when the claim is rewritten.
 *
 * `receipts.refundClaimId` is the only foreign key in the database, declared
 * `ON DELETE SET NULL`. The claim DAO used to write through
 * `OnConflictStrategy.REPLACE`, which SQLite carries out as a DELETE followed by
 * an INSERT — and the DELETE fires the foreign-key action. So every rewrite of an
 * existing claim (an edit, a sync pull, adopting the server's copy after a
 * rejected push) silently cleared the receipt's link.
 *
 * Nothing else points back at the image. `openReceiptForClaim`, the form restore
 * and `retryPendingReceiptUploads` all find it through `getByRefundClaimId`, so a
 * detached receipt can no longer be opened from its claim and — if it had not
 * reached cloud storage yet — is never uploaded.
 *
 * This runs against a real Room database rather than `FakeRefundClaimDao`,
 * because the defect lives in what SQLite does with the statement, which a fake
 * cannot show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RefundClaimReceiptLinkTest {

    private lateinit var db: ElmTrackrDatabase
    private lateinit var claims: RefundClaimDao
    private lateinit var receipts: ReceiptDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            ElmTrackrDatabase::class.java,
        ).allowMainThreadQueries().build()
        claims = db.refundClaimDao()
        receipts = db.receiptDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun `rewriting a claim through upsert keeps its scanned receipt linked`() = runTest {
        claims.insertClaim(claim())
        receipts.insert(receipt())

        // What LocalRefundsRepository.updateClaim and the three sync paths do.
        claims.upsertClaim(claim(amount = 42.0, syncStatus = SyncStatus.SYNCED))

        assertEquals(42.0, claims.getClaimById(CLAIM_ID)!!.amount, 0.0)
        assertNotNull(
            "the receipt lost its claim when the claim row was rewritten",
            receipts.getByRefundClaimId(CLAIM_ID),
        )
    }

    @Test
    fun `re-inserting an existing claim keeps its scanned receipt linked`() = runTest {
        claims.insertClaim(claim())
        receipts.insert(receipt())

        // A backup restored over data that is already on the device.
        claims.insertClaim(claim(amount = 17.5))

        assertEquals(17.5, claims.getClaimById(CLAIM_ID)!!.amount, 0.0)
        assertNotNull(receipts.getByRefundClaimId(CLAIM_ID))
    }

    @Test
    fun `upsert still creates a claim that does not exist yet`() = runTest {
        claims.upsertClaim(claim())

        assertEquals(CLAIM_ID, claims.getClaimById(CLAIM_ID)!!.localId)
    }

    @Test
    fun `upsert replaces every column of an existing claim`() = runTest {
        claims.insertClaim(claim())

        val rewritten = claim(amount = 9.0, syncStatus = SyncStatus.SYNCED).copy(
            remoteId = "remote-1",
            notes = "taxi home",
            receiptPath = "user-1/claim-1.jpg",
            lastSyncedAt = 5_000L,
        )
        claims.upsertClaim(rewritten)

        assertEquals(rewritten, claims.getClaimById(CLAIM_ID))
    }

    private fun claim(
        amount: Double = 30.0,
        syncStatus: SyncStatus = SyncStatus.PENDING_CREATE,
    ) = RefundClaimEntity(
        localId = CLAIM_ID,
        remoteId = null,
        shiftLocalId = "shift-1",
        userId = USER,
        direction = "TO_WORK",
        provider = "GETT",
        amount = amount,
        rideAt = 1_000L,
        notes = null,
        receiptPath = null,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        deletedAt = null,
        syncStatus = syncStatus,
        lastSyncError = null,
        lastSyncedAt = null,
    )

    private fun receipt() = ReceiptEntity(
        id = "receipt-1",
        userId = USER,
        refundClaimId = CLAIM_ID,
        localImageUri = "/data/receipts/receipt-1.jpg",
        merchantName = null,
        amount = 30.0,
        currency = "ILS",
        receiptDate = 1_000L,
        rawOcrText = null,
        parserVersion = "1",
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private companion object {
        const val USER = "user-1"
        const val CLAIM_ID = "claim-1"
    }
}
