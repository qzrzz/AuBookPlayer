/*
 * Copyright (c) 2026 Auxio Project
 * SongPlayProgressStore.kt is part of Auxio.
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
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Per-song listening progress shared across playlists, folders, albums, and the queue.
 *
 * Progress is the **maximum** position reached for a song. A song is considered fully listened
 * once that max position is at least [COMPLETED_FRACTION] of its duration.
 */
interface SongPlayProgressStore {
    /** Highest position (ms) reached for [songUid], or 0 if never tracked. */
    fun maxPositionMs(songUid: Music.UID): Long

    /**
     * Progress in `0f..1f` for [song]. Uses [Song.durationMs]; returns 0 if duration is unknown.
     */
    fun fraction(song: Song): Float

    /** Whether [song] has been listened past [COMPLETED_FRACTION] of its length. */
    fun isCompleted(song: Song): Boolean

    /**
     * Record a new position. Only raises the stored max (re-listening from the start will not
     * erase progress until [clear] / [clearAll]).
     */
    fun record(song: Song, positionMs: Long)

    /** Clear progress for a single song. */
    fun clear(songUid: Music.UID)

    /** Clear progress for many songs (e.g. reset a playlist / folder / album). */
    fun clearAll(songUids: Collection<Music.UID>)

    /** Emits a song UID whenever its progress changes. */
    val updates: SharedFlow<Music.UID>

    companion object {
        /** Fraction of duration that must be reached to count as fully played. */
        const val COMPLETED_FRACTION = 0.8f
    }
}

@Singleton
class SongPlayProgressStoreImpl
@Inject
constructor(@ApplicationContext context: Context) : SongPlayProgressStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updates = MutableSharedFlow<Music.UID>(extraBufferCapacity = 32)
    override val updates: SharedFlow<Music.UID> = _updates.asSharedFlow()

    override fun maxPositionMs(songUid: Music.UID): Long =
        prefs.getLong(key(songUid), 0L).coerceAtLeast(0L)

    override fun fraction(song: Song): Float {
        val duration = song.durationMs
        if (duration <= 0L) return 0f
        return (maxPositionMs(song.uid).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    override fun isCompleted(song: Song): Boolean =
        fraction(song) >= SongPlayProgressStore.COMPLETED_FRACTION

    override fun record(song: Song, positionMs: Long) {
        val duration = song.durationMs
        if (duration <= 0L) return
        val capped = positionMs.coerceIn(0L, duration)
        // Once past the completion threshold, snap to duration so UI shows full bar / greyscale.
        val effective =
            if (capped.toFloat() / duration >= SongPlayProgressStore.COMPLETED_FRACTION) {
                duration
            } else {
                capped
            }
        val previous = maxPositionMs(song.uid)
        if (effective <= previous) return
        prefs.edit { putLong(key(song.uid), effective) }
        L.d("Song progress ${song.uid}: ${previous}ms -> ${effective}ms / ${duration}ms")
        _updates.tryEmit(song.uid)
    }

    override fun clear(songUid: Music.UID) {
        if (!prefs.contains(key(songUid))) return
        prefs.edit { remove(key(songUid)) }
        _updates.tryEmit(songUid)
    }

    override fun clearAll(songUids: Collection<Music.UID>) {
        if (songUids.isEmpty()) return
        prefs.edit {
            for (uid in songUids) {
                remove(key(uid))
            }
        }
        for (uid in songUids) {
            _updates.tryEmit(uid)
        }
    }

    private fun key(songUid: Music.UID) = "max_$songUid"

    private companion object {
        const val PREFS_NAME = "song_play_progress"
    }
}
