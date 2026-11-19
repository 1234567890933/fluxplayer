package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.effect.ColorPreset
import dev.anilbeesetti.nextplayer.core.effect.ColorSettings
import dev.anilbeesetti.nextplayer.core.effect.ColorTemperature
import dev.anilbeesetti.nextplayer.core.effect.PlayerEffectsBridge

/**
 * The colour sheet the brief asks for: five preset chips, six sliders and the white balance.
 *
 * Every change goes straight through [PlayerEffectsBridge], so the grade is visible while the
 * finger is still on the slider — that is the whole point of the engine in core/effect.
 *
 * The grade is local state for now. Persisting it per file is the Room table from section 13.2 of
 * the brief and is not part of this change.
 */
@Composable
fun BoxScope.ColorGradingView(
    modifier: Modifier = Modifier,
    show: Boolean,
    initial: ColorSettings = ColorSettings(),
) {
    var settings by remember(show) { mutableStateOf(initial) }

    fun apply(next: ColorSettings) {
        settings = next
        PlayerEffectsBridge.setColorSettings(next)
    }

    OverlayView(
        modifier = modifier,
        show = show,
        title = "Colour grading",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.preset == preset,
                        onClick = { apply(ColorSettings.of(preset)) },
                        label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            GradeSlider(
                label = "Brightness",
                value = settings.brightness,
                range = -1f..1f,
                onChange = {
                    apply(
                        settings.copy(
                            brightness = it,
                            preset = ColorPreset.CUSTOM,
                        ),
                    )
                },
            )
            GradeSlider(
                label = "Contrast",
                value = settings.contrast,
                range = 0.5f..2f,
                onChange = {
                    apply(
                        settings.copy(
                            contrast = it,
                            preset = ColorPreset.CUSTOM,
                        ),
                    )
                },
            )
            GradeSlider(
                label = "Saturation",
                value = settings.saturation,
                range = 0f..2f,
                onChange = {
                    apply(
                        settings.copy(
                            saturation = it,
                            preset = ColorPreset.CUSTOM,
                        ),
                    )
                },
            )
            GradeSlider(
                label = "Sharpness",
                value = settings.sharpness,
                range = 0f..1f,
                onChange = {
                    apply(
                        settings.copy(
                            sharpness = it,
                            preset = ColorPreset.CUSTOM,
                        ),
                    )
                },
            )
            GradeSlider(
                label = "Clarity",
                value = settings.clarity,
                range = 0f..1f,
                onChange = {
                    apply(
                        settings.copy(
                            clarity = it,
                            preset = ColorPreset.CUSTOM,
                        ),
                    )
                },
            )
            GradeSlider(
                label = "Noise reduction",
                value = settings.noiseReduce,
                range = 0f..1f,
                onChange = {
                    apply(
                        settings.copy(
                            noiseReduce = it,
                            preset = ColorPreset.CUSTOM,
                        ),
                    )
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                ColorTemperature.entries.forEach { temperature ->
                    FilterChip(
                        selected = settings.temperature == temperature,
                        onClick = {
                            apply(
                                settings.copy(
                                    temperature = temperature,
                                    preset = ColorPreset.CUSTOM,
                                ),
                            )
                        },
                        label = { Text(temperature.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}
