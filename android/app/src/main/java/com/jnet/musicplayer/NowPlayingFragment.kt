package com.jnet.musicplayer

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.jnet.musicplayer.databinding.FragmentNowPlayingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NowPlayingFragment : Fragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!
    private var isServiceBound = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupControls()
        observeServiceState()
        updateUI()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            sendServiceAction(MusicService.ACTION_PLAY_PAUSE)
        }

        binding.btnNext.setOnClickListener {
            sendServiceAction(MusicService.ACTION_NEXT)
        }

        binding.btnPrevious.setOnClickListener {
            sendServiceAction(MusicService.ACTION_PREVIOUS)
        }

        binding.btnShuffle.setOnClickListener {
            MusicService.toggleShuffle() // Direct since it's static
            updateShuffleButton()
        }

        binding.btnRepeat.setOnClickListener {
            MusicService.toggleRepeat()
            updateRepeatButton()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    MusicService.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun observeServiceState() {
        MusicService.onSongChanged = { song -> updateUI() }
        MusicService.onPlaybackStateChanged = { isPlaying -> updatePlayPauseButton(isPlaying) }
        MusicService.onPositionChanged = { position, duration -> updateSeekbar(position, duration) }
        MusicService.onShuffleChanged = { updateShuffleButton() }
        MusicService.onRepeatChanged = { updateRepeatButton() }
    }

    private fun updateUI() {
        val song = MusicService.currentSong ?: return

        binding.tvTitle.text = song.displayTitle
        binding.tvArtist.text = song.displayArtist
        binding.tvAlbum.text = song.album

        // Load album art
        val uri = android.content.ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .centerCrop()
            .into(binding.ivAlbumArt)

        updatePlayPauseButton(MusicService.isPlaying)
        updateShuffleButton()
        updateRepeatButton()
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateSeekbar(position: Int, duration: Int) {
        binding.seekBar.max = duration
        binding.seekBar.progress = position
        binding.tvCurrentTime.text = formatTime(position)
        binding.tvTotalTime.text = formatTime(duration)
    }

    private fun updateShuffleButton() {
        binding.btnShuffle.setImageResource(
            if (MusicService.shuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        )
        binding.btnShuffle.alpha = if (MusicService.shuffleEnabled) 1f else 0.5f
    }

    private fun updateRepeatButton() {
        val icon = when (MusicService.repeatMode) {
            MusicService.RepeatMode.OFF -> R.drawable.ic_repeat
            MusicService.RepeatMode.ALL -> R.drawable.ic_repeat_on
            MusicService.RepeatMode.ONE -> R.drawable.ic_repeat_one
        }
        binding.btnRepeat.setImageResource(icon)
        binding.btnRepeat.alpha = when (MusicService.repeatMode) {
            MusicService.RepeatMode.OFF -> 0.5f
            else -> 1f
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(requireContext(), MusicService::class.java).apply {
            this.action = action
        }
        requireContext().startService(intent)
    }

    private fun formatTime(ms: Int): String {
        val seconds = ms / 1000
        val mins = seconds / 60
        val secs = seconds % 60
        return "%d:%02d".format(mins, secs)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}