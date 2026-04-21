package com.jnet.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.jnet.musicplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var musicRepository: MusicRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var pagerAdapter: MusicPagerAdapter

    private var allSongs = emptyList<Song>()

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        musicRepository = MusicRepository(this)
        playlistRepository = PlaylistRepository(this)

        setupViewPager()
        checkPermissionsAndLoad()
    }

    private fun setupViewPager() {
        pagerAdapter = MusicPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Songs"
                1 -> "Artists"
                2 -> "Albums"
                3 -> "Playlists"
                else -> ""
            }
        }.attach()
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
            allSongs = musicRepository.getAllSongs()
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (allSongs.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "No music found on device",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Pass songs to fragments
                pagerAdapter.updateSongs(allSongs)
            }
        }
    }

    fun getAllSongs() = allSongs

    fun getMusicRepository() = musicRepository
    fun getPlaylistRepository() = playlistRepository

    fun playSong(songList: List<Song>, index: Int) {
        val intent = Intent(this, MusicService::class.java).apply {
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, index)
            putExtra(MusicService.EXTRA_SHUFFLE, MusicService.shuffleEnabled)
        }
        startService(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView

        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    pagerAdapter.updateSongs(allSongs)
                }
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            val results = musicRepository.searchSongs(query)
            pagerAdapter.updateSearchResults(results)
        }
    }

    private fun showAboutDialog() {
        val aboutFragment = AboutFragment()
        aboutFragment.show(supportFragmentManager, "about")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't stop service - let it continue playing
    }
}