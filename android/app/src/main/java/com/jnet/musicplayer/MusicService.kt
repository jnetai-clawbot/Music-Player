package com.jnet.musicplayer

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.IOException

class MusicService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "jnet_music_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.jnet.musicplayer.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.jnet.musicplayer.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.jnet.musicplayer.ACTION_PREVIOUS"
        const val ACTION_STOP = "com.jnet.musicplayer.ACTION_STOP"
        const val EXTRA_SONG_LIST = "song_list"
        const val EXTRA_SONG_INDEX = "song_index"
        const val EXTRA_SHUFFLE = "shuffle"

        var isRunning = false
            private set

        var currentSong: Song? = null
            private set
        var isPlaying = false
            private set
        var currentPosition: Int = 0
            private set
        var duration: Int = 0
            private set
        var shuffleEnabled = false
            private set
        var repeatMode: RepeatMode = RepeatMode.OFF
            private set

        var onSongChanged: ((Song?) -> Unit)? = null
        var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
        var onPositionChanged: ((Int, Int) -> Unit)? = null
        var onShuffleChanged: ((Boolean) -> Unit)? = null
        var onRepeatChanged: ((RepeatMode) -> Unit)? = null

        // Direct control methods (called via intent or from UI)
        fun toggleShuffle() {
            shuffleEnabled = !shuffleEnabled
            onShuffleChanged?.invoke(shuffleEnabled)
        }

        fun toggleRepeat() {
            repeatMode = when (repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            onRepeatChanged?.invoke(repeatMode)
        }

        fun seekTo(position: Int) {
            // This is called from UI - the actual service instance handles it
            // via the _instance reference
            _instance?.mediaPlayer?.seekTo(position)
        }

        private var _instance: MusicService? = null
    }

    enum class RepeatMode { OFF, ALL, ONE }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var songs: List<Song> = emptyList()
    private var currentIndex: Int = 0
    private var shuffledIndices: MutableList<Int> = mutableListOf()
    private var shufflePointer: Int = 0
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeFocusLoss = false
    private var progressJob: Job? = null

    private val binder = MusicBinder()

    inner class MusicBinder : android.os.Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        isRunning = true
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initMediaSession()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        when (action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_STOP -> {
                stopSelf()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }
            else -> {
                // Initial play request
                if (intent != null && intent.hasExtra(EXTRA_SONG_LIST)) {
                    @Suppress("DEPRECATION")
                    val songList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(EXTRA_SONG_LIST, Song::class.java)
                    } else {
                        intent.getParcelableArrayListExtra(EXTRA_SONG_LIST)
                    }
                    songList?.let {
                        songs = it
                        currentIndex = intent.getIntExtra(EXTRA_SONG_INDEX, 0)
                        shuffleEnabled = intent.getBooleanExtra(EXTRA_SHUFFLE, false)
                        if (shuffleEnabled) buildShuffleList()
                        playSong(currentIndex)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
        abandonAudioFocus()
        isRunning = false
        currentSong = null
        isPlaying = false
        _instance = null
        onSongChanged = null
        onPlaybackStateChanged = null
        onPositionChanged = null
        onShuffleChanged = null
        onRepeatChanged = null
    }

    // --- Notification Receiver ---
    class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val serviceIntent = Intent(context, MusicService::class.java).apply {
                action = intent.action
            }
            context.startService(serviceIntent)
        }
    }

    // --- Playback Controls ---

    private fun playSong(index: Int) {
        if (index < 0 || index >= songs.size) return

        currentIndex = index
        val song = songs[currentIndex]
        currentSong = song

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setWakeMode(this@MusicService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@MusicService, Uri.parse(song.path))
                setOnPreparedListener { mp ->
                    mp.start()
                    isPlaying = true
                    duration = mp.duration
                    onSongChanged?.invoke(song)
                    onPlaybackStateChanged?.invoke(true)
                    updateMediaSession()
                    startProgressUpdates()
                    requestAudioFocus()
                    showNotification()
                }
                setOnCompletionListener {
                    onSongComplete()
                }
                setOnErrorListener { _, _, _ ->
                    playNext()
                    true
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            playNext()
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                isPlaying = false
                onPlaybackStateChanged?.invoke(false)
                progressJob?.cancel()
            } else {
                mp.start()
                isPlaying = true
                onPlaybackStateChanged?.invoke(true)
                startProgressUpdates()
            }
            updateMediaSession()
            showNotification()
        }
    }

    fun playNext() {
        if (songs.isEmpty()) return
        val nextIndex = when {
            shuffleEnabled -> getNextShuffledIndex()
            repeatMode == RepeatMode.ONE -> currentIndex
            currentIndex >= songs.size - 1 -> if (repeatMode == RepeatMode.ALL) 0 else return
            else -> currentIndex + 1
        }
        playSong(nextIndex)
    }

    fun playPrevious() {
        if (songs.isEmpty()) return
        mediaPlayer?.let { mp ->
            if (mp.currentPosition > 3000) {
                mp.seekTo(0)
                return
            }
        }
        val prevIndex = when {
            shuffleEnabled -> getPrevShuffledIndex()
            repeatMode == RepeatMode.ONE -> currentIndex
            currentIndex <= 0 -> if (repeatMode == RepeatMode.ALL) songs.size - 1 else return
            else -> currentIndex - 1
        }
        playSong(prevIndex)
    }

    // --- Shuffle Helpers ---

    private fun buildShuffleList() {
        shuffledIndices = ((songs.indices.toSet() - currentIndex).shuffled().toMutableList())
        shuffledIndices.add(0, currentIndex)
        shufflePointer = 0
    }

    private fun getNextShuffledIndex(): Int {
        if (shuffledIndices.isEmpty()) buildShuffleList()
        shufflePointer = (shufflePointer + 1).coerceAtMost(shuffledIndices.size - 1)
        if (shufflePointer >= shuffledIndices.size - 1 && repeatMode == RepeatMode.ALL) {
            buildShuffleList()
            shufflePointer = 0
        }
        return shuffledIndices.getOrElse(shufflePointer) { 0 }
    }

    private fun getPrevShuffledIndex(): Int {
        shufflePointer = (shufflePointer - 1).coerceAtLeast(0)
        return shuffledIndices.getOrElse(shufflePointer) { 0 }
    }

    // --- Song Complete ---

    private fun onSongComplete() {
        when (repeatMode) {
            RepeatMode.ONE -> playSong(currentIndex)
            else -> playNext()
        }
    }

    // --- Progress Updates ---

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    currentPosition = mp.currentPosition
                    duration = mp.duration
                    onPositionChanged?.invoke(currentPosition, duration)
                }
                delay(200)
            }
        }
    }

    // --- Audio Focus ---

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange -> handleAudioFocusChange(focusChange) },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeFocusLoss = mediaPlayer?.isPlaying ?: false
                mediaPlayer?.pause()
                isPlaying = false
                onPlaybackStateChanged?.invoke(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = mediaPlayer?.isPlaying ?: false
                mediaPlayer?.pause()
                isPlaying = false
                onPlaybackStateChanged?.invoke(false)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlayingBeforeFocusLoss) {
                    mediaPlayer?.start()
                    isPlaying = true
                    onPlaybackStateChanged?.invoke(true)
                }
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    // --- Media Session ---

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "JNetMusicPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { togglePlayPause() }
                override fun onPause() { togglePlayPause() }
                override fun onSkipToNext() { playNext() }
                override fun onSkipToPrevious() { playPrevious() }
                override fun onSeekTo(pos: Long) { mediaPlayer?.seekTo(pos.toInt()) }
            })
            isActive = true
        }
    }

    private fun updateMediaSession() {
        val song = currentSong ?: return
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.displayTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.displayArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .build()
        mediaSession?.setMetadata(metadata)

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0, 1f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val song = currentSong ?: return

        val playPauseIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val nextIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_NEXT
        }
        val prevIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_PREVIOUS
        }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.displayTitle)
            .setContentText(song.displayArtist)
            .setSubText(song.album)
            .setSmallIcon(R.drawable.ic_music_note)
            .addAction(
                R.drawable.ic_skip_previous,
                "Previous",
                PendingIntent.getBroadcast(this, 0, prevIntent, flag)
            )
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                PendingIntent.getBroadcast(this, 1, playPauseIntent, flag)
            )
            .addAction(
                R.drawable.ic_skip_next,
                "Next",
                PendingIntent.getBroadcast(this, 2, nextIntent, flag)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}