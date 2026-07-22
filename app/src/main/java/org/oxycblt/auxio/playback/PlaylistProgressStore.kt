/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistProgressStore.kt is part of Auxio.
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
import org.oxycblt.musikr.Music
import timber.log.Timber as L

/**
 * Last-listened progress for a playlist: which song and where within that song.
 *
 * Used by the playlist detail "Continue" control to resume audiobook-style listening.
 */
data class PlaylistProgress(
    val songUid: Music.UID,
    /** Position within the song, in milliseconds. */
    val positionMs: Long,
) {
    companion object {
        fun parse(raw: String): PlaylistProgress? {
            val sep = raw.indexOf('|')
            if (sep <= 0 || sep >= raw.length - 1) return null
            val uid = Music.UID.fromString(raw.substring(0, sep)) ?: return null
            val positionMs = raw.substring(sep + 1).toLongOrNull()?.coerceAtLeast(0L) ?: return null
            return PlaylistProgress(uid, positionMs)
        }
    }

    fun serialize(): String = "$songUid|$positionMs"
}

/**
 * Persists [PlaylistProgress] keyed by playlist [Music.UID] in SharedPreferences.
 */
interface PlaylistProgressStore {
    fun get(playlistUid: Music.UID): PlaylistProgress?

    fun set(playlistUid: Music.UID, progress: PlaylistProgress)

    fun clear(playlistUid: Music.UID)
}

@Singleton
class PlaylistProgressStoreImpl
@Inject
constructor(@ApplicationContext context: Context) : PlaylistProgressStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(playlistUid: Music.UID): PlaylistProgress? {
        val raw = prefs.getString(key(playlistUid), null) ?: return null
        return PlaylistProgress.parse(raw)
    }

    override fun set(playlistUid: Music.UID, progress: PlaylistProgress) {
        L.d("Saving progress for $playlistUid: song=${progress.songUid} @ ${progress.positionMs}ms")
        prefs.edit { putString(key(playlistUid), progress.serialize()) }
    }

    override fun clear(playlistUid: Music.UID) {
        prefs.edit { remove(key(playlistUid)) }
    }

    private fun key(playlistUid: Music.UID) = "progress_$playlistUid"

    private companion object {
        const val PREFS_NAME = "playlist_progress"
    }
}
