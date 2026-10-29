package moe.chensi.volume.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Card with [content] (icon, name, value) on top and a slider with a clear, draggable dot below.
 * Uses the Material slider for gesture handling: tap anywhere on the track to jump,
 * drag the dot (or anywhere on the track) to adjust. Vertical list scrolling is not stolen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer,
    onTrackColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    @Suppress("UNUSED_PARAMETER") onFillColor: Color = MaterialTheme.colorScheme.onPrimary,
    cornerRadius: Dp = 8.dp,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val range = if (valueRange.endInclusive > valueRange.start) valueRange else 0f..1f
    val coercedValue = value.coerceIn(range.start, range.endInclusive)
    val latestValue by rememberUpdatedState(coercedValue)
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val active = pressed || dragged

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
    ) {
        CompositionLocalProvider(LocalContentColor provides onTrackColor) {
            Box(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }

        Slider(
            value = coercedValue,
            onValueChange = { newValue ->
                if (newValue == latestValue) return@Slider
                val hitEdge = newValue == range.start || newValue == range.endInclusive
                onValueChange(newValue)
                if (hitEdge) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            enabled = enabled,
            valueRange = range,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            thumb = {
                // The dot: white ring + accent fill, grows a bit while touched
                val size = if (active) 28.dp else 24.dp
                Box(
                    modifier = Modifier
                        .size(size)
                        .shadow(3.dp, CircleShape)
                        .background(fillColor, CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                )
            },
            track = { state ->
                val span = state.valueRange.endInclusive - state.valueRange.start
                val fraction = if (span > 0f) {
                    ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
                } else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(onTrackColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(fillColor, CircleShape)
                    )
                }
            }
        )
    }
}
