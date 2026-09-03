package com.jnet.musicplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    private val githubLatestReleaseUrl = "https://github.com/jnetai-clawbot/Music-Player/releases/latest"
    private var latestTag: String? = null
    private var latestHtmlUrl: String = githubLatestReleaseUrl

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAppName.text = "JNet Music Player"
        binding.tvMadeBy.text = "Made by jnetai.com"

        // Local version (versionName) - the tag "vX.Y.Z" matches the GitHub release tag
        val localVersion = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName?.let { if (it.startsWith("v")) it else "v$it" } ?: "v1.1.0"
        } catch (e: Exception) {
            "v1.1.0"
        }
        binding.tvVersion.text = "Version $localVersion"

        binding.btnCheckUpdates.setOnClickListener { checkForUpdates() }
        binding.btnShare.setOnClickListener { shareLatestRelease() }
        binding.btnGitHub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubLatestReleaseUrl)))
        }

        // Pre-fetch latest release so the version footer and share are accurate,
        // and the update button has data when tapped.
        CoroutineScope(Dispatchers.IO).launch {
            val tag = fetchLatestTag()
            withContext(Dispatchers.Main) {
                if (tag != null && tag.isNotBlank()) {
                    latestTag = tag
                    binding.tvVersion.text = "Version $localVersion · Latest $tag"
                }
            }
        }
    }

    private fun checkForUpdates() {
        binding.btnCheckUpdates.isEnabled = false
        binding.btnCheckUpdates.text = "Checking..."
        CoroutineScope(Dispatchers.IO).launch {
            val fetched = fetchLatestTag()
            withContext(Dispatchers.Main) {
                val latest = latestTag ?: fetched
                binding.btnCheckUpdates.isEnabled = true
                binding.btnCheckUpdates.text = "Check for Updates"
                if (latest.isNullOrBlank()) {
                    Toast.makeText(
                        requireContext(),
                        "Could not check for updates. Check your internet connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@withContext
                }
                val current = try {
                    requireContext().packageManager
                        .getPackageInfo(requireContext().packageName, 0)
                        .versionName?.let { if (it.startsWith("v")) it else "v$it" } ?: ""
                } catch (e: Exception) {
                    ""
                }
                if (isNewer(latest, current)) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Update available")
                        .setMessage("A new version ($latest) is available.\nOpen it on GitHub?")
                        .setPositiveButton("Open on GitHub") { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestHtmlUrl)))
                        }
                        .setNegativeButton("Not now", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "You're on the latest version!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareLatestRelease() {
        val text = "JNet Music Player (latest release) - $latestHtmlUrl"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "JNet Music Player")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, "Share latest release"))
    }

    /** Fetches latest release tag_name + html_url. */
    private fun fetchLatestTag(): String? = try {
        val url = URL("https://api.github.com/repos/jnetai-clawbot/Music-Player/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        latestHtmlUrl = json.optString("html_url", githubLatestReleaseUrl)
        json.optString("tag_name", "").ifBlank { null }
    } catch (e: Exception) {
        null
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestNum = latest.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val currentNum = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(latestNum.size, currentNum.size)) {
            val l = latestNum.getOrElse(i) { 0 }
            val c = currentNum.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}