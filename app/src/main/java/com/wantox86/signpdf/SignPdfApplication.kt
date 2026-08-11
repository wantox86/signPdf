package com.wantox86.signpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class SignPdfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        // Wajib dipanggil sekali sebelum pdfbox-android dipake (load font/cmap bawaan dari
        // assets) -- kelewat ini bikin crash pas load/render PDF pertama kali.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
