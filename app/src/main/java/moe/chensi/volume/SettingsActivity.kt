package moe.chensi.volume

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import moe.chensi.volume.data.AppSettings
import moe.chensi.volume.ui.theme.VolumeManagerTheme
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {

    private fun showPreview() {
        sendBroadcast(
            Intent(Service.ACTION_SHOW_VIEW)
                .setPackage(packageName)
                .putExtra(Service.EXTRA_PREVIEW, true)
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyApplication
        val s = app.settings

        setContent {
            var confirmReset by remember { mutableStateOf(false) }

            // Live preview: every change to a panel setting pops the panel up (touch-through)
            LaunchedEffect(Unit) {
                merge(
                    s.panelScalePct.flow, s.panelWidthPct.flow, s.panelMaxHeightPct.flow,
                    s.panelHorizontalAnchor.flow, s.panelVerticalAnchor.flow,
                    s.panelOffsetXDp.flow, s.panelOffsetYDp.flow, s.panelBackgroundPct.flow,
                    s.panelTimeoutSec.flow,
                ).drop(9) // skip the initial value of each StateFlow
                    .collect { showPreview() }
            }

            VolumeManagerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.settings_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.settings_back)
                                    )
                                }
                            },
                            actions = {
                                TextButton(onClick = { confirmReset = true }) {
                                    Text(stringResource(R.string.settings_reset_all))
                                }
                            }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            FilledTonalButton(
                                onClick = ::showPreview,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                                Text(
                                    stringResource(R.string.settings_preview),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Text(
                                stringResource(R.string.settings_preview_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // ---------- Panel ----------
                        item { Section(stringResource(R.string.settings_section_panel)) }
                        item {
                            IntSlider(stringResource(R.string.settings_panel_timeout), s.panelTimeoutSec) { "$it s" }
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_panel_animation), s.panelAnimationMs) {
                                if (it == 0) stringResource(R.string.settings_off) else "$it ms"
                            }
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_panel_scale), s.panelScalePct) { "$it %" }
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_panel_width), s.panelWidthPct) {
                                if (it == 0) stringResource(R.string.settings_auto) else "$it %"
                            }
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_panel_max_height), s.panelMaxHeightPct) {
                                if (it == 0) stringResource(R.string.settings_unlimited) else "$it %"
                            }
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_panel_background), s.panelBackgroundPct) { "$it %" }
                        }
                        item { BoolRow(stringResource(R.string.settings_panel_blur), null, s.panelBlur) }
                        item {
                            BoolRow(
                                stringResource(R.string.settings_panel_outside_touch),
                                null, s.panelHideOnOutsideTouch
                            )
                        }

                        // ---------- Position ----------
                        item { Section(stringResource(R.string.settings_section_position)) }
                        item {
                            Choice(
                                stringResource(R.string.settings_position_horizontal),
                                listOf(
                                    stringResource(R.string.settings_left),
                                    stringResource(R.string.settings_center),
                                    stringResource(R.string.settings_right)
                                ),
                                s.panelHorizontalAnchor
                            )
                        }
                        item {
                            Choice(
                                stringResource(R.string.settings_position_vertical),
                                listOf(
                                    stringResource(R.string.settings_top),
                                    stringResource(R.string.settings_center),
                                    stringResource(R.string.settings_bottom)
                                ),
                                s.panelVerticalAnchor
                            )
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_offset_x), s.panelOffsetXDp) { signedDp(it) }
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_offset_y), s.panelOffsetYDp) { signedDp(it) }
                        }

                        // ---------- Volume keys ----------
                        item { Section(stringResource(R.string.settings_section_keys)) }
                        item {
                            val hooks by app.systemVolumeButtonHooksEnabled.collectAsState()
                            SwitchRow(
                                stringResource(R.string.system_volume_button_hooks_toggle),
                                stringResource(R.string.settings_keys_hooks_desc),
                                hooks
                            ) { app.setSystemVolumeButtonHooksEnabled(it) }
                        }
                        item {
                            BoolRow(
                                stringResource(R.string.settings_keys_first_press),
                                stringResource(R.string.settings_keys_first_press_desc),
                                s.keysFirstPressAdjusts
                            )
                        }
                        item {
                            BoolRow(
                                stringResource(R.string.settings_keys_always_media),
                                stringResource(R.string.settings_keys_always_media_desc),
                                s.keysAlwaysMedia
                            )
                        }
                        item {
                            BoolRow(
                                stringResource(R.string.settings_keys_ignore_screen_off),
                                null, s.keysIgnoreScreenOff
                            )
                        }
                        item {
                            BoolRow(
                                stringResource(R.string.settings_keys_ignore_lock_screen),
                                null, s.keysIgnoreLockScreen
                            )
                        }
                        item {
                            BoolRow(
                                stringResource(R.string.settings_keys_ignore_call),
                                null, s.keysIgnoreInCall
                            )
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_keys_repeat_delay), s.keysRepeatInitialDelayMs) { "$it ms" }
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_keys_repeat_interval), s.keysRepeatIntervalMs) { "$it ms" }
                        }

                        // ---------- Floating button ----------
                        item { Section(stringResource(R.string.settings_section_floating)) }
                        item {
                            val enabled by app.floatingButtonEnabled.collectAsState()
                            SwitchRow(
                                stringResource(R.string.floating_button_toggle), null, enabled
                            ) { app.setFloatingButtonEnabled(it) }
                        }
                        item {
                            val size by app.floatingButtonSize.collectAsState()
                            SliderRow(
                                label = stringResource(R.string.settings_floating_size),
                                valueText = "$size dp",
                                value = size,
                                min = MyApplication.MIN_FLOATING_BUTTON_SIZE_DP,
                                max = MyApplication.MAX_FLOATING_BUTTON_SIZE_DP,
                                step = MyApplication.FLOATING_BUTTON_SIZE_STEP_DP,
                                isDefault = size == MyApplication.DEFAULT_FLOATING_BUTTON_SIZE_DP,
                                onReset = { app.setFloatingButtonSize(MyApplication.DEFAULT_FLOATING_BUTTON_SIZE_DP) },
                                onChange = { app.setFloatingButtonSize(it) }
                            )
                        }
                        item {
                            IntSlider(stringResource(R.string.settings_floating_opacity), s.floatingCollapsedOpacityPct) { "$it %" }
                        }
                    }
                }

                if (confirmReset) {
                    AlertDialog(
                        onDismissRequest = { confirmReset = false },
                        title = { Text(stringResource(R.string.settings_reset_all)) },
                        text = { Text(stringResource(R.string.settings_reset_confirm)) },
                        confirmButton = {
                            Button(onClick = {
                                s.resetAll()
                                confirmReset = false
                            }) { Text(stringResource(R.string.settings_reset_all)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmReset = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun signedDp(v: Int) = if (v > 0) "+$v dp" else "$v dp"

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun IntSlider(label: String, setting: AppSettings.IntSetting, format: @Composable (Int) -> String) {
    val value by setting.flow.collectAsState()
    SliderRow(
        label = label,
        valueText = format(value),
        value = value,
        min = setting.min,
        max = setting.max,
        step = setting.step,
        isDefault = value == setting.default,
        onReset = setting::reset,
        onChange = setting::set
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    isDefault: Boolean,
    onReset: () -> Unit,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onReset, enabled = !isDefault) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.settings_reset),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Continuous slider snapped to `step` (avoids hundreds of tick marks on wide ranges)
        Slider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (((raw - min) / step).roundToInt() * step + min).coerceIn(min, max)
                if (snapped != value) onChange(snapped)
            },
            valueRange = min.toFloat()..max.toFloat()
        )
    }
}

@Composable
private fun BoolRow(label: String, description: String?, setting: AppSettings.BoolSetting) {
    val value by setting.flow.collectAsState()
    SwitchRow(label, description, value, setting::set)
}

@Composable
private fun SwitchRow(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(label: String, options: List<String>, setting: AppSettings.IntSetting) {
    val value by setting.flow.collectAsState()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, text ->
                FilterChip(
                    selected = value == i,
                    onClick = { setting.set(i) },
                    label = { Text(text) }
                )
            }
        }
    }
}
