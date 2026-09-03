package com.jnet.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.jnet.musicplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    interface SongsConsumer {
        fun onLibraryChanged(songs: List<Song>)
    }

    private lateinit var binding: ActivityMainBinding
    lateinit var musicRepository: MusicRepository
    lateinit var playlistRepository: PlaylistRepository
    lateinit var settingsRepository: SettingsRepository

    var allSongs: List<Song> = emptyList()
        private set

    private val songsConsumers = mutableListOf<SongsConsumer>()

    fun registerSongsConsumer(consumer: SongsConsumer) {
        if (!songsConsumers.contains(consumer)) songsConsumers.add(consumer)
    }

    fun unregisterSongsConsumer(consumer: SongsConsumer) {
        songsConsumers.remove(consumer)
    }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.all { it.value }
        if (granted) {
            loadMusic()
        } else {
            Toast.makeText(this, "Permission required to access music", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        companionInstance = this
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        musicRepository = MusicRepository(this)
        playlistRepository = PlaylistRepository(this)
        settingsRepository = SettingsRepository(this)

        setupViewPager()
        setupMiniPlayer()
        checkPermissionsAndLoad()
        showCrashReportIfAny()
    }

    /** Shows a dialog with the last crash report and a copy-to-clipboard button. */
    private fun showCrashReportIfAny() {
        val report = CrashHandler.readLastReport() ?: return
        CrashHandler.clearReport()
        MaterialAlertDialogBuilder(this)
            .setTitle("Previous crash detected")
            .setMessage("The app crashed last time. Copy the report so we can fix it.")
            .setPositiveButton("Copy Error") { _, _ ->
                val clip = android.content.ClipData.newPlainText(
                    "crash_report",
                    report
                )
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(clip)
                Toast.makeText(this, "Crash report copied to clipboard", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    private fun setupViewPager() {
        val adapter = MusicPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Songs"
                1 -> "Artists"
                2 -> "Albums"
                3 -> "Playlists"
                4 -> "Scan"
                else -> ""
            }
        }.attach()
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            showNowPlaying()
        }
        binding.btnMiniPlayPause.setOnClickListener {
            sendServiceAction(MusicService.ACTION_PLAY_PAUSE)
        }
        binding.btnMiniNext.setOnClickListener {
            sendServiceAction(MusicService.ACTION_NEXT)
        }

        songChangedListener = { updateMiniPlayer() }
        playbackStateListener = {
            updateMiniPlayerPlayButton()
            applyKeepScreen()
        }
        MusicService.addOnSongChanged(songChangedListener)
        MusicService.addOnPlaybackStateChanged(playbackStateListener)
    }

    private var songChangedListener: ((Song?) -> Unit)? = null
    private var playbackStateListener: ((Boolean) -> Unit)? = null

    private fun applyKeepScreen() {
        val keep = settingsRepository.get().keepScreenOn && MusicService.isPlaying
        if (keep) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun showNowPlaying() {
        val bottomSheet = NowPlayingBottomSheet()
        bottomSheet.show(supportFragmentManager, "now_playing")
    }

    private fun updateMiniPlayer() {
        val song = MusicService.currentSong ?: run {
            binding.miniPlayer.visibility = View.GONE
            return
        }
        binding.miniPlayer.visibility = View.VISIBLE
        binding.tvMiniTitle.text = song.displayTitle
        binding.tvMiniArtist.text = song.displayArtist

        val uri = android.content.ContentUris.withAppendedId(
            android.net.Uri.parse("content://media/external/audio/albumart"),
            song.albumId
        )
        com.bumptech.glide.Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.ic_music_note)
            .into(binding.ivMiniAlbumArt)

        updateMiniPlayerPlayButton()
    }

    private fun updateMiniPlayerPlayButton() {
        binding.btnMiniPlayPause.setImageResource(
            if (MusicService.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkPermissionsAndLoad() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            permissionRequest.launch(permissions)
        } else {
            loadMusic()
        }
    }

    private fun loadMusic() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            allSongs = withContext(Dispatchers.IO) {
                val songs = musicRepository.getAllSongs()
                if (songs.isEmpty() && settingsRepository.get().scanOnStartup) {
                    musicRepository.scanForNewMusic()
                    musicRepository.getAllSongs()
                } else {
                    songs
                }
            }
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                pushSongsToConsumers()
                if (allSongs.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No music found - tap Scan to search", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun pushSongsToConsumers() {
        songsConsumers.toList().forEach { it.onLibraryChanged(allSongs) }
    }

    fun refreshLibraryAfterScan() {
        lifecycleScope.launch {
            allSongs = withContext(Dispatchers.IO) { musicRepository.getAllSongs() }
            withContext(Dispatchers.Main) { pushSongsToConsumers() }
        }
    }

    fun playSong(songList: List<Song>, index: Int) {
        // Pass the queue in-process (companion) instead of through the intent
        // binder parcel to avoid TransactionTooLargeException on big libraries.
        MusicService.setQueue(songList)
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_QUEUE
            putExtra(MusicService.EXTRA_SONG_INDEX, index)
            putExtra(MusicService.EXTRA_SHUFFLE, MusicService.shuffleEnabled)
        }
        startService(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_about -> {
                val aboutFragment = AboutFragment()
                aboutFragment.show(supportFragmentManager, "about")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (companionInstance === this) companionInstance = null
        songChangedListener?.let { MusicService.removeOnSongChanged(it) }
        playbackStateListener?.let { MusicService.removeOnPlaybackStateChanged(it) }
    }

    companion object {
        @Volatile
        private var companionInstance: MainActivity? = null

        fun refreshKeepScreenOn() {
            companionInstance?.applyKeepScreen()
        }
    }
}