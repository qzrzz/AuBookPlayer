/*
 * Copyright (c) 2026 Auxio Project
 * SongFolder.kt is part of Auxio.
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

package org.oxycblt.auxio.music

import org.oxycblt.musikr.Song
import org.oxycblt.musikr.fs.Path

/**
 * A group of [Song]s that share the same parent directory on disk.
 *
 * This is an app-level library view (not part of Musikr's MusicParent hierarchy). It lets the home
 * "Folders" tab list songs by source folder.
 *
 * @param path The shared parent directory of [songs].
 * @param songs Songs contained in this folder (non-empty).
 */
data class SongFolder(val path: Path, val songs: List<Song>) {
    /** Stable key for navigation / lookup. */
    val key: String
        get() = path.toString()

    /** Display name (last path component), or a fallback for the volume root. */
    val name: String
        get() = path.name ?: path.toString()

    val durationMs: Long
        get() = songs.sumOf { it.durationMs }

    companion object {
        /**
         * Group [songs] by their parent directory and return folders sorted by name.
         */
        fun fromSongs(songs: Collection<Song>): List<SongFolder> =
            songs
                .groupBy { it.path.directory }
                .map { (dir, folderSongs) -> SongFolder(dir, folderSongs) }
                .sortedBy { it.name.lowercase() }
    }
}
