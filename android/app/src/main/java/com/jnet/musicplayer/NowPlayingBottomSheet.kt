package com.jnet.musicplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jnet.musicplayer.databinding.FragmentNowPlayingBinding

class NowPlayingBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNowPlayingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        registerListeners()
        updateUI()
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { sendAction(MusicService.ACTION_PLAY_PAUSE) }
        binding.btnNext.setOnClickListener { sendAction(MusicService.ACTION_NEXT) }
        binding.btnPrevious.setOnClickListener { sendAction(MusicService.ACTION_PREVIOUS) }
        binding.btnShuffle.setOnClickListener { MusicService.toggleShuffle(); updateShuffleButton() }
        binding.btnRepeat.setOnClickListener { MusicService.toggleRepeat(); updateRepeatButton() }
        binding.btnAddToPlaylist.setOnClickListener {
            val song = MusicService.currentSong ?: return@setOnClickListener
            val mainActivity = activity as? MainActivity ?: return@setOnClickListener
            PlaylistDialogs.showAddToPlaylist(
                requireContext(), this, mainActivity.playlistRepository, song
            )
        }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) MusicService.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun updateUI() {
        val song = MusicService.currentSong ?: return
        if (_binding == null) return
        binding.tvTitle.text = song.displayTitle
        binding.tvArtist.text = song.displayArtist
        binding.tvAlbum.text = song.album

        val uri = Uri.parse("content://media/external/audio/albumart").buildUpon()
            .appendPath(song.albumId.toString()).build()
        Glide.with(this).load(uri).placeholder(R.drawable.ic_music_note).into(binding.ivAlbumArt)

        updatePlayPauseButton(MusicService.isPlaying)
        updateShuffleButton()
        updateRepeatButton()
    }

    private var listenersRegistered = false

    private fun registerListeners() {
        if (listenersRegistered) return
        listenersRegistered = true
        playbackStateListener = { updatePlayPauseButton(it) }
        positionListener = { pos, dur -> updateSeekbar(pos, dur) }
        songListener = { updateUI() }
        MusicService.addOnPlaybackStateChanged(playbackStateListener)
        MusicService.addOnPositionChanged(positionListener)
        MusicService.addOnSongChanged(songListener)
    }

    private var playbackStateListener: ((Boolean) -> Unit)? = null
    private var positionListener: ((Int, Int) -> Unit)? = null
    private var songListener: ((Song?) -> Unit)? = null

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
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
    }

    private fun updateRepeatButton() {
        val icon = when (MusicService.repeatMode) {
            MusicService.RepeatMode.OFF -> R.drawable.ic_repeat
            MusicService.RepeatMode.ALL -> R.drawable.ic_repeat_on
            MusicService.RepeatMode.ONE -> R.drawable.ic_repeat_one
        }
        binding.btnRepeat.setImageResource(icon)
    }

    private fun sendAction(action: String) {
        val intent = Intent(requireContext(), MusicService::class.java).apply {
            this.action = action
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (listenersRegistered) {
            playbackStateListener?.let { MusicService.removeOnPlaybackStateChanged(it) }
            positionListener?.let { MusicService.removeOnPositionChanged(it) }
            songListener?.let { MusicService.removeOnSongChanged(it) }
        }
        listenersRegistered = false
        playbackStateListener = null
        positionListener = null
        songListener = null
        _binding = null
    }
}