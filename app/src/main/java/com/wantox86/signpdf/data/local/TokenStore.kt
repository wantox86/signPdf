package com.wantox86.signpdf.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Session credentials only (token/expiry/username) -- deliberately separate from
// SignatureMetadataStore (plain SharedPreferences, phase 6), which holds non-secret sync
// bookkeeping that's fine to leave un-encrypted and backed up.
class TokenStore(context: Context) : TokenStorage {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun save(token: String, expiresAt: String, username: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    override fun token(): String? = prefs.getString(KEY_TOKEN, null)

    override fun expiresAt(): String? = prefs.getString(KEY_EXPIRES_AT, null)

    override fun username(): String? = prefs.getString(KEY_USERNAME, null)

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "signpdf_token_store"
        private const val KEY_TOKEN = "token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USERNAME = "username"
    }
}
