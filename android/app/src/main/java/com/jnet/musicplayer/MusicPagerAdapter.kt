package com.jnet.musicplayer

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MusicPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    private val fragments = listOf(
        LibraryFragment(),
        ArtistListFragment(),
        AlbumListFragment(),
        PlaylistListFragment()
    )

    override fun getItemCount() = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun updateSongs(songs: List<Song>) {
        (fragments[0] as? LibraryFragment)?.updateSongs(songs)
        (fragments[1] as? ArtistListFragment)?.updateSongs(songs)
        (fragments[2] as? AlbumListFragment)?.updateSongs(songs)
    }

    fun updateSearchResults(songs: List<Song>) {
        (fragments[0] as? LibraryFragment)?.updateSongs(songs)
    }
}