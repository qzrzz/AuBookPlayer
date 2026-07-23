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
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
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
import kotlin.math.abs
import kotlin.math.min

/**
 * Coil request payload for a generated text cover (used when a playlist / folder / album has no
 * album art to compose).
 */
data class TextCoverRequest(
    val text: String,
    val seed: Int,
    @ColorInt val backgroundColor: Int,
    @ColorInt val foregroundColor: Int,
)

/**
 * Renders [TextCoverRequest.text] as a Material You–styled monogram cover.
 *
 * Design goals:
 * - Soft tonal field + subtle gradient blob (Material You wallpaper feel)
 * - Slightly smaller, bold type with comfortable padding
 * - Moderate contrast (on-container blended into the container, not pure black/white)
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
        val bitmap =
            render(
                data.text,
                squareSize,
                data.backgroundColor,
                data.foregroundColor,
                data.seed,
            )
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
        seed: Int,
    ): Bitmap {
        val label = text.ifBlank { "?" }
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        // Soft Material You base + one gentle radial bloom for depth.
        canvas.drawColor(backgroundColor)
        drawTonalBloom(canvas, size, backgroundColor, seed)

        // Soften text: blend on-container into the container so contrast stays moderate.
        val softText = ColorUtils.blendARGB(foregroundColor, backgroundColor, FOREGROUND_BLEND)

        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = softText
                // Real bold weight — avoid fake-bold which looks muddy at small sizes.
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isFakeBoldText = false
                // Slight tracking helps Latin monograms; CJK is unaffected visually.
                letterSpacing = 0.02f
            }

        val chars = label.toList()
        if (chars.size <= 2) {
            drawSingleLine(canvas, label, size, paint)
        } else {
            drawGrid(canvas, chars.take(4), size, paint)
        }
        return bitmap
    }

    /**
     * Soft off-center radial gradient (lighter / slightly shifted hue) — similar to Material You
     * tonal wallpapers without high-contrast hard edges.
     */
    private fun drawTonalBloom(canvas: Canvas, size: Int, @ColorInt base: Int, seed: Int) {
        val rnd = abs(seed)
        // Place the bloom off-center using the seed for stable variety across covers.
        val cx = size * (0.28f + (rnd % 100) / 100f * 0.44f)
        val cy = size * (0.22f + ((rnd / 7) % 100) / 100f * 0.40f)
        val radius = size * (0.55f + ((rnd / 13) % 40) / 100f)

        // Lighten base slightly for the bloom center (Material “surface bright” feel).
        val bloom = ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), BLOOM_LIGHTEN)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        cx,
                        cy,
                        radius,
                        intArrayOf(
                            ColorUtils.setAlphaComponent(bloom, 0xA0),
                            ColorUtils.setAlphaComponent(bloom, 0x40),
                            ColorUtils.setAlphaComponent(bloom, 0x00),
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Very soft second accent blob for a bit more depth (lower opacity).
        val cx2 = size - cx
        val cy2 = size * 0.72f
        val paint2 =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    RadialGradient(
                        cx2,
                        cy2,
                        size * 0.42f,
                        intArrayOf(
                            ColorUtils.setAlphaComponent(bloom, 0x38),
                            ColorUtils.setAlphaComponent(bloom, 0x00),
                        ),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint2)
    }

    private fun drawSingleLine(canvas: Canvas, label: String, size: Int, paint: TextPaint) {
        // More padding than before — monogram sits in a calmer optical center.
        val maxWidth = size * 0.62f
        // Slightly smaller than the previous 0.48 / 0.40 ratios.
        var textSize = size * if (label.length <= 1) 0.38f else 0.32f
        paint.textSize = textSize
        while (paint.measureText(label) > maxWidth && textSize > size * 0.16f) {
            textSize -= 1f
            paint.textSize = textSize
        }
        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(label, x, y, paint)
    }

    private fun drawGrid(canvas: Canvas, chars: List<Char>, size: Int, paint: TextPaint) {
        // Generous inset so the 2×2 grid doesn't crowd the edges.
        val inset = size * 0.14f
        val usable = size - 2 * inset
        val cell = usable / 2f
        // Smaller than the previous 0.52 cell ratio.
        paint.textSize = cell * 0.42f

        fun drawAt(ch: Char, col: Int, row: Int) {
            val cx = inset + col * cell + cell / 2f
            val cy = inset + row * cell + cell / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(ch.toString(), cx, cy, paint)
        }

        chars.forEachIndexed { index, ch ->
            drawAt(ch, index % 2, index / 2)
        }
    }

    class Factory @Inject constructor() : Fetcher.Factory<TextCoverRequest> {
        override fun create(data: TextCoverRequest, options: Options, imageLoader: ImageLoader) =
            TextCoverFetcher(options.context, data, options.size)
    }

    class Keyer @Inject constructor() : CoilKeyer<TextCoverRequest> {
        override fun key(data: TextCoverRequest, options: Options): String =
            // Bump version when visual style changes so Coil cache refreshes.
            "textcover:v2:${data.text}:${data.seed}:${data.backgroundColor}:" +
                "${data.foregroundColor}.${options.size.width}.${options.size.height}"
    }

    private companion object {
        /** Blend of on-container into container — softens contrast (Material You tonal). */
        const val FOREGROUND_BLEND = 0.32f
        /** How much to lift the bloom center toward white. */
        const val BLOOM_LIGHTEN = 0.22f
    }
}
