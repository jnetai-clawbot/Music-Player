package com.jnet.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jnet.musicplayer.databinding.FragmentAlbumListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumListFragment : Fragment() {

    private var _binding: FragmentAlbumListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AlbumAdapter
    private var songs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlbumListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = AlbumAdapter(emptyList()) { album ->
            showAlbumSongs(album.name)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        lifecycleScope.launch {
            val albums = withContext(Dispatchers.IO) {
                songs.groupBy { it.album }
                    .map { (name, list) -> AlbumItem(name, list.first().displayArtist, list.size) }
                    .sortedBy { it.name.lowercase() }
            }
            adapter.updateAlbums(albums)
            binding.tvEmpty.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAlbumSongs(albumName: String) {
        val albumSongs = songs.filter { it.album == albumName }
        val fragment = AlbumDetailFragment.newInstance(albumName, ArrayList(albumSongs))
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}