package com.jnet.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jnet.musicplayer.databinding.FragmentLibraryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SongAdapter(
            songs = emptyList(),
            onSongClick = { song, index ->
                val mainActivity = activity as? MainActivity ?: return@SongAdapter
                mainActivity.playSong(songs, index)
            },
            onSongLongClick = { song ->
                showSongOptionsDialog(song)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Listen for current song changes
        MusicService.onSongChanged = { song ->
            song?.id?.let { adapter.setCurrentPlaying(it) }
        }
    }

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        adapter.updateSongs(newSongs)
        binding.tvEmpty.visibility = if (newSongs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSongOptionsDialog(song: Song) {
        val mainActivity = activity as? MainActivity ?: return
        val playlistRepo = mainActivity.getPlaylistRepository()

        lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) { playlistRepo.getAllPlaylists() }

            val options = mutableListOf<String>().apply {
                add("Add to Playlist")
                add("Song Details")
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(song.displayTitle)
                .setItems(options.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> showAddToPlaylistDialog(song, playlists, playlistRepo)
                        1 -> showSongDetails(song)
                    }
                }
                .show()
        }
    }

    private fun showAddToPlaylistDialog(
        song: Song, playlists: List<Playlist>, playlistRepo: PlaylistRepository
    ) {
        val names = playlists.map { it.name }.toMutableList()
        names.add(0, "+ Create New Playlist")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add to Playlist")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    showCreatePlaylistDialog(song, playlistRepo)
                } else {
                    val playlist = playlists[which - 1]
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val existing = playlistRepo.getPlaylistSongs(playlist.id)
                            playlistRepo.addSongToPlaylist(
                                playlist.id, song.id, existing.size
                            )
                        }
                        Toast.makeText(requireContext(), "Added to ${playlist.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showCreatePlaylistDialog(song: Song, playlistRepo: PlaylistRepository) {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Playlist name"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val id = withContext(Dispatchers.IO) { playlistRepo.createPlaylist(name) }
                        withContext(Dispatchers.IO) { playlistRepo.addSongToPlaylist(id, song.id, 0) }
                        Toast.makeText(requireContext(), "Playlist \"$name\" created", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSongDetails(song: Song) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(song.displayTitle)
            .setMessage(
                "Artist: ${song.displayArtist}\n" +
                "Album: ${song.album}\n" +
                "Duration: ${song.displayDuration}\n" +
                "Path: ${song.path}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}