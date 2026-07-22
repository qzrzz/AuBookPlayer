/*
 * Copyright (c) 2026 Auxio Project
 * SleepTimer.kt is part of Auxio.
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

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import timber.log.Timber as L

/**
 * Application-scoped sleep timer that pauses playback after a chosen duration.
 *
 * Lives outside any single UI component so the countdown continues while the
 * playback service is active in the background.
 */
@Singleton
class SleepTimer
@Inject
constructor(private val playbackManager: PlaybackStateManager) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var timerJob: Job? = null

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    /** Current sleep-timer state. [SleepTimerState.Off] when inactive. */
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    /**
     * Start (or restart) a sleep timer for the given duration in minutes.
     *
     * @param minutes Wall-clock minutes until playback should pause.
     */
    fun start(minutes: Int) {
        require(minutes > 0) { "Sleep timer duration must be positive" }
        cancelInternal()

        val totalMs = minutes * 60_000L
        val endAtElapsed = SystemClock.elapsedRealtime() + totalMs
        L.d("Starting sleep timer for $minutes minutes")
        _state.value = SleepTimerState.Active(durationMinutes = minutes, remainingMs = totalMs)

        timerJob =
            scope.launch {
                while (true) {
                    val remaining = endAtElapsed - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break
                    _state.value =
                        SleepTimerState.Active(durationMinutes = minutes, remainingMs = remaining)
                    // Tick once a second so UI can show remaining time if desired.
                    delay(minOf(1_000L, remaining))
                }
                L.d("Sleep timer expired, pausing playback")
                timerJob = null
                // Keep a distinct "expired" state so the UI can show the pause icon
                // until the user clears or restarts the timer.
                _state.value = SleepTimerState.Expired
                playbackManager.playing(false)
            }
    }

    /** Cancel any active or expired sleep timer. */
    fun cancel() {
        if (_state.value is SleepTimerState.Off) return
        L.d("Cancelling sleep timer")
        cancelInternal()
    }

    private fun cancelInternal() {
        timerJob?.cancel()
        timerJob = null
        _state.value = SleepTimerState.Off
    }

    companion object {
        /** Available sleep-timer durations in minutes. */
        val AVAILABLE_MINUTES = listOf(15, 25, 35, 45, 60, 90)
    }
}

/**
 * Snapshot of the sleep timer.
 *
 * Icon mapping:
 * - [Off] → hourglass_empty
 * - [Active] → hourglass_top (countdown running)
 * - [Expired] → hourglass_pause (timer fired and paused playback)
 */
sealed interface SleepTimerState {
    /** Timer is not set. */
    data object Off : SleepTimerState

    /** Countdown is running. */
    data class Active(
        /** Originally selected duration, in minutes. */
        val durationMinutes: Int,
        /** Remaining wall-clock time until pause, in milliseconds. */
        val remainingMs: Long,
    ) : SleepTimerState {
        val remainingMinutesCeil: Int
            get() = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    /** Timer finished and paused playback; waiting for user to clear or restart. */
    data object Expired : SleepTimerState
}
