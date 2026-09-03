package com.jnet.musicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jnet.musicplayer.databinding.ItemSettingsHeaderBinding
import com.jnet.musicplayer.databinding.ItemSettingsSwitchBinding
import com.jnet.musicplayer.databinding.ItemSettingsValueBinding

sealed class SettingsRow {
    data class Header(val title: String) : SettingsRow()
    data class Switch(
        val id: String,
        val title: String,
        val checked: Boolean,
        val onChanged: (Boolean) -> Unit
    ) : SettingsRow()

    data class Value(
        val id: String,
        val title: String,
        val value: String,
        val onTap: () -> Unit
    ) : SettingsRow()
}

class SettingsAdapter(
    private val rows: List<SettingsRow>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val filtered = rows.toMutableList()

    fun setFilter(query: String) {
        val q = query.trim().lowercase()
        filtered.clear()
        if (q.isEmpty()) {
            filtered.addAll(rows)
        } else {
            rows.forEach { row ->
                when (row) {
                    is SettingsRow.Switch -> if (row.title.lowercase().contains(q)) filtered.add(row)
                    is SettingsRow.Value -> if (row.title.lowercase().contains(q)) filtered.add(row)
                    is SettingsRow.Header -> Unit
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (filtered[position]) {
        is SettingsRow.Header -> TYPE_HEADER
        is SettingsRow.Switch -> TYPE_SWITCH
        is SettingsRow.Value -> TYPE_VALUE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemSettingsHeaderBinding.inflate(inflater, parent, false))
            TYPE_SWITCH -> SwitchVH(ItemSettingsSwitchBinding.inflate(inflater, parent, false))
            else -> ValueVH(ItemSettingsValueBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = filtered[position]) {
            is SettingsRow.Header -> (holder as HeaderVH).bind(row)
            is SettingsRow.Switch -> (holder as SwitchVH).bind(row)
            is SettingsRow.Value -> (holder as ValueVH).bind(row)
        }
    }

    override fun getItemCount(): Int = filtered.size

    class HeaderVH(private val binding: ItemSettingsHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SettingsRow.Header) {
            binding.tvHeader.text = row.title
        }
    }

    class SwitchVH(private val binding: ItemSettingsSwitchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SettingsRow.Switch) {
            binding.tvTitle.text = row.title
            binding.switchControl.isChecked = row.checked
            binding.switchControl.setOnClickListener {
                row.onChanged(binding.switchControl.isChecked)
            }
        }
    }

    class ValueVH(private val binding: ItemSettingsValueBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SettingsRow.Value) {
            binding.tvTitle.text = row.title
            binding.tvValue.text = row.value
            binding.root.setOnClickListener { row.onTap() }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SWITCH = 1
        const val TYPE_VALUE = 2
    }
}