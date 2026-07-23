/*
 * Copyright (c) 2026 Auxio Project
 * AlbumProgressStore.kt is part of Auxio.
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
 * Persists last-listened progress for albums, keyed by album [Music.UID].
 *
 * Reuses [PlaylistProgress] as the value type (song UID + position).
 */
interface AlbumProgressStore {
    fun get(albumUid: Music.UID): PlaylistProgress?

    fun set(albumUid: Music.UID, progress: PlaylistProgress)

    fun clear(albumUid: Music.UID)
}

@Singleton
class AlbumProgressStoreImpl
@Inject
constructor(@ApplicationContext context: Context) : AlbumProgressStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(albumUid: Music.UID): PlaylistProgress? {
        val raw = prefs.getString(key(albumUid), null) ?: return null
        return PlaylistProgress.parse(raw)
    }

    override fun set(albumUid: Music.UID, progress: PlaylistProgress) {
        L.d("Saving album progress for $albumUid: song=${progress.songUid} @ ${progress.positionMs}ms")
        prefs.edit { putString(key(albumUid), progress.serialize()) }
    }

    override fun clear(albumUid: Music.UID) {
        prefs.edit { remove(key(albumUid)) }
    }

    private fun key(albumUid: Music.UID) = "progress_$albumUid"

    private companion object {
        const val PREFS_NAME = "album_progress"
    }
}
