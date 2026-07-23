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
import androidx.core.graphics.ColorUtils
import com.google.android.material.R as MR
import org.oxycblt.auxio.R
import org.oxycblt.auxio.image.coil.StackCoverComposition
import org.oxycblt.auxio.image.coil.TextCoverRequest
import org.oxycblt.auxio.music.SongFolder
import org.oxycblt.auxio.util.getAttrColorCompat
import org.oxycblt.auxio.util.getColorCompat
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import org.oxycblt.musikr.covers.CoverCollection
import org.oxycblt.musikr.tag.Name

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
    return playlistTextCoverRequest(context, playlist)
}

/** Build a [TextCoverRequest] for a playlist with no album art. */
fun playlistTextCoverRequest(context: Context, playlist: Playlist): TextCoverRequest {
    val seed = playlist.uid.toString().hashCode()
    val (bg, fg) = context.textCoverPalette(seed)
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
        seed = seed,
        backgroundColor = bg,
        foregroundColor = fg,
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
    return folderTextCoverRequest(context, folder)
}

/** Build a [TextCoverRequest] for a folder with no album art. */
fun folderTextCoverRequest(context: Context, folder: SongFolder): TextCoverRequest {
    val seed = folder.key.hashCode()
    val (bg, fg) = context.textCoverPalette(seed)
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
        seed = seed,
        backgroundColor = bg,
        foregroundColor = fg,
    )
}

/**
 * Coil request data for an album cover:
 * 1. User-chosen custom cover if present
 * 2. Most prominent embedded song cover
 * 3. Generated text cover from album name / song titles
 */
fun resolveAlbumCoverData(
    context: Context,
    album: Album,
    @ColorInt backgroundColor: Int = context.coverBackgroundColor(),
    @ColorInt foregroundColor: Int = context.coverForegroundColor(),
    customCover: AlbumCustomCover? = null,
): Any {
    customCover?.let {
        return it
    }
    album.covers.covers
        .groupBy { it.id }
        .maxByOrNull { it.value.size }
        ?.value
        ?.firstOrNull()
        ?.let {
            return it
        }
    return albumTextCoverRequest(context, album)
}

/** Build a [TextCoverRequest] for an album with no album art. */
fun albumTextCoverRequest(context: Context, album: Album): TextCoverRequest {
    val seed = album.uid.toString().hashCode()
    val (bg, fg) = context.textCoverPalette(seed)
    val titles =
        album.songs
            .asSequence()
            .take(10)
            .mapNotNull { (it.name as? Name.Known)?.raw }
            .filter { it.isNotBlank() }
            .toList()
    val albumRaw = (album.name as? Name.Known)?.raw.orEmpty()
    val text =
        derivePlaylistCoverText(titles).ifEmpty {
            albumRaw.filter { it.isLetterOrDigit() }.take(4).ifEmpty {
                albumRaw.take(4).ifEmpty { "?" }
            }
        }
    return TextCoverRequest(
        text = text,
        seed = seed,
        backgroundColor = bg,
        foregroundColor = fg,
    )
}

fun Context.coverBackgroundColor(): Int = getColorCompat(R.color.sel_cover_bg).defaultColor

fun Context.coverForegroundColor(): Int = getColorCompat(R.color.sel_on_cover_bg).defaultColor

/**
 * Material You tonal pair for generated monogram covers.
 *
 * Picks primary / secondary / tertiary container by [seed] for gentle variety, then softens the
 * on-container color so body text is not high-contrast black-on-pastel.
 */
fun Context.textCoverPalette(seed: Int): Pair<Int, Int> {
    val role =
        when (Math.floorMod(seed, 3)) {
            0 ->
                MR.attr.colorPrimaryContainer to MR.attr.colorOnPrimaryContainer
            1 ->
                MR.attr.colorSecondaryContainer to MR.attr.colorOnSecondaryContainer
            else ->
                MR.attr.colorTertiaryContainer to MR.attr.colorOnTertiaryContainer
        }
    val background =
        runCatching { getAttrColorCompat(role.first).defaultColor }
            .getOrDefault(getColorCompat(R.color.sel_cover_bg).defaultColor)
    val onContainer =
        runCatching { getAttrColorCompat(role.second).defaultColor }
            .getOrDefault(getColorCompat(R.color.sel_on_cover_bg).defaultColor)
    // Soften: pull on-container toward the container fill (~30%) for M3 tonal feel.
    val foreground = ColorUtils.blendARGB(onContainer, background, 0.30f)
    return background to foreground
}
