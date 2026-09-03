package com.jnet.musicplayer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared logic for exporting playlists to JSON and re-adding imported
 * playlists. Uses the MediaStore song id as the stable key so duplicates
 * (already-present or already-added) are skipped.
 */
object PlaylistIO {

    fun exportJson(playlists: List<Pair<String, List<Long>>>): String {
        val root = JSONObject().put("version", 1)
        val arr = JSONArray()
        playlists.forEach { (name, songIds) ->
            val p = JSONObject().put("name", name)
            val ids = JSONArray()
            songIds.forEach { ids.put(it) }
            p.put("songs", ids)
            arr.put(p)
        }
        root.put("playlists", arr)
        return root.toString(2)
    }

    data class ImportResult(val addedPlaylists: Int, val addedSongs: Int, val skippedDuplicates: Int)

    /**
     * Parses exported JSON and adds playlists. Skips songs that are no longer
     * in the library or already present in the target playlist. Returns counts.
     */
    suspend fun importJson(
        text: String,
        repo: PlaylistRepository,
        library: List<Song>
    ): ImportResult {
        val root = JSONObject(text)
        val arr = root.optJSONArray("playlists") ?: return ImportResult(0, 0, 0)
        val idToLibrary = library.associateBy { it.id }

        var addedPlaylists = 0
        var addedSongs = 0
        var skipped = 0

        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            val name = p.optString("name", "Imported Playlist").ifBlank { "Imported Playlist" }
            val songIds = p.optJSONArray("songs") ?: continue

            val existing = repo.getPlaylistByIdByName(name)
            var playlistId = existing
            if (playlistId == null) {
                playlistId = repo.createPlaylist(name)
                addedPlaylists += 1
            }

            val present = repo.getPlaylistSongs(playlistId).map { it.songId }.toSet()
            var order = repo.getPlaylistSongs(playlistId).size

            for (j in 0 until songIds.length()) {
                val mediaId = songIds.getLong(j)
                val song = idToLibrary[mediaId]
                if (song == null) {
                    skipped += 1
                    continue
                }
                if (mediaId in present) {
                    skipped += 1
                    continue
                }
                repo.addSongToPlaylist(playlistId, mediaId, order)
                order += 1
                present += mediaId
                addedSongs += 1
            }
        }
        return ImportResult(addedPlaylists, addedSongs, skipped)
    }
}