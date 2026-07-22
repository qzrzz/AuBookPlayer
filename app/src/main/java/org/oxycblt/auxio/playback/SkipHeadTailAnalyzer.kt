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
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteOrder
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
 * 3. For tails, reverse the PCM so index 0 is the song end (common outro is at the edge).
 * 4. Pairwise compare with lag-tolerant envelope + correlation from the edge inward.
 * 5. Use the median pairwise match length as the suggested skip duration.
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

            // Heads: index 0 = song start.
            val heads =
                usable.mapNotNull { song ->
                    extractEdge(song, fromStart = true)?.let { clip -> song to clip }
                }
            // Tails: reverse so index 0 = song end (outro edge).
            val tails =
                usable.mapNotNull { song ->
                    extractEdge(song, fromStart = false)?.let { clip ->
                        song to clip.reversedArray()
                    }
                }

            L.d("Decoded ${heads.size} heads, ${tails.size} tails")

            val headMs = estimateCommonEdgeMs(heads.map { it.second }, label = "head")
            val tailMs = estimateCommonEdgeMs(tails.map { it.second }, label = "tail")

            val headSec = snapSeconds(headMs)
            val tailSec = snapSeconds(tailMs)
            L.d(
                "Smart skip result: head=${headSec}s tail=${tailSec}s " +
                    "(raw head=${headMs}ms tail=${tailMs}ms)"
            )
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
            // Prefer a spread sample when the current song is unknown.
            if (allSongs.size <= MAX_SONGS) return allSongs
            val step = (allSongs.size - 1).toFloat() / (MAX_SONGS - 1)
            return (0 until MAX_SONGS).map { i -> allSongs[(i * step).toInt()] }
        }
        val from = (idx - NEIGHBORS).coerceAtLeast(0)
        val to = (idx + NEIGHBORS + 1).coerceAtMost(allSongs.size)
        var window = allSongs.subList(from, to).toList()
        // Pad with more neighbors if the window is small at list edges.
        if (window.size < MAX_SONGS) {
            val need = MAX_SONGS - window.size
            val before = allSongs.subList(0, from)
            val after = allSongs.subList(to, allSongs.size)
            val extra = (before.takeLast(need) + after.take(need)).take(need)
            window = (window + extra).distinctBy { it.uid }.take(MAX_SONGS)
        }
        return window
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
            val trackIndex =
                (0 until extractor.trackCount).firstOrNull { i ->
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
            val pcmEncoding =
                if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }

            // Seek may land earlier than startMs; we discard samples before startUs below.
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val startUs = startMs * 1000L
            val endUs = (startMs + durationMs) * 1000L
            val expectedSamples =
                (TARGET_SAMPLE_RATE * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
            val samples = ArrayList<Float>(expectedSamples)
            var inputDone = false
            var outputDone = false

            // Decimate source-rate mono samples down to TARGET_SAMPLE_RATE, but only count
            // samples that fall inside [startUs, endUs].
            val step = srcSampleRate.toDouble() / TARGET_SAMPLE_RATE
            var nextSampleAt = 0.0
            var srcSampleIndex = 0.0
            var inRangeStarted = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        val sampleTime = extractor.sampleTime
                        if (sampleSize < 0 || sampleTime < 0 || sampleTime > endUs) {
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
                        val pts = info.presentationTimeUs
                        if (info.size > 0 && pts <= endUs) {
                            // Drop any audio from before the intended start (seek overshoot).
                            if (pts + 0 >= startUs) {
                                if (!inRangeStarted) {
                                    // Reset decimation so the first kept sample maps to index 0.
                                    nextSampleAt = 0.0
                                    srcSampleIndex = 0.0
                                    inRangeStarted = true
                                }
                                val outBuf = codec.getOutputBuffer(outIndex)!!
                                outBuf.order(ByteOrder.LITTLE_ENDIAN)
                                when (pcmEncoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> {
                                        val floatCount = info.size / 4
                                        var i = 0
                                        while (i + channelCount <= floatCount) {
                                            var sum = 0f
                                            for (c in 0 until channelCount) {
                                                sum +=
                                                    outBuf.getFloat(
                                                        info.offset + (i + c) * 4
                                                    )
                                            }
                                            val mono = sum / channelCount
                                            if (srcSampleIndex + 1e-9 >= nextSampleAt) {
                                                samples.add(mono)
                                                nextSampleAt += step
                                            }
                                            srcSampleIndex += 1.0
                                            i += channelCount
                                        }
                                    }
                                    else -> {
                                        // Default: 16-bit little-endian PCM (most devices).
                                        val shortCount = info.size / 2
                                        var i = 0
                                        while (i + channelCount <= shortCount) {
                                            var sum = 0
                                            for (c in 0 until channelCount) {
                                                sum +=
                                                    outBuf
                                                        .getShort(info.offset + (i + c) * 2)
                                                        .toInt()
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
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (pts > endUs) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Decoder may renegotiate PCM encoding; re-read if present.
                    }
                }
            }

            if (samples.size < TARGET_SAMPLE_RATE / 2) {
                // Less than ~0.5s of audio — not useful.
                return null
            }
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
     * Estimate common edge length (ms) across mono PCM clips by pairwise lag-tolerant comparison,
     * then taking the median of successful pair matches.
     */
    private fun estimateCommonEdgeMs(clips: List<FloatArray>, label: String): Long {
        if (clips.size < 2) return 0L
        val matches = ArrayList<Long>()
        for (i in clips.indices) {
            for (j in i + 1 until clips.size) {
                val ms = commonEdgeMs(clips[i], clips[j])
                if (ms >= MIN_MATCH_MS) {
                    matches.add(ms)
                    L.d("Pair $label[$i,$j] match=${ms}ms")
                }
            }
        }
        if (matches.isEmpty()) {
            L.d("No $label pairs matched above ${MIN_MATCH_MS}ms")
            return 0L
        }
        matches.sort()
        // Median is more robust than a low percentile for real shared jingles.
        val median = matches[matches.size / 2]
        L.d("$label matches=$matches median=${median}ms")
        return median
    }

    /**
     * Find how long two edge-aligned clips stay similar, walking inward from the edge.
     *
     * A short lag search (±[MAX_LAG_MS]) is done first so tracks with slightly different
     * leading silence still align. Pure silence alone is not treated as a shared intro/outro
     * unless followed by matching non-quiet content (or both sides stay quiet only briefly).
     */
    private fun commonEdgeMs(a: FloatArray, b: FloatArray): Long {
        val window = (TARGET_SAMPLE_RATE * WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
        val maxLagSamples =
            (TARGET_SAMPLE_RATE * MAX_LAG_MS / 1000L).toInt().coerceAtLeast(window)
        val lagStep = (window / 2).coerceAtLeast(1)

        // Find best lag so that a[lag..] aligns with b[0..] (or vice versa via negative lag).
        var bestLag = 0
        var bestScore = -1f
        var lag = -maxLagSamples
        while (lag <= maxLagSamples) {
            val score = alignmentScore(a, b, lag, window)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
            lag += lagStep
        }
        if (bestScore < ALIGN_MIN_SCORE) {
            return 0L
        }

        val aOff = if (bestLag > 0) bestLag else 0
        val bOff = if (bestLag < 0) -bestLag else 0
        val available =
            min(a.size - aOff, b.size - bOff).coerceAtLeast(0)
        val maxWindows = available / window
        if (maxWindows <= 0) return 0L

        var matching = 0
        var contentMatched = 0
        var silenceOnly = 0
        for (w in 0 until maxWindows) {
            val sa = aOff + w * window
            val sb = bOff + w * window
            val ea = windowEnergy(a, sa, window)
            val eb = windowEnergy(b, sb, window)
            val corr = windowCorrelation(a, b, sa, sb, window)

            val bothQuiet = ea < QUIET_ENERGY && eb < QUIET_ENERGY
            val energyClose =
                (ea + eb) < 1e-6f ||
                    abs(ea - eb) / maxOf(ea, eb, 1e-6f) < ENERGY_RATIO_TOLERANCE
            val similar =
                if (bothQuiet) {
                    true
                } else {
                    corr >= CORR_THRESHOLD && energyClose
                }

            if (!similar) break
            matching++
            if (bothQuiet) {
                silenceOnly++
            } else {
                contentMatched++
                silenceOnly = 0 // reset pure-silence streak after real content
            }

            // Stop if we only ever saw silence for too long with no shared content.
            if (contentMatched == 0 && matching > MAX_LEADING_SILENCE_WINDOWS) {
                // Keep the silence trim only (will be validated below).
                break
            }
        }

        val ms = matching * WINDOW_MS
        // Require either real shared content, or a short mutual silence pad.
        return when {
            contentMatched > 0 && ms >= MIN_MATCH_MS -> ms
            contentMatched == 0 &&
                matching in 1..MAX_LEADING_SILENCE_WINDOWS &&
                ms >= MIN_SILENCE_MATCH_MS -> ms
            else -> 0L
        }
    }

    /** Score how well [a] and [b] align when [a] is shifted by [lagSamples] relative to [b]. */
    private fun alignmentScore(
        a: FloatArray,
        b: FloatArray,
        lagSamples: Int,
        window: Int,
    ): Float {
        val aOff = if (lagSamples > 0) lagSamples else 0
        val bOff = if (lagSamples < 0) -lagSamples else 0
        // Score a few windows near the edge.
        var total = 0f
        var count = 0
        for (w in 0 until ALIGN_WINDOWS) {
            val sa = aOff + w * window
            val sb = bOff + w * window
            if (sa + window > a.size || sb + window > b.size) break
            val ea = windowEnergy(a, sa, window)
            val eb = windowEnergy(b, sb, window)
            val corr = windowCorrelation(a, b, sa, sb, window)
            val bothQuiet = ea < QUIET_ENERGY && eb < QUIET_ENERGY
            total +=
                if (bothQuiet) {
                    0.5f // silence is weak evidence for alignment
                } else {
                    corr.coerceAtLeast(0f)
                }
            count++
        }
        return if (count == 0) -1f else total / count
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
        startA: Int,
        startB: Int,
        len: Int,
    ): Float {
        val endA = min(startA + len, a.size)
        val endB = min(startB + len, b.size)
        val n = min(endA - startA, endB - startB)
        if (n <= 1) return 0f
        var sumA = 0.0
        var sumB = 0.0
        for (i in 0 until n) {
            sumA += a[startA + i]
            sumB += b[startB + i]
        }
        val meanA = sumA / n
        val meanB = sumB / n
        var num = 0.0
        var denA = 0.0
        var denB = 0.0
        for (i in 0 until n) {
            val da = a[startA + i] - meanA
            val db = b[startB + i] - meanB
            num += da * db
            denA += da * da
            denB += db * db
        }
        val den = sqrt(denA * denB)
        if (den < 1e-9) {
            // Both nearly constant — only treat as correlated if near silence.
            return if (abs(meanA) < 0.02 && abs(meanB) < 0.02) 1f else 0f
        }
        return (num / den).toFloat().coerceIn(-1f, 1f)
    }

    private fun snapSeconds(ms: Long): Int {
        if (ms < MIN_MATCH_MS && ms < MIN_SILENCE_MATCH_MS) return 0
        // Round to nearest second, prefer slight under-skip so we don't cut unique speech.
        val sec = (ms / 1000.0).toInt().coerceAtLeast(if (ms >= MIN_MATCH_MS) 1 else 0)
        return sec.coerceIn(0, MAX_SKIP_SECONDS)
    }

    companion object {
        private const val MAX_SONGS = 6
        private const val NEIGHBORS = 2
        /** How much of each edge to decode. */
        private const val ANALYZE_SECONDS = 60
        private const val TARGET_SAMPLE_RATE = 8000
        private const val WINDOW_MS = 100L
        /** Minimum shared non-trivial match. */
        private const val MIN_MATCH_MS = 1500L
        private const val MIN_SILENCE_MATCH_MS = 1000L
        private const val MIN_SONG_DURATION_MS = 15_000L
        private const val MAX_SKIP_SECONDS = 120
        /** Pearson correlation threshold for non-quiet windows. */
        private const val CORR_THRESHOLD = 0.75f
        private const val QUIET_ENERGY = 0.015f
        private const val ENERGY_RATIO_TOLERANCE = 0.55f
        /** Max leading silence counted without shared content (~4s). */
        private const val MAX_LEADING_SILENCE_WINDOWS = 40
        /** Max alignment lag between tracks. */
        private const val MAX_LAG_MS = 1500L
        private const val ALIGN_WINDOWS = 8
        private const val ALIGN_MIN_SCORE = 0.35f
    }
}
