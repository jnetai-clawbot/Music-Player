package com.jnet.musicplayer

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash reporter. Captures any uncaught exception, writes a report file
 * to internal storage, then lets the system terminate the process normally
 * (avoids the "app isn't responding" hang). The report is shown with a
 * copy-to-clipboard button on the next app launch via [MainActivity].
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_FILE = "crash_report.txt"

    private var appContext: Context? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(app: Application) {
        appContext = app.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(thread, throwable)
        }
    }

    private fun handleCrash(thread: Thread, throwable: Throwable) {
        try {
            val report = buildReport(thread, throwable)
            saveReport(report)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash report", e)
        }
        // Propagate to the system handler so the OS logs and kills the process
        // cleanly. This prevents the main thread dying silently and leaving the
        // app in an unresponsive ("isn't responding") state.
        previousHandler?.uncaughtException(thread, throwable)
            ?: run {
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