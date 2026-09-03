package com.jnet.musicplayer

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jnet.musicplayer.databinding.ItemSongBinding

typealias SongClickHandler = (Song, Int) -> Unit

class SongAdapter(
    private var songs: List<Song> = emptyList(),
    private val onSongClick: SongClickHandler,
    private val onSongLongClick: ((Song) -> Unit)? = null,
    private val onAddToPlaylist: ((Song, Int) -> Unit)? = null
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var currentPlayingId: Long = -1

    inner class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        with(holder.binding) {
            tvTitle.text = song.displayTitle
            tvArtist.text = song.displayArtist
            tvDuration.text = song.displayDuration

            val isCurrent = song.id == currentPlayingId
            if (isCurrent) {
                tvTitle.setTextColor(root.context.getColor(R.color.md_theme_primary))
                btnPlay.setImageResource(
                    if (MusicService.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            } else {
                tvTitle.setTextColor(root.context.getColor(R.color.md_theme_onSurface))
                btnPlay.setImageResource(R.drawable.ic_play)
            }

            loadAlbumArt(ivAlbumArt, song.albumId)

            // Row tap plays the song
            root.setOnClickListener { onSongClick(song, position) }
            root.setOnLongClickListener {
                onSongLongClick?.invoke(song)
                true
            }

            // Explicit play/pause button
            btnPlay.setOnClickListener { onSongClick(song, position) }

            // Add to playlist button
            btnAdd.setOnClickListener { onAddToPlaylist?.invoke(song, position) }
        }
    }

    override fun getItemCount() = songs.size

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun setCurrentPlaying(songId: Long) {
        currentPlayingId = songId
        notifyDataSetChanged()
    }

    companion object {
        fun loadAlbumArt(imageView: ImageView, albumId: Long) {
            val uri = android.content.ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
            Glide.with(imageView.context)
                .load(uri)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .centerCrop()
                .into(imageView)
        }
    }
}