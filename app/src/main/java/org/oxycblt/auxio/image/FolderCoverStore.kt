/*
 * Copyright (c) 2026 Auxio Project
 * FolderCoverStore.kt is part of Auxio.
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
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import timber.log.Timber as L

/**
 * Coil request payload for a user-chosen folder cover stored on disk.
 *
 * [version] (file mtime) busts memory cache when the image is replaced.
 */
data class FolderCustomCover(val folderKey: String, val file: File, val version: Long)

/**
 * Stores optional custom cover images for [org.oxycblt.auxio.music.SongFolder]s under the app's
 * private files directory.
 */
interface FolderCoverStore {
    fun getCustomCover(folderKey: String): FolderCustomCover?

    fun hasCustomCover(folderKey: String): Boolean

    suspend fun setCover(folderKey: String, uri: Uri): Boolean

    suspend fun clearCover(folderKey: String)

    /** Emits the folder key whenever a custom cover is set or cleared. */
    val updates: SharedFlow<String>
}

@Singleton
class FolderCoverStoreImpl
@Inject
constructor(@ApplicationContext private val context: Context) : FolderCoverStore {
    private val dir: File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private val _updates = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val updates: SharedFlow<String> = _updates.asSharedFlow()

    override fun getCustomCover(folderKey: String): FolderCustomCover? {
        val file = fileFor(folderKey)
        if (!file.isFile || file.length() == 0L) return null
        return FolderCustomCover(folderKey, file, file.lastModified())
    }

    override fun hasCustomCover(folderKey: String): Boolean = getCustomCover(folderKey) != null

    override suspend fun setCover(folderKey: String, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val decoded = decodeBitmap(uri)
                if (decoded == null) {
                    L.e("Unable to decode folder cover from $uri")
                    return@withContext false
                }
                val square = centerCropToSquare(decoded, MAX_EDGE_PX)
                if (square !== decoded) {
                    decoded.recycle()
                }
                val dest = fileFor(folderKey)
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
                L.d("Saved square custom cover for folder $folderKey (${dest.length()} bytes)")
                _updates.tryEmit(folderKey)
                true
            } catch (e: Exception) {
                L.e(e)
                false
            }
        }

    override suspend fun clearCover(folderKey: String) {
        withContext(Dispatchers.IO) {
            val file = fileFor(folderKey)
            if (file.exists()) {
                file.delete()
                L.d("Cleared custom cover for folder $folderKey")
            }
        }
        _updates.tryEmit(folderKey)
    }

    private fun fileFor(folderKey: String): File {
        val safe = folderKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        // Cap length so path keys don't blow past filesystem limits.
        val name = if (safe.length > 120) safe.take(100) + "_" + safe.hashCode() else safe
        return File(dir, "$name.jpg")
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_DECODE_EDGE_PX) {
                    val sample =
                        (longest.toFloat() / MAX_DECODE_EDGE_PX).toInt().coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            }
        }
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
        const val DIR_NAME = "folder_covers"
        const val MAX_EDGE_PX = 1024
        const val MAX_DECODE_EDGE_PX = 2048
        const val JPEG_QUALITY = 88
    }
}
