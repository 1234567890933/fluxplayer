package dev.anilbeesetti.nextplayer.feature.player

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import dev.anilbeesetti.nextplayer.core.effect.ColorTemperature
import dev.anilbeesetti.nextplayer.core.effect.DeviceTier
import dev.anilbeesetti.nextplayer.core.effect.EnhancePreset
import dev.anilbeesetti.nextplayer.core.effect.EnhanceTuning
import dev.anilbeesetti.nextplayer.core.effect.HdrContentDetector
import dev.anilbeesetti.nextplayer.core.effect.PlayerEffectsBridge
import dev.anilbeesetti.nextplayer.core.effect.VideoInfo
import dev.anilbeesetti.nextplayer.core.model.ControlButtonsPosition
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.feature.player.extensions.nameRes
import dev.anilbeesetti.nextplayer.feature.player.state.ChaptersState
import dev.anilbeesetti.nextplayer.feature.player.state.ControlsVisibilityState
import dev.anilbeesetti.nextplayer.feature.player.state.SeekGestureState
import dev.anilbeesetti.nextplayer.feature.player.state.VideoZoomAndContentScaleState
import dev.anilbeesetti.nextplayer.feature.player.state.seekAmountFormatted
import dev.anilbeesetti.nextplayer.feature.player.state.seekToPositionFormated
import dev.anilbeesetti.nextplayer.feature.player.ui.ColorGradeState
import dev.anilbeesetti.nextplayer.feature.player.ui.InfoView
import dev.anilbeesetti.nextplayer.feature.player.ui.OverlayView
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsBottomView
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsMiddleView
import dev.anilbeesetti.nextplayer.feature.player.ui.controls.ControlsTopView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.DecoderMode
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerControls(
    player: Player?,
    title: String,
    playerPreferences: PlayerPreferences,
    videoDecoderMode: DecoderMode?,
    controlsVisibilityState: ControlsVisibilityState,
    seekGestureState: SeekGestureState,
    videoZoomAndContentScaleState: VideoZoomAndContentScaleState,
    progressState: ProgressStateWithTickInterval,
    chaptersState: ChaptersState,
    isPipSupported: Boolean,
    onShowOverlay: (OverlayView) -> Unit,
    onBackClick: () -> Unit,
    onPlayInBackgroundClick: () -> Unit,
    onToggleTimeDisplay: () -> Unit,
    onEnhanceChanged: (EnhancePreset) -> Unit = {},
    onPictureInPictureClick: () -> Unit,
    modifier: Modifier = Modifier,
    middleControlsModifier: Modifier = Modifier,
) {
    // The video info overlay: filled from the real track metadata when the button is pressed,
    // then hidden again after four seconds as the brief asks.
    var showVideoInfo by remember { mutableStateOf(false) }
    // The HDR switch lives here so the button, the engine and the confirmation text all agree.
    var hdrOn by remember { mutableStateOf(false) }
    // Smart Enhance: the chip cycles through the presets and each one is tuned to this device.
    var enhancePreset by remember {
        mutableStateOf(EnhancePreset.of(playerPreferences.smartEnhancePreset))
    }
    val deviceTier = remember {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val ramMb = if (memory.totalMem > 0) memory.totalMem / (1024L * 1024L) else 2048L
        // reqGlEsVersion packs major in the high 16 bits, so 0x00030000 means GLES 3.
        val glMajor =
            (activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: 0x00030000) shr 16
        DeviceTier.of(ramMb, glMajor)
    }
    // A panel that cannot do HDR still deserves the experience, so the look switches itself on
    // there. On a real HDR screen the switch stays off and the engine passes the signal through.
    val hostView = LocalView.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        PlayerEffectsBridge.applyEnhance(enhancePreset, deviceTier)
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) hostView.display else null
        val panelDoesHdr = display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        if (!panelDoesHdr && PlayerEffectsBridge.setHdrSwitch(true)) {
            hdrOn = true
        }
    }
    var videoInfoText by remember { mutableStateOf("") }
    LaunchedEffect(showVideoInfo) {
        if (showVideoInfo) {
            delay(4000)
            showVideoInfo = false
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // The GPU effect path is the good one, but a phone whose driver refuses it still has to
        // feel the switch and the sliders. This canvas is plain drawing work, so it lands on every
        // device: it is what makes HDR and the grade visible on an ordinary LCD.
        val grade = ColorGradeState.current
        Canvas(modifier = Modifier.matchParentSize()) {
            if (hdrOn) {
                drawRect(
                    color = Color.White.copy(alpha = 0.06f),
                    blendMode = BlendMode.Screen,
                )
                drawRect(
                    color = Color(0xFF808080).copy(alpha = 0.10f),
                    blendMode = BlendMode.Overlay,
                )
            }
            if (grade.brightness > 0f) {
                drawRect(
                    color = Color.White.copy(alpha = grade.brightness * 0.30f),
                    blendMode = BlendMode.Screen,
                )
            } else if (grade.brightness < 0f) {
                drawRect(color = Color.Black.copy(alpha = -grade.brightness * 0.30f))
            }
            if (grade.contrast > 1f) {
                drawRect(
                    color = Color(0xFF808080).copy(alpha = (grade.contrast - 1f) * 0.45f),
                    blendMode = BlendMode.Overlay,
                )
            } else if (grade.contrast < 1f) {
                drawRect(
                    color = Color(0xFF808080).copy(alpha = (1f - grade.contrast) * 0.35f),
                    blendMode = BlendMode.Screen,
                )
            }
            if (grade.saturation > 1f) {
                drawRect(
                    color = Color(0xFFFF7043).copy(alpha = (grade.saturation - 1f) * 0.18f),
                    blendMode = BlendMode.Saturation,
                )
            } else if (grade.saturation < 1f) {
                drawRect(
                    color = Color.Gray.copy(alpha = (1f - grade.saturation) * 0.35f),
                    blendMode = BlendMode.Saturation,
                )
            }
            when (grade.temperature) {
                ColorTemperature.WARM -> drawRect(
                    color = Color(0xFFFF8A3D).copy(alpha = 0.07f),
                    blendMode = BlendMode.Color,
                )

                ColorTemperature.COOL -> drawRect(
                    color = Color(0xFF4A9BFF).copy(alpha = 0.07f),
                    blendMode = BlendMode.Color,
                )

                else -> Unit
            }
        }
        Column {
            AnimatedVisibility(
                visible = controlsVisibilityState.controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ControlsTopView(
                    title = title,
                    videoDecoderMode = videoDecoderMode,
                    onDecoderClick = { onShowOverlay(OverlayView.DECODER_SELECTOR) },
                    onAudioClick = { onShowOverlay(OverlayView.AUDIO_SELECTOR) },
                    onSubtitleClick = { onShowOverlay(OverlayView.SUBTITLE_SELECTOR) },
                    onPlaylistClick = { onShowOverlay(OverlayView.PLAYLIST) },
                    onBackClick = onBackClick,
                    hdrOn = hdrOn,
                    onHdrToggle = {
                        val wanted = !hdrOn
                        // The switch only turns on when the engine actually accepted the call.
                        if (PlayerEffectsBridge.setHdrSwitch(wanted)) {
                            hdrOn = wanted
                            videoInfoText = when {
                                !wanted -> "HDR off"
                                PlayerEffectsBridge.isDegraded -> "HDR on (GPU refused the grade)"
                                else -> "HDR on"
                            }
                        } else {
                            videoInfoText = "HDR unavailable - no player attached"
                        }
                        showVideoInfo = true
                    },
                    onInfoClick = {
                        val info = VideoInfo.of(
                            HdrContentDetector.selectedVideoFormat(player?.currentTracks),
                        )
                        videoInfoText = if (info == null) {
                            ""
                        } else {
                            "${info.resolutionLabel}  ${info.frameRateLabel}\n" +
                                "${info.codecLabel}  ${info.hdrLabel}"
                        }
                        showVideoInfo = info != null
                    },
                    onColorGradingClick = { onShowOverlay(OverlayView.COLOR_GRADING) },
                    enhanceLabel = enhancePreset.label,
                    onEnhanceClick = {
                        enhancePreset = enhancePreset.next()
                        PlayerEffectsBridge.applyEnhance(enhancePreset, deviceTier)
                        ColorGradeState.current =
                            EnhanceTuning.settingsFor(enhancePreset, deviceTier)
                        // Remembered, so the picture looks the same the next time.
                        onEnhanceChanged(enhancePreset)
                    },
                )
            }
            Spacer(modifier = Modifier.weight(weight = 1f))
            AnimatedVisibility(
                visible =
                    controlsVisibilityState.controlsVisible &&
                        !controlsVisibilityState.controlsLocked,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ControlsBottomView(
                    player = player,
                    progressState = progressState,
                    chaptersState = chaptersState,
                    onChaptersClick = { onShowOverlay(OverlayView.CHAPTERS) },
                    controlsAlignment = when (playerPreferences.controlButtonsPosition) {
                        ControlButtonsPosition.LEFT -> Alignment.Start
                        ControlButtonsPosition.RIGHT -> Alignment.End
                    },
                    videoContentScale = videoZoomAndContentScaleState.videoContentScale,
                    isPipSupported = isPipSupported,
                    showRemainingTime = playerPreferences.showRemainingTime,
                    onToggleTimeDisplay = onToggleTimeDisplay,
                    onSeek = seekGestureState::onSeek,
                    onSeekEnd = seekGestureState::onSeekEnd,
                    onPlaybackSpeedClick = { onShowOverlay(OverlayView.PLAYBACK_SPEED) },
                    onPlayInBackgroundClick = onPlayInBackgroundClick,
                    onLockControlsClick = {
                        controlsVisibilityState.showControls()
                        controlsVisibilityState.lockControls()
                    },
                    onVideoContentScaleClick = {
                        controlsVisibilityState.showControls()
                        videoZoomAndContentScaleState.switchToNextVideoContentScale()
                    },
                    onVideoContentScaleLongClick = {
                        onShowOverlay(OverlayView.VIDEO_CONTENT_SCALE)
                    },
                    onPictureInPictureClick = onPictureInPictureClick,
                )
            }
        }
        when {
            seekGestureState.seekAmount != null -> InfoView(
                info =
                    "${seekGestureState.seekAmountFormatted}\n" +
                        "[${seekGestureState.seekToPositionFormated}]",
            )

            videoZoomAndContentScaleState.isZooming -> InfoView(
                info = "${(videoZoomAndContentScaleState.zoom * 100).toInt()}%",
            )

            showVideoInfo -> InfoView(
                info = videoInfoText,
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            videoZoomAndContentScaleState.showContentScaleIndicator -> InfoView(
                info = stringResource(videoZoomAndContentScaleState.videoContentScale.nameRes()),
            )

            controlsVisibilityState.controlsVisible -> ControlsMiddleView(
                player = player,
                modifier = middleControlsModifier,
            )
        }
    }
}
