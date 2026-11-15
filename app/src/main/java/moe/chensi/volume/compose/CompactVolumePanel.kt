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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
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

private val TRACK_WIDTH = 40.dp
private val TRACK_HEIGHT = 150.dp

/** Bottom part of the track that holds the icon; also the tap target for mute. */
private val ICON_ZONE = 40.dp

/**
 * One UI style vertical slider: a wide rounded track that fills from the bottom, with the
 * stream icon sitting *inside* the bottom of the track.
 *
 * There is deliberately no thumb. The earlier version used a thin 10 dp track with a
 * white-bordered dot, which is a Material slider turned upright and is what made the panel
 * read as "not Samsung" even though the behaviour was close: on One UI the fill level itself
 * is the handle.
 *
 * The icon is drawn twice, once in the unfilled colour and once in the on-fill colour with
 * the second copy clipped to the filled part of the track. That is why the icon changes
 * colour exactly where the fill passes over it instead of staying one flat tone. Tinting it
 * by a threshold was tried first and looks wrong halfway, because the whole glyph flips at
 * once while the fill edge sits across it.
 *
 * Vertical drags adjust, a tap jumps to that spot, a tap inside the icon zone calls
 * [onIconTap] (mute / unmute), and horizontal swipes are left to the parent scroller so
 * scrolling through app sliders never changes a volume by accident.
 */
@Composable
fun OneUiVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: @Composable (tint: Color) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    height: Dp = TRACK_HEIGHT,
    dimmed: Boolean = false,
    onIconTap: (() -> Unit)? = null,
) {
    val range = if (valueRange.endInclusive > valueRange.start) valueRange else 0f..1f
    val span = range.endInclusive - range.start
    val v = value.coerceIn(range.start, range.endInclusive)
    val fraction = ((v - range.start) / span).coerceIn(0f, 1f)
    val latest by rememberUpdatedState(v)
    val onChange by rememberUpdatedState(onValueChange)
    val onIcon by rememberUpdatedState(onIconTap)
    val haptics = LocalHapticFeedback.current
    // Vibrate / silent mode greys the ring slider out but leaves it usable, so this is only
    // an alpha on the colours, not a disabled state.
    val alpha = if (dimmed) 0.4f else 1f
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
    val trackBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f * alpha)
    val iconOff = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val iconOn = MaterialTheme.colorScheme.onPrimary.copy(alpha = alpha)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .width(TRACK_WIDTH)
            .height(height)
            .clip(RoundedCornerShape(TRACK_WIDTH / 2))
            .background(trackBg)
    ) {
        val hPx = with(density) { maxHeight.toPx() }
        val iconZonePx = with(density) { ICON_ZONE.toPx() }

        fun valueAt(y: Float): Float {
            val f = 1f - (y / hPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
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

        // Fill, from the bottom up
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .background(fill)
        )
        // Icon in the unfilled colour
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ICON_ZONE),
            contentAlignment = Alignment.Center
        ) { icon(iconOff) }
        // The same icon again, clipped to the filled part
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fraction)
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(ICON_ZONE),
                contentAlignment = Alignment.Center
            ) { icon(iconOn) }
        }
        // Gestures on top of the painted layers
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(range, hPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
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
                            // Plain tap (not taken over by the horizontal scroller)
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) {
                                up.consume()
                                val tap = onIcon
                                if (tap != null && up.position.y >= hPx - iconZonePx) {
                                    tap()
                                } else {
                                    emit(up.position.y)
                                }
                            }
                        }
                    }
                }
        )
    }
}
/** One app: its icon sits in the bottom of the track. Tapping the icon mutes / unmutes. */
@Composable
fun VerticalAppSlider(app: App, onChange: (() -> Unit)?) {
    var lastNonZero by remember { mutableFloatStateOf(if (app.volume > 0f) app.volume else 0.5f) }
    val muted = app.volume == 0f

    OneUiVolumeSlider(
        value = app.volume,
        onValueChange = {
            app.volume = it
            if (it > 0f) lastNonZero = it
            onChange?.invoke()
        },
        // An app icon is a bitmap, so it keeps its own colours; the tint only applies to
        // the vector stream icons.
        icon = { _ ->
            val bitmap = app.icon
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { alpha = if (muted) 0.35f else 1f }
                )
            } else {
                Box(Modifier.size(24.dp).background(Color.Gray, CircleShape))
            }
        },
        modifier = Modifier.semantics {
            contentDescription = app.name
            stateDescription = "${(app.volume * 100).roundToInt()}%"
        },
        onIconTap = {
            app.volume = if (muted) lastNonZero else 0f
            onChange?.invoke()
        }
    )
}

