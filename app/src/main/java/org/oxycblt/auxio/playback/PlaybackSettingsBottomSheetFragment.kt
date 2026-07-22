package org.oxycblt.auxio.playback

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogPlaybackSettingsBinding
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.showToast

@AndroidEntryPoint
class PlaybackSettingsBottomSheetFragment :
    ViewBindingBottomSheetDialogFragment<DialogPlaybackSettingsBinding>() {

    private val playbackModel: PlaybackViewModel by activityViewModels()

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogPlaybackSettingsBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: DialogPlaybackSettingsBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        // 1. Repeat Mode
        binding.settingRepeatItem.setOnClickListener {
            playbackModel.toggleRepeatMode()
        }

        // 2. Shuffle Mode
        binding.settingShuffleItem.setOnClickListener {
            playbackModel.toggleShuffled()
        }

        // 3. Playback Speed (single-choice menu)
        binding.settingSpeedItem.setOnClickListener { view ->
            showPlaybackSpeedMenu(view, playbackModel, showToast = false)
        }

        // 4. Skip head / tail (per playlist)
        binding.settingSkipHeadTailItem.setOnClickListener {
            if (!playbackModel.isPlaylistParent.value) {
                requireContext().showToast(R.string.lng_skip_head_tail_need_playlist)
                return@setOnClickListener
            }
            SkipHeadTailBottomSheetFragment().show(parentFragmentManager, "SkipHeadTail")
        }

        // 5. Sleep timer (single-choice menu)
        binding.settingSleepTimerItem.setOnClickListener { view ->
            showSleepTimerMenu(view, playbackModel)
        }

        // 6. Equalizer
        binding.settingEqualizerItem.setOnClickListener {
            openEqualizer()
        }

        // Collect state updates
        collectImmediately(playbackModel.repeatMode, ::updateRepeat)
        collectImmediately(playbackModel.isShuffled, ::updateShuffle)
        collectImmediately(playbackModel.playbackSpeed, ::updateSpeed)
        collectImmediately(
            playbackModel.isPlaylistParent,
            playbackModel.playlistSkip,
            ::updateSkipHeadTail,
        )
        collectImmediately(playbackModel.sleepTimerState, ::updateSleepTimer)
    }

    private fun updateRepeat(repeatMode: RepeatMode) {
        val binding = requireBinding()
        when (repeatMode) {
            RepeatMode.NONE -> {
                binding.settingRepeatStatus.text = "关闭"
                binding.settingRepeatIcon.setImageResource(R.drawable.ic_repeat_off_24)
            }
            RepeatMode.ALL -> {
                binding.settingRepeatStatus.text = "列表循环"
                binding.settingRepeatIcon.setImageResource(R.drawable.ic_repeat_on_24)
            }
            RepeatMode.TRACK -> {
                binding.settingRepeatStatus.text = "单曲循环"
                binding.settingRepeatIcon.setImageResource(R.drawable.ic_repeat_one_24)
            }
        }
    }

    private fun updateShuffle(isShuffled: Boolean) {
        val binding = requireBinding()
        binding.settingShuffleStatus.text = if (isShuffled) "开启" else "关闭"
    }

    private fun updateSpeed(speed: Float) {
        requireBinding().settingSpeedStatus.text = formatSpeedLabel(speed)
    }

    private fun updateSkipHeadTail(isPlaylistParent: Boolean, skip: SkipHeadTail) {
        val binding = requireBinding()
        binding.settingSkipHeadTailItem.alpha = if (isPlaylistParent) 1f else 0.5f
        binding.settingSkipHeadTailStatus.text =
            when {
                !isPlaylistParent -> getString(R.string.lng_skip_head_tail_need_playlist)
                !skip.isActive -> getString(R.string.lbl_skip_head_tail_off)
                else ->
                    getString(
                        R.string.fmt_skip_head_tail_status,
                        formatSkipSeconds(skip.headSeconds),
                        formatSkipSeconds(skip.tailSeconds),
                    )
            }
    }

    private fun formatSkipSeconds(seconds: Int): String =
        if (seconds <= 0) {
            getString(R.string.fmt_skip_seconds_off)
        } else {
            getString(R.string.fmt_skip_seconds, seconds)
        }

    private fun updateSleepTimer(state: SleepTimerState) {
        val binding = requireBinding()
        binding.settingSleepTimerStatus.text = requireContext().formatSleepTimerStatus(state)
        val iconRes =
            when (state) {
                is SleepTimerState.Off -> R.drawable.hourglass_empty_40
                is SleepTimerState.Active -> R.drawable.hourglass_top_40
                is SleepTimerState.Expired -> R.drawable.hourglass_pause_40
            }
        binding.settingSleepTimerIcon.setImageResource(iconRes)
    }

    private fun openEqualizer() {
        val equalizerIntent =
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playbackModel.currentAudioSessionId)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        try {
            startActivity(equalizerIntent)
        } catch (e: ActivityNotFoundException) {
            requireContext().showToast(R.string.err_no_app)
        }
    }
}
