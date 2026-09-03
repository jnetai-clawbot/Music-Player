package com.jnet.musicplayer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash reporter. Captures any uncaught exception, writes a report file
 * to internal storage, and launches [CrashActivity] so the user can copy the
 * error to the clipboard.
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_FILE = "crash_report.txt"

    private var appContext: Context? = null

    fun install(app: Application) {
        appContext = app.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
        }
    }

    private fun handleCrash(thread: Thread, throwable: Throwable) {
        try {
            val report = buildReport(thread, throwable)
            saveReport(report)

            val ctx = appContext ?: return
            val intent = Intent(ctx, CrashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(CrashActivity.EXTRA_REPORT, report)
            }
            // Launch the crash screen. The process stays alive (we never call the
            // original default handler), so the crash screen renders even though
            // some other thread threw. The user closes it explicitly.
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show crash screen", e)
            Process.killProcess(Process.myPid())
            System.exit(2)
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        sb.appendLine("JNet Music Player - Crash Report")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Thread: ${thread.name}")
        sb.appendLine("--------------------------------")
        sb.append(Log.getStackTraceString(throwable))
        return sb.toString()
    }

    private fun saveReport(report: String) {
        val ctx = appContext ?: return
        try {
            File(ctx.filesDir, CRASH_FILE).writeText(report)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash report", e)
        }
    }

    fun readLastReport(): String? {
        val ctx = appContext ?: return null
        val f = File(ctx.filesDir, CRASH_FILE)
        return if (f.exists()) {
            try {
                f.readText()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun clearReport() {
        val ctx = appContext ?: return
        try {
            File(ctx.filesDir, CRASH_FILE).delete()
        } catch (e: Exception) {
            // ignore
        }
    }
}