/**
 * A system stream (media, ring, ...) as a One UI slider.
 *
 * [forceZero] shows 0 no matter what the system has stored for the stream: in vibrate or
 * silent mode Samsung shows the ring at 0 while Android keeps the old level, so the shown
 * value has to be decoupled from the stream's own value.
 *
 * [linkedStreamType] keeps a second stream at the same fraction. Ring and notification are
 * linked on Samsung, and on most devices Android aliases the two anyway, so moving one
 * without the other leaves the panel showing a difference that does not really exist.
 */
@Composable
fun VerticalStreamSlider(
    streamType: Int,
    icon: ImageVector,
    name: String,
    audioManager: AudioManager,
    onChange: (() -> Unit)?,
    iconOverride: ImageVector? = null,
    onIconClickOverride: (() -> Unit)? = null,
    dimmed: Boolean = false,
    forceZero: Boolean = false,
    onRaiseFromZero: (() -> Unit)? = null,
    linkedStreamType: Int? = null,
) {
    val context = LocalContext.current
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(streamType)) }
    val maxVolume =
        remember(streamType) { audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1) }
    var lastNonZero by remember { mutableIntStateOf(volume.takeIf { it > 0 } ?: (maxVolume / 2)) }

    DisposableEffect(context) {
        VolumeChangeObserver.startObserving(context)
        onDispose { VolumeChangeObserver.stopObserving() }
    }
    val changed = VolumeChangeObserver.volumeChangedCount
    LaunchedEffect(changed) {
        volume = audioManager.getStreamVolume(streamType)
    }

    val shown = if (forceZero) 0 else volume

    fun set(target: Int) {
        // Dragging the ring slider up is how you leave vibrate / silent mode on Samsung, so
        // the ringer mode has to change before the level: setting a ring volume while still
        // in silent mode is swallowed by the system.
        if (forceZero && target > 0) onRaiseFromZero?.invoke()
        if (target == volume && !forceZero) return
        volume = target
        if (target > 0) lastNonZero = target
        runCatching { audioManager.setStreamVolume(streamType, target, 0) }
        linkedStreamType?.let { linked ->
            val linkedMax = audioManager.getStreamMaxVolume(linked).coerceAtLeast(1)
            val scaled = (target.toFloat() / maxVolume * linkedMax).roundToInt()
            runCatching { audioManager.setStreamVolume(linked, scaled, 0) }
            // Nudge the other slider to re-read; the system broadcast is not guaranteed for
            // an aliased stream.
            VolumeChangeObserver.notifyVolumeChanged()
        }
        onChange?.invoke()
    }

    OneUiVolumeSlider(
        value = shown.toFloat(),
        valueRange = 0f..maxVolume.toFloat(),
        onValueChange = { set(round(it).toInt()) },
        dimmed = dimmed,
        icon = { tint ->
            Icon(
                imageVector = iconOverride
                    ?: if (shown == 0) Icons.AutoMirrored.Filled.VolumeOff else icon,
                contentDescription = name,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        },
        modifier = Modifier.semantics {
            contentDescription = name
            stateDescription = "$shown/$maxVolume"
        },
        onIconTap = onIconClickOverride ?: { set(if (shown > 0) 0 else lastNonZero) }
    )
}

/** Round icon button used for the sound-mode selector and the header actions. */
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

/** What the gear in the header opens: the system sound settings, like Samsung's overflow. */
private fun openSoundSettings(context: Context): Boolean {
    return runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}

/**
 * The ring slider, used both collapsed and expanded, so the behaviour cannot drift between
 * the two states.
 */
