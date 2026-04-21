package com.jnet.musicplayer

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val resolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationColumn)
                if (duration > 5000) { // Filter out very short clips (< 5 sec)
                    songs.add(
                        Song(
                            id = cursor.getLong(idColumn),
                            title = cursor.getString(titleColumn) ?: "",
                            artist = cursor.getString(artistColumn) ?: "",
                            album = cursor.getString(albumColumn) ?: "",
                            duration = duration,
                            path = cursor.getString(dataColumn) ?: "",
                            albumId = cursor.getLong(albumIdColumn),
                            trackNumber = cursor.getInt(trackColumn)
                        )
                    )
                }
            }
        }
        songs
    }

    suspend fun getSongsByArtist(artist: String): List<Song> = withContext(Dispatchers.IO) {
        getAllSongs().filter { it.artist.equals(artist, ignoreCase = true) }
    }

    suspend fun getSongsByAlbum(album: String): List<Song> = withContext(Dispatchers.IO) {
        getAllSongs().filter { it.album.equals(album, ignoreCase = true) }
    }

    suspend fun getArtists(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        getAllSongs().groupBy { it.displayArtist }
            .map { (artist, songs) -> artist to songs.size }
            .sortedBy { it.first.lowercase() }
    }

    suspend fun getAlbums(): List<Triple<String, String, Int>> = withContext(Dispatchers.IO) {
        getAllSongs().groupBy { it.album }
            .map { (album, songs) -> Triple(album, songs.first().displayArtist, songs.size) }
            .sortedBy { it.first.lowercase() }
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val lowerQuery = query.lowercase()
        getAllSongs().filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.artist.lowercase().contains(lowerQuery) ||
            it.album.lowercase().contains(lowerQuery)
        }
    }
}