package com.elmtrackr.app.data.local

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Raised when a freshly generated passphrase could not be stored.
 *
 * Fatal on purpose. See [DatabasePassphraseStore.getOrCreatePassphrase].
 */
class DatabasePassphraseNotStoredException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Stores the SQLCipher passphrase in Android Keystore-backed encrypted prefs.
 */
class DatabasePassphraseStore(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * The key the database is encrypted with, minting one on first run.
     *
     * A new key that cannot be stored throws instead of being returned, and that
     * has to stay true however tempting it looks to carry on. The caller encrypts
     * the database with whatever comes back from here the moment it returns; if
     * the key never reached disk, the next launch mints a different one and every
     * shift, claim and project the user recorded in between is unreadable —
     * permanently, because the only key that could open the file existed solely in
     * a process that has since exited.
     *
     * Failing here costs the user a launch. Succeeding with an unsaved key costs
     * them everything they have recorded, and does it silently, one launch later.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val encoded = securePrefs.getString(KEY_PASSPHRASE, null)
        if (encoded != null) {
            return Base64.decode(encoded, Base64.NO_WRAP)
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val encodedPassphrase = Base64.encodeToString(passphrase, Base64.NO_WRAP)

        // commit(), not apply(): apply() writes asynchronously and reports nothing,
        // so a process kill between here and the flush would lose the key while the
        // database on disk was already encrypted with it.
        @Suppress("ApplySharedPref")
        val committed = try {
            securePrefs.edit().putString(KEY_PASSPHRASE, encodedPassphrase).commit()
        } catch (e: Exception) {
            throw DatabasePassphraseNotStoredException(
                "Could not store a new database encryption key; refusing to open the " +
                    "database with a key that would be lost on the next launch.",
                e,
            )
        }
        if (!committed) {
            throw DatabasePassphraseNotStoredException(
                "Storing a new database encryption key reported failure; refusing to " +
                    "open the database with a key that would be lost on the next launch.",
            )
        }

        // Read back rather than trusting the commit. commit() reports that the
        // preferences file was written, not that this value survived the
        // encryption layer above it, and a key that reads back as anything other
        // than what was just written is exactly the case this guard exists for.
        val readBack = securePrefs.getString(KEY_PASSPHRASE, null)
        if (readBack != encodedPassphrase) {
            throw DatabasePassphraseNotStoredException(
                "A new database encryption key did not read back as written; refusing " +
                    "to open the database with a key that cannot be recovered.",
            )
        }

        return passphrase
    }

    companion object {
        private const val PREFS_NAME = "elmtrackr_db_secrets"
        private const val KEY_PASSPHRASE = "room_passphrase"
        private const val PASSPHRASE_BYTES = 32
    }
}
