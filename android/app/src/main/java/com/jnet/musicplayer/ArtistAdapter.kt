package com.jnet.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jnet.musicplayer.databinding.ItemArtistBinding

data class ArtistItem(val name: String, val songCount: Int)

class ArtistAdapter(
    private var artists: List<ArtistItem> = emptyList(),
    private val onArtistClick: (ArtistItem) -> Unit
) : RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

    inner class ArtistViewHolder(val binding: ItemArtistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val binding = ItemArtistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ArtistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]
        with(holder.binding) {
            tvArtistName.text = artist.name
            tvSongCount.text = root.context.resources.getQuantityString(
                R.plurals.song_count, artist.songCount, artist.songCount
            )
            root.setOnClickListener { onArtistClick(artist) }
        }
    }

    override fun getItemCount() = artists.size

    fun updateArtists(newArtists: List<ArtistItem>) {
        artists = newArtists
        notifyDataSetChanged()
    }
}