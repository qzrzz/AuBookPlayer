/*
 * Copyright (c) 2026 Auxio Project
 * CoverResolve.kt is part of Auxio.
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

package org.oxycblt.auxio.image

import android.content.Context
import androidx.annotation.ColorInt
import org.oxycblt.auxio.R
import org.oxycblt.auxio.image.coil.StackCoverComposition
import org.oxycblt.auxio.image.coil.TextCoverRequest
import org.oxycblt.auxio.music.SongFolder
import org.oxycblt.auxio.util.getColorCompat
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.covers.CoverCollection

/**
 * Coil request data for a song cover, falling back to the playlist cover when the song has none.
 *
 * @param cornerRadiusRatio Relative corner radius for stack compositions (0–1).
 * @param customCover Optional user-chosen playlist cover used as fallback.
 */
fun resolveSongCoverData(
    context: Context,
    song: Song,
    fallbackPlaylist: Playlist?,
    cornerRadiusRatio: Float = 0f,
    customCover: PlaylistCustomCover? = null,
): Any? {
    song.cover?.let {
        return it
    }
    val playlist = fallbackPlaylist ?: return null
    return resolvePlaylistCoverData(
        context,
        playlist,
        cornerRadiusRatio,
        customCover = customCover,
    )
}

/**
 * Coil request data for a playlist cover:
 * 1. User-chosen custom cover if present
 * 2. Stacked album art when songs have covers
 * 3. Generated text cover from shared title words
 */
fun resolvePlaylistCoverData(
    context: Context,
    playlist: Playlist,
    cornerRadiusRatio: Float = 0f,
    @ColorInt backgroundColor: Int = context.coverBackgroundColor(),
    @ColorInt foregroundColor: Int = context.coverForegroundColor(),
    customCover: PlaylistCustomCover? = null,
): Any {
    customCover?.let {
        return it
    }
    if (playlist.covers.covers.isNotEmpty()) {
        return StackCoverComposition(
            playlist.covers,
            cornerRadiusRatio,
            playlist.uid.toString().hashCode(),
            backgroundColor,
        )
    }
    return playlistTextCoverRequest(playlist, backgroundColor, foregroundColor)
}

/** Build a [TextCoverRequest] for a playlist with no album art. */
fun playlistTextCoverRequest(
    playlist: Playlist,
    @ColorInt backgroundColor: Int,
    @ColorInt foregroundColor: Int,
): TextCoverRequest {
    val titles =
        playlist.songs
            .asSequence()
            .take(10)
            .map { it.name.raw }
            .filter { it.isNotBlank() }
            .toList()
    val text =
        derivePlaylistCoverText(titles).ifEmpty {
            playlist.name.raw.filter { it.isLetterOrDigit() }.take(4).ifEmpty {
                playlist.name.raw.take(4).ifEmpty { "?" }
            }
        }
    return TextCoverRequest(
        text = text,
        seed = playlist.uid.toString().hashCode(),
        backgroundColor = backgroundColor,
        foregroundColor = foregroundColor,
    )
}

/**
 * Coil request data for a folder cover:
 * 1. User-chosen custom cover if present
 * 2. Stacked album art when songs have covers
 * 3. Generated text cover from shared title words / folder name
 */
fun resolveFolderCoverData(
    context: Context,
    folder: SongFolder,
    cornerRadiusRatio: Float = 0f,
    @ColorInt backgroundColor: Int = context.coverBackgroundColor(),
    @ColorInt foregroundColor: Int = context.coverForegroundColor(),
    customCover: FolderCustomCover? = null,
): Any {
    customCover?.let {
        return it
    }
    val covers = CoverCollection.from(folder.songs.mapNotNull { it.cover })
    if (covers.covers.isNotEmpty()) {
        return StackCoverComposition(
            covers,
            cornerRadiusRatio,
            folder.key.hashCode(),
            backgroundColor,
        )
    }
    return folderTextCoverRequest(folder, backgroundColor, foregroundColor)
}

/** Build a [TextCoverRequest] for a folder with no album art. */
fun folderTextCoverRequest(
    folder: SongFolder,
    @ColorInt backgroundColor: Int,
    @ColorInt foregroundColor: Int,
): TextCoverRequest {
    val titles =
        folder.songs
            .asSequence()
            .take(10)
            .map { it.name.raw }
            .filter { it.isNotBlank() }
            .toList()
    val text =
        derivePlaylistCoverText(titles).ifEmpty {
            folder.name.filter { it.isLetterOrDigit() }.take(4).ifEmpty {
                folder.name.take(4).ifEmpty { "?" }
            }
        }
    return TextCoverRequest(
        text = text,
        seed = folder.key.hashCode(),
        backgroundColor = backgroundColor,
        foregroundColor = foregroundColor,
    )
}

fun Context.coverBackgroundColor(): Int = getColorCompat(R.color.sel_cover_bg).defaultColor

fun Context.coverForegroundColor(): Int = getColorCompat(R.color.sel_on_cover_bg).defaultColor
