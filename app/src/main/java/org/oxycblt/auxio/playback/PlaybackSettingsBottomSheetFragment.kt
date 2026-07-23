package org.oxycblt.auxio.playback

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogPlaybackSettingsBinding
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.ui.ViewBindingSideSheetDialogFragment
import org.oxycblt.auxio.ui.accent.AccentCustomizeDialog
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.showToast
import timber.log.Timber as L

/**
 * Right-side drawer for quick playback settings, plus a shortcut to the look-and-feel color scheme.
 */
@AndroidEntryPoint
class PlaybackSettingsBottomSheetFragment :
    ViewBindingSideSheetDialogFragment<DialogPlaybackSettingsBinding>() {

    private val playbackModel: PlaybackViewModel by activityViewModels()

    /**
     * AudioEffect control panels must be started with startActivityForResult (no result is used).
     * Plain [startActivity] is ignored or fails silently on many devices.
     */
    private var equalizerLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        equalizerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                // System equalizer returns no useful result.
            }
    }

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogPlaybackSettingsBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: DialogPlaybackSettingsBinding,
        savedInstanceState: Bundle?,
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        // 1. Repeat Mode
        binding.settingRepeatItem.setOnClickListener { playbackModel.toggleRepeatMode() }

        // 2. Shuffle Mode
        binding.settingShuffleItem.setOnClickListener { playbackModel.toggleShuffled() }

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
        binding.settingEqualizerItem.setOnClickListener { openEqualizer() }

        // 7. Color scheme (from main Look and feel settings)
        binding.settingAccentStatus.text = getString(uiSettings.accent.name)
        binding.settingAccentItem.setOnClickListener {
            AccentCustomizeDialog().show(parentFragmentManager, "Accent")
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

    override fun onResume() {
        super.onResume()
        // Accent may have been changed (and activity recreated) while the drawer was open.
        binding?.settingAccentStatus?.text = getString(uiSettings.accent.name)
    }

    override fun onDestroyBinding(binding: DialogPlaybackSettingsBinding) {
        equalizerLauncher = null
    }

    private fun updateRepeat(repeatMode: RepeatMode) {
        val binding = requireBinding()
        when (repeatMode) {
            RepeatMode.NONE -> {
                binding.settingRepeatStatus.setText(R.string.lbl_repeat_off)
                binding.settingRepeatIcon.setImageResource(R.drawable.ic_repeat_off_24)
            }
            RepeatMode.ALL -> {
                binding.settingRepeatStatus.setText(R.string.lbl_repeat_all)
                binding.settingRepeatIcon.setImageResource(R.drawable.ic_repeat_on_24)
            }
            RepeatMode.TRACK -> {
                binding.settingRepeatStatus.setText(R.string.lbl_repeat_one)
                binding.settingRepeatIcon.setImageResource(R.drawable.ic_repeat_one_24)
            }
        }
    }

    private fun updateShuffle(isShuffled: Boolean) {
        val binding = requireBinding()
        binding.settingShuffleStatus.setText(
            if (isShuffled) R.string.lbl_on else R.string.lbl_off
        )
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
        // Make sure the system equalizer can attach to our audio path (even while paused).
        playbackModel.ensureAudioEffectSession()

        val sessionId = playbackModel.currentAudioSessionId
        if (sessionId == null || sessionId == 0) {
            L.w("No audio session for equalizer")
            requireContext().showToast(R.string.err_equalizer_no_session)
            return
        }

        val equalizerIntent =
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, requireContext().packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

        val launcher = equalizerLauncher
        if (launcher == null) {
            L.w("Equalizer launcher not registered")
            requireContext().showToast(R.string.err_no_app)
            return
        }

        try {
            // Required API: panel intents must be started for result.
            launcher.launch(equalizerIntent)
        } catch (_: ActivityNotFoundException) {
            requireContext().showToast(R.string.err_no_app)
        } catch (e: Exception) {
            L.e("Failed to open equalizer")
            L.e(e.stackTraceToString())
            requireContext().showToast(R.string.err_no_app)
        }
    }
}
