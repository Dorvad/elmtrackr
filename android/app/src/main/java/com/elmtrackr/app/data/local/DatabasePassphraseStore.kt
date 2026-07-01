package com.elmtrackr.app.data.local

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

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

    fun getOrCreatePassphrase(): ByteArray {
        val encoded = securePrefs.getString(KEY_PASSPHRASE, null)
        if (encoded != null) {
            return Base64.decode(encoded, Base64.NO_WRAP)
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        securePrefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(passphrase, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    companion object {
        private const val PREFS_NAME = "elmtrackr_db_secrets"
        private const val KEY_PASSPHRASE = "room_passphrase"
        private const val PASSPHRASE_BYTES = 32
    }
}
