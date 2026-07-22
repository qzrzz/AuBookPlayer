/*
 * Copyright (c) 2022 Auxio Project
 * PlaybackUtil.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.TextPaint
import android.text.format.DateUtils
import android.view.View
import androidx.appcompat.widget.PopupMenu
import java.util.Locale
import kotlin.math.abs
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.showToast

/**
 * Convert milliseconds into deci-seconds (1/10th of a second).
 *
 * @return A converted deci-second value.
 */
fun Long.msToDs() = floorDiv(100)

/**
 * Convert milliseconds into seconds.
 *
 * @return A converted second value.
 */
fun Long.msToSecs() = floorDiv(1000)

/**
 * Convert deci-seconds (1/10th of a second) into milliseconds.
 *
 * @return A converted millisecond value.
 */
fun Long.dsToMs() = times(100)

/**
 * Convert deci-seconds (1/10th of a second) into seconds.
 *
 * @return A converted second value.
 */
fun Long.dsToSecs() = floorDiv(10)

/**
 * Convert a millisecond value into a string duration.
 *
 * @param isElapsed Whether this duration is represents elapsed time. If this is false, then --:--
 *   will be returned if the second value is 0.
 */
fun Long.formatDurationMs(isElapsed: Boolean) = msToSecs().formatDurationSecs(isElapsed)

/**
 * Format a millisecond duration into a compact, locale-aware bucket string suitable for fast-scroll
 * popups. Durations are bucketed into the most significant time unit:
 * - Less than 1 minute: "<1m" (using locale-narrow minute abbreviation)
 * - 1–59 minutes: "Nm" (e.g., "5m", "42m")
 * - 1+ hours: "Nh" (e.g., "2h", "142h")
 */
fun Long.formatDurationMsPopup(): String {
    val totalMinutes = floorDiv(60_000)
    val totalHours = totalMinutes / 60
    val fmt = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.NARROW)
    return when {
        totalMinutes < 1 -> "<" + fmt.format(Measure(1, MeasureUnit.MINUTE))
        totalHours < 1 -> fmt.format(Measure(totalMinutes, MeasureUnit.MINUTE))
        else -> fmt.format(Measure(totalHours, MeasureUnit.HOUR))
    }
}

/**
 * // * Format a deci-second value (1/10th of a second) into a string duration.
 *
 * @param isElapsed Whether this duration is represents elapsed time. If this is false, then --:--
 *   will be returned if the second value is 0.
 */
fun Long.formatDurationDs(isElapsed: Boolean) = dsToSecs().formatDurationSecs(isElapsed)

/**
 * Convert a second value into a string duration.
 *
 * @param isElapsed Whether this duration is represents elapsed time. If this is false, then --:--
 *   will be returned if the second value is 0.
 */
fun Long.formatDurationSecs(isElapsed: Boolean): String {
    if (!isElapsed && this == 0L) {
        // Non-elapsed duration is zero, return default value.
        return "--:--"
    }

    var durationString = DateUtils.formatElapsedTime(this)
    // Remove trailing zero values [i.e 01:42]. This is primarily for aesthetics.
    if (durationString[0] == '0') {
        durationString = durationString.slice(1 until durationString.length)
    }
    return durationString
}

/** Human-readable playback-speed label (e.g. `1.5X`, `2X`). */
fun formatSpeedLabel(speed: Float): String =
    when {
        abs(speed - 0.75f) < 0.01f -> "0.75X"
        abs(speed - 1.0f) < 0.01f -> "1X"
        abs(speed - 1.1f) < 0.01f -> "1.1X"
        abs(speed - 1.2f) < 0.01f -> "1.2X"
        abs(speed - 1.25f) < 0.01f -> "1.25X"
        abs(speed - 1.3f) < 0.01f -> "1.3X"
        abs(speed - 1.5f) < 0.01f -> "1.5X"
        abs(speed - 2.0f) < 0.01f -> "2X"
        else -> {
            val rounded = (speed * 100f).toInt() / 100f
            if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}X" else "${rounded}X"
        }
    }

/**
 * Compact speed label for drawing inside the playback control icon
 * (e.g. `1.5×`, `2×`).
 */
fun formatSpeedIconLabel(speed: Float): String = formatSpeedLabel(speed).replace('X', '×')

/**
 * Build a tintable white-on-transparent icon that shows the current playback
 * speed so it can replace the static speed glyph on the playback panel.
 */
