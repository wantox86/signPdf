package com.wantox86.signpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.wantox86.signpdf.data.AuthRepository
import com.wantox86.signpdf.data.SignatureRepository
import com.wantox86.signpdf.data.SyncRepository
import com.wantox86.signpdf.data.local.SignatureMetadataStore
import com.wantox86.signpdf.data.local.TokenStore
import com.wantox86.signpdf.data.remote.ApiClient

class SignPdfApplication : Application() {
    // App-wide singletons for state that's genuinely global (one login session, one pair of
    // local signature files) -- not per-screen. There's no DI framework in this app; this is
    // the smallest thing that avoids each ViewModel `new`-ing its own AuthRepository/
    // SignatureRepository and ending up with independent, out-of-sync in-memory StateFlows
    // (e.g. Home triggers a Sync that downloads a new signature, but the signature-canvas
    // screen was still holding a different SignatureRepository instance that never heard
    // about it).
    val tokenStore by lazy { TokenStore(this) }
    val signatureRepository by lazy { SignatureRepository(this) }
    private val signatureMetadataStore by lazy { SignatureMetadataStore(this) }
    private val apiService by lazy { ApiClient.create(tokenStore) }
    val authRepository by lazy { AuthRepository(this, apiService, tokenStore) }
    val syncRepository by lazy {
        SyncRepository(this, apiService, signatureRepository, signatureMetadataStore, authRepository, tokenStore)
    }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        // Wajib dipanggil sekali sebelum pdfbox-android dipake (load font/cmap bawaan dari
        // assets) -- kelewat ini bikin crash pas load/render PDF pertama kali.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
