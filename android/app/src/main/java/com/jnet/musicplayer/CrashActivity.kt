package com.jnet.musicplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jnet.musicplayer.databinding.ActivityCrashBinding

class CrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val report = intent.getStringExtra(EXTRA_REPORT)
            ?: CrashHandler.readLastReport()
            ?: "Unknown error"
        binding.tvCrashInfo.text = report

        binding.btnCopy.setOnClickListener {
            copyToClipboard(report)
        }

        binding.btnShare.setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, report)
            }
            startActivity(Intent.createChooser(share, "Share crash report"))
        }

        binding.btnClose.setOnClickListener {
            finishAffinity()
            Process.killProcess(Process.myPid())
            System.exit(0)
        }
    }

    private fun copyToClipboard(report: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("crash_report", report))
        Toast.makeText(this, "Crash report copied to clipboard", Toast.LENGTH_LONG).show()
    }

    companion object {
        const val EXTRA_REPORT = "crash_report"
    }
}