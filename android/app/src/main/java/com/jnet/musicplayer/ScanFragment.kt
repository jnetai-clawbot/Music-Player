package com.jnet.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jnet.musicplayer.databinding.FragmentScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnScan.setOnClickListener { runScan() }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        updateSummary()
        updateLibraryCount()
    }

    override fun onResume() {
        super.onResume()
        updateSummary()
        updateLibraryCount()
    }

    private fun updateSummary() {
        val mainActivity = activity as? MainActivity ?: return
        val settings = mainActivity.settingsRepository.get()
        val parts = mutableListOf<String>()
        parts += if (settings.mp3Only) "MP3 only" else "All audio"
        parts += "≥ ${formatLen(settings.minTrackLengthSec)}"
        parts += if (settings.includePaths.isEmpty()) "all folders" else "only ${settings.includePaths.size} included folder(s)"
        if (settings.excludePaths.isNotEmpty()) parts += "skipping ${settings.excludePaths.size} folder(s)"
        binding.tvSummary.text = parts.joinToString(" · ")
    }

    private fun updateLibraryCount() {
        val mainActivity = activity as? MainActivity ?: return
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                mainActivity.musicRepository.getLibraryCount()
            }
            binding.tvLibrarySize.text = "Library: $count song(s)"
        }
    }

    private fun runScan() {
        val mainActivity = activity as? MainActivity ?: return
        binding.btnScan.isEnabled = false
        binding.progressIndicator.visibility = View.VISIBLE
        binding.tvResult.text = ""

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                mainActivity.musicRepository.scanForNewMusic()
            }
            val message = buildString {
                append("Added ${result.added} new\n")
                append("Already in library: ${result.alreadyInLibrary}\n")
                append("Removed: ${result.removed}")
            }
            binding.tvResult.text = message
            binding.btnScan.isEnabled = true
            binding.progressIndicator.visibility = View.GONE
            updateLibraryCount()
            mainActivity.refreshLibraryAfterScan()
            Toast.makeText(
                requireContext(),
                "Scan complete: ${result.added} new, ${result.removed} removed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun formatLen(sec: Int): String = if (sec < 60) "$sec sec" else "${sec / 60} min"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}