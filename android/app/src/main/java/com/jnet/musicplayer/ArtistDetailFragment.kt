package com.jnet.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.jnet.musicplayer.databinding.FragmentArtistDetailBinding

class ArtistDetailFragment : Fragment() {

    private var _binding: FragmentArtistDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArtistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val artistName = arguments?.getString(ARG_ARTIST_NAME) ?: ""
        @Suppress("DEPRECATION")
        songs = arguments?.getParcelableArrayList(ARG_SONGS) ?: emptyList()

        binding.tvArtistName.text = artistName

        adapter = SongAdapter(
            onSongClick = { song, index ->
                val mainActivity = activity as? MainActivity ?: return@SongAdapter
                mainActivity.playSong(songs, index)
            }
        )
        adapter.updateSongs(songs)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    companion object {
        private const val ARG_ARTIST_NAME = "artist_name"
        private const val ARG_SONGS = "songs"

        fun newInstance(artistName: String, songs: ArrayList<Song>): ArtistDetailFragment {
            return ArtistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTIST_NAME, artistName)
                    putParcelableArrayList(ARG_SONGS, songs)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}