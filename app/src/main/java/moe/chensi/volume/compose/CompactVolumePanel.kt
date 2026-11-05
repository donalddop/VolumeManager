package moe.chensi.volume.compose

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRouter2
import android.provider.Settings
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
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
import moe.chensi.volume.system.NotificationManagerProxy
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
    iconOverride: ImageVector? = null,
    onIconClickOverride: (() -> Unit)? = null,
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
        runCatching { audioManager.setStreamVolume(streamType, target, 0) }
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
                imageVector = iconOverride
                    ?: if (volume == 0) Icons.AutoMirrored.Filled.VolumeOff else icon,
                contentDescription = name,
                modifier = Modifier.size(26.dp)
            )
        },
        onIconClick = onIconClickOverride ?: { set(if (volume > 0) 0 else lastNonZero) }
    )
}

/** Round icon button used for the sound-mode selector. */
@Composable
private fun ModeButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
    }
}

private fun openDndAccessSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Opens Android's own output switcher (Bluetooth, speaker, audio sharing...). */
private fun openOutputSwitcher(context: Context): Boolean {
    val shown = android.os.Build.VERSION.SDK_INT >= 34 && runCatching {
        MediaRouter2.getInstance(context).showSystemOutputSwitcher()
    }.getOrDefault(false)
    if (shown) return true
    return runCatching {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}

/**
 * Samsung-style compact panel:
 *  - top: sound / vibrate / silent + Do Not Disturb
 *  - middle: ringtone, notifications, media (+ alarm) and one thin slider per app that
 *    is actually playing sound right now
 *  - bottom: output switcher and "show all"
 */
@Composable
fun CompactVolumePanel(
    apps: Collection<App>,
    audioManager: AudioManager,
    notificationManagerProxy: NotificationManagerProxy,
    showAlarm: Boolean,
    onChange: (() -> Unit)?,
    onExpand: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    val context = LocalContext.current
    var ringerMode by remember { mutableIntStateOf(audioManager.ringerMode) }
    var filter by remember {
        mutableIntStateOf(runCatching { notificationManagerProxy.getCurrentInterruptionFilter() }
            .getOrDefault(NotificationManager.INTERRUPTION_FILTER_ALL))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                ringerMode = audioManager.ringerMode
                filter = runCatching { notificationManagerProxy.getCurrentInterruptionFilter() }
                    .getOrDefault(filter)
                VolumeChangeObserver.notifyVolumeChanged()
            }
        }
        val intentFilter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun setRinger(mode: Int) {
        try {
            audioManager.ringerMode = mode
        } catch (_: SecurityException) {
            openDndAccessSettings(context) // needs "Do Not Disturb access" once
            onCloseRequest()
            return
        }
        ringerMode = audioManager.ringerMode
        VolumeChangeObserver.notifyVolumeChanged()
        onChange?.invoke()
    }

    val dndOn = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
        filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    fun toggleDnd() {
        try {
            notificationManagerProxy.setInterruptionFilter(
                if (dndOn) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            filter = notificationManagerProxy.getCurrentInterruptionFilter()
        } catch (_: SecurityException) {
            openDndAccessSettings(context)
            onCloseRequest()
            return
        }
        onChange?.invoke()
    }

    val playing = apps
        .filter { it.isPlayer && !it.hidden && it.hasBeenPlayingRecently }
        .sortedWith(App.comparator)

    val ringIcon = when (ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
        AudioManager.RINGER_MODE_SILENT -> Icons.Default.NotificationsOff
        else -> Icons.Default.RingVolume
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- Sound mode + DND ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ModeButton(
                    Icons.Default.NotificationsActive, stringResource(R.string.mode_sound),
                    ringerMode == AudioManager.RINGER_MODE_NORMAL
                ) { setRinger(AudioManager.RINGER_MODE_NORMAL) }
                ModeButton(
                    Icons.Default.Vibration, stringResource(R.string.mode_vibrate),
                    ringerMode == AudioManager.RINGER_MODE_VIBRATE
                ) { setRinger(AudioManager.RINGER_MODE_VIBRATE) }
                ModeButton(
                    Icons.Default.NotificationsOff, stringResource(R.string.mode_silent),
                    ringerMode == AudioManager.RINGER_MODE_SILENT
                ) { setRinger(AudioManager.RINGER_MODE_SILENT) }
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(2.dp)
            ) {
                ModeButton(
                    Icons.Default.DoNotDisturbOn, stringResource(R.string.mode_dnd), dndOn
                ) { toggleDnd() }
            }
        }

        // ---- Sliders ----
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            VerticalStreamSlider(
                streamType = AudioManager.STREAM_RING,
                icon = Icons.Default.RingVolume,
                name = stringResource(R.string.stream_ring),
                audioManager = audioManager,
                onChange = onChange,
                iconOverride = ringIcon,
                // Tap the ring icon: sound -> vibrate -> silent -> sound (like Samsung)
                onIconClickOverride = {
                    setRinger(
                        when (ringerMode) {
                            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                            else -> AudioManager.RINGER_MODE_NORMAL
                        }
                    )
                }
            )
            VerticalStreamSlider(
                streamType = AudioManager.STREAM_NOTIFICATION,
                icon = Icons.Default.NotificationsNone,
                name = stringResource(R.string.stream_notification),
                audioManager = audioManager,
                onChange = onChange
            )
            VerticalStreamSlider(
                streamType = AudioManager.STREAM_MUSIC,
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                name = stringResource(R.string.compact_media),
                audioManager = audioManager,
                onChange = onChange
            )
            if (showAlarm) {
                VerticalStreamSlider(
                    streamType = AudioManager.STREAM_ALARM,
                    icon = Icons.Default.Alarm,
                    name = stringResource(R.string.stream_alarm),
                    audioManager = audioManager,
                    onChange = onChange
                )
            }

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
        }

        // ---- Output + show all ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {
                    if (openOutputSwitcher(context)) onCloseRequest()
                },
                label = { Text(stringResource(R.string.compact_output)) },
                leadingIcon = {
                    Icon(Icons.Default.Speaker, contentDescription = null, Modifier.size(18.dp))
                }
            )
            AssistChip(
                onClick = onExpand,
                label = { Text(stringResource(R.string.compact_expand)) },
                leadingIcon = {
                    Icon(Icons.Default.UnfoldMore, contentDescription = null, Modifier.size(18.dp))
                }
            )
        }
    }
}
