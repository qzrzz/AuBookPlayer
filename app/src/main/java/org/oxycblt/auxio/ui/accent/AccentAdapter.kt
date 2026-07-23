/*
 * Copyright (c) 2021 Auxio Project
 * AccentAdapter.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.ui.accent

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.databinding.ItemAccentBinding
import org.oxycblt.auxio.list.ClickableListListener
import org.oxycblt.auxio.util.inflater

/**
 * A [RecyclerView.Adapter] that displays [Accent] choices.
 *
 * @param listener A [ClickableListListener] to bind interactions to.
 * @param useBlackTheme Whether previews should resolve black-theme color roles.
 * @author Alexander Capehart (OxygenCobalt)
 */
class AccentAdapter(
    private val listener: ClickableListListener<Accent>,
    private val useBlackTheme: Boolean = false,
) : RecyclerView.Adapter<AccentViewHolder>() {
    /** The currently selected [Accent]. */
    var selectedAccent: Accent? = null
        private set

    override fun getItemCount() = Accent.MAX

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        AccentViewHolder.from(parent)

    override fun onBindViewHolder(holder: AccentViewHolder, position: Int) =
        throw NotImplementedError()

    override fun onBindViewHolder(
        holder: AccentViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        val item = Accent.from(position)
        if (payloads.isEmpty()) {
            // Not a re-selection, re-bind with new data.
            holder.bind(item, listener, useBlackTheme)
        }
        holder.setSelected(item == selectedAccent)
    }

    /**
     * Update the currently selected [Accent].
     *
     * @param accent The new [Accent] to select.
     */
    fun setSelectedAccent(accent: Accent) {
        if (accent == selectedAccent) {
            // Nothing to do.
            return
        }

        // Update ViewHolders for the old selected accent and new selected accent.
        selectedAccent?.let { old -> notifyItemChanged(old.index, PAYLOAD_SELECTION_CHANGED) }
        selectedAccent = accent
        notifyItemChanged(accent.index, PAYLOAD_SELECTION_CHANGED)
    }

    private companion object {
        val PAYLOAD_SELECTION_CHANGED = Any()
    }
}

/**
 * A [RecyclerView.ViewHolder] that displays an [Accent] choice. Use [from] to create an instance.
 *
 * Dual-tone swatch: outer ring = [AccentPreviewColors.primaryContainer] (tonal fills), inner disc =
 * [AccentPreviewColors.primary] (icons / selected controls).
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
class AccentViewHolder private constructor(private val binding: ItemAccentBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private var onPrimary: Int = 0

    /**
     * Bind new data to this instance.
     *
     * @param accent The new [Accent] to bind.
     * @param listener A [ClickableListListener] to bind interactions to.
     * @param useBlackTheme Resolve colors against the black theme variant.
     */
    fun bind(
        accent: Accent,
        listener: ClickableListListener<Accent>,
        useBlackTheme: Boolean = false,
    ) {
        listener.bind(accent, this, binding.accent)
        val colors = accent.previewColors(binding.root.context, useBlackTheme)
        onPrimary = colors.onPrimary

        // Solid oval tints — no MaterialButton theme overlay muting the hue.
        binding.accentOuter.backgroundTintList = ColorStateList.valueOf(colors.primaryContainer)
        binding.accentInner.backgroundTintList = ColorStateList.valueOf(colors.primary)

        binding.accent.contentDescription = binding.root.context.getString(accent.name)
        TooltipCompat.setTooltipText(binding.accent, binding.accent.contentDescription)
    }

    /**
     * Set whether this [Accent] is selected or not.
     *
     * @param isSelected Whether this [Accent] is currently selected.
     */
    fun setSelected(isSelected: Boolean) {
        binding.accentCheck.isVisible = isSelected
        if (isSelected) {
            binding.accentCheck.imageTintList = ColorStateList.valueOf(onPrimary)
        }
    }

    companion object {
        /**
         * Create a new instance.
         *
         * @param parent The parent to inflate this instance from.
         * @return A new instance.
         */
        fun from(parent: View) =
            AccentViewHolder(ItemAccentBinding.inflate(parent.context.inflater))
    }
}
