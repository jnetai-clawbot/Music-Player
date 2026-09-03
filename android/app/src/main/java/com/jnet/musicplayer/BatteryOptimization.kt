package com.jnet.musicplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helpers for keeping playback alive in the background on aggressive
 * battery managers (Samsung "app sleeping", Pixel Adaptive Battery, etc).
 */
object BatteryOptimization {

    private const val TAG = "BatteryOptimization"

    /** True when the app is exempt from battery optimization. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens the system screen that lets the user allow this app. */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Request screen not available, opening settings list", e)
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e2: Exception) {
                Log.e(TAG, "Could not open battery settings", e2)
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open battery settings", e)
            return false
        }
    }
}