/*
 * Copyright (c) 2026 Auxio Project
 * LastSessionProgressStore.kt is part of Auxio.
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

/** Where the last listening session was rooted. */
enum class LastSessionScope {
    ALBUM,
    FOLDER,
    PLAYLIST,
    /** Playing from the full library (songs tab / shuffle-all replacement). */
    ALL,
}

/**
 * Most recent listening session for the home "Continue" FAB: scope + song + position.
 *
 * Separate from per-album/folder/playlist stores so the home button can resume the single
 * most recent context regardless of which tab is open.
 */
data class LastSessionProgress(
    val scope: LastSessionScope,
    /**
     * Album/playlist UID string, or folder key. Empty for [LastSessionScope.ALL].
     */
    val key: String,
    val songUid: Music.UID,
    val positionMs: Long,
)

interface LastSessionProgressStore {
    fun get(): LastSessionProgress?

    fun set(progress: LastSessionProgress)

    fun clear()
}

@Singleton
class LastSessionProgressStoreImpl
@Inject
constructor(@ApplicationContext context: Context) : LastSessionProgressStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(): LastSessionProgress? {
        val scopeName = prefs.getString(KEY_SCOPE, null) ?: return null
        val scope =
            try {
                LastSessionScope.valueOf(scopeName)
            } catch (_: IllegalArgumentException) {
                return null
            }
        val key = prefs.getString(KEY_KEY, "") ?: ""
        val songUid = Music.UID.fromString(prefs.getString(KEY_SONG, null) ?: return null) ?: return null
        val positionMs = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
        return LastSessionProgress(scope, key, songUid, positionMs)
    }

    override fun set(progress: LastSessionProgress) {
        L.d(
            "Saving last session: ${progress.scope} key=${progress.key} " +
                "song=${progress.songUid} @ ${progress.positionMs}ms"
        )
        prefs.edit {
            putString(KEY_SCOPE, progress.scope.name)
            putString(KEY_KEY, progress.key)
            putString(KEY_SONG, progress.songUid.toString())
            putLong(KEY_POSITION, progress.positionMs)
        }
    }

    override fun clear() {
        prefs.edit {
            remove(KEY_SCOPE)
            remove(KEY_KEY)
            remove(KEY_SONG)
            remove(KEY_POSITION)
        }
    }

    private companion object {
        const val PREFS_NAME = "last_session_progress"
        const val KEY_SCOPE = "scope"
        const val KEY_KEY = "key"
        const val KEY_SONG = "song"
        const val KEY_POSITION = "position"
    }
}
