/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistCustomCoverFetcher.kt is part of Auxio.
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

package org.oxycblt.auxio.image.coil

import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer as CoilKeyer
import coil3.request.Options
import javax.inject.Inject
import org.oxycblt.auxio.image.PlaylistCustomCover
import org.oxycblt.auxio.image.centerCropToSquare

/**
 * Loads a [PlaylistCustomCover] JPEG from app-private storage into Coil.
 *
 * Always center-crops to a square so older non-square saved covers still fill the cover view
 * without stretching.
 */
class PlaylistCustomCoverFetcher
private constructor(private val data: PlaylistCustomCover, private val options: Options) :
    Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (!data.file.isFile) return null
        val decoded = BitmapFactory.decodeFile(data.file.absolutePath) ?: return null
        // New saves are already square; this also upgrades legacy rectangular covers at load time.
        val square = centerCropToSquare(decoded, maxEdge = Int.MAX_VALUE)
        return ImageFetchResult(
            image = square.toDrawable(options.context.resources).asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory @Inject constructor() : Fetcher.Factory<PlaylistCustomCover> {
        override fun create(
            data: PlaylistCustomCover,
            options: Options,
            imageLoader: ImageLoader,
        ) = PlaylistCustomCoverFetcher(data, options)
    }

    class Keyer @Inject constructor() : CoilKeyer<PlaylistCustomCover> {
        override fun key(data: PlaylistCustomCover, options: Options): String =
            // Bump key prefix so in-memory cache drops old stretched rectangular covers.
            "plc-sq:${data.playlistUid}:${data.version}:${options.size}"
    }
}
