/*
 * Copyright (c) 2026 Auxio Project
 * FolderCustomCoverFetcher.kt is part of Auxio.
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
import org.oxycblt.auxio.image.FolderCustomCover
import org.oxycblt.auxio.image.centerCropToSquare

/** Loads a [FolderCustomCover] JPEG from app-private storage into Coil. */
class FolderCustomCoverFetcher
private constructor(private val data: FolderCustomCover, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        if (!data.file.isFile) return null
        val decoded = BitmapFactory.decodeFile(data.file.absolutePath) ?: return null
        val square = centerCropToSquare(decoded, maxEdge = Int.MAX_VALUE)
        return ImageFetchResult(
            image = square.toDrawable(options.context.resources).asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory @Inject constructor() : Fetcher.Factory<FolderCustomCover> {
        override fun create(
            data: FolderCustomCover,
            options: Options,
            imageLoader: ImageLoader,
        ) = FolderCustomCoverFetcher(data, options)
    }

    class Keyer @Inject constructor() : CoilKeyer<FolderCustomCover> {
        override fun key(data: FolderCustomCover, options: Options): String =
            "fcc-sq:${data.folderKey}:${data.version}:${options.size}"
    }
}
