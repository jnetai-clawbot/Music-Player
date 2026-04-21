package com.jnet.musicplayer

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MusicPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount() = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LibraryFragment()
            1 -> ArtistListFragment()
            2 -> AlbumListFragment()
            3 -> PlaylistListFragment()
            else -> LibraryFragment()
        }
    }
}