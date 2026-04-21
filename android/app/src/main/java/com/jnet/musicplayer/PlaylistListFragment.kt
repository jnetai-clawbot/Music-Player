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
import com.google.android.material.textfield.TextInputEditText
import com.jnet.musicplayer.databinding.FragmentPlaylistListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistListFragment : Fragment() {

    private var _binding: FragmentPlaylistListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: PlaylistAdapter
    private lateinit var playlistRepo: PlaylistRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlistRepo = (activity as? MainActivity)?.playlistRepository ?: return

        adapter = PlaylistAdapter(emptyList(),
            onPlaylistClick = { playlist -> openPlaylist(playlist) },
            onPlaylistLongClick = { playlist -> showPlaylistOptions(playlist) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAddPlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        loadPlaylists()
    }

    private fun loadPlaylists() {
        lifecycleScope.launch {
            val playlists = withContext(Dispatchers.IO) { playlistRepo.getAllPlaylists() }
            adapter.updatePlaylists(playlists)
            binding.tvEmpty.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showCreatePlaylistDialog() {
        val input = TextInputEditText(requireContext()).apply {
            hint = "Playlist name"
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { playlistRepo.createPlaylist(name) }
                        loadPlaylists()
                        Toast.makeText(requireContext(), "Playlist \"$name\" created", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPlaylist(playlist: Playlist) {
        val fragment = PlaylistDetailFragment.newInstance(playlist.id, playlist.name)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showPlaylistOptions(playlist: Playlist) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(playlist.name)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameDialog(playlist)
                    1 -> showDeleteDialog(playlist)
                }
            }
            .show()
    }

    private fun showRenameDialog(playlist: Playlist) {
        val input = TextInputEditText(requireContext()).apply {
            hint = "New name"
            setText(playlist.name)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Playlist")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            playlistRepo.createPlaylist(name) // Re-create with new name
                            playlistRepo.deletePlaylist(playlist.id)
                        }
                        loadPlaylists()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(playlist: Playlist) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete \"${playlist.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { playlistRepo.deletePlaylist(playlist.id) }
                    loadPlaylists()
                    Toast.makeText(requireContext(), "Playlist deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}