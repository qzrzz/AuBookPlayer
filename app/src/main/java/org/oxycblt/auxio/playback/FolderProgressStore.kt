/*
 * Copyright (c) 2026 Auxio Project
 * FolderProgressStore.kt is part of Auxio.
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
import timber.log.Timber as L

/**
 * Persists last-listened progress for [org.oxycblt.auxio.music.SongFolder]s, keyed by
 * [org.oxycblt.auxio.music.SongFolder.key].
 *
 * Reuses [PlaylistProgress] as the value type (song UID + position).
 */
interface FolderProgressStore {
    fun get(folderKey: String): PlaylistProgress?

    fun set(folderKey: String, progress: PlaylistProgress)

    fun clear(folderKey: String)
}

@Singleton
class FolderProgressStoreImpl
@Inject
constructor(@ApplicationContext context: Context) : FolderProgressStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(folderKey: String): PlaylistProgress? {
        val raw = prefs.getString(key(folderKey), null) ?: return null
        return PlaylistProgress.parse(raw)
    }

    override fun set(folderKey: String, progress: PlaylistProgress) {
        L.d("Saving folder progress for $folderKey: song=${progress.songUid} @ ${progress.positionMs}ms")
        prefs.edit { putString(key(folderKey), progress.serialize()) }
    }

    override fun clear(folderKey: String) {
        prefs.edit { remove(key(folderKey)) }
    }

    private fun key(folderKey: String): String {
        // Path keys may be long / contain separators — keep prefs keys stable and safe.
        val safe = folderKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "progress_$safe"
    }

    private companion object {
        const val PREFS_NAME = "folder_progress"
    }
}
