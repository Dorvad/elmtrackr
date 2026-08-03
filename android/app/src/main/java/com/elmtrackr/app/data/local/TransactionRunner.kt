package com.elmtrackr.app.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a suspending block inside a single Room transaction.
 *
 * Exists so callers that hold DAOs rather than the database — the sync
 * repository, which is constructed from seven DAOs — can still make a
 * multi-table write atomic. Injecting [ElmTrackrDatabase] everywhere would
 * work but hands every caller the ability to reach past its own DAOs, and
 * makes the JVM unit tests (which have no Room instance at all) impossible to
 * construct.
 */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: ElmTrackrDatabase,
) : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction(block)
}

/**
 * For JVM unit tests, where the DAOs are in-memory fakes and there is no
 * transaction to join. Runs the block directly.
 */
object DirectTransactionRunner : TransactionRunner {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}