fun Context.createSpeedIconDrawable(speed: Float, sizeDp: Int = 24): Drawable {
    val density = resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
    val label = formatSpeedIconLabel(speed)

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

    // Fit the label inside the icon bounds with a small inset.
    val maxWidth = sizePx * 0.92f
    var textSize = sizePx * if (label.length <= 2) 0.52f else 0.42f
    paint.textSize = textSize
    while (paint.measureText(label) > maxWidth && textSize > sizePx * 0.22f) {
        textSize -= density
        paint.textSize = textSize
    }

    val x = sizePx / 2f
    val y = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, x, y, paint)

    return BitmapDrawable(resources, bitmap)
}

/** Status text for the sleep-timer row in settings. */
fun Context.formatSleepTimerStatus(state: SleepTimerState): String =
    when (state) {
        is SleepTimerState.Off -> getString(R.string.lbl_sleep_timer_off)
        is SleepTimerState.Active ->
            getString(R.string.fmt_sleep_timer_remaining, state.remainingMinutesCeil)
        is SleepTimerState.Expired -> getString(R.string.lng_sleep_timer_expired)
    }

/**
 * Show a single-choice speed popup anchored to [anchor].
 *
 * @param showToast When true, toast the newly selected speed.
 */
fun showPlaybackSpeedMenu(
    anchor: View,
    playbackModel: PlaybackViewModel,
    showToast: Boolean = true,
) {
    val context = anchor.context
    val popup = PopupMenu(context, anchor)
    val speeds = playbackModel.availableSpeeds
    val currentSpeed = playbackModel.playbackSpeed.value

    speeds.forEachIndexed { index, speed ->
        val menuItem = popup.menu.add(0, index, index, formatSpeedLabel(speed))
        menuItem.isCheckable = true
        if (abs(speed - currentSpeed) < 0.01f) {
            menuItem.isChecked = true
        }
    }
    // Exclusive checkable group → radio-style single choice.
    popup.menu.setGroupCheckable(0, true, true)

    popup.setOnMenuItemClickListener { menuItem ->
        val selectedSpeed = speeds[menuItem.itemId]
        playbackModel.setPlaybackSpeed(selectedSpeed)
        if (showToast) {
            context.showToast(
                context.getString(R.string.lng_playback_speed_set, formatSpeedLabel(selectedSpeed))
            )
        }
        true
    }
    popup.show()
}

/** Show a single-choice sleep-timer popup anchored to [anchor]. */
fun showSleepTimerMenu(anchor: View, playbackModel: PlaybackViewModel) {
    val context = anchor.context
    val popup = PopupMenu(context, anchor)
    val durations = playbackModel.availableSleepTimerMinutes
    val current = playbackModel.sleepTimerState.value
    val activeMinutes = (current as? SleepTimerState.Active)?.durationMinutes
    val timerEngaged = current !is SleepTimerState.Off

    val offItem =
        popup.menu.add(0, SLEEP_TIMER_MENU_OFF, 0, context.getString(R.string.lbl_sleep_timer_off))
    offItem.isCheckable = true
    offItem.isChecked = !timerEngaged

    durations.forEachIndexed { index, minutes ->
        val label = context.getString(R.string.fmt_sleep_timer_minutes, minutes)
        val menuItem = popup.menu.add(0, minutes, index + 1, label)
        menuItem.isCheckable = true
        menuItem.isChecked = minutes == activeMinutes
    }
    popup.menu.setGroupCheckable(0, true, true)

    popup.setOnMenuItemClickListener { menuItem ->
        when (menuItem.itemId) {
            SLEEP_TIMER_MENU_OFF -> {
                if (timerEngaged) {
                    playbackModel.cancelSleepTimer()
                    context.showToast(R.string.lng_sleep_timer_cancelled)
                }
            }
            else -> {
                val minutes = menuItem.itemId
                if (minutes == activeMinutes) {
                    playbackModel.cancelSleepTimer()
                    context.showToast(R.string.lng_sleep_timer_cancelled)
                } else {
                    playbackModel.setSleepTimer(minutes)
                    context.showToast(context.getString(R.string.lng_sleep_timer_set, minutes))
                }
            }
        }
        true
    }
    popup.show()
}

private const val SLEEP_TIMER_MENU_OFF = 0
