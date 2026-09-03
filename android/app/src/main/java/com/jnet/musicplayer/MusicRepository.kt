package com.jnet.musicplayer

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(
    val added: Int,
    val alreadyInLibrary: Int,
    val removed: Int,
    val total: Int
)

class MusicRepository(private val context: Context) {

    private val songDao by lazy { MusicDatabase.getInstance(context).scannedSongDao() }
    private val settingsRepo by lazy { SettingsRepository(context) }

    // --- Library DB ---

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAll().map { it.toSong() }
    }

    suspend fun getLibraryCount(): Int = withContext(Dispatchers.IO) {
        songDao.count()
    }

    /** Removes songs from the stored library (e.g. a track the user removed). */
    suspend fun removeSongsByPaths(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isNotEmpty()) songDao.deleteByPaths(paths)
    }

    // --- Scanning ---

    /**
     * Scans the device (per settings) and adds songs not already in the library DB.
     * Also removes scanned paths that no longer exist / no longer match settings.
     */
    suspend fun scanForNewMusic(): ScanResult = withContext(Dispatchers.IO) {
        val settings = settingsRepo.get()
        val found = scanMediaStore(settings)

        val existingPaths = songDao.getAllPaths().toMutableSet()
        val foundPaths = found.map { it.path }.toSet()

        val newSongs = found.filter { it.path !in existingPaths }
        if (newSongs.isNotEmpty()) {
            songDao.insertAll(newSongs.map { it.toScannedSong() })
        }

        // Remove entries whose files are gone OR no longer match current settings
        val toRemove = existingPaths.filter { it !in foundPaths }
        if (toRemove.isNotEmpty()) {
            songDao.deleteByPaths(toRemove)
        }

        val total = songDao.count()
        ScanResult(
            added = newSongs.size,
            alreadyInLibrary = foundPaths.size - newSongs.size,
            removed = toRemove.size,
            total = total
        )
    }

    /** Re-syncs library with the device, marking missing files as removed without adding new ones beyond settings. */
    suspend fun rescan(): ScanResult = scanForNewMusic()

    private fun scanMediaStore(settings: AppSettings): List<Song> {
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
                val path = cursor.getString(dataColumn) ?: continue
                val duration = cursor.getLong(durationColumn)

                if (!matchesSettings(settings, path, duration)) continue

                songs.add(
                    Song(
                        id = cursor.getLong(idColumn),
                        title = cursor.getString(titleColumn) ?: "",
                        artist = cursor.getString(artistColumn) ?: "",
                        album = cursor.getString(albumColumn) ?: "",
                        duration = duration,
                        path = path,
                        albumId = cursor.getLong(albumIdColumn),
                        trackNumber = cursor.getInt(trackColumn)
                    )
                )
            }
        }
        return songs
    }

    private fun matchesSettings(settings: AppSettings, path: String, duration: Long): Boolean {
        if (settings.mp3Only && !path.lowercase().endsWith(".mp3")) return false

        if (settings.minTrackLengthSec > 0 &&
            duration < settings.minTrackLengthSec * 1000L
        ) return false

        // Exclude beats include: anything under an excluded path is dropped first
        if (settings.excludePaths.isNotEmpty() &&
            settings.excludePaths.any { path.startsWith(it.trimEnd('/') + "/") || path == it.trimEnd('/') }
        ) return false

        // If include paths are defined, the file must live under one of them
        if (settings.includePaths.isNotEmpty() &&
            settings.includePaths.none { path.startsWith(it.trimEnd('/') + "/") || path == it.trimEnd('/') }
        ) return false

        return true
    }

    // --- Derived lists (from library DB) ---

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