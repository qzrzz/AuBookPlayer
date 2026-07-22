/*
 * Copyright (c) 2022 Auxio Project
 * BitmapProvider.kt is part of Auxio.
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
import android.graphics.Bitmap
import android.os.Build
import coil3.ImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * A utility to provide bitmaps in a race-less manner.
 *
 * When it comes to components that load images manually as [Bitmap] instances, queued
 * [ImageRequest]s may cause a race condition that results in the incorrect image being drawn. This
 * utility resolves this by keeping track of the current request, and disposing it as soon as a new
 * request is queued or if another, competing request is newer.
 *
 * @param context [Context] required to load images.
 * @author Alexander Capehart (OxygenCobalt)
 */
class BitmapProvider
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
    private val playlistCoverStore: PlaylistCoverStore,
) {
    /**
     * An extension of [Disposable] with an additional [Target] to deliver the final [Bitmap] to.
     */
    private data class Request(val disposable: Disposable, val callback: Target)

    /** The target that will receive the requested [Bitmap]. */
    interface Target {
        /**
         * Configure the [ImageRequest.Builder] to enable [Target]-specific configuration.
         *
         * @param builder The [ImageRequest.Builder] that will be used to request the desired
         *   [Bitmap].
         * @return The same [ImageRequest.Builder] in order to easily chain configuration methods.
         */
        fun onConfigRequest(builder: ImageRequest.Builder): ImageRequest.Builder = builder

        /**
         * Called when the loading process is completed.
         *
         * @param bitmap The loaded bitmap, or null if the bitmap could not be loaded.
         */
        fun onCompleted(bitmap: Bitmap?)
    }

    private var currentRequest: Request? = null
    private var currentHandle = 0L

    /** If this provider is currently attempting to load something. */
    val isBusy: Boolean
        get() = currentRequest?.run { !disposable.isDisposed } ?: false

    /**
     * Load the Album cover [Bitmap] from a [Song].
     *
     * @param song The song to load a [Bitmap] of it's album cover from.
     * @param target The [Target] to deliver the [Bitmap] to asynchronously.
     * @param fallbackPlaylist When [Song.cover] is null, use this playlist's cover instead.
     */
    @Synchronized
    fun load(song: Song, target: Target, fallbackPlaylist: Playlist? = null) {
        // Increment the handle, indicating a newer request has been created
        val handle = ++currentHandle
        currentRequest?.run { disposable.dispose() }
        currentRequest = null

        val coverData =
            resolveSongCoverData(
                context,
                song,
                fallbackPlaylist,
                customCover = fallbackPlaylist?.let { playlistCoverStore.getCustomCover(it.uid) },
            )

        if (coverData == null) {
            L.d("No cover data for $song (fallback=$fallbackPlaylist)")
            target.onCompleted(null)
            return
        }

        val imageRequest =
            target
                .onConfigRequest(
                    ImageRequest.Builder(context)
                        .data(coverData)
                        // Fixed size is enough for notifications / system media controls and
                        // avoids ORIGINAL-size edge cases in composition / text fetchers.
                        .size(Size(MEDIA_COVER_EDGE_PX, MEDIA_COVER_EDGE_PX))
                        // System media session / notification cannot use hardware bitmaps and may
                        // show a solid purple placeholder when given one (or when conversion fails).
                        .allowHardware(false)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                )
                .target(
                    onSuccess = { image ->
                        synchronized(this) {
                            if (currentHandle == handle) {
                                target.onCompleted(image.toBitmap().toSoftwareArgb8888())
                            }
                        }
                    },
                    onError = { err ->
                        L.w("Cover load failed for $song: $err")
                        synchronized(this) {
                            if (currentHandle == handle) {
                                target.onCompleted(null)
                            }
                        }
                    },
                )
        currentRequest = Request(imageLoader.enqueue(imageRequest.build()), target)
    }

    /** Release this instance, cancelling any currently running operations. */
    @Synchronized
    fun release() {
        ++currentHandle
        currentRequest?.run { disposable.dispose() }
        currentRequest = null
    }

    private companion object {
        /** Edge length for system / widget cover bitmaps. */
        const val MEDIA_COVER_EDGE_PX = 512
    }
}

/**
 * Convert to a software [Bitmap.Config.ARGB_8888] bitmap suitable for [android.media.session]
 * metadata and notifications. Hardware bitmaps often appear as a solid purple tile in system UI.
 */
private fun Bitmap.toSoftwareArgb8888(): Bitmap {
    var result = this
    val isHardware =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE
    if (isHardware || config != Bitmap.Config.ARGB_8888) {
        result = copy(Bitmap.Config.ARGB_8888, /* isMutable= */ false) ?: return this
    }
    // Cap size so Binder / system UI does not drop oversized album art.
    val longest = max(result.width, result.height)
    if (longest > 1024) {
        val scale = 1024f / longest
        val w = (result.width * scale).toInt().coerceAtLeast(1)
        val h = (result.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(result, w, h, true)
        if (scaled !== result && result !== this) {
            // Only recycle intermediates we created, not the original Coil bitmap.
            result.recycle()
        }
        result = scaled
    }
    return result
}
