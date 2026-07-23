/*
 * Copyright (c) 2026 Auxio Project
 * QueueSortDialog.kt is part of Auxio.
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

import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.list.sort.SortDialog
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist

/**
 * 一个 [SortDialog]，用于根据当前播放的 Context（Album、Folder、Artist、Genre、Playlist 或全曲库）
 * 动态显示并切换对应的 [Sort] 规则。
 */
@AndroidEntryPoint
class QueueSortDialog : SortDialog() {
    private val playbackModel: PlaybackViewModel by activityViewModels()
    @Inject lateinit var listSettings: ListSettings

    override fun getInitialSort(): Sort {
        val parent = playbackModel.parent.value
        val folder = playbackModel.activeFolder.value
        return when {
            parent is Album -> listSettings.albumSongSort
            folder != null -> listSettings.folderSongSort
            parent is Artist -> listSettings.artistSongSort
            parent is Genre -> listSettings.genreSongSort
            parent is Playlist -> listSettings.playlistSort
            else -> listSettings.songSort
        }
    }

    override fun applyChosenSort(sort: Sort) {
        val parent = playbackModel.parent.value
        val folder = playbackModel.activeFolder.value
        when {
            parent is Album -> listSettings.albumSongSort = sort
            folder != null -> listSettings.folderSongSort = sort
            parent is Artist -> listSettings.artistSongSort = sort
            parent is Genre -> listSettings.genreSongSort = sort
            parent is Playlist -> listSettings.playlistSort = sort
            else -> listSettings.songSort = sort
        }
    }

    override fun getModeChoices(): List<Sort.Mode> {
        val parent = playbackModel.parent.value
        return if (parent is Album) {
            listOf(Sort.Mode.ByDisc, Sort.Mode.ByTrack, Sort.Mode.ByName, Sort.Mode.ByDuration)
        } else {
            listOf(
                Sort.Mode.ByName,
                Sort.Mode.ByArtist,
                Sort.Mode.ByAlbum,
                Sort.Mode.ByDate,
                Sort.Mode.ByDuration,
            )
        }
    }
}
