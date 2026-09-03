package com.jnet.musicplayer

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Entities ---

@Entity(tableName = "scanned_songs")
data class ScannedSong(
    @PrimaryKey val path: String,
    val mediaId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val albumId: Long,
    val trackNumber: Int,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = mediaId,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        path = path,
        albumId = albumId,
        trackNumber = trackNumber
    )
}

fun Song.toScannedSong(): ScannedSong = ScannedSong(
    path = path,
    mediaId = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    albumId = albumId,
    trackNumber = trackNumber
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val order: Int
)

// --- DAOs ---

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun getAllPlaylists(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY `order` ASC")
    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSong>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(playlistSong: PlaylistSong)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}

// --- Scanned Songs DAO ---

@Dao
interface ScannedSongDao {
    @Query("SELECT * FROM scanned_songs ORDER BY title COLLATE NOCASE ASC")
    suspend fun getAll(): List<ScannedSong>

    @Query("SELECT path FROM scanned_songs")
    suspend fun getAllPaths(): List<String>

    @Query("SELECT COUNT(*) FROM scanned_songs")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<ScannedSong>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<ScannedSong>)

    @Query("DELETE FROM scanned_songs WHERE path IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)
}

// --- Database ---

@Database(
    entities = [Playlist::class, PlaylistSong::class, ScannedSong::class],
    version = 2,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun scannedSongDao(): ScannedSongDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `scanned_songs` (
                        `path` TEXT NOT NULL,
                        `mediaId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `albumId` INTEGER NOT NULL,
                        `trackNumber` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`path`)
                    )"""
                )
            }
        }

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "jnet_music_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository wrapper with IO dispatcher enforcement ---

class PlaylistRepository(private val context: Context) {

    private val dao: PlaylistDao by lazy {
        MusicDatabase.getInstance(context).playlistDao()
    }

    suspend fun getAllPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        dao.getAllPlaylists()
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylistById(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, order: Int) = withContext(Dispatchers.IO) {
        dao.insertPlaylistSong(PlaylistSong(playlistId, songId, order))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        dao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSong> = withContext(Dispatchers.IO) {
        dao.getPlaylistSongs(playlistId)
    }

    suspend fun clearPlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.clearPlaylist(playlistId)
    }
}