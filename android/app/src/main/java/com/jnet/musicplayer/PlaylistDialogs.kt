package com.jnet.musicplayer

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable "add song to playlist" UI used by the song lists and the
 * now-playing sheet.
 */
object PlaylistDialogs {

    fun showAddToPlaylist(
        context: Context,
        owner: LifecycleOwner,
        repo: PlaylistRepository,
        song: Song
    ) {
        owner.lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) { repo.getAllPlaylists() }
            val names = playlists.map { it.name }.toMutableList()
            names.add(0, "+ Create New Playlist")

            MaterialAlertDialogBuilder(context)
                .setTitle("Add to Playlist")
                .setItems(names.toTypedArray()) { _, which ->
                    if (which == 0) {
                        showCreatePlaylist(context, owner, repo, song)
                    } else {
                        val playlist = playlists[which - 1]
                        owner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                val existing = repo.getPlaylistSongs(playlist.id)
                                repo.addSongToPlaylist(playlist.id, song.id, existing.size)
                            }
                            Toast.makeText(context, "Added to \"${playlist.name}\"", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }
    }

    private fun showCreatePlaylist(
        context: Context,
        owner: LifecycleOwner,
        repo: PlaylistRepository,
        song: Song
    ) {
        val input = TextInputEditText(context).apply {
            hint = "Playlist name"
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("New Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    owner.lifecycleScope.launch {
                        val id = withContext(Dispatchers.IO) { repo.createPlaylist(name) }
                        withContext(Dispatchers.IO) { repo.addSongToPlaylist(id, song.id, 0) }
                        Toast.makeText(context, "Playlist \"$name\" created", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}