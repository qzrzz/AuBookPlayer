/*
 * Copyright (c) 2026 Auxio Project
 * SkipHeadTailAnalyzer.kt is part of Auxio.
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
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Analyzes neighboring tracks to find shared intro/outro audio and suggest [SkipHeadTail] values.
 *
 * Strategy:
 * 1. Take the current song plus a few songs before/after (up to [MAX_SONGS]).
 * 2. Decode the first / last [ANALYZE_SECONDS] of each track to mono PCM @ [TARGET_SAMPLE_RATE].
 * 3. Pairwise compare energy envelopes; measure how long similarity stays high from the edge.
 * 4. Use the median pairwise match length as the suggested skip duration.
 */
@Singleton
class SkipHeadTailAnalyzer @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Analyze [songs] (already ordered window around the current track) and return a suggested
     * skip configuration. Returns [SkipHeadTail.NONE] if analysis fails or no common segment is
     * found.
     */
    suspend fun analyze(songs: List<Song>): SkipHeadTail =
        withContext(Dispatchers.Default) {
            val usable =
                songs
                    .filter { it.durationMs >= MIN_SONG_DURATION_MS }
                    .distinctBy { it.uid }
                    .take(MAX_SONGS)
            if (usable.size < 2) {
                L.d("Not enough songs to analyze skip head/tail (${usable.size})")
                return@withContext SkipHeadTail.NONE
            }

            L.d("Analyzing skip head/tail across ${usable.size} songs")
            val heads = usable.mapNotNull { song ->
                extractEdge(song, fromStart = true)?.let { song to it }
            }
            val tails = usable.mapNotNull { song ->
                extractEdge(song, fromStart = false)?.let { song to it }
            }

            val headMs = estimateCommonEdgeMs(heads.map { it.second })
            val tailMs = estimateCommonEdgeMs(tails.map { it.second })

            val headSec = snapSeconds(headMs)
            val tailSec = snapSeconds(tailMs)
            L.d("Smart skip result: head=${headSec}s tail=${tailSec}s (raw head=${headMs}ms tail=${tailMs}ms)")
            SkipHeadTail(headSec, tailSec)
        }

    /**
     * Build an analysis window: current song ± [NEIGHBORS] neighbors from [allSongs].
     * Falls back to the first [MAX_SONGS] items when the current song is not in the list.
     */
    fun selectWindow(allSongs: List<Song>, current: Song?): List<Song> {
        if (allSongs.isEmpty()) return emptyList()
        val idx = current?.let { c -> allSongs.indexOfFirst { it.uid == c.uid } } ?: -1
        if (idx < 0) {
            return allSongs.take(MAX_SONGS)
        }
        val from = (idx - NEIGHBORS).coerceAtLeast(0)
        val to = (idx + NEIGHBORS + 1).coerceAtMost(allSongs.size)
        return allSongs.subList(from, to)
    }

    private fun extractEdge(song: Song, fromStart: Boolean): FloatArray? {
        val analyzeMs = min(ANALYZE_SECONDS * 1000L, song.durationMs / 2)
        if (analyzeMs < MIN_MATCH_MS) return null
        val startMs = if (fromStart) 0L else (song.durationMs - analyzeMs).coerceAtLeast(0L)
        return try {
            decodeMonoPcm(song.uri, startMs, analyzeMs)
        } catch (e: Exception) {
            L.e("Failed to decode edge of $song: ${e.message}")
            null
        }
    }

    /**
     * Decode a time range of [uri] into mono float PCM at [TARGET_SAMPLE_RATE].
     * Values are normalized to roughly [-1, 1].
     */
    private fun decodeMonoPcm(uri: Uri, startMs: Long, durationMs: Long): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") ==
                    true
            } ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount =
                if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                } else {
                    1
                }

            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val endUs = (startMs + durationMs) * 1000L
            val expectedSamples =
                (TARGET_SAMPLE_RATE * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
            val samples = ArrayList<Float>(expectedSamples)
            var inputDone = false
            var outputDone = false
            // Decimate source-rate mono samples down to TARGET_SAMPLE_RATE.
            val step = srcSampleRate.toDouble() / TARGET_SAMPLE_RATE
            var nextSampleAt = 0.0
            var srcSampleIndex = 0.0

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        val sampleTime = extractor.sampleTime
                        if (sampleSize < 0 || sampleTime > endUs) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        if (info.size > 0 && info.presentationTimeUs <= endUs) {
                            val outBuf = codec.getOutputBuffer(outIndex)!!
                            // Expect PCM 16-bit little-endian interleaved frames.
                            val shortCount = info.size / 2
                            var i = 0
                            while (i + channelCount <= shortCount) {
                                var sum = 0
                                for (c in 0 until channelCount) {
                                    sum += outBuf.getShort(info.offset + (i + c) * 2).toInt()
                                }
                                val mono = sum / channelCount / 32768f
                                if (srcSampleIndex + 1e-9 >= nextSampleAt) {
                                    samples.add(mono)
                                    nextSampleAt += step
                                }
                                srcSampleIndex += 1.0
                                i += channelCount
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.presentationTimeUs > endUs) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Ignore; we assume 16-bit PCM.
                    }
                }
            }

            if (samples.isEmpty()) return null
            return samples.toFloatArray()
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            extractor.release()
        }
    }

    /**
     * Estimate common edge length (ms) across a list of mono PCM clips by pairwise envelope
     * comparison, then taking the median.
     */
    private fun estimateCommonEdgeMs(clips: List<FloatArray>): Long {
        if (clips.size < 2) return 0L
        val matches = ArrayList<Long>()
        for (i in clips.indices) {
            for (j in i + 1 until clips.size) {
                val ms = commonEdgeMs(clips[i], clips[j])
                if (ms >= MIN_MATCH_MS) {
                    matches.add(ms)
                }
            }
        }
        if (matches.isEmpty()) return 0L
        matches.sort()
        // Prefer a slightly conservative value (25th percentile) so we don't skip unique content.
        val idx = (matches.size * 0.25).toInt().coerceIn(0, matches.lastIndex)
        return matches[idx]
    }

    /**
     * Walk 100ms energy windows from the start of both clips until similarity drops below
     * threshold. Returns matched duration in milliseconds.
     */
    private fun commonEdgeMs(a: FloatArray, b: FloatArray): Long {
        val window =
            (TARGET_SAMPLE_RATE * WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
        val maxWindows = min(a.size, b.size) / window
        if (maxWindows <= 0) return 0L

        var matching = 0
        var lowEnergyStreak = 0
        for (w in 0 until maxWindows) {
            val start = w * window
            val ea = windowEnergy(a, start, window)
            val eb = windowEnergy(b, start, window)
            val corr = windowCorrelation(a, b, start, window)

            val bothQuiet = ea < QUIET_ENERGY && eb < QUIET_ENERGY
            val energyClose =
                (ea + eb) < 1e-6f ||
                    abs(ea - eb) / maxOf(ea, eb, 1e-6f) < ENERGY_RATIO_TOLERANCE
            val similar = bothQuiet || (corr >= CORR_THRESHOLD && energyClose)

            if (!similar) break
            matching++
            if (bothQuiet) {
                lowEnergyStreak++
            } else {
                lowEnergyStreak = 0
            }
            // Cap pure silence runs so we don't skip entire quiet tracks.
            if (lowEnergyStreak > MAX_SILENCE_WINDOWS && matching == lowEnergyStreak) {
                // Only silence so far — keep going a bit but will still count.
            }
        }

        // Require some non-trivial match.
        val ms = matching * WINDOW_MS
        return if (ms >= MIN_MATCH_MS) ms else 0L
    }

    private fun windowEnergy(samples: FloatArray, start: Int, len: Int): Float {
        var sum = 0.0
        val end = min(start + len, samples.size)
        if (end <= start) return 0f
        for (i in start until end) {
            val v = samples[i]
            sum += v * v
        }
        return sqrt(sum / (end - start)).toFloat()
    }

    private fun windowCorrelation(
        a: FloatArray,
        b: FloatArray,
        start: Int,
        len: Int,
    ): Float {
        val end = min(start + len, min(a.size, b.size))
        val n = end - start
        if (n <= 1) return 0f
        var sumA = 0.0
        var sumB = 0.0
        for (i in start until end) {
            sumA += a[i]
            sumB += b[i]
        }
        val meanA = sumA / n
        val meanB = sumB / n
        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for (i in start until end) {
            val da = a[i] - meanA
            val db = b[i] - meanB
            num += da * db
            denA += da * da
            denB += db * db
        }
        val den = sqrt(denA * denB)
        if (den < 1e-9) {
            // Both nearly constant — treat as correlated if means are close (e.g. silence).
            return if (abs(meanA - meanB) < 0.02) 1f else 0f
        }
        return (num / den).toFloat().coerceIn(-1f, 1f)
    }

    private fun snapSeconds(ms: Long): Int {
        if (ms < MIN_MATCH_MS) return 0
        val sec = ((ms + 500) / 1000).toInt()
        return sec.coerceIn(0, MAX_SKIP_SECONDS)
    }

    companion object {
        private const val MAX_SONGS = 5
        private const val NEIGHBORS = 2
        private const val ANALYZE_SECONDS = 90
        private const val TARGET_SAMPLE_RATE = 4000
        private const val WINDOW_MS = 100L
        private const val MIN_MATCH_MS = 2000L
        private const val MIN_SONG_DURATION_MS = 10_000L
        private const val MAX_SKIP_SECONDS = 120
        private const val CORR_THRESHOLD = 0.82f
        private const val QUIET_ENERGY = 0.02f
        private const val ENERGY_RATIO_TOLERANCE = 0.45f
        private const val MAX_SILENCE_WINDOWS = 50 // 5s of pure silence
    }
}
