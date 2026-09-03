package com.jnet.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jnet.musicplayer.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment(), MainActivity.SongsConsumer {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()
    private var registered = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SongAdapter(
            onSongClick = { song, index ->
                val mainActivity = activity as? MainActivity ?: return@SongAdapter
                mainActivity.playSong(songs, index)
            },
            onSongLongClick = { song ->
                showSongOptionsDialog(song)
            },
            onAddToPlaylist = { song, _ ->
                val mainActivity = activity as? MainActivity ?: return@SongAdapter
                PlaylistDialogs.showAddToPlaylist(
                    requireContext(), this, mainActivity.playlistRepository, song
                )
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        val listener = { song: Song? ->
            song?.id?.let { adapter.setCurrentPlaying(it) }
        }
        songChangedListener = listener
        MusicService.addOnSongChanged(listener)

        registerAsConsumer()
    }

    private var songChangedListener: ((Song?) -> Unit)? = null

    override fun onResume() {
        super.onResume()
        registerAsConsumer()
        (activity as? MainActivity)?.allSongs?.let { onLibraryChanged(it) }
    }

    private fun registerAsConsumer() {
        if (!registered) {
            (activity as? MainActivity)?.registerSongsConsumer(this)
            registered = true
        }
    }

    override fun onLibraryChanged(newSongs: List<Song>) {
        updateSongs(newSongs)
    }

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        adapter.updateSongs(newSongs)
        binding.tvEmpty.visibility = if (newSongs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSongOptionsDialog(song: Song) {
        val mainActivity = activity as? MainActivity ?: return
        val playlistRepo = mainActivity.playlistRepository

        val repo = playlistRepo
        val options = mutableListOf<String>().apply {
            add("Add to Playlist")
            add("Song Details")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(song.displayTitle)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> PlaylistDialogs.showAddToPlaylist(requireContext(), this, repo, song)
                    1 -> showSongDetails(song)
                }
            }
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
        if (registered) {
            (activity as? MainActivity)?.unregisterSongsConsumer(this)
            registered = false
        }
        songChangedListener?.let { MusicService.removeOnSongChanged(it) }
        songChangedListener = null
        _binding = null
    }
}