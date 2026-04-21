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
import com.jnet.musicplayer.databinding.FragmentPlaylistDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailFragment : Fragment() {

    private var _binding: FragmentPlaylistDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter
    private var playlistSongs: List<Song> = emptyList()
    private var playlistId: Long = -1
    private var playlistName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistId = arguments?.getLong(ARG_PLAYLIST_ID) ?: -1
        playlistName = arguments?.getString(ARG_PLAYLIST_NAME) ?: ""

        binding.tvPlaylistName.text = playlistName

        adapter = SongAdapter(emptyList(),
            onSongClick = { song, index ->
                val mainActivity = activity as? MainActivity ?: return@SongAdapter
                mainActivity.playSong(playlistSongs, index)
            },
            onSongLongClick = { song ->
                showRemoveFromPlaylistDialog(song)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadPlaylistSongs()
    }

    private fun loadPlaylistSongs() {
        if (playlistId < 0) return
        val mainActivity = activity as? MainActivity ?: return
        val playlistRepo = mainActivity.getPlaylistRepository()
        val allSongs = mainActivity.getAllSongs()

        lifecycleScope.launch {
            val playlistSongEntries = withContext(Dispatchers.IO) {
                playlistRepo.getPlaylistSongs(playlistId)
            }
            playlistSongs = playlistSongEntries.mapNotNull { entry ->
                allSongs.find { it.id == entry.songId }
            }
            adapter.updateSongs(playlistSongs)
            binding.tvEmpty.visibility = if (playlistSongs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showRemoveFromPlaylistDialog(song: Song) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Song")
            .setMessage("Remove \"${song.displayTitle}\" from this playlist?")
            .setPositiveButton("Remove") { _, _ ->
                val mainActivity = activity as? MainActivity ?: return@PositiveButton
                val playlistRepo = mainActivity.getPlaylistRepository()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        playlistRepo.removeSongFromPlaylist(playlistId, song.id)
                    }
                    loadPlaylistSongs()
                    Toast.makeText(requireContext(), "Song removed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val ARG_PLAYLIST_ID = "playlist_id"
        private const val ARG_PLAYLIST_NAME = "playlist_name"

        fun newInstance(playlistId: Long, playlistName: String): PlaylistDetailFragment {
            return PlaylistDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_PLAYLIST_ID, playlistId)
                    putString(ARG_PLAYLIST_NAME, playlistName)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}