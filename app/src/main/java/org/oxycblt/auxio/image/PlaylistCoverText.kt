/*
 * Copyright (c) 2026 Auxio Project
 * PlaylistCoverText.kt is part of Auxio.
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

package org.oxycblt.auxio.image

/**
 * Derive a short cover label from playlist song titles when no cover art is available.
 *
 * Algorithm:
 * 1. Take up to the first [maxTitles] titles.
 * 2. Tokenize each title into words (split on whitespace / punctuation).
 * 3. Rank tokens by how many titles they appear in (document frequency).
 * 4. Walk the ranked tokens and append characters until [maxChars] are collected.
 */
fun derivePlaylistCoverText(
    titles: List<String>,
    maxTitles: Int = 10,
    maxChars: Int = 4,
): String {
    require(maxChars > 0)
    val sample = titles.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(maxTitles).toList()
    if (sample.isEmpty()) return ""

    // token (lowercase for ranking) → (document frequency, first-seen original spelling)
    val frequency = LinkedHashMap<String, Int>()
    val originals = LinkedHashMap<String, String>()

    for (title in sample) {
        val tokens = tokenizeTitle(title)
        // Count each distinct token once per title so long titles don't dominate.
        for (token in tokens.distinctBy { it.lowercase() }) {
            val key = token.lowercase()
            if (!isUsefulToken(key)) continue
            frequency[key] = (frequency[key] ?: 0) + 1
            originals.putIfAbsent(key, token)
        }
    }

    if (frequency.isEmpty()) {
        // No useful tokens — fall back to the first letters/digits of the first title.
        return sample.first().filter { it.isLetterOrDigit() }.take(maxChars)
    }

    val ranked =
        frequency.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenByDescending { it.key.length }
                .thenBy { it.key }
        )

    val out = StringBuilder(maxChars)
    for (entry in ranked) {
        if (out.length >= maxChars) break
        val original = originals[entry.key] ?: entry.key
        for (ch in original) {
            if (out.length >= maxChars) break
            if (ch.isWhitespace()) continue
            out.append(ch)
        }
    }

    if (out.isEmpty()) {
        return sample.first().filter { it.isLetterOrDigit() }.take(maxChars)
    }
    return out.toString()
}

/** Split a title into word tokens on whitespace and common punctuation. */
internal fun tokenizeTitle(title: String): List<String> {
    // Keep letters and numbers (Unicode-aware) as token characters.
    // Everything else is a separator so "三体-01" and "三体 01" both yield "三体".
    return TOKEN_REGEX.findAll(title).map { it.value }.filter { it.isNotEmpty() }.toList()
}

private fun isUsefulToken(normalized: String): Boolean {
    if (normalized.isEmpty()) return false
    // Pure numbers are almost always episode indices, not series names.
    if (normalized.all { it.isDigit() }) return false
    // Single ASCII letters are too noisy to form a useful cover.
    if (normalized.length == 1 && normalized[0].code < 128) return false
    if (normalized in STOPWORDS) return false
    return true
}

private val TOKEN_REGEX = Regex("""[\p{L}\p{N}]+""")

/** Tiny stopword list for mixed Chinese/English audiobook titles. */
private val STOPWORDS =
    setOf(
        "the",
        "a",
        "an",
        "of",
        "and",
        "or",
        "to",
        "in",
        "on",
        "for",
        "vol",
        "volume",
        "chapter",
        "ep",
        "episode",
        "part",
        "disc",
        "cd",
        "track",
        "集",
        "章",
        "回",
        "部",
        "卷",
        "篇",
        "季",
        "期",
        "话",
        "話",
        "第",
    )
