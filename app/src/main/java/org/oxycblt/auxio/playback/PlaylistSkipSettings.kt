/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistSkipSettings.kt is part of Auxio.
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
import timber.log.Timber as L

/**
 * Per-playlist "skip head / tail" settings: when playing from a playlist, the first [headSeconds]
 * and last [tailSeconds] of each track are skipped.
 */
data class SkipHeadTail(
    /** Seconds to skip from the start of each track. */
    val headSeconds: Int = 0,
    /** Seconds to skip from the end of each track. */
    val tailSeconds: Int = 0,
) {
    val headMs: Long
        get() = headSeconds * 1000L

    val tailMs: Long
        get() = tailSeconds * 1000L

    val isActive: Boolean
        get() = headSeconds > 0 || tailSeconds > 0

    companion object {
        val NONE = SkipHeadTail(0, 0)

        /** Inclusive upper bound for manual / smart skip durations (seconds). */
        const val MAX_SECONDS = 120
    }
}

/**
 * Persists [SkipHeadTail] settings keyed by playlist [Music.UID].
 *
 * Lives in app-level SharedPreferences so different playlists can keep independent skip values
 * without altering the playlist database schema.
 */
interface PlaylistSkipSettings {
    fun get(playlistUid: Music.UID): SkipHeadTail

    fun set(playlistUid: Music.UID, skip: SkipHeadTail)

    /** Emits whenever any playlist's skip settings change. */
    val updates: SharedFlow<Music.UID>
}

@Singleton
class PlaylistSkipSettingsImpl
@Inject
constructor(@ApplicationContext context: Context) : PlaylistSkipSettings {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _updates = MutableSharedFlow<Music.UID>(extraBufferCapacity = 8)
    override val updates: SharedFlow<Music.UID> = _updates.asSharedFlow()

    override fun get(playlistUid: Music.UID): SkipHeadTail {
        val raw = prefs.getString(key(playlistUid), null) ?: return SkipHeadTail.NONE
        val parts = raw.split(',')
        if (parts.size != 2) return SkipHeadTail.NONE
        val head = parts[0].toIntOrNull() ?: return SkipHeadTail.NONE
        val tail = parts[1].toIntOrNull() ?: return SkipHeadTail.NONE
        return SkipHeadTail(head.coerceAtLeast(0), tail.coerceAtLeast(0))
    }

    override fun set(playlistUid: Music.UID, skip: SkipHeadTail) {
        val normalized =
            SkipHeadTail(skip.headSeconds.coerceAtLeast(0), skip.tailSeconds.coerceAtLeast(0))
        L.d("Saving skip settings for $playlistUid: $normalized")
        prefs.edit {
            if (!normalized.isActive) {
                remove(key(playlistUid))
            } else {
                putString(key(playlistUid), "${normalized.headSeconds},${normalized.tailSeconds}")
            }
        }
        _updates.tryEmit(playlistUid)
    }

    private fun key(playlistUid: Music.UID) = "skip_$playlistUid"

    private companion object {
        const val PREFS_NAME = "playlist_skip_head_tail"
    }
}
