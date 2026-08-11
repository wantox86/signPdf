package com.wantox86.signpdf

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.findNavController

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navController = findNavController(R.id.nav_host_fragment)
        requestInitialPermissionsIfNeeded()
        handleIncomingIntent(intent)
        showLastCrashIfAny()
    }

    private fun showLastCrashIfAny() {
        val crashLog = CrashHandler.consumeLastCrash(this) ?: return
        val padding = (16 * resources.displayMetrics.density).toInt()
        // TextView selectable (bukan setMessage() biasa) biar teksnya bisa di-long-press,
        // di-select, dan di-copy -- setMessage() render pesan dialog sebagai teks statis.
        val textView = TextView(this).apply {
            text = crashLog
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
            textSize = 12f
        }
        val scrollView = ScrollView(this).apply { addView(textView) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.crash_dialog_title))
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val bundle = bundleOf("pdfUri" to intent.data.toString())
            navController.navigate(R.id.pdfEditorFragment, bundle)
        }
    }

    private fun requestInitialPermissionsIfNeeded() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
