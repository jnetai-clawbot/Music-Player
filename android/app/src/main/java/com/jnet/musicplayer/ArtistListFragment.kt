package com.jnet.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jnet.musicplayer.databinding.FragmentArtistListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtistListFragment : Fragment(), MainActivity.SongsConsumer {

    private var _binding: FragmentArtistListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ArtistAdapter
    private var songs: List<Song> = emptyList()
    private var allArtists: List<ArtistItem> = emptyList()
    private var registered = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArtistListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ArtistAdapter(emptyList()) { artist ->
            showArtistSongs(artist.name)
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
            val artists = withContext(Dispatchers.IO) {
                songs.groupBy { it.displayArtist }
                    .map { (name, list) -> ArtistItem(name, list.size) }
                    .sortedBy { it.name.lowercase() }
            }
            allArtists = artists
            binding.searchInput.setText("")
            adapter.updateArtists(artists)
            binding.tvEmpty.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            allArtists
        } else {
            allArtists.filter { it.name.lowercase().contains(q) }
        }
        adapter.updateArtists(filtered)
        binding.tvEmpty.visibility =
            if (allArtists.isEmpty()) View.VISIBLE else if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showArtistSongs(artistName: String) {
        val artistSongs = songs.filter { it.displayArtist == artistName }
        val fragment = ArtistDetailFragment.newInstance(artistName, ArrayList(artistSongs))
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