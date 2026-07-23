/*
 * Copyright (c) 2021 Auxio Project
 * QueueAdapter.kt is part of Auxio.
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

package org.oxycblt.auxio.playback.queue

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MR
import com.google.android.material.shape.MaterialShapeDrawable
import org.oxycblt.auxio.databinding.ItemEditableSongBinding
import org.oxycblt.auxio.list.ClickableListListener
import org.oxycblt.auxio.list.adapter.FlexibleListAdapter
import org.oxycblt.auxio.list.adapter.PlayingIndicatorAdapter
import org.oxycblt.auxio.list.recycler.SongViewHolder
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.music.resolveNames
import org.oxycblt.auxio.playback.SongPlayProgressStore
import org.oxycblt.auxio.playback.ui.SongPlayProgressUi
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.getAttrColorCompat
import org.oxycblt.auxio.util.inflater
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A [RecyclerView.Adapter] that shows the playback queue (order is fixed; no drag reorder).
 *
 * Greyscale / progress fill comes from shared [SongPlayProgressStore], not queue position.
 */
class QueueAdapter(
    private val listener: ClickableListListener<Song>,
    private val playProgressStore: SongPlayProgressStore,
) : FlexibleListAdapter<Song, QueueSongViewHolder>(QueueSongViewHolder.DIFF_CALLBACK) {
    private var currentIndex = 0
    private var isPlaying = false
    /** When set, songs without cover art fall back to this playlist's cover. */
    var fallbackPlaylist: Playlist? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        QueueSongViewHolder.from(parent)

    override fun onBindViewHolder(holder: QueueSongViewHolder, position: Int) =
        throw IllegalStateException()

    override fun onBindViewHolder(
        viewHolder: QueueSongViewHolder,
        position: Int,
        payload: List<Any>,
    ) {
        if (payload.isEmpty() || payload.contains(PAYLOAD_PROGRESS)) {
            viewHolder.bind(getItem(position), listener, fallbackPlaylist, playProgressStore)
        }
        viewHolder.updatePlayingIndicator(position == currentIndex, isPlaying)
    }

    /**
     * Set the position of the currently playing item in the queue.
     *
     * @param index The position of the currently playing item in the queue.
     * @param isPlaying Whether playback is ongoing or paused.
     */
    fun setPosition(index: Int, isPlaying: Boolean) {
        L.d("Updating index")
        val lastIndex = currentIndex
        currentIndex = index
        // Only refresh playing indicator on the previous + current rows.
        if (lastIndex in 0 until itemCount) {
            notifyItemChanged(lastIndex, PAYLOAD_PLAYING)
        }
        if (currentIndex in 0 until itemCount && currentIndex != lastIndex) {
            notifyItemChanged(currentIndex, PAYLOAD_PLAYING)
        }
        this.isPlaying = isPlaying
    }

    /** Rebind a single song when its shared listen progress changes. */
    fun notifyProgressChanged(songUid: Music.UID) {
        val index = currentList.indexOfFirst { it.uid == songUid }
        if (index >= 0) {
            notifyItemChanged(index, PAYLOAD_PROGRESS)
        }
    }

    private companion object {
        val PAYLOAD_PLAYING = Any()
        val PAYLOAD_PROGRESS = Any()
    }
}

/**
 * A [PlayingIndicatorAdapter.ViewHolder] that displays a queue [Song].
 */
class QueueSongViewHolder private constructor(private val binding: ItemEditableSongBinding) :
    PlayingIndicatorAdapter.ViewHolder(binding.root) {
    private val liftableBackground =
        MaterialShapeDrawable.createWithElevationOverlay(binding.root.context).apply {
            fillColor = binding.context.getAttrColorCompat(MR.attr.colorSurfaceContainerHighest)
            alpha = 0
        }

    private val roundableBackground: Drawable =
        MaterialShapeDrawable.createWithElevationOverlay(binding.context).apply {
            fillColor = binding.context.getAttrColorCompat(MR.attr.colorSurfaceContainerHigh)
        }

    init {
        binding.body.background = LayerDrawable(arrayOf(roundableBackground, liftableBackground))
        binding.songDragHandle.isVisible = false
        binding.songMenu.isVisible = false
        binding.background.isInvisible = true
        pinTextToEnd()
    }

    private fun pinTextToEnd() {
        fun ConstraintLayout.LayoutParams.pinEndToParent() {
            endToStart = ConstraintLayout.LayoutParams.UNSET
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        (binding.songName.layoutParams as ConstraintLayout.LayoutParams).apply {
            pinEndToParent()
            binding.songName.layoutParams = this
        }
        (binding.songInfo.layoutParams as ConstraintLayout.LayoutParams).apply {
            pinEndToParent()
            binding.songInfo.layoutParams = this
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun bind(
        song: Song,
        listener: ClickableListListener<Song>,
        fallbackPlaylist: Playlist? = null,
        playProgressStore: SongPlayProgressStore,
    ) {
        listener.bind(song, this, binding.body)
        binding.songAlbumCover.bind(song, fallbackPlaylist)
        binding.songName.text = song.name.resolve(binding.context)
        binding.songInfo.text = song.artists.resolveNames(binding.context)
        SongPlayProgressUi.bind(
            song,
            playProgressStore,
            binding.songPlayProgressFill,
            binding.songName,
            binding.songInfo,
            binding.songAlbumCover,
            binding.songPlayProgressBar,
        )
    }

    override fun updatePlayingIndicator(isActive: Boolean, isPlaying: Boolean) {
        binding.interactBody.isSelected = isActive
        binding.songAlbumCover.setPlaying(isPlaying)
    }

    companion object {
        fun from(parent: View) =
            QueueSongViewHolder(ItemEditableSongBinding.inflate(parent.context.inflater))

        val DIFF_CALLBACK = SongViewHolder.DIFF_CALLBACK
    }
}
