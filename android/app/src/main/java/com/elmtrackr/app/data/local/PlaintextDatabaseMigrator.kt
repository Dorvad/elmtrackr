package com.elmtrackr.app.data.local

import android.content.Context
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.io.FileInputStream

/**
 * One-time migration from the legacy plaintext SQLite file to SQLCipher.
 */
object PlaintextDatabaseMigrator {

    private const val TAG = "PlaintextDbMigrator"
    private const val DB_NAME = "elmtrackr.db"
    private const val MIGRATION_FLAG = "db_encrypted_v1"

    fun migrateIfNeeded(context: Context, passphrase: ByteArray) {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATION_FLAG, false)) return
        if (!isPlaintextSqlite(dbFile)) {
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
            return
        }

        SQLiteDatabase.loadLibs(appContext)
        val passphraseChars = passphrase.toPassphraseChars()
        val tempFile = File(dbFile.parentFile, "$DB_NAME.encrypting")
        if (tempFile.exists()) tempFile.delete()

        var plaintextDb: SQLiteDatabase? = null
        try {
            plaintextDb = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                "",
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            val escapedPath = tempFile.absolutePath.replace("'", "''")
            plaintextDb.execSQL(
                "ATTACH DATABASE '$escapedPath' AS encrypted KEY '${passphraseChars.escapeForSql()}'",
            )
            plaintextDb.execSQL("SELECT sqlcipher_export('encrypted')")
            plaintextDb.execSQL("DETACH DATABASE encrypted")
            plaintextDb.close()
            plaintextDb = null

            if (!dbFile.delete()) {
                throw IllegalStateException("Could not remove plaintext database")
            }
            if (!tempFile.renameTo(dbFile)) {
                throw IllegalStateException("Could not replace database with encrypted copy")
            }
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply()
        } catch (t: Throwable) {
            Log.e(TAG, "SQLCipher migration failed", t)
            plaintextDb?.close()
            if (tempFile.exists()) tempFile.delete()
            throw t
        }
    }

    private fun isPlaintextSqlite(file: File): Boolean {
        FileInputStream(file).use { input ->
            val header = ByteArray(15)
            if (input.read(header) < header.size) return false
            return header.toString(Charsets.US_ASCII).startsWith("SQLite format 3")
        }
    }

    private fun ByteArray.toPassphraseChars(): CharArray =
        map { (it.toInt() and 0xFF).toChar() }.toCharArray()

    private fun CharArray.escapeForSql(): String =
        joinToString(separator = "") { char ->
            if (char == '\'') "''" else char.toString()
        }

    private const val PREFS_NAME = "elmtrackr_db_migration"
}
