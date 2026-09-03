package com.jnet.musicplayer

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    // Playback
    val crossfadeEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 3,
    val autoRepeatEnabled: Boolean = true,
    val playbackSpeed: Float = 1f,
    val pauseOnUnplug: Boolean = true,
    val keepScreenOn: Boolean = true,
    // Scanning
    val mp3Only: Boolean = true,
    val minTrackLengthSec: Int = 60,
    val includePaths: List<String> = emptyList(),
    val excludePaths: List<String> = emptyList(),
    val scanOnStartup: Boolean = true
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jnet_music_prefs", Context.MODE_PRIVATE)

    fun get(): AppSettings = AppSettings(
        crossfadeEnabled = prefs.getBoolean(KEY_CROSSFADE, true),
        crossfadeDurationSec = prefs.getInt(KEY_CROSSFADE_DUR, 3),
        autoRepeatEnabled = prefs.getBoolean(KEY_AUTO_REPEAT, true),
        playbackSpeed = prefs.getFloat(KEY_SPEED, 1f),
        pauseOnUnplug = prefs.getBoolean(KEY_PAUSE_UNPLUG, true),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN, true),
        mp3Only = prefs.getBoolean(KEY_MP3_ONLY, true),
        minTrackLengthSec = prefs.getInt(KEY_MIN_LENGTH, 60),
        includePaths = getStringList(KEY_INCLUDE),
        excludePaths = getStringList(KEY_EXCLUDE),
        scanOnStartup = prefs.getBoolean(KEY_SCAN_ON_STARTUP, true)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_CROSSFADE, settings.crossfadeEnabled)
            .putInt(KEY_CROSSFADE_DUR, settings.crossfadeDurationSec)
            .putBoolean(KEY_AUTO_REPEAT, settings.autoRepeatEnabled)
            .putFloat(KEY_SPEED, settings.playbackSpeed)
            .putBoolean(KEY_PAUSE_UNPLUG, settings.pauseOnUnplug)
            .putBoolean(KEY_KEEP_SCREEN, settings.keepScreenOn)
            .putBoolean(KEY_MP3_ONLY, settings.mp3Only)
            .putInt(KEY_MIN_LENGTH, settings.minTrackLengthSec)
            .putBoolean(KEY_SCAN_ON_STARTUP, settings.scanOnStartup)
            .putStringSet(KEY_INCLUDE, settings.includePaths.toHashSet())
            .putStringSet(KEY_EXCLUDE, settings.excludePaths.toHashSet())
            .apply()
    }

    private fun getStringList(key: String): List<String> =
        prefs.getStringSet(key, emptySet())?.toList()?.sorted() ?: emptyList()

    private companion object {
        const val KEY_CROSSFADE = "crossfade_enabled"
        const val KEY_CROSSFADE_DUR = "crossfade_duration"
        const val KEY_AUTO_REPEAT = "auto_repeat"
        const val KEY_SPEED = "playback_speed"
        const val KEY_PAUSE_UNPLUG = "pause_on_unplug"
        const val KEY_KEEP_SCREEN = "keep_screen_on"
        const val KEY_MP3_ONLY = "mp3_only"
        const val KEY_MIN_LENGTH = "min_track_length"
        const val KEY_INCLUDE = "include_paths"
        const val KEY_EXCLUDE = "exclude_paths"
        const val KEY_SCAN_ON_STARTUP = "scan_on_startup"
    }
}