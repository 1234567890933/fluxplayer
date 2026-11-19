package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.anilbeesetti.nextplayer.core.effect.ColorSettings

/**
 * The grade the viewer picked, shared by the sheet that edits it and the canvas that draws it.
 * It lives here rather than in the sheet because the picture has to follow the sliders live,
 * even on a device whose GPU refuses the effect pipeline.
 */
object ColorGradeState {
    var current by mutableStateOf(ColorSettings())
}
