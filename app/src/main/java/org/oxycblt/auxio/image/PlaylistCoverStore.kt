/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistCoverStore.kt is part of Auxio.
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
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Music
import timber.log.Timber as L

/**
 * Coil request payload for a user-chosen playlist cover stored on disk.
 *
 * [version] (file mtime) busts memory cache when the image is replaced.
 */
data class PlaylistCustomCover(val playlistUid: Music.UID, val file: File, val version: Long)

/**
 * Stores optional custom cover images for playlists under the app's private files directory.
 *
 * Files are named by playlist UID and written as compressed JPEGs so they stay reasonably small.
 * User-chosen images are **center-cropped to a square** (cover/fill, no stretch) before save.
 */
interface PlaylistCoverStore {
    /** Returns a Coil data object if a custom cover exists for [playlistUid], otherwise null. */
    fun getCustomCover(playlistUid: Music.UID): PlaylistCustomCover?

    /** Whether a custom cover is currently stored for [playlistUid]. */
    fun hasCustomCover(playlistUid: Music.UID): Boolean

    /**
     * Copy, center-crop to a square, and compress the image at [uri] as the cover for
     * [playlistUid].
     *
     * @return true on success.
     */
    suspend fun setCover(playlistUid: Music.UID, uri: Uri): Boolean

    /** Remove the custom cover for [playlistUid] if present. */
    suspend fun clearCover(playlistUid: Music.UID)

    /** Emits whenever a playlist's custom cover is set or cleared. */
    val updates: SharedFlow<Music.UID>
}

@Singleton
class PlaylistCoverStoreImpl
@Inject
constructor(@ApplicationContext private val context: Context) : PlaylistCoverStore {
    private val dir: File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private val _updates = MutableSharedFlow<Music.UID>(extraBufferCapacity = 8)
    override val updates: SharedFlow<Music.UID> = _updates.asSharedFlow()

    override fun getCustomCover(playlistUid: Music.UID): PlaylistCustomCover? {
        val file = fileFor(playlistUid)
        if (!file.isFile || file.length() == 0L) return null
        return PlaylistCustomCover(playlistUid, file, file.lastModified())
    }

    override fun hasCustomCover(playlistUid: Music.UID): Boolean =
        getCustomCover(playlistUid) != null

    override suspend fun setCover(playlistUid: Music.UID, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodeBitmap(uri)
                if (decoded == null) {
                    L.e("Unable to decode playlist cover from $uri")
                    return@withContext false
                }
                // Center-crop to square (fill without stretch), then downscale if needed.
                val square = centerCropToSquare(decoded, MAX_EDGE_PX)
                if (square !== decoded) {
                    decoded.recycle()
                }
                val dest = fileFor(playlistUid)
                val tmp = File(dest.absolutePath + ".tmp")
                FileOutputStream(tmp).use { out ->
                    square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    out.flush()
                }
                square.recycle()
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                L.d(
                    "Saved square custom cover for $playlistUid " +
                        "(${dest.length()} bytes, max edge $MAX_EDGE_PX)"
                )
                _updates.tryEmit(playlistUid)
                true
            } catch (e: Exception) {
                L.e(e)
                false
            }
        }

    override suspend fun clearCover(playlistUid: Music.UID) {
        withContext(Dispatchers.IO) {
            val file = fileFor(playlistUid)
            if (file.exists()) {
                file.delete()
                L.d("Cleared custom cover for $playlistUid")
            }
        }
        _updates.tryEmit(playlistUid)
    }

    private fun fileFor(playlistUid: Music.UID): File {
        // UID string may contain characters unsafe for filenames; use a stable hash-based name.
        val safe = playlistUid.toString().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safe.jpg")
    }

    /**
     * Decode [uri], applying EXIF orientation so camera photos keep the correct rotation.
     * API 28+ [ImageDecoder] handles orientation natively.
     */
    private fun decodeBitmap(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Prefer software bitmaps so JPEG compress always works.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DECODE_EDGE_PX) {
                    val sample =
                        (longest.toFloat() / MAX_DECODE_EDGE_PX)
                            .toInt()
                            .coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            }
        }
        // Pre-P: decode then apply EXIF orientation manually.
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return applyExifOrientation(decoded, bytes)
    }

    private fun applyExifOrientation(bitmap: Bitmap, jpegBytes: ByteArray): Bitmap {
        val orientation =
            try {
                ExifInterface(jpegBytes.inputStream())
                    .getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
        val degrees =
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    private companion object {
        const val DIR_NAME = "playlist_covers"
        /** Output square edge length (px). */
        const val MAX_EDGE_PX = 1024
        /** Max edge when decoding large sources before crop (saves memory). */
        const val MAX_DECODE_EDGE_PX = 2048
        const val JPEG_QUALITY = 88
    }
}

/**
 * Center-crop [src] to a square (cover/fill — no stretch), then scale so the edge is at most
 * [maxEdge] px.
 *
 * Landscape images lose left/right; portrait images lose top/bottom. Already-square sources are
 * only downscaled when larger than [maxEdge].
 *
 * @return A new bitmap, or [src] itself when no work is needed.
 */
internal fun centerCropToSquare(src: Bitmap, maxEdge: Int): Bitmap {
    val side = min(src.width, src.height).coerceAtLeast(1)
    val x = (src.width - side) / 2
    val y = (src.height - side) / 2
    val cropped =
        if (src.width == side && src.height == side) {
            src
        } else {
            Bitmap.createBitmap(src, x, y, side, side)
        }
    if (side <= maxEdge) {
        return cropped
    }
    val scaled = Bitmap.createScaledBitmap(cropped, maxEdge, maxEdge, true)
    if (scaled !== cropped && cropped !== src) {
        cropped.recycle()
    }
    return scaled
}
