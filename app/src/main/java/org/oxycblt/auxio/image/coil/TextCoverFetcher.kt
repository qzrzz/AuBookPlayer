/*
 * Copyright (c) 2026 Auxio Project
 * TextCoverFetcher.kt is part of Auxio.
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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer as CoilKeyer
import coil3.request.Options
import coil3.size.Size
import coil3.size.pxOrElse
import javax.inject.Inject
import kotlin.math.min

/**
 * Coil request payload for a generated text cover (used when a playlist has no
 * album art to compose).
 */
data class TextCoverRequest(
    val text: String,
    val seed: Int,
    @ColorInt val backgroundColor: Int,
    @ColorInt val foregroundColor: Int,
)

/**
 * Renders [TextCoverRequest.text] as a square cover image.
 *
 * Layout:
 * - 1–2 characters: single centered line
 * - 3–4 characters: 2×2 character grid (common for Chinese short labels)
 */
class TextCoverFetcher
private constructor(private val context: Context, private val data: TextCoverRequest, size: Size) :
    Fetcher {
    private val squareSize =
        min(size.width.pxOrElse { 512 }, size.height.pxOrElse { 512 }).coerceAtLeast(1)

    override suspend fun fetch(): FetchResult {
        val bitmap = render(data.text, squareSize, data.backgroundColor, data.foregroundColor)
        return ImageFetchResult(
            image = bitmap.toDrawable(context.resources).asImage(),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    private fun render(
        text: String,
        size: Int,
        @ColorInt backgroundColor: Int,
        @ColorInt foregroundColor: Int,
    ): Bitmap {
        val label = text.ifBlank { "?" }
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = foregroundColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }

        val chars = label.toList()
        if (chars.size <= 2) {
            drawSingleLine(canvas, label, size, paint)
        } else {
            // Pack into a 2×2 grid for 3–4 characters.
            drawGrid(canvas, chars.take(4), size, paint)
        }
        return bitmap
    }

    private fun drawSingleLine(canvas: Canvas, label: String, size: Int, paint: TextPaint) {
        val maxWidth = size * 0.78f
        var textSize = size * if (label.length <= 1) 0.48f else 0.40f
        paint.textSize = textSize
        while (paint.measureText(label) > maxWidth && textSize > size * 0.18f) {
            textSize -= 1f
            paint.textSize = textSize
        }
        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(label, x, y, paint)
    }

    private fun drawGrid(canvas: Canvas, chars: List<Char>, size: Int, paint: TextPaint) {
        // Two rows: top row first two chars, bottom row remaining.
        val cell = size / 2f
        val inset = size * 0.08f
        paint.textSize = cell * 0.52f

        fun drawAt(ch: Char, col: Int, row: Int) {
            val cx = inset + col * (size - 2 * inset) / 2f + (size - 2 * inset) / 4f
            val cy =
                inset +
                    row * (size - 2 * inset) / 2f +
                    (size - 2 * inset) / 4f -
                    (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(ch.toString(), cx, cy, paint)
        }

        chars.forEachIndexed { index, ch ->
            val col = index % 2
            val row = index / 2
            drawAt(ch, col, row)
        }
    }

    class Factory @Inject constructor() : Fetcher.Factory<TextCoverRequest> {
        override fun create(data: TextCoverRequest, options: Options, imageLoader: ImageLoader) =
            TextCoverFetcher(options.context, data, options.size)
    }

    class Keyer @Inject constructor() : CoilKeyer<TextCoverRequest> {
        override fun key(data: TextCoverRequest, options: Options): String =
            "textcover:${data.text}:${data.seed}:${data.backgroundColor}:${data.foregroundColor}" +
                ".${options.size.width}.${options.size.height}"
    }
}