@Composable
private fun RingSlider(
    audioManager: AudioManager,
    ringerMode: Int,
    onSetRinger: (Int) -> Unit,
    onChange: (() -> Unit)?,
) {
    val silent = ringerMode != AudioManager.RINGER_MODE_NORMAL
    val ringIcon = when (ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
        AudioManager.RINGER_MODE_SILENT -> Icons.Default.NotificationsOff
        else -> Icons.Default.RingVolume
    }

    VerticalStreamSlider(
        streamType = AudioManager.STREAM_RING,
        icon = Icons.Default.RingVolume,
        name = stringResource(R.string.stream_ring),
        audioManager = audioManager,
        onChange = onChange,
        iconOverride = ringIcon,
        dimmed = silent,
        forceZero = silent,
        onRaiseFromZero = { onSetRinger(AudioManager.RINGER_MODE_NORMAL) },
        linkedStreamType = AudioManager.STREAM_NOTIFICATION,
        // Tap the ring icon: sound -> vibrate -> silent -> sound (like Samsung)
        onIconClickOverride = {
            onSetRinger(
                when (ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
            )
        }
    )
}

/** The media slider, used both collapsed and expanded. */
@Composable
private fun MediaSlider(audioManager: AudioManager, onChange: (() -> Unit)?) {
    VerticalStreamSlider(
        streamType = AudioManager.STREAM_MUSIC,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        name = stringResource(R.string.compact_media),
        audioManager = audioManager,
        onChange = onChange
    )
}
/**
 * Samsung-style compact panel.
 *
 * Opens collapsed: one slider for whatever you are most likely reaching for, plus a chevron.
 * Tapping the chevron reveals the sound mode row, every system stream and one slider per app
 * that is actually playing sound right now. The header then carries the output switcher, the
 * jump to the full list and a gear to the system sound settings.
 *
 * The header uses plain icon buttons rather than an overflow menu: a DropdownMenu needs its
 * own popup window, and this panel lives in an accessibility overlay with no Activity behind
 * it, where popup windows are unreliable.
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

    // The view is recreated on every show, so the panel starts collapsed again each time.
    var showAllSliders by remember { mutableStateOf(false) }

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

    val silent = ringerMode != AudioManager.RINGER_MODE_NORMAL

    if (!showAllSliders) {
        // ---- Collapsed: the one slider you probably came for ----
        // Media if anything is (or just was) playing, otherwise the ringer, which is the
        // stream the volume keys target when nothing plays.
        val mediaActive = playing.isNotEmpty() ||
            runCatching { audioManager.isMusicActive }.getOrDefault(false)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mediaActive) {
                MediaSlider(audioManager = audioManager, onChange = onChange)
            } else {
                RingSlider(
                    audioManager = audioManager,
                    ringerMode = ringerMode,
                    onSetRinger = { setRinger(it) },
                    onChange = onChange
                )
            }
            ModeButton(
                Icons.Default.KeyboardArrowDown,
                stringResource(R.string.compact_show_sliders),
                false
            ) {
                showAllSliders = true
                // Expanding counts as activity, otherwise the panel can hide itself right
                // after you opened it up.
                onChange?.invoke()
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- Header: sound mode + DND on the left, actions on the right ----
        Row(
            modifier = Modifier.fillMaxWidth(),
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

            Spacer(Modifier.weight(1f))

            ModeButton(
                Icons.Default.Speaker, stringResource(R.string.compact_output), false
            ) { if (openOutputSwitcher(context)) onCloseRequest() }
            ModeButton(
                Icons.Default.UnfoldMore, stringResource(R.string.compact_expand), false
            ) { onExpand() }
            ModeButton(
                Icons.Default.SettingsIcon, stringResource(R.string.compact_sound_settings), false
            ) { if (openSoundSettings(context)) onCloseRequest() }
        }

        // ---- Sliders ----
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            RingSlider(
                audioManager = audioManager,
                ringerMode = ringerMode,
                onSetRinger = { setRinger(it) },
                onChange = onChange
            )
            VerticalStreamSlider(
                streamType = AudioManager.STREAM_NOTIFICATION,
                icon = Icons.Default.NotificationsNone,
                name = stringResource(R.string.stream_notification),
                audioManager = audioManager,
                onChange = onChange,
                dimmed = silent,
                forceZero = silent,
                onRaiseFromZero = { setRinger(AudioManager.RINGER_MODE_NORMAL) },
                // Linked both ways, so the pair stays in step whichever one you grab.
                linkedStreamType = AudioManager.STREAM_RING
            )
            MediaSlider(audioManager = audioManager, onChange = onChange)
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
                        .width(1.dp)
                        .height(TRACK_HEIGHT)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }

            playing.forEach { app ->
                key(app.packageName) {
                    VerticalAppSlider(app, onChange)
                }
            }
        }
    }
}