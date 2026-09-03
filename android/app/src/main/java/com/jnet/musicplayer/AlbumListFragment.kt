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

class AlbumListFragment : Fragment(), MainActivity.SongsConsumer {

    private var _binding: FragmentAlbumListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AlbumAdapter
    private var songs: List<Song> = emptyList()
    private var allAlbums: List<AlbumItem> = emptyList()
    private var registered = false

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

        binding.searchInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    applyFilter(s?.toString().orEmpty())
                }
            }
        )

        registerAsConsumer()
        (activity as? MainActivity)?.allSongs?.let { onLibraryChanged(it) }
    }

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
        lifecycleScope.launch {
            val albums = withContext(Dispatchers.IO) {
                songs.groupBy { it.album }
                    .map { (name, list) -> AlbumItem(name, list.first().displayArtist, list.size) }
                    .sortedBy { it.name.lowercase() }
            }
            allAlbums = albums
            binding.searchInput.setText("")
            adapter.updateAlbums(albums)
            binding.tvEmpty.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allAlbums
        } else {
            allAlbums.filter {
                it.name.lowercase().contains(q) ||
                it.artist.lowercase().contains(q)
            }
        }
        adapter.updateAlbums(filtered)
        binding.tvEmpty.visibility =
            if (allAlbums.isEmpty() || filtered.isEmpty()) View.VISIBLE else View.GONE
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
        if (registered) {
            (activity as? MainActivity)?.unregisterSongsConsumer(this)
            registered = false
        }
        _binding = null
    }
}