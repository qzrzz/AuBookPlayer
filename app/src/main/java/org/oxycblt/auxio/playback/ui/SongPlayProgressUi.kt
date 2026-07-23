/*
 * Copyright (c) 2026 Auxio Project
 * SongPlayProgressUi.kt is part of Auxio.
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

package org.oxycblt.auxio.playback.ui

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.R as MR
import org.oxycblt.auxio.playback.SongPlayProgressStore
import org.oxycblt.auxio.util.getAttrColorCompat
import org.oxycblt.musikr.Song

/**
 * Material You presentation of per-song listen progress on list rows.
 *
 * - Soft full-row grey fill ([colorSurfaceContainerHighest]).
 * - 2dp bottom track in theme [colorPrimary], rounded ends, slightly deeper when completed.
 * - Completed (≥80%): full bars + disabled (greyed) title / subtitle / cover.
 */
object SongPlayProgressUi {
    private const val MIN_VISIBLE_FRACTION = 0.02f

    /**
     * Apply [store] progress for [song] onto the row chrome.
     *
     * @param progressView Full-row soft fill **or** legacy single progress view.
     * @param nameView Primary title (disabled when completed).
     * @param infoView Secondary line (disabled when completed).
     * @param coverView Optional cover / track art (disabled when completed).
     * @param progressBar Optional 2dp bottom bar with rounded ends.
     */
    fun bind(
        song: Song,
        store: SongPlayProgressStore,
        progressView: View,
        nameView: TextView,
        infoView: TextView,
        coverView: View? = null,
        progressBar: View? = null,
    ) {
        val fraction = store.fraction(song)
        val completed = fraction >= SongPlayProgressStore.COMPLETED_FRACTION
        val visible = fraction >= MIN_VISIBLE_FRACTION
        val scale = fraction.coerceIn(0f, 1f)

        // --- Soft full-row fill (neutral grey) ---
        progressView.isVisible = visible
        if (visible) {
            progressView.pivotX = 0f
            progressView.pivotY = progressView.height / 2f
            progressView.scaleX = scale
            val fillColor =
                progressView.context
                    .getAttrColorCompat(MR.attr.colorSurfaceContainerHighest)
                    .defaultColor
            val fillAlpha = if (completed) 0xCC else 0xB0
            progressView.background =
                GradientDrawable().apply {
                    setColor((fillAlpha shl 24) or (fillColor and 0x00FFFFFF))
                }
        }

        // --- 2dp bottom theme-colored bar with pill ends ---
        if (progressBar != null) {
            progressBar.isVisible = visible
            if (visible) {
                progressBar.pivotX = 0f
                progressBar.pivotY = progressBar.height / 2f
                progressBar.scaleX = scale
                val barColor =
                    progressBar.context
                        .getAttrColorCompat(androidx.appcompat.R.attr.colorPrimary)
                        .defaultColor
                // Theme primary at 50% opacity.
                val barAlpha = 0x80
                val radiusPx =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        1f,
                        progressBar.resources.displayMetrics,
                    )
                progressBar.background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = radiusPx
                        setColor((barAlpha shl 24) or (barColor and 0x00FFFFFF))
                    }
            }
        }

        // Greyscale completed tracks (existing selectors already fade disabled text).
        nameView.isEnabled = !completed
        infoView.isEnabled = !completed
        coverView?.isEnabled = !completed
    }
}
