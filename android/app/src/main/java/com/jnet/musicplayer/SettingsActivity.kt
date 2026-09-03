package com.jnet.musicplayer

import android.os.Bundle
import android.text.Editable
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.jnet.musicplayer.databinding.ActivitySettingsBinding
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var adapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepo = SettingsRepository(this)
        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = SettingsAdapter(buildRows())
        binding.settingsList.layoutManager = LinearLayoutManager(this)
        binding.settingsList.adapter = adapter

        binding.searchInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    adapter.setFilter(s?.toString().orEmpty())
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val filter = binding.searchInput.text?.toString().orEmpty()
        adapter = SettingsAdapter(buildRows())
        binding.settingsList.adapter = adapter
        adapter.setFilter(filter)
    }

    private fun buildRows(): List<SettingsRow> {
        val s = settingsRepo.get()
        val rows = mutableListOf<SettingsRow>()

        rows.add(SettingsRow.Header("Playback"))

        rows.add(
            SettingsRow.Switch(
                id = "crossfade",
                title = "Crossfade",
                checked = s.crossfadeEnabled
            ) { on ->
                settingsRepo.save(settingsRepo.get().copy(crossfadeEnabled = on))
            }
        )

        rows.add(
            SettingsRow.Value(
                id = "crossfade_duration",
                title = "Crossfade duration",
                value = "${s.crossfadeDurationSec} sec"
            ) {
                showSliderDialog(
                    title = "Crossfade duration",
                    min = 1f,
                    max = 10f,
                    step = 1f,
                    current = s.crossfadeDurationSec.toFloat(),
                    format = { "${it.roundToInt()} sec" }
                ) { value ->
                    settingsRepo.save(settingsRepo.get().copy(crossfadeDurationSec = value.roundToInt()))
                    refresh()
                }
            }
        )

        rows.add(
            SettingsRow.Switch(
                id = "auto_repeat",
                title = "Auto repeat playlist",
                checked = s.autoRepeatEnabled
            ) { on ->
                settingsRepo.save(settingsRepo.get().copy(autoRepeatEnabled = on))
            }
        )

        rows.add(
            SettingsRow.Value(
                id = "playback_speed",
                title = "Playback speed",
                value = "${s.playbackSpeed}x"
            ) {
                val speeds = arrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                val idx = speeds.indexOfFirst { it == s.playbackSpeed }.coerceAtLeast(0)
                MaterialAlertDialogBuilder(this)
                    .setTitle("Playback speed")
                    .setSingleChoiceItems(speeds.map { "${it}x" }.toTypedArray(), idx) { dlg, which ->
                        settingsRepo.save(settingsRepo.get().copy(playbackSpeed = speeds[which]))
                        dlg.dismiss()
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        rows.add(
            SettingsRow.Switch(
                id = "pause_on_unplug",
                title = "Pause when headphones unplugged",
                checked = s.pauseOnUnplug
            ) { on ->
                settingsRepo.save(settingsRepo.get().copy(pauseOnUnplug = on))
            }
        )

        rows.add(
            SettingsRow.Switch(
                id = "keep_screen_on",
                title = "Keep screen on while playing",
                checked = s.keepScreenOn
            ) { on ->
                settingsRepo.save(settingsRepo.get().copy(keepScreenOn = on))
                MainActivity.refreshKeepScreenOn()
            }
        )

        val ignoringBattery = BatteryOptimization.isIgnoringBatteryOptimizations(this)
        rows.add(
            SettingsRow.Value(
                id = "background_playback",
                title = "Keep playing in the background",
                value = if (ignoringBattery) "Allowed" else "Not allowed - tap to allow"
            ) {
                if (BatteryOptimization.requestIgnoreBatteryOptimizations(this)) {
                    Toast.makeText(
                        this,
                        "Choose \"Allow\" on the next screen to let music keep playing",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Open battery settings to allow playback", Toast.LENGTH_LONG).show()
                }
            }
        )

        rows.add(SettingsRow.Header("Library Scanning"))

        rows.add(
            SettingsRow.Switch(
                id = "mp3_only",
                title = "Only find MP3 files",
                checked = s.mp3Only
            ) { on ->
                settingsRepo.save(settingsRepo.get().copy(mp3Only = on))
            }
        )

        rows.add(
            SettingsRow.Value(
                id = "min_length",
                title = "Minimum track length",
                value = formatMinLength(s.minTrackLengthSec)
            ) {
                val options = listOf(15, 30, 60, 120, 300, 600)
                val labels = options.map { formatMinLength(it) }.toTypedArray()
                val idx = options.indexOf(s.minTrackLengthSec).coerceAtLeast(0)
                MaterialAlertDialogBuilder(this)
                    .setTitle("Minimum track length")
                    .setSingleChoiceItems(labels, idx) { dlg, which ->
                        settingsRepo.save(settingsRepo.get().copy(minTrackLengthSec = options[which]))
                        dlg.dismiss()
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        rows.add(
            SettingsRow.Value(
                id = "include_paths",
                title = "Only scan these folders (optional)",
                value = if (s.includePaths.isEmpty()) "All folders" else s.includePaths.size.toString() + " folder(s)"
            ) {
                showPathsDialog(
                    title = "Scan only these folders",
                    hint = "One absolute folder path per line, e.g.\n/storage/emulated/0/Music",
                    current = s.includePaths
                ) { paths ->
                    settingsRepo.save(settingsRepo.get().copy(includePaths = paths))
                    refresh()
                }
            }
        )

        rows.add(
            SettingsRow.Value(
                id = "exclude_paths",
                title = "Skip these folders (optional)",
                value = if (s.excludePaths.isEmpty()) "None" else s.excludePaths.size.toString() + " folder(s)"
            ) {
                showPathsDialog(
                    title = "Skip these folders",
                    hint = "One absolute folder path per line, e.g.\n/storage/emulated/0/Recordings",
                    current = s.excludePaths
                ) { paths ->
                    settingsRepo.save(settingsRepo.get().copy(excludePaths = paths))
                    refresh()
                }
            }
        )

        rows.add(
            SettingsRow.Switch(
                id = "scan_on_startup",
                title = "Scan on app start",
                checked = s.scanOnStartup
            ) { on ->
                settingsRepo.save(settingsRepo.get().copy(scanOnStartup = on))
            }
        )

        return rows
    }

    private fun formatMinLength(sec: Int): String = when {
        sec < 60 -> "$sec sec"
        sec % 60 == 0 -> "${sec / 60} min"
        else -> sec.toString() + " sec"
    }

    private fun showSliderDialog(
        title: String,
        min: Float,
        max: Float,
        step: Float,
        current: Float,
        format: (Float) -> String,
        onSelected: (Float) -> Unit
    ) {
        val slider = Slider(this).apply {
            valueFrom = min
            valueTo = max
            stepSize = step
            value = current.coerceIn(min, max)
        }
        val preview = android.widget.TextView(this).apply {
            text = format(current)
            setTextColor(resources.getColor(R.color.md_theme_onSurface, null))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
        }
        slider.addOnChangeListener { _, value, _ ->
            preview.text = format(value)
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16)
            addView(preview)
            addView(slider)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("OK") { _, _ -> onSelected(slider.value) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPathsDialog(
        title: String,
        hint: String,
        current: List<String>,
        onSaved: (List<String>) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(current.joinToString("\n"))
            setHint(hint)
            isSingleLine = false
            minLines = 4
            gravity = android.view.Gravity.TOP
        }
        val scroll = android.widget.ScrollView(this).apply {
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val paths = input.text.toString()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                onSaved(paths)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}