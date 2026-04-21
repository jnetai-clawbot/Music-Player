package com.jnet.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jnet.musicplayer.databinding.ItemAlbumBinding

data class AlbumItem(val name: String, val artist: String, val songCount: Int)

class AlbumAdapter(
    private var albums: List<AlbumItem> = emptyList(),
    private val onAlbumClick: (AlbumItem) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    inner class AlbumViewHolder(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]
        with(holder.binding) {
            tvAlbumName.text = album.name
            tvAlbumArtist.text = album.artist
            tvSongCount.text = root.context.resources.getQuantityString(
                R.plurals.song_count, album.songCount, album.songCount
            )
            root.setOnClickListener { onAlbumClick(album) }
        }
    }

    override fun getItemCount() = albums.size

    fun updateAlbums(newAlbums: List<AlbumItem>) {
        albums = newAlbums
        notifyDataSetChanged()
    }
}