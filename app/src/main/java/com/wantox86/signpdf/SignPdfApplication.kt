package com.wantox86.signpdf

import android.app.Application

class SignPdfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
