package com.jnet.musicplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jnet.musicplayer.databinding.FragmentAboutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AboutFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get version from PackageInfo
        try {
            val packageInfo = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
            binding.tvVersion.text = "Version ${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            binding.tvVersion.text = "Version 1.0.0"
        }

        binding.tvAppName.text = "JNet Music Player"
        binding.tvDescription.text = "A simple, beautiful music player for your local music library."

        // GitHub link
        binding.btnGitHub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jnetai-clawbot/Music-Player"))
            startActivity(intent)
        }

        // Check for updates
        binding.btnCheckUpdates.setOnClickListener {
            checkForUpdates()
        }

        // Share
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "JNet Music Player")
                putExtra(Intent.EXTRA_TEXT, "Check out JNet Music Player! https://github.com/jnetai-clawbot/Music-Player")
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }
    }

    private fun checkForUpdates() {
        binding.btnCheckUpdates.isEnabled = false
        binding.btnCheckUpdates.text = "Checking..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.github.com/repos/jnetai-clawbot/Music-Player/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val latestVersion = json.optString("tag_name", "unknown")
                val htmlUrl = json.optString("html_url", "")
                val releaseName = json.optString("name", latestVersion)

                withContext(Dispatchers.Main) {
                    try {
                        val packageInfo = requireContext().packageManager
                            .getPackageInfo(requireContext().packageName, 0)
                        val currentVersion = packageInfo.versionName

                        if (latestVersion != currentVersion && latestVersion != "unknown") {
                            Toast.makeText(
                                requireContext(),
                                "Update available: $releaseName\nDownload from GitHub",
                                Toast.LENGTH_LONG
                            ).show()
                            // Open release page
                            if (htmlUrl.isNotEmpty()) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)))
                            }
                        } else {
                            Toast.makeText(requireContext(), "You're on the latest version!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Version check complete", Toast.LENGTH_SHORT).show()
                    }
                    binding.btnCheckUpdates.isEnabled = true
                    binding.btnCheckUpdates.text = "Check for Updates"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Could not check for updates. Check your internet connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.btnCheckUpdates.isEnabled = true
                    binding.btnCheckUpdates.text = "Check for Updates"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}