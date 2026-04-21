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

class ArtistListFragment : Fragment() {

    private var _binding: FragmentArtistListBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ArtistAdapter
    private var songs: List<Song> = emptyList()

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
    }

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        lifecycleScope.launch {
            val artists = withContext(Dispatchers.IO) {
                songs.groupBy { it.displayArtist }
                    .map { (name, list) -> ArtistItem(name, list.size) }
                    .sortedBy { it.name.lowercase() }
            }
            adapter.updateArtists(artists)
            binding.tvEmpty.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
        }
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
        _binding = null
    }
}