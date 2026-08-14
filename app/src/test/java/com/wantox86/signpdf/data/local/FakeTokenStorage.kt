package com.wantox86.signpdf.data.local

// Plain in-memory stand-in for TokenStore -- TokenStore's real EncryptedSharedPreferences
// backing needs Android Keystore, which doesn't exist under Robolectric/plain JVM tests.
class FakeTokenStorage : TokenStorage {
    private var token: String? = null
    private var expiresAt: String? = null
    private var username: String? = null

    override fun save(token: String, expiresAt: String, username: String) {
        this.token = token
        this.expiresAt = expiresAt
        this.username = username
    }

    override fun token(): String? = token

    override fun expiresAt(): String? = expiresAt

    override fun username(): String? = username

    override fun clear() {
        token = null
        expiresAt = null
        username = null
    }
}
