package com.elmtrackr.app.data.receipts

import com.elmtrackr.app.domain.model.RefundDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PhotoFileManagerTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `cleanup deletes only old pending receipt photos`() {
        val root = temp.newFolder("refund_receipts")
        val manager = PhotoFileManager(
            rootDir = root,
            uriForFile = { error("content uri is not used in this test") },
            clock = Clock.fixed(Instant.ofEpochMilli(100_000), ZoneOffset.UTC),
        )
        val old = manager.createPendingPhotoFile("shift", RefundDirection.TO_WORK).apply {
            writeBytes(byteArrayOf(1))
            setLastModified(1_000)
        }
        val fresh = manager.createPendingPhotoFile("shift", RefundDirection.FROM_WORK).apply {
            writeBytes(byteArrayOf(2))
            setLastModified(99_000)
        }

        val deleted = manager.cleanupOrphans(olderThanMillis = 10_000)

        assertEquals(1, deleted)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun `delete ignores files outside receipt root`() {
        val root = temp.newFolder("refund_receipts")
        val outside = temp.newFile("outside.jpg").apply { writeBytes(byteArrayOf(1)) }
        val manager = PhotoFileManager(root, { error("content uri is not used in this test") })

        manager.delete(outside.absolutePath)

        assertTrue(outside.exists())
    }
}
