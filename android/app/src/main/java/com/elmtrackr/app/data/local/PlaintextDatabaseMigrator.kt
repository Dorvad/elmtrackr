package com.elmtrackr.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

/**
 * One-time migration from the legacy plaintext SQLite file to SQLCipher.
 *
 * The encrypted copy is created by opening the target with the same raw
 * passphrase bytes Room later passes to SupportOpenHelperFactory, then pulling
 * the plaintext content in via sqlcipher_export. Embedding the passphrase in
 * an ATTACH ... KEY '<text>' statement instead would derive the key from the
 * UTF-8 encoding of that text, which differs from the raw bytes for any byte
 * >= 0x80 and would leave the exported database unopenable by Room.
 */
object PlaintextDatabaseMigrator {

    private const val TAG = "PlaintextDbMigrator"
    private const val DB_NAME = "elmtrackr.db"
    private const val MIGRATION_FLAG = "db_encrypted_v1"

    fun migrateIfNeeded(context: Context, passphrase: ByteArray) {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(DB_NAME)
        val tempFile = File(dbFile.parentFile, "$DB_NAME.encrypting")
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!dbFile.exists()) {
            recoverInterruptedSwap(dbFile, tempFile, prefs)
            return
        }

        if (prefs.getBoolean(MIGRATION_FLAG, false)) return
        if (!isPlaintextSqlite(dbFile)) {
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
            return
        }

        System.loadLibrary("sqlcipher")
        // Remove all leftovers of an interrupted earlier attempt. Deleting only
        // the main file is not enough: a stale .encrypting-journal sitting next
        // to a freshly created file of the same name is treated by SQLite as a
        // hot journal of that file and can make it unopenable (code 14).
        deleteWithSidecars(tempFile)

        var encryptedDb: SQLiteDatabase? = null
        try {
            encryptedDb = SQLiteDatabase.openOrCreateDatabase(tempFile, passphrase, null, null)
            val escapedPath = dbFile.absolutePath.replace("'", "''")
            encryptedDb.execSQL("ATTACH DATABASE '$escapedPath' AS plaintext KEY ''")
            encryptedDb.execSQL("SELECT sqlcipher_export('main', 'plaintext')")
            // sqlcipher_export copies schema and data but not user_version;
            // without it Room would treat the migrated database as version 0.
            val userVersion = encryptedDb.rawQuery("PRAGMA plaintext.user_version", null)
                .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
            encryptedDb.execSQL("DETACH DATABASE plaintext")
            encryptedDb.execSQL("PRAGMA user_version = $userVersion")
            encryptedDb.close()
            encryptedDb = null

            if (!dbFile.delete()) {
                throw IllegalStateException("Could not remove plaintext database")
            }
            // The plaintext database may leave -wal/-journal files behind; they
            // must not survive next to the encrypted file under the same name.
            deleteSidecars(dbFile)
            if (!tempFile.renameTo(dbFile)) {
                throw IllegalStateException("Could not replace database with encrypted copy")
            }
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
        } catch (t: Throwable) {
            Log.e(TAG, "SQLCipher migration failed; ${diagnostics(dbFile, tempFile)}", t)
            encryptedDb?.close()
            // Only discard the encrypted copy while the plaintext original is
            // still in place. Once dbFile has been deleted, tempFile is the
            // only remaining copy of the user's data; recoverInterruptedSwap
            // moves it into place on the next launch.
            if (dbFile.exists()) {
                deleteWithSidecars(tempFile)
            }
            throw t
        }
    }

    /**
     * Finishes a migration that was killed between deleting the plaintext file
     * and renaming the encrypted copy into place. The temp file is only ever
     * the sole survivor after sqlcipher_export completed, so it is a full,
     * consistent copy. Without this, the early no-database return would let
     * Room create a fresh empty database and silently lose the data.
     */
    private fun recoverInterruptedSwap(
        dbFile: File,
        tempFile: File,
        prefs: SharedPreferences,
    ) {
        if (!tempFile.exists() || prefs.getBoolean(MIGRATION_FLAG, false)) return
        deleteSidecars(dbFile)
        if (tempFile.renameTo(dbFile)) {
            deleteSidecars(tempFile)
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
            Log.i(TAG, "Recovered interrupted SQLCipher migration")
        } else {
            Log.e(TAG, "Failed to recover interrupted SQLCipher migration")
        }
    }

    /** Context for code-14 style failures: file states and free disk space. */
    private fun diagnostics(dbFile: File, tempFile: File): String {
        val dir = dbFile.parentFile
        return "dbSize=${dbFile.length()}" +
            ", tempExists=${tempFile.exists()}, tempSize=${tempFile.length()}" +
            ", staleTempJournal=${File(dir, "${tempFile.name}-journal").exists()}" +
            ", staleTempWal=${File(dir, "${tempFile.name}-wal").exists()}" +
            ", usableSpace=${dir?.usableSpace}"
    }

    private fun deleteWithSidecars(file: File) {
        file.delete()
        deleteSidecars(file)
    }

    private fun deleteSidecars(file: File) {
        for (suffix in SIDECAR_SUFFIXES) {
            File(file.parentFile, file.name + suffix).delete()
        }
    }

    private fun isPlaintextSqlite(file: File): Boolean {
        FileInputStream(file).use { input ->
            val header = ByteArray(15)
            if (input.read(header) < header.size) return false
            return header.toString(Charsets.US_ASCII).startsWith("SQLite format 3")
        }
    }

    private val SIDECAR_SUFFIXES = listOf("-journal", "-wal", "-shm")
    private const val PREFS_NAME = "elmtrackr_db_migration"
}
