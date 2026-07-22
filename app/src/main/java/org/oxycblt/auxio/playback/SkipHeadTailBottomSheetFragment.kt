package org.oxycblt.auxio.playback

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSkipHeadTailBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.showToast

/**
 * Bottom sheet for configuring per-playlist skip-head / skip-tail durations.
 *
 * Head/tail can be adjusted one second at a time with − / + steppers (long-press to repeat).
 * Smart detection from neighboring tracks is also available.
 */
@AndroidEntryPoint
class SkipHeadTailBottomSheetFragment :
    ViewBindingBottomSheetDialogFragment<DialogSkipHeadTailBinding>() {

    private val playbackModel: PlaybackViewModel by activityViewModels()
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSkipHeadTailBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: DialogSkipHeadTailBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.skipSmartItem.setOnClickListener {
            if (playbackModel.skipAnalyzeState.value is SkipAnalyzeState.Running) {
                return@setOnClickListener
            }
            playbackModel.analyzeSkipHeadTail()
        }

        bindStepper(
            minus = binding.skipHeadMinus,
            plus = binding.skipHeadPlus,
            isHead = true,
        )
        bindStepper(
            minus = binding.skipTailMinus,
            plus = binding.skipTailPlus,
            isHead = false,
        )

        collectImmediately(playbackModel.playlistSkip, ::updateSkip)
        collectImmediately(playbackModel.skipAnalyzeState, ::updateAnalyzeState)
    }

    override fun onDestroyBinding(binding: DialogSkipHeadTailBinding) {
        stopRepeat()
        super.onDestroyBinding(binding)
        // Leave Idle so a reopened sheet doesn't re-toast an old success.
        playbackModel.consumeSkipAnalyzeState()
    }

    private fun bindStepper(minus: View, plus: View, isHead: Boolean) {
        // Touch handles both single tap (+1s) and long-press repeat; avoid click+touch double-fire.
        minus.setOnTouchListener(repeatTouchListener(isHead, delta = -1))
        plus.setOnTouchListener(repeatTouchListener(isHead, delta = +1))
        minus.isClickable = true
        plus.isClickable = true
    }

    private fun repeatTouchListener(isHead: Boolean, delta: Int): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    // First step immediately; further steps while held after a short delay.
                    adjustSeconds(isHead, delta)
                    val runnable =
                        object : Runnable {
                            override fun run() {
                                adjustSeconds(isHead, delta)
                                repeatHandler.postDelayed(this, REPEAT_INTERVAL_MS)
                            }
                        }
                    stopRepeat()
                    repeatRunnable = runnable
                    repeatHandler.postDelayed(runnable, REPEAT_INITIAL_DELAY_MS)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    stopRepeat()
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    stopRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun stopRepeat() {
        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        repeatRunnable = null
    }

    private fun adjustSeconds(isHead: Boolean, delta: Int) {
        val current = playbackModel.playlistSkip.value
        val nextSeconds =
            (if (isHead) current.headSeconds else current.tailSeconds) + delta
        val clamped = nextSeconds.coerceIn(MIN_SECONDS, MAX_SECONDS)
        if (isHead && clamped == current.headSeconds) return
        if (!isHead && clamped == current.tailSeconds) return
        val next =
            if (isHead) {
                current.copy(headSeconds = clamped)
            } else {
                current.copy(tailSeconds = clamped)
            }
        playbackModel.setPlaylistSkip(next)
    }

    private fun updateSkip(skip: SkipHeadTail) {
        val binding = requireBinding()
        binding.skipHeadStatus.text = formatSeconds(skip.headSeconds)
        binding.skipTailStatus.text = formatSeconds(skip.tailSeconds)
        binding.skipHeadMinus.isEnabled = skip.headSeconds > MIN_SECONDS
        binding.skipHeadPlus.isEnabled = skip.headSeconds < MAX_SECONDS
        binding.skipTailMinus.isEnabled = skip.tailSeconds > MIN_SECONDS
        binding.skipTailPlus.isEnabled = skip.tailSeconds < MAX_SECONDS
    }

    private fun updateAnalyzeState(state: SkipAnalyzeState) {
        val binding = binding ?: return
        when (state) {
            is SkipAnalyzeState.Idle -> {
                binding.skipSmartProgress.isVisible = false
                binding.skipSmartItem.isEnabled = true
                binding.skipSmartStatus.text = getString(R.string.lng_skip_smart_desc)
            }
            is SkipAnalyzeState.Running -> {
                binding.skipSmartProgress.isVisible = true
                binding.skipSmartItem.isEnabled = false
                binding.skipSmartStatus.text = getString(R.string.lng_skip_smart_running)
            }
            is SkipAnalyzeState.Success -> {
                binding.skipSmartProgress.isVisible = false
                binding.skipSmartItem.isEnabled = true
                binding.skipSmartStatus.text = getString(R.string.lng_skip_smart_desc)
                requireContext()
                    .showToast(
                        getString(
                            R.string.lng_skip_smart_success,
                            formatSeconds(state.skip.headSeconds),
                            formatSeconds(state.skip.tailSeconds),
                        )
                    )
                playbackModel.consumeSkipAnalyzeState()
            }
            is SkipAnalyzeState.Error -> {
                binding.skipSmartProgress.isVisible = false
                binding.skipSmartItem.isEnabled = true
                binding.skipSmartStatus.text = getString(R.string.lng_skip_smart_desc)
                requireContext().showToast(errorMessage(state.reason))
                playbackModel.consumeSkipAnalyzeState()
            }
        }
    }

    private fun errorMessage(reason: SkipAnalyzeError): String =
        when (reason) {
            SkipAnalyzeError.NEED_PLAYLIST -> getString(R.string.lng_skip_smart_need_playlist)
            SkipAnalyzeError.NOT_ENOUGH_SONGS -> getString(R.string.lng_skip_smart_not_enough)
            SkipAnalyzeError.NO_COMMON_SEGMENT -> getString(R.string.lng_skip_smart_no_common)
            SkipAnalyzeError.FAILED -> getString(R.string.lng_skip_smart_failed)
        }

    private fun formatSeconds(seconds: Int): String =
        if (seconds <= 0) {
            getString(R.string.fmt_skip_seconds_off)
        } else {
            getString(R.string.fmt_skip_seconds, seconds)
        }

    private companion object {
        const val MIN_SECONDS = 0
        /** Matches smart-detect cap. */
        const val MAX_SECONDS = 120
        const val REPEAT_INITIAL_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 80L
    }
}
