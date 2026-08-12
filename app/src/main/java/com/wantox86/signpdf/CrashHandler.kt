package com.wantox86.signpdf

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Crash reporting minimal buat dev tanpa Android Studio/logcat: stack trace crash terakhir
 * ditulis ke file lokal, lalu ditampilkan sebagai dialog di launch berikutnya (lihat
 * MainActivity.showLastCrashIfAny()). Bukan pengganti Crashlytics, cuma biar nggak "crash
 * diem-diem" pas testing manual di device.
 */
object CrashHandler {
    private const val CRASH_FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, throwable)
            } catch (_: Exception) {
                // Jangan sampai logging crash malah nge-crash lagi; biarin defaultHandler yang urus.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(context: Context, throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        File(context.filesDir, CRASH_FILE_NAME).writeText(writer.toString())
    }

    /** @return isi stack trace crash terakhir, atau null kalau nggak ada crash tersimpan. */
    fun consumeLastCrash(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE_NAME)
        if (!file.exists()) return null
        val content = file.readText()
        file.delete()
        return content
    }
}
