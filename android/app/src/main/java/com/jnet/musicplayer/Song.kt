package com.jnet.musicplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumId: Long,
    val trackNumber: Int = 0
) : Parcelable {
    val displayTitle: String
        get() = title.ifBlank { path.substringAfterLast('/') }

    val displayArtist: String
        get() = artist.ifBlank { "Unknown Artist" }

    val displayDuration: String
        get() {
            val seconds = duration / 1000
            val mins = seconds / 60
            val secs = seconds % 60
            return "%d:%02d".format(mins, secs)
        }
}