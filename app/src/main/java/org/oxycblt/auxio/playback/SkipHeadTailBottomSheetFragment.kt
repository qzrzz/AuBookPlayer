package org.oxycblt.auxio.playback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogSkipHeadTailBinding
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.showToast

/**
 * Bottom sheet for configuring per-playlist skip-head / skip-tail durations,
 * including smart detection from neighboring tracks.
 */
@AndroidEntryPoint
class SkipHeadTailBottomSheetFragment :
    ViewBindingBottomSheetDialogFragment<DialogSkipHeadTailBinding>() {

    private val playbackModel: PlaybackViewModel by activityViewModels()

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogSkipHeadTailBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: DialogSkipHeadTailBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.skipSmartItem.setOnClickListener {
            if (playbackModel.skipAnalyzeState.value is SkipAnalyzeState.Running) return@setOnClickListener
            playbackModel.analyzeSkipHeadTail()
        }
        binding.skipHeadItem.setOnClickListener { view ->
            showDurationMenu(view, isHead = true)
        }
        binding.skipTailItem.setOnClickListener { view ->
            showDurationMenu(view, isHead = false)
        }

        collectImmediately(playbackModel.playlistSkip, ::updateSkip)
        collectImmediately(playbackModel.skipAnalyzeState, ::updateAnalyzeState)
    }

    override fun onDestroyBinding(binding: DialogSkipHeadTailBinding) {
        super.onDestroyBinding(binding)
        // Leave Idle so a reopened sheet doesn't re-toast an old success.
        playbackModel.consumeSkipAnalyzeState()
    }

    private fun updateSkip(skip: SkipHeadTail) {
        val binding = requireBinding()
        binding.skipHeadStatus.text = formatSeconds(skip.headSeconds)
        binding.skipTailStatus.text = formatSeconds(skip.tailSeconds)
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

    private fun showDurationMenu(anchor: View, isHead: Boolean) {
        val popup = PopupMenu(requireContext(), anchor)
        val current = playbackModel.playlistSkip.value
        val currentSeconds = if (isHead) current.headSeconds else current.tailSeconds

        SkipHeadTail.DURATION_OPTIONS_SECONDS.forEachIndexed { index, seconds ->
            val label = formatSeconds(seconds)
            val item = popup.menu.add(0, seconds, index, label)
            item.isCheckable = true
            item.isChecked = seconds == currentSeconds
        }
        // Also allow the currently detected non-preset value to appear checked.
        if (currentSeconds !in SkipHeadTail.DURATION_OPTIONS_SECONDS && currentSeconds > 0) {
            val item =
                popup.menu.add(
                    0,
                    currentSeconds,
                    SkipHeadTail.DURATION_OPTIONS_SECONDS.size,
                    formatSeconds(currentSeconds),
                )
            item.isCheckable = true
            item.isChecked = true
        }
        popup.menu.setGroupCheckable(0, true, true)

        popup.setOnMenuItemClickListener { menuItem ->
            val seconds = menuItem.itemId
            val next =
                if (isHead) {
                    current.copy(headSeconds = seconds)
                } else {
                    current.copy(tailSeconds = seconds)
                }
            playbackModel.setPlaylistSkip(next)
            true
        }
        popup.show()
    }

    private fun formatSeconds(seconds: Int): String =
        if (seconds <= 0) {
            getString(R.string.fmt_skip_seconds_off)
        } else {
            getString(R.string.fmt_skip_seconds, seconds)
        }
}
