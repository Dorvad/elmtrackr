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
 * Raised when an *existing* passphrase cannot be read back.
 *
 * Distinct from [DatabasePassphraseNotStoredException] because the two need opposite
 * handling. That one means "no key was ever saved"; this one means "a key exists and
 * this device can no longer decrypt it" — the Keystore master key is gone or
 * invalidated (a factory reset restoring app data, a lock-screen credential removed on
 * some OEM builds, a corrupted keyset file).
 *
 * There is nothing to retry, so the caller must not loop, and it must not mint a
 * replacement key either: the database on disk is encrypted with the old one, so a new
 * key silently converts every shift, claim and project the user recorded into an
 * unopenable file. Surface it instead.
 */
class DatabasePassphraseUnreadableException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Stores the SQLCipher passphrase in Android Keystore-backed encrypted prefs.
 *
 * ### `EncryptedSharedPreferences` is deprecated, and this still uses it
 *
 * `androidx.security:security-crypto` 1.1.0 stabilised and deprecated the API in the
 * same release: `EncryptedSharedPreferences` and `MasterKey` both carry a class-level
 * `@Deprecated` (verified in the 1.1.0 artifact; `1.1.0-alpha06`, which this app used
 * until recently, does not). The `create` overload called below is not itself marked,
 * but the class it lives on is, so every call here warns.
 *
 * Staying on it is deliberate and is the lesser risk of the two available:
 *
 * - The alternative is not "use the replacement" — it is "rewrite the store". This file
 *   holds the only key that can open the user's database. Any replacement has to read
 *   the *existing* Tink keyset written by this library, or every shift, claim and
 *   project on the device becomes unopenable. That is a migration with a one-way failure
 *   mode, not a dependency swap.
 * - Deprecated is not removed. 1.1.0 is a stable release and the API works; the previous
 *   pin was an alpha, which is the worse place for the component guarding the database
 *   key.
 *
 * **What is owed**: check the current AndroidX guidance for the recommended replacement
 * — this file does not name one, because the artifact's `@Deprecated` carries no message
 * and inventing one here would be worse than leaving the question open — then plan the
 * keyset migration with an on-device upgrade test. [DatabasePassphraseUnreadableException]
 * exists partly for that day: it is what stops a failed migration from silently minting
 * a fresh key over an encrypted database.
 */
@Suppress("DEPRECATION")
class DatabasePassphraseStore(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Built lazily and through [openSecurePrefs] so a Keystore failure arrives as
     * [DatabasePassphraseUnreadableException] from [getOrCreatePassphrase] rather than
     * as a raw GeneralSecurityException from a constructor. Thrown from a constructor
     * it reached the caller before any of the reasoning below could run, and the
     * database open path turned it into a launch crash on every start — a loop with no
     * message and no way out but clearing app data.
     */
    private val securePrefs by lazy { openSecurePrefs() }

    private fun openSecurePrefs(): android.content.SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        throw DatabasePassphraseUnreadableException(
            "This device's encrypted key store could not be opened, so the database " +
                "key cannot be recovered.",
            e,
        )
    }

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
        // A read failure here is NOT "no key yet". EncryptedSharedPreferences throws
        // when the keyset cannot be decrypted, and falling through to the mint branch
        // on that would encrypt the existing database with a second key and lose the
        // first — the same permanent data loss the store-failure guard below exists to
        // prevent, arrived at from the other direction.
        val encoded = try {
            securePrefs.getString(KEY_PASSPHRASE, null)
        } catch (e: Exception) {
            throw DatabasePassphraseUnreadableException(
                "The stored database key could not be read back on this device.",
                e,
            )
        }
        if (encoded != null) {
            return try {
                Base64.decode(encoded, Base64.NO_WRAP)
            } catch (e: IllegalArgumentException) {
                throw DatabasePassphraseUnreadableException(
                    "The stored database key is not valid Base64 and cannot be used.",
                    e,
                )
            }
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
