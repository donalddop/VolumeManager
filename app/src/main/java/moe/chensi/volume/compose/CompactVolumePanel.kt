package moe.chensi.volume.compose

import android.media.AudioManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.chensi.volume.R
import moe.chensi.volume.data.App
import kotlin.math.round
import kotlin.math.roundToInt

private val THUMB_MAX = 26.dp

/**
 * Thin vertical slider with a draggable dot. Bottom = 0.
 * Vertical drag adjusts, a tap jumps to that spot, a horizontal swipe is left to the
 * scrolling row (so scrolling through apps never changes a volume by accident).
 */
@Composable
fun VerticalVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    height: Dp = 150.dp,
) {
    val range = if (valueRange.endInclusive > valueRange.start) valueRange else 0f..1f
    val span = range.endInclusive - range.start
    val v = value.coerceIn(range.start, range.endInclusive)
    val fraction = (v - range.start) / span
    val latest by rememberUpdatedState(v)
    val onChange by rememberUpdatedState(onValueChange)
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableStateOf(false) }
    val fill = MaterialTheme.colorScheme.primary
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val thumbSize = if (active) THUMB_MAX else 22.dp
    val density = LocalDensity.current

    BoxWithConstraints(modifier.width(44.dp).height(height)) {
        val hPx = with(density) { maxHeight.toPx() }
        val thumbPx = with(density) { THUMB_MAX.toPx() }

        fun valueAt(y: Float): Float {
            val usable = (hPx - thumbPx).coerceAtLeast(1f)
            val f = 1f - ((y - thumbPx / 2f) / usable).coerceIn(0f, 1f)
            return range.start + f * span
        }

        fun emit(y: Float) {
            val nv = valueAt(y)
            if (nv == latest) return
            onChange(nv)
            if (nv == range.start || nv == range.endInclusive) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(range, hPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        active = true
                        try {
                            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (drag != null) {
                                emit(drag.position.y)
                                verticalDrag(drag.id) { change ->
                                    change.consume()
                                    emit(change.position.y)
                                }
                            } else {
                                // Plain tap (not taken over by the horizontal scroller): jump
                                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                if (up != null && !up.pressed && !up.isConsumed) {
                                    up.consume()
                                    emit(up.position.y)
                                }
                            }
                        } finally {
                            active = false
                        }
                    }
                }
        ) {
            // Track, inset by half a thumb so the fill ends exactly at the dot's centre
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(10.dp)
                    .fillMaxHeight()
                    .padding(vertical = THUMB_MAX / 2)
                    .clip(CircleShape)
                    .background(trackBg)
            ) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .background(fill, CircleShape)
                )
            }
            // The dot
            val thumbTop = with(density) { ((1f - fraction) * (hPx - thumbPx)).toDp() } +
                (THUMB_MAX - thumbSize) / 2
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbTop)
                    .size(thumbSize)
                    .shadow(3.dp, CircleShape)
                    .background(fill, CircleShape)
                    .border(4.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun SliderColumn(
    slider: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    onIconClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slider()
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onIconClick),
            contentAlignment = Alignment.Center
        ) { icon() }
    }
}

/** One app: vertical slider + its icon. Tapping the icon mutes / unmutes. */
@Composable
fun VerticalAppSlider(app: App, onChange: (() -> Unit)?) {
    var lastNonZero by remember { mutableFloatStateOf(if (app.volume > 0f) app.volume else 0.5f) }
    val muted = app.volume == 0f

    SliderColumn(
        slider = {
            VerticalVolumeSlider(
                value = app.volume,
                onValueChange = {
                    app.volume = it
                    if (it > 0f) lastNonZero = it
                    onChange?.invoke()
                },
                modifier = Modifier.semantics {
                    contentDescription = app.name
                    stateDescription = "${(app.volume * 100).roundToInt()}%"
                }
            )
        },
        icon = {
            val bitmap = app.icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (muted) 0.35f else 1f }
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Gray, CircleShape))
            }
        },
        onIconClick = {
            app.volume = if (muted) lastNonZero else 0f
            onChange?.invoke()
        }
    )
}

/** A system stream (e.g. media) as a vertical slider with a symbol icon. */
@Composable
fun VerticalStreamSlider(
    streamType: Int,
    icon: ImageVector,
    name: String,
    audioManager: AudioManager,
    onChange: (() -> Unit)?,
) {
    val context = LocalContext.current
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(streamType)) }
    val maxVolume = remember(streamType) { audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1) }
    var lastNonZero by remember { mutableIntStateOf(volume.takeIf { it > 0 } ?: (maxVolume / 2)) }

    DisposableEffect(context) {
        VolumeChangeObserver.startObserving(context)
        onDispose { VolumeChangeObserver.stopObserving() }
    }
    val changed = VolumeChangeObserver.volumeChangedCount
    LaunchedEffect(changed) {
        volume = audioManager.getStreamVolume(streamType)
    }

    fun set(target: Int) {
        if (target == volume) return
        volume = target
        if (target > 0) lastNonZero = target
        audioManager.setStreamVolume(streamType, target, 0)
        onChange?.invoke()
    }

    SliderColumn(
        slider = {
            VerticalVolumeSlider(
                value = volume.toFloat(),
                valueRange = 0f..maxVolume.toFloat(),
                onValueChange = { set(round(it).toInt()) },
                modifier = Modifier.semantics {
                    contentDescription = name
                    stateDescription = "$volume/$maxVolume"
                }
            )
        },
        icon = {
            Icon(
                imageVector = if (volume == 0) Icons.AutoMirrored.Filled.VolumeOff else icon,
                contentDescription = name,
                modifier = Modifier.size(26.dp)
            )
        },
        onIconClick = { set(if (volume > 0) 0 else lastNonZero) }
    )
}

/**
 * Samsung-style compact panel: media volume, then one thin slider per app that is
 * actually playing sound right now, then a button to expand to the full list.
 */
@Composable
fun CompactVolumePanel(
    apps: Collection<App>,
    audioManager: AudioManager,
    onChange: (() -> Unit)?,
    onExpand: () -> Unit,
) {
    val playing = apps
        .filter { it.isPlayer && !it.hidden && it.hasBeenPlayingRecently }
        .sortedWith(App.comparator)

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        VerticalStreamSlider(
            streamType = AudioManager.STREAM_MUSIC,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            name = stringResource(R.string.compact_media),
            audioManager = audioManager,
            onChange = onChange
        )

        if (playing.isNotEmpty()) {
            Box(
                Modifier
                    .padding(bottom = 42.dp)
                    .width(1.dp)
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }

        playing.forEach { app ->
            key(app.packageName) {
                VerticalAppSlider(app, onChange)
            }
        }

        IconButton(onClick = onExpand, modifier = Modifier.padding(bottom = 0.dp)) {
            Icon(
                Icons.Default.UnfoldMore,
                contentDescription = stringResource(R.string.compact_expand)
            )
        }
    }
}
