package moe.chensi.volume.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All user-tunable behavior in one place. Defaults equal the original hardcoded values,
 * so nothing changes until the user touches a setting.
 */
class AppSettings(private val prefs: SharedPreferences) {

    inner class IntSetting(
        private val key: String,
        val default: Int,
        val min: Int,
        val max: Int,
        val step: Int = 1,
    ) {
        private val _flow = MutableStateFlow(prefs.getInt(key, default).coerceIn(min, max))
        val flow: StateFlow<Int> = _flow.asStateFlow()
        val value: Int get() = _flow.value

        fun set(v: Int) {
            val c = v.coerceIn(min, max)
            if (c == _flow.value) return
            _flow.value = c
            prefs.edit { putInt(key, c) }
        }

        fun reset() = set(default)
    }

    inner class BoolSetting(private val key: String, val default: Boolean) {
        private val _flow = MutableStateFlow(prefs.getBoolean(key, default))
        val flow: StateFlow<Boolean> = _flow.asStateFlow()
        val value: Boolean get() = _flow.value

        fun set(v: Boolean) {
            if (v == _flow.value) return
            _flow.value = v
            prefs.edit { putBoolean(key, v) }
        }

        fun reset() = set(default)
    }

    // ---- Popup panel ----
    /** Milliseconds before the panel hides itself (1-5 s, ms precision). */
    val panelTimeoutMs = IntSetting("panel_timeout_ms", 5000, 1000, 5000)
    /** Fade in/out duration. 0 = instant. */
    val panelAnimationMs = IntSetting("panel_animation_ms", 300, 0, 800, 50)
    /** UI scale of the panel content in percent. */
    val panelScalePct = IntSetting("panel_scale_pct", 100, 60, 160, 5)
    /** Panel width as % of screen width. 0 = fit content (original). */
    val panelWidthPct = IntSetting("panel_width_pct", 0, 0, 100, 5)
    /** Max panel height as % of screen height. 0 = no limit (original). */
    val panelMaxHeightPct = IntSetting("panel_max_height_pct", 0, 0, 100, 5)
    /** 0 = left, 1 = center, 2 = right */
    val panelHorizontalAnchor = IntSetting("panel_h_anchor", 1, 0, 2)
    /** 0 = top, 1 = center, 2 = bottom */
    val panelVerticalAnchor = IntSetting("panel_v_anchor", 1, 0, 2)
    /** Offset from the anchor in dp (positive = right / down). */
    val panelOffsetXDp = IntSetting("panel_offset_x_dp", 0, -300, 300, 4)
    val panelOffsetYDp = IntSetting("panel_offset_y_dp", 0, -600, 600, 4)
    /** Background tint opacity in percent (original: 30). */
    val panelBackgroundPct = IntSetting("panel_background_pct", 30, 0, 100, 5)
    val panelBlur = BoolSetting("panel_blur", true)
    /** 0 = compact row of vertical sliders, 1 = full list (original) */
    val panelLayout = IntSetting("panel_layout", 0, 0, 1)
    /** Seconds an app keeps its slider after it stops playing. */
    val panelPlayingGraceSec = IntSetting("panel_playing_grace_sec", 10, 0, 30)
    val panelHideOnOutsideTouch = BoolSetting("panel_hide_on_outside_touch", true)

    // ---- Volume buttons ----
    /** Pass the keys to the system while any camera is in use or a camera app is in front. */
    val keysIgnoreCamera = BoolSetting("keys_ignore_camera", true)
    /** Pass the keys to the system while the screen is off. */
    val keysIgnoreScreenOff = BoolSetting("keys_ignore_screen_off", false)
    /** Pass the keys to the system on the lock screen. */
    val keysIgnoreLockScreen = BoolSetting("keys_ignore_lock_screen", false)
    /** Pass the keys to the system during calls. */
    val keysIgnoreInCall = BoolSetting("keys_ignore_in_call", false)
    /** If false (original) the first press only opens the panel. */
    val keysFirstPressAdjusts = BoolSetting("keys_first_press_adjusts", false)
    /** If true, keys always change media volume instead of the "suggested" stream. */
    val keysAlwaysMedia = BoolSetting("keys_always_media", false)
    val keysRepeatInitialDelayMs = IntSetting("keys_repeat_initial_ms", 500, 150, 1500, 50)
    val keysRepeatIntervalMs = IntSetting("keys_repeat_interval_ms", 100, 30, 400, 10)

    // ---- Floating button ----
    /** Opacity of the floating button when tucked against the edge (original: 50). */
    val floatingCollapsedOpacityPct = IntSetting("floating_collapsed_opacity_pct", 50, 10, 100, 5)

    val all: List<Any> = listOf(
        panelTimeoutMs, panelAnimationMs, panelScalePct, panelWidthPct, panelMaxHeightPct,
        panelHorizontalAnchor, panelVerticalAnchor, panelOffsetXDp, panelOffsetYDp,
        panelBackgroundPct, panelBlur, panelLayout, panelPlayingGraceSec, panelHideOnOutsideTouch,
        keysIgnoreCamera, keysIgnoreScreenOff, keysIgnoreLockScreen, keysIgnoreInCall, keysFirstPressAdjusts,
        keysAlwaysMedia, keysRepeatInitialDelayMs, keysRepeatIntervalMs,
        floatingCollapsedOpacityPct,
    )

    fun resetAll() {
        all.forEach {
            when (it) {
                is IntSetting -> it.reset()
                is BoolSetting -> it.reset()
            }
        }
    }
}
