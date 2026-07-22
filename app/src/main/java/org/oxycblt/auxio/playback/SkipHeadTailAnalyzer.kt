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

            // Heads: chronological PCM, index 0 = song start.
            val heads =
                usable.mapNotNull { song ->
                    extractEdge(song, fromStart = true)?.let { clip -> song to clip }
                }
            // Tails: chronological PCM of the last ANALYZE_SECONDS (index 0 is earlier,
            // last samples are the true song end).
            val tails =
                usable.mapNotNull { song ->
                    extractEdge(song, fromStart = false)?.let { clip -> song to clip }
                }

            L.d("Decoded ${heads.size} heads, ${tails.size} tails")

            val headMs = estimateCommonPrefixMs(heads.map { it.second }, label = "head")
            val tailMs = estimateCommonSuffixMs(tails.map { it.second }, label = "tail")

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

            // Prefer closest sync so tail seeks land nearer the intended start.
            // Samples before startUs are still discarded below.
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

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

    /** Median pairwise common-prefix length (for intros at song start). */
    private fun estimateCommonPrefixMs(clips: List<FloatArray>, label: String): Long {
        if (clips.size < 2) return 0L
        val matches = ArrayList<Long>()
        for (i in clips.indices) {
            for (j in i + 1 until clips.size) {
                val ms = commonPrefixMs(clips[i], clips[j])
                if (ms >= MIN_MATCH_MS) {
                    matches.add(ms)
                    L.d("Pair $label[$i,$j] match=${ms}ms")
                }
            }
        }
        return medianOrZero(matches, label)
    }

    /**
     * Median pairwise common-suffix length (for outros at song end).
     *
     * Tails are chronological (last sample = song end). We walk backward from the end after
     * stripping per-track trailing silence so different fade-out lengths do not break alignment.
     */
    private fun estimateCommonSuffixMs(clips: List<FloatArray>, label: String): Long {
        if (clips.size < 2) return 0L
        val matches = ArrayList<Long>()
        val silencePads = ArrayList<Long>()
        for (clip in clips) {
            silencePads.add(trailingSilenceMs(clip))
        }
        for (i in clips.indices) {
            for (j in i + 1 until clips.size) {
                val ms = commonSuffixMs(clips[i], clips[j])
                if (ms >= MIN_MATCH_MS) {
                    matches.add(ms)
                    L.d("Pair $label[$i,$j] contentMatch=${ms}ms")
                }
            }
        }
        if (matches.isEmpty()) {
            L.d("No $label pairs matched above ${MIN_MATCH_MS}ms")
            return 0L
        }
        matches.sort()
        val contentMedian = matches[matches.size / 2]
        // Include typical trailing silence so skip covers jingle + fade/silence.
        silencePads.sort()
        val silenceMedian = silencePads[silencePads.size / 2]
        // Cap silence contribution so we don't over-skip unique content on short fades.
        val silenceAdd = silenceMedian.coerceAtMost(MAX_TAIL_SILENCE_ADD_MS)
        val total = contentMedian + silenceAdd
        L.d(
            "$label contentMedian=${contentMedian}ms silenceMedian=${silenceMedian}ms " +
                "→ total=${total}ms"
        )
        return total
    }

    private fun medianOrZero(matches: ArrayList<Long>, label: String): Long {
        if (matches.isEmpty()) {
            L.d("No $label pairs matched above ${MIN_MATCH_MS}ms")
            return 0L
        }
        matches.sort()
        val median = matches[matches.size / 2]
        L.d("$label matches=$matches median=${median}ms")
        return median
    }

    /**
     * How long trailing quiet audio is at the end of [clip], in ms.
     * Walks backward in [WINDOW_MS] steps until energy rises.
     */
    private fun trailingSilenceMs(clip: FloatArray): Long {
        val window = (TARGET_SAMPLE_RATE * WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
        var quietWindows = 0
        var end = clip.size
        while (end - window >= 0) {
            val start = end - window
            if (windowEnergy(clip, start, window) >= QUIET_ENERGY) break
            quietWindows++
            end = start
            if (quietWindows > MAX_TRAILING_SILENCE_WINDOWS) break
        }
        return quietWindows * WINDOW_MS
    }

    /**
     * Index just past the last non-quiet sample (exclusive end of content), with a small pad of
     * quiet samples retained so fades are not cut harshly.
     */
    private fun contentEndIndex(clip: FloatArray): Int {
        val window = (TARGET_SAMPLE_RATE * WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
        var end = clip.size
        while (end - window >= 0) {
            val start = end - window
            if (windowEnergy(clip, start, window) >= QUIET_ENERGY) {
                // Keep a short pad of silence after content for fade tails.
                val pad = (TARGET_SAMPLE_RATE * TAIL_SILENCE_PAD_MS / 1000L).toInt()
                return min(clip.size, end + pad)
            }
            end = start
        }
        return clip.size
    }

    /**
     * Common prefix from song start (head). Lag-tolerant so slightly different leading silence
     * still lines up.
     */
    private fun commonPrefixMs(a: FloatArray, b: FloatArray): Long {
        val window = (TARGET_SAMPLE_RATE * WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
        val maxLagSamples =
            (TARGET_SAMPLE_RATE * MAX_LAG_MS / 1000L).toInt().coerceAtLeast(window)
        val lagStep = (window / 2).coerceAtLeast(1)

        var bestLag = 0
        var bestScore = -1f
        var lag = -maxLagSamples
        while (lag <= maxLagSamples) {
            val score = alignmentScoreForward(a, b, lag, window)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
            lag += lagStep
        }
        if (bestScore < ALIGN_MIN_SCORE) return 0L

        val aOff = if (bestLag > 0) bestLag else 0
        val bOff = if (bestLag < 0) -bestLag else 0
        return walkMatchForward(a, b, aOff, bOff, window)
    }

    /**
     * Common suffix from song end (tail). Compares chronological tail clips by walking backward
     * from each track's content end (after ignoring trailing silence).
     */
    private fun commonSuffixMs(a: FloatArray, b: FloatArray): Long {
        val window = (TARGET_SAMPLE_RATE * WINDOW_MS / 1000L).toInt().coerceAtLeast(1)
        val endA = contentEndIndex(a)
        val endB = contentEndIndex(b)
        if (endA < window || endB < window) return 0L

        // Lag on content ends so tracks with slightly different outro timing still align.
        val maxLagSamples =
            (TARGET_SAMPLE_RATE * MAX_TAIL_LAG_MS / 1000L).toInt().coerceAtLeast(window)
        val lagStep = (window / 2).coerceAtLeast(1)

        var bestLag = 0 // applied to A's end relative to B's end
        var bestScore = -1f
        var lag = -maxLagSamples
        while (lag <= maxLagSamples) {
            val score = alignmentScoreBackward(a, b, endA + lag, endB, window)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
            lag += lagStep
        }
        if (bestScore < ALIGN_MIN_SCORE) {
            L.d("Tail alignment failed (bestScore=$bestScore)")
            return 0L
        }

        val alignedEndA = (endA + bestLag).coerceIn(window, a.size)
        val alignedEndB = endB.coerceIn(window, b.size)
        return walkMatchBackward(a, b, alignedEndA, alignedEndB, window)
    }

    /** Walk forward from offsets while windows stay similar. Returns matched ms. */
    private fun walkMatchForward(
        a: FloatArray,
        b: FloatArray,
        aOff: Int,
        bOff: Int,
        window: Int,
    ): Long {
        val available = min(a.size - aOff, b.size - bOff).coerceAtLeast(0)
        val maxWindows = available / window
        if (maxWindows <= 0) return 0L

        var matching = 0
        var contentMatched = 0
        for (w in 0 until maxWindows) {
            val sa = aOff + w * window
            val sb = bOff + w * window
            if (!windowsSimilar(a, b, sa, sb, window)) break
            matching++
            val bothQuiet =
                windowEnergy(a, sa, window) < QUIET_ENERGY &&
                    windowEnergy(b, sb, window) < QUIET_ENERGY
            if (bothQuiet) {
                if (contentMatched == 0 && matching > MAX_LEADING_SILENCE_WINDOWS) break
            } else {
                contentMatched++
            }
        }
        val ms = matching * WINDOW_MS
        return when {
            contentMatched > 0 && ms >= MIN_MATCH_MS -> ms
            contentMatched == 0 &&
                matching in 1..MAX_LEADING_SILENCE_WINDOWS &&
                ms >= MIN_SILENCE_MATCH_MS -> ms
            else -> 0L
        }
    }

    /**
     * Walk backward from exclusive end indices while windows stay similar.
     * Returns matched content duration in ms (silence after content is handled separately).
     */
    private fun walkMatchBackward(
        a: FloatArray,
        b: FloatArray,
        endA: Int,
        endB: Int,
        window: Int,
    ): Long {
        val maxWindows = min(endA, endB) / window
        if (maxWindows <= 0) return 0L

        var matching = 0
        var contentMatched = 0
        for (w in 0 until maxWindows) {
            val sa = endA - (w + 1) * window
            val sb = endB - (w + 1) * window
            if (sa < 0 || sb < 0) break
            if (!windowsSimilar(a, b, sa, sb, window)) break
            matching++
            val bothQuiet =
                windowEnergy(a, sa, window) < QUIET_ENERGY &&
                    windowEnergy(b, sb, window) < QUIET_ENERGY
            if (!bothQuiet) contentMatched++
            // Require shared content; pure silence-from-end is not a useful outro match.
            if (contentMatched == 0 && matching > MAX_TAIL_SILENCE_WALK_WINDOWS) break
        }

        val ms = matching * WINDOW_MS
        return if (contentMatched > 0 && ms >= MIN_MATCH_MS) ms else 0L
    }

    private fun windowsSimilar(
        a: FloatArray,
        b: FloatArray,
        sa: Int,
        sb: Int,
        window: Int,
    ): Boolean {
        val ea = windowEnergy(a, sa, window)
        val eb = windowEnergy(b, sb, window)
        val corr = windowCorrelation(a, b, sa, sb, window)
        val bothQuiet = ea < QUIET_ENERGY && eb < QUIET_ENERGY
        if (bothQuiet) return true
        val energyClose =
            (ea + eb) < 1e-6f ||
                abs(ea - eb) / maxOf(ea, eb, 1e-6f) < ENERGY_RATIO_TOLERANCE
        return corr >= CORR_THRESHOLD && energyClose
    }

    /** Score forward alignment: [a] shifted by [lagSamples] vs [b] at the start. */
    private fun alignmentScoreForward(
        a: FloatArray,
        b: FloatArray,
        lagSamples: Int,
        window: Int,
    ): Float {
        val aOff = if (lagSamples > 0) lagSamples else 0
        val bOff = if (lagSamples < 0) -lagSamples else 0
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
            total += if (bothQuiet) 0.4f else corr.coerceAtLeast(0f)
            count++
        }
        return if (count == 0) -1f else total / count
    }

    /**
     * Score backward alignment near exclusive ends [endA]/[endB].
     * Prefers matching non-quiet content over silence.
     */
    private fun alignmentScoreBackward(
        a: FloatArray,
        b: FloatArray,
        endA: Int,
        endB: Int,
        window: Int,
    ): Float {
        if (endA < window || endB < window) return -1f
        if (endA > a.size || endB > b.size) return -1f
        var total = 0f
        var count = 0
        var contentWindows = 0
        for (w in 0 until ALIGN_WINDOWS) {
            val sa = endA - (w + 1) * window
            val sb = endB - (w + 1) * window
            if (sa < 0 || sb < 0) break
            val ea = windowEnergy(a, sa, window)
            val eb = windowEnergy(b, sb, window)
            val corr = windowCorrelation(a, b, sa, sb, window)
            val bothQuiet = ea < QUIET_ENERGY && eb < QUIET_ENERGY
            if (bothQuiet) {
                total += 0.25f // silence is weak for tail alignment
            } else {
                total += corr.coerceAtLeast(0f)
                contentWindows++
            }
            count++
        }
        if (count == 0) return -1f
        // Require at least some non-quiet evidence near the end.
        if (contentWindows == 0) return 0f
        return total / count
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
        /** Max tracks to decode/compare (current ± neighbors, padded if at list edge). */
        private const val MAX_SONGS = 3
        /** Neighbors on each side of the current track (1 before + current + 1 after = 3). */
        private const val NEIGHBORS = 1
        /** How much of each edge to decode. */
        private const val ANALYZE_SECONDS = 75
        private const val TARGET_SAMPLE_RATE = 8000
        private const val WINDOW_MS = 100L
        /** Minimum shared non-trivial match. */
        private const val MIN_MATCH_MS = 1500L
        private const val MIN_SILENCE_MATCH_MS = 1000L
        private const val MIN_SONG_DURATION_MS = 15_000L
        private const val MAX_SKIP_SECONDS = 120
        /** Pearson correlation threshold for non-quiet windows. */
        private const val CORR_THRESHOLD = 0.72f
        private const val QUIET_ENERGY = 0.015f
        private const val ENERGY_RATIO_TOLERANCE = 0.55f
        /** Max leading silence counted without shared content (~4s). */
        private const val MAX_LEADING_SILENCE_WINDOWS = 40
        /** How far backward to walk pure silence at the end before giving up. */
        private const val MAX_TAIL_SILENCE_WALK_WINDOWS = 50
        private const val MAX_TRAILING_SILENCE_WINDOWS = 80 // 8s measured silence
        /** Silence pad kept after content end for fade tails. */
        private const val TAIL_SILENCE_PAD_MS = 300L
        /** Max silence added on top of matched outro content. */
        private const val MAX_TAIL_SILENCE_ADD_MS = 8_000L
        /** Max alignment lag for intros. */
        private const val MAX_LAG_MS = 2500L
        /** Max alignment lag for outros (after silence trim, residual only). */
        private const val MAX_TAIL_LAG_MS = 2000L
        private const val ALIGN_WINDOWS = 10
        private const val ALIGN_MIN_SCORE = 0.30f
    }
}
