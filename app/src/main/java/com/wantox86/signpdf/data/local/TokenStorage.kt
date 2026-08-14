package com.wantox86.signpdf.data.local

// Extracted so AuthRepository/SyncRepository can depend on this instead of the concrete
// EncryptedSharedPreferences-backed TokenStore -- Android Keystore (which TokenStore needs
// for its MasterKey) doesn't exist under Robolectric/plain JVM tests, so tests substitute an
// in-memory fake here rather than exercising real encrypted storage. Same repository-
// interface pattern already used on the Go backend for the same reason.
interface TokenStorage {
    fun save(token: String, expiresAt: String, username: String)
    fun token(): String?
    fun expiresAt(): String?
    fun username(): String?
    fun clear()
}